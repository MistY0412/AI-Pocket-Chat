package com.situ.aichat.moments

import android.content.Context
import android.util.Log
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.ApiFunction
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.model.MomentNotificationType
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.prompt.PromptStrings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * 被提醒者兑现器（朋友圈发布页重构·甲 §3.3.2·J-1 / J-3 / J-4 / J-5）：
 * - 分流：此刻来不了的（见面中 / 睡着·判定只经 [MomentMentionAvailability]）入待互动队列，醒后 / 散场后由
 *   [MomentPendingDrain] 兑现；
 * - 恢复场景 D（[MomentRecoveryService]）：算「醒着、还欠评论、也不在队列里」的被提醒者并补兑现——必赞必评、
 *   直接评帖不接话（J-2）、等槽不跳过（J-3）、失败入队下次再试（J-4）。
 */
@Singleton
class MomentMentionInteractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val momentRepo: MomentRepository,
    private val characterRepo: CharacterRepository,
    private val apiConfigRepo: ApiConfigRepository,
    private val settingsRepo: SettingsRepository,
    private val availability: MomentMentionAvailability,
    private val generator: MomentCommentGenerator,
    private val notifications: MomentInteractionNotifications,
    private val llmSlot: MomentLlmSlot,
) {

    /** 逐个判可用性：非 [MentionAvailability.AVAILABLE] 的入队；返回此刻能来的（保持入参顺序）。 */
    suspend fun deferUnavailable(
        post: MomentPostEntity,
        mentioned: List<CharacterEntity>,
        scheduleSystemEnabled: Boolean,
        nowMillis: Long,
        zone: ZoneId,
    ): List<CharacterEntity> {
        val awake = mutableListOf<CharacterEntity>()
        for (c in mentioned) {
            if (availability.of(c.uuid, scheduleSystemEnabled, nowMillis, zone) == MentionAvailability.AVAILABLE) {
                awake.add(c)
            } else {
                defer(post, c.uuid, nowMillis)
            }
        }
        return awake
    }

    /** 入待互动队列（store 按 (帖, 角色) 自带去重）。 */
    fun defer(post: MomentPostEntity, characterUuid: String, nowMillis: Long) {
        MomentPendingInteractionStore.add(context, post.uuid, post.timestamp, post.characterUuid, characterUuid, nowMillis)
    }

    /**
     * 恢复场景 D 用：该帖还欠评论、且不在待互动队列里的被提醒者——此刻来不了的顺手入队，返回醒着能来的。
     */
    suspend fun takeAwakeOwed(post: MomentPostEntity, nowMillis: Long, zone: ZoneId): List<CharacterEntity> {
        val commented = momentRepo.commentsForPost(post.uuid)
            .filter { it.authorTypeRaw == MomentAuthorType.CHARACTER.raw }
            .mapNotNull { it.characterUuid }
            .toSet()
        val queued = MomentPendingInteractionStore.load(context)
            .filter { it.postUuid == post.uuid }
            .mapTo(HashSet()) { it.characterUuid }
        val owed = MomentMentionRules.owedMentions(post, characterRepo.getAll(), commented).filter { it.uuid !in queued }
        if (owed.isEmpty()) return emptyList()
        return deferUnavailable(post, owed, settingsRepo.getAppSettings().scheduleSystemEnabled, nowMillis, zone)
    }

    /**
     * 恢复场景 D 的兑现：逐个必赞必评（不看自动点赞开关 / 评论名额）；等槽约 60 秒仍拿不到、生成为空或出错 → 入队。
     * 落评论前查「已评过」防与 drain / 延迟链重复。
     */
    suspend fun settle(postUuid: String, awake: List<CharacterEntity>, zone: ZoneId) {
        val post = momentRepo.getPost(postUuid) ?: return
        if (post.isSoftDeleted) return
        val config = apiConfigRepo.resolveConfigValues(ApiFunction.MOMENT_GENERATION)
        if (config == null) {
            val now = System.currentTimeMillis()
            awake.forEach { defer(post, it.uuid, now) }
            return
        }
        val settings = settingsRepo.getAppSettings()
        val userNickname = generator.userNickname()
        val strings = MomentCommentPromptStrings.from(PromptStrings(context))
        val names = characterRepo.getAll().associate { it.uuid to it.name }

        awake.forEachIndexed { index, c ->
            val now = System.currentTimeMillis()
            if (momentRepo.commentCountByCharacter(post.uuid, c.uuid) > 0) return@forEachIndexed
            if (!momentRepo.hasLiked(post.uuid, c.uuid)) {
                momentRepo.addLike(post.uuid, MomentAuthorType.CHARACTER, c.uuid)
                notifications.create(MomentNotificationType.LIKE_ON_USER_POST, c.uuid, post, nowMillis = now)
            }
            if (!llmSlot.acquireWaiting(MENTION_SLOT_ATTEMPTS, MENTION_SLOT_POLL_MS)) {
                defer(post, c.uuid, now)
                return@forEachIndexed
            }
            try {
                val content = generator.generate(
                    character = c,
                    post = post,
                    replyTarget = null,
                    userNickname = userNickname,
                    characterNames = names,
                    config = config,
                    strings = strings,
                    scheduleSystemEnabled = settings.scheduleSystemEnabled,
                    nowMillis = now,
                    zone = zone,
                    mentioned = true,
                )
                if (!content.isNullOrBlank()) {
                    momentRepo.addComment(post.uuid, content, MomentAuthorType.CHARACTER, c.uuid)
                    notifications.create(MomentNotificationType.COMMENT_ON_USER_POST, c.uuid, post, content, now)
                } else {
                    defer(post, c.uuid, now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "被提醒者评论生成失败 post=${post.uuid.take(6)}，入队下次再试", e)
                defer(post, c.uuid, now)
            } finally {
                llmSlot.release()
            }
            if (index < awake.lastIndex) {
                delay(Random.nextLong(MomentPendingDrain.PENDING_DELAY_MIN_MS, MomentPendingDrain.PENDING_DELAY_MAX_MS + 1))
            }
        }
    }

    companion object {
        /** 被提醒者等 LLM 槽：共试 31 次、每次间隔 2 秒（约 60 秒·J-3·锁定）。 */
        internal const val MENTION_SLOT_ATTEMPTS = 31
        internal const val MENTION_SLOT_POLL_MS = 2_000L
        private const val TAG = "MomentMention"
    }
}
