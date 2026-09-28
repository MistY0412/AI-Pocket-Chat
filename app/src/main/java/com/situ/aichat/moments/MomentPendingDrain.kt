package com.situ.aichat.moments

import android.content.Context
import android.util.Log
import com.situ.aichat.data.local.dao.ConversationDao
import com.situ.aichat.data.model.ApiFunction
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.model.MomentNotificationType
import com.situ.aichat.data.model.mentionedCharacterUuids
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.offline.OfflineMeetingGate
import com.situ.aichat.prompt.PromptStrings
import com.situ.aichat.prompt.schedule.CharacterSleepChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * 待互动队列 drain（睡眠 / 见面中的角色醒后 / 散场后补互动，7.2.5）。自 [MomentInteractionService] 只搬不改拆出
 * （朋友圈发布页重构·甲 chunk 1·图纸 docs/handoff/2026-09-27-朋友圈发布页-甲-底子.md §2.1 / §9 ④）；
 * 对外入口仍是 [MomentInteractionService.processPendingInteractions]（回前台由 AppViewModel 调）。
 */
@Singleton
class MomentPendingDrain @Inject constructor(
    @ApplicationContext private val context: Context,
    private val momentRepo: MomentRepository,
    private val characterRepo: CharacterRepository,
    private val apiConfigRepo: ApiConfigRepository,
    private val settingsRepo: SettingsRepository,
    private val sleepChecker: CharacterSleepChecker,
    private val conversationDao: ConversationDao,
    private val commentGenerator: MomentCommentGenerator,
    private val notifications: MomentInteractionNotifications,
) {
    /** drain 重入锁（乙 L-1）。 */
    private val running = AtomicBoolean(false)

    /**
     * 处理待互动队列（1:1 iOS `processPendingInteractions`）：睡着时入队的互动，醒后补点赞 + 评论。
     * 回前台调用。移除 24h 前入队的项；仍在睡的角色保留待下次；帖没了/已互动则丢弃；每项后 delay 5~20s。
     * **幂等**：靠 alreadyInteracted 去重，故中途被取消（未 save remaining）下次重跑也不会重复互动。
     * 沉浸模式跳过且**不清空队列**（退出沉浸后下次继续，P10 stub）。
     */
    suspend fun drain(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        // 重入锁（乙 L-1）：回前台一轮与前台 4 分钟循环重叠时，后来的直接返回。
        if (!running.compareAndSet(false, true)) return
        try {
            val queue = MomentPendingInteractionStore.load(context)
            if (queue.isEmpty()) return
            // 移除 24h 前入队的项（过期）。
            val fresh = queue.filter { it.queuedAtMillis >= nowMillis - DAY_MS }
            val config = apiConfigRepo.resolveConfigValues(ApiFunction.MOMENT_GENERATION)
            if (config == null) {
                MomentPendingInteractionStore.replaceKeepingNewcomers(context, queue, fresh)
                return
            }
            val settings = settingsRepo.getAppSettings()
            val userNickname = commentGenerator.userNickname()
            val strings = MomentCommentPromptStrings.from(PromptStrings(context))
            val characterNames = characterRepo.getAll().associate { it.uuid to it.name }

            val remaining = mutableListOf<MomentPendingInteractionStore.PendingInteraction>()
            for (item in fresh) {
                // 仍在睡 → 保留待下次（不消费）。
                if (sleepChecker.isSleeping(item.characterUuid, settings.scheduleSystemEnabled, nowMillis, zone)) {
                    remaining.add(item)
                    continue
                }
                // 见面中 → 同样保留待下次（卷一 B1·不消费）。
                if (OfflineMeetingGate.characterInMeeting(conversationDao, item.characterUuid)) {
                    remaining.add(item)
                    continue
                }
                val post = momentRepo.getPost(item.postUuid) ?: continue
                if (post.isSoftDeleted) continue
                val character = characterRepo.get(item.characterUuid) ?: continue
                // 被提醒者（甲 §3.3·J-1 / J-4）：只以「已评论」为兑现——赞过不算、评论名额 0 也照评、失败留队下次再试。
                val isMentioned = character.uuid in post.mentionedCharacterUuids
                val commented = momentRepo.commentCountByCharacter(post.uuid, character.uuid) > 0
                if (commented || (!isMentioned && momentRepo.hasLiked(post.uuid, character.uuid))) continue
                // 被提醒项已放弃（乙 L-2·复核 R1）：原样留队当「占位」，不再调 AI，随 24h 过期自然清掉——恢复场景 D 只补
                // 「不在队列里」的被提醒者，出队就会被它当成新欠账从 0 次重来，上限形同虚设。
                if (isMentioned && item.failedAttempts >= MENTION_MAX_ATTEMPTS) {
                    remaining.add(item)
                    continue
                }
                // 被提醒项重试间隔（乙 L-2）：上一次尝试不满 20 分钟 → 原样留队，本轮不碰。
                if (isMentioned && item.lastAttemptAtMillis > 0L && nowMillis - item.lastAttemptAtMillis < MENTION_RETRY_GAP_MS) {
                    remaining.add(item)
                    continue
                }

                val postByUser = post.authorTypeRaw == MomentAuthorType.USER.raw
                // 点赞（待互动总是点赞，不看 autoLike 设置=iOS）；被提醒者可能已赞过（上一轮赞了、评论失败留队）→ 不重复赞。
                if (!momentRepo.hasLiked(post.uuid, character.uuid)) {
                    momentRepo.addLike(post.uuid, MomentAuthorType.CHARACTER, character.uuid)
                    if (postByUser) {
                        notifications.create(MomentNotificationType.LIKE_ON_USER_POST, character.uuid, post, nowMillis = nowMillis)
                    } else if (momentRepo.hasUserLike(post.uuid)) {
                        notifications.create(MomentNotificationType.CO_LIKE, character.uuid, post, nowMillis = nowMillis)
                    }
                }
                // 评论（仅频率>0；被提醒者不看名额；replyTarget=null 不接话=iOS）。
                if (!isMentioned && settings.momentAutoCommentFrequency <= 0) continue
                val genNow = System.currentTimeMillis()
                try {
                    val content = commentGenerator.generate(
                        character = character,
                        post = post,
                        replyTarget = null,
                        userNickname = userNickname,
                        characterNames = characterNames,
                        config = config,
                        strings = strings,
                        scheduleSystemEnabled = settings.scheduleSystemEnabled,
                        nowMillis = genNow,
                        zone = zone,
                        mentioned = isMentioned,
                    )
                    if (!content.isNullOrBlank()) {
                        momentRepo.addComment(post.uuid, content, MomentAuthorType.CHARACTER, character.uuid)
                        if (postByUser) {
                            notifications.create(MomentNotificationType.COMMENT_ON_USER_POST, character.uuid, post, content, genNow)
                        }
                    } else if (isMentioned) {
                        remaining.keepFailedMention(item, nowMillis) // 被提醒者生成为空 → 留队下次再试（甲 J-4·乙 L-2 计次）
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "待互动评论生成失败 post=${post.uuid.take(6)}", e)
                    if (isMentioned) remaining.keepFailedMention(item, nowMillis)
                }
                delay(Random.nextLong(PENDING_DELAY_MIN_MS, PENDING_DELAY_MAX_MS + 1))
            }
            MomentPendingInteractionStore.replaceKeepingNewcomers(context, queue, remaining)
        } finally {
            running.set(false)
        }
    }

    /**
     * 被提醒项失败（乙 L-2）：记一次尝试、留队；累计满 [MENTION_MAX_ATTEMPTS] 次放弃——放弃后仍留队占位（见 drain 里的
     * 「已放弃」闸·复核 R1），不再调 AI，24h 过期时随队列清理出队。
     */
    private fun MutableList<MomentPendingInteractionStore.PendingInteraction>.keepFailedMention(
        item: MomentPendingInteractionStore.PendingInteraction,
        nowMillis: Long,
    ) {
        val retried = item.copy(failedAttempts = item.failedAttempts + 1, lastAttemptAtMillis = nowMillis)
        add(retried)
        if (retried.failedAttempts >= MENTION_MAX_ATTEMPTS) {
            Log.w(TAG, "被提醒项重试 ${retried.failedAttempts} 次仍失败，放弃 post=${item.postUuid.take(6)}")
        }
    }

    companion object {
        /** 日志 tag 沿用互动总管原值（只搬不改·日志字串一字不改）。 */
        private const val TAG = "MomentInteract"

        /** 待互动队列项过期窗口：24 小时（iOS `oneDayAgo` 清理）。 */
        internal const val DAY_MS = 24L * 3600 * 1000

        /** 待互动队列每项处理后随机延迟 5~20s（iOS `Task.sleep(5...20)`）。 */
        internal const val PENDING_DELAY_MIN_MS = 5_000L
        internal const val PENDING_DELAY_MAX_MS = 20_000L

        /** 被提醒项两次尝试的最小间隔：20 分钟（乙 L-2·锁定）。 */
        internal const val MENTION_RETRY_GAP_MS = 20L * 60 * 1000

        /** 被提醒项累计失败满这么多次放弃（乙 L-2·锁定；放弃 = 留队占位不再试·复核 R1）。 */
        internal const val MENTION_MAX_ATTEMPTS = 6
    }
}
