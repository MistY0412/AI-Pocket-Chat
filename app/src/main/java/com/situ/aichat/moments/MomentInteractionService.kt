package com.situ.aichat.moments

import android.content.Context
import android.util.Log
import com.situ.aichat.R
import com.situ.aichat.data.local.dao.ConversationDao
import com.situ.aichat.data.local.dao.MessageDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentCommentEntity
import com.situ.aichat.data.model.ApiFunction
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.model.MomentNotificationType
import com.situ.aichat.data.model.dynamicInterests
import com.situ.aichat.data.model.mentionedCharacterUuids
import com.situ.aichat.data.model.relationshipQuality
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.offline.OfflineMeetingGate
import com.situ.aichat.prompt.PromptStrings
import com.situ.aichat.prompt.schedule.CharacterSleepChecker
import com.situ.aichat.util.DateFormatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * 朋友圈 AI 互动引擎（M06 7.2.4b）。1:1 移植 iOS `MomentGenerationActor+Interactions`
 * （`autoInteractWithPost` ~21-306 / `generateReplyToComment` ~312 / 互动通知 ~405 /
 * `processPendingInteractions` ~437）。M06 最复杂的异步块——spec §4 韧性头号风险。
 *
 * 职责：对一条动态按相关性评分决定哪些角色点赞 / 评论，AI 还会互相接话；并在用户评论后延迟回复。
 * 调度（延迟 30~120s 后触发本类、用户操作后触发）由 [MomentDelayedTaskRegistry] 管理；并发 LLM 调用
 * 由全局 [MomentLlmSlot]（上限 2）限流。跨协程一律用 uuid 重查、绝不传 Room 对象跨线程（spec §4.3）。
 *
 * **韧性不变量**：所有延迟产物（评论 / 点赞 / 回复）都必须能被前台恢复（7.2.5）补偿重建——被 HyperOS
 * 杀后台后，本类的延迟循环会丢失，靠恢复重跑。睡眠角色不丢，入 [MomentPendingInteractionStore] 待醒后补。
 *
 * **安卓有意偏差**：评论 vision 暂走「盲图」分支——安卓 LLM 客户端 [ChatMessageDto] 当前纯文本（多模态
 * 后续才接），故 `visionEnabled=false`，提示词告知「有 N 张图但看不到」；多模态落地后翻开即真 vision。
 */
@Singleton
class MomentInteractionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val momentRepo: MomentRepository,
    private val characterRepo: CharacterRepository,
    private val apiConfigRepo: ApiConfigRepository,
    private val settingsRepo: SettingsRepository,
    private val sleepChecker: CharacterSleepChecker,
    private val messageDao: MessageDao,
    private val llmSlot: MomentLlmSlot,
    private val conversationDao: ConversationDao,
    private val commentGenerator: MomentCommentGenerator,
    private val notifications: MomentInteractionNotifications,
    private val pendingDrain: MomentPendingDrain,
    private val mentionInteractor: MomentMentionInteractor,
) {

    /**
     * 对某条动态自动互动：评分 → 点赞（同步）→ 评论（活跃度打散时间 + 逐条延迟 + 三重校验）。
     * 1:1 iOS `autoInteractWithPost`。跨协程安全的业务键 [postUuid] 真实查库一次；查不到 / 软删 → 静默返回
     * （绝不触碰僵尸对象，M-21 教训）。被取消（删帖）时 `delay`/suspend 调用抛 [CancellationException] 退出。
     */
    suspend fun autoInteractWithPost(
        postUuid: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        // 帖子存活校验（过滤软删，避免对已删帖产生互动）。
        val post = momentRepo.getPost(postUuid) ?: return
        if (post.isSoftDeleted) return
        // API 守卫。
        val config = apiConfigRepo.resolveConfigValues(ApiFunction.MOMENT_GENERATION) ?: return
        val settings = settingsRepo.getAppSettings()

        val allCharacters = characterRepo.getAll()
        if (allCharacters.isEmpty()) return

        // 候选 = 全角色 排帖子作者 + 排已评论角色（Recovery 路径可能在已有 AI 评论的帖上重跑）。
        val existingAICommentUuids = momentRepo.commentsForPost(postUuid)
            .filter { it.authorTypeRaw == MomentAuthorType.CHARACTER.raw }
            .mapNotNull { it.characterUuid }
            .toSet()
        // 被提醒者（朋友圈发布页重构·甲 §3.3·J-1）：帖上 @ 的、仍在、未评论的——不进打分抽签，走保底（必赞必评·不占评论名额）。
        val mentioned = MomentMentionRules.owedMentions(post, allCharacters, existingAICommentUuids)
        val mentionedUuids = mentioned.mapTo(HashSet()) { it.uuid }
        val candidates = allCharacters.filter {
            it.uuid != post.characterUuid && it.uuid !in existingAICommentUuids && it.uuid !in mentionedUuids
        }
        if (candidates.isEmpty() && mentioned.isEmpty()) return

        // 见面门（卷一 B1·置于睡眠分流前，日程开/关两分支都覆盖）：见面中的角色本轮不互动、不入待互动队列。
        val availableCandidates = candidates.filterNot { OfflineMeetingGate.characterInMeeting(conversationDao, it.uuid) }
        // 被提醒者分流（拍板③）：见面中 / 睡着 → 入待互动队列（醒后 / 见面完由 drain 兑现）；其余本轮兑现。
        val awakeMentioned = mentionInteractor.deferUnavailable(post, mentioned, settings.scheduleSystemEnabled, nowMillis, zone)
        if (availableCandidates.isEmpty() && awakeMentioned.isEmpty()) return

        // 睡着的角色入待互动队列（仅日程系统开启时），醒后由前台恢复补处理（7.2.5）。
        val awakeCandidates: List<CharacterEntity> = if (settings.scheduleSystemEnabled) {
            val awake = mutableListOf<CharacterEntity>()
            for (candidate in availableCandidates) {
                if (sleepChecker.isSleeping(candidate.uuid, scheduleSystemEnabled = true, nowMillis, zone)) {
                    MomentPendingInteractionStore.add(
                        context = context,
                        postUuid = post.uuid,
                        postTimestampMillis = post.timestamp,
                        postAuthorUuid = post.characterUuid,
                        characterUuid = candidate.uuid,
                        nowMillis = nowMillis,
                    )
                } else {
                    awake.add(candidate)
                }
            }
            awake
        } else {
            availableCandidates
        }
        if (awakeCandidates.isEmpty() && awakeMentioned.isEmpty()) return

        // 相关性打分（关系 0.4 + 兴趣 0.35 + 活跃 0.25），复用 7.2.2 纯算法评分器。
        val candidateScores = (awakeCandidates + awakeMentioned).map { character ->
            MomentRelevanceScorer.CandidateScore(
                characterUuid = character.uuid,
                score = MomentRelevanceScorer.score(
                    relationship = character.relationshipQuality,
                    initialInterests = character.initialInterests,
                    dynamicInterests = character.dynamicInterests,
                    recentMessageCount = recentMessageCount(character.uuid, nowMillis),
                    postContent = post.content,
                ),
            )
        }

        // 剩余评论预算 = max(0, 上限 - 已有 AI 评论数)；Recovery 已补过 / 用户调小上限时自动收敛。
        // 被提醒者不占名额（甲 J-1）：只扣「没被提醒的已评论者」。
        val nonMentionedCommenters = existingAICommentUuids.count { it !in post.mentionedCharacterUuids }
        val remainingCommentBudget = maxOf(0, settings.momentAutoCommentFrequency - nonMentionedCommenters)
        val selectionConfig = MomentRelevanceScorer.SelectionConfig.withUserSettings(
            likeUpperBound = LIKE_UPPER_BOUND,
            commentUpperBound = remainingCommentBudget,
            autoLikeEnabled = settings.momentAutoLikeEnabled,
        )
        val selection = MomentRelevanceScorer.selectInteractions(
            candidates = candidateScores.filter { it.characterUuid !in mentionedUuids },
            config = selectionConfig,
            random = { Random.nextDouble() },
        )
        val characterByUuid = (awakeCandidates + awakeMentioned).associateBy { it.uuid }
        Log.d(
            TAG,
            "朋友圈互动筛选 post=${postUuid.take(6)} 候选=${awakeCandidates.size} → " +
                "点赞=${selection.likeUuids.size} 评论=${selection.commentUuids.size}" + " 保底=${awakeMentioned.size}",
        )

        // 自动点赞（selection 已保证「评论者必点赞」不变量 commentUuids ⊆ likeUuids）。
        val postByUser = post.authorTypeRaw == MomentAuthorType.USER.raw
        for (uuid in selection.likeUuids + awakeMentioned.map { it.uuid }) {
            if (uuid !in characterByUuid) continue
            if (momentRepo.hasLiked(postUuid, uuid)) continue
            momentRepo.addLike(postUuid, MomentAuthorType.CHARACTER, uuid)
            if (postByUser) {
                notifications.create(MomentNotificationType.LIKE_ON_USER_POST, uuid, post, nowMillis = nowMillis)
            } else if (momentRepo.hasUserLike(postUuid)) {
                notifications.create(MomentNotificationType.CO_LIKE, uuid, post, nowMillis = nowMillis)
            }
        }

        // 自动评论（按活跃度打散时间，整体窗口约 1-15 分钟）。
        val commentUuids = selection.commentUuids + awakeMentioned.map { it.uuid }
        if (commentUuids.isEmpty()) return
        val userNickname = commentGenerator.userNickname()
        val strings = MomentCommentPromptStrings.from(PromptStrings(context))
        val characterNames = allCharacters.associate { it.uuid to it.name }
        val scoresByUuid = candidateScores.associate { it.characterUuid to it.score }

        // 为每个评论候选预算延迟（活跃越高越快），按延迟升序（先到先评）。
        val slots = commentUuids.map { uuid ->
            val activity = scoresByUuid[uuid]?.activity ?: 0.0
            val delaySeconds = MomentRelevanceScorer.interactionDelaySeconds(
                activityScore = activity,
                randomJitter = Random.nextDouble(),
            )
            CommentSlot(uuid = uuid, delayMs = (delaySeconds * 1000).toLong())
        }.sortedBy { it.delayMs }

        val startTime = System.currentTimeMillis()
        for (slot in slots) {
            // sleep 到目标时间点（相对进入评论阶段的起点）；取消时 delay 抛 CancellationException 退出（=iOS break）。
            val remaining = slot.delayMs - (System.currentTimeMillis() - startTime)
            if (remaining > 0) delay(remaining)

            // 中途校验：帖子已删 → 中止评论链（取消由 suspend 调用自身处理）。
            val livePost = momentRepo.getPost(postUuid)
            if (livePost == null || livePost.isSoftDeleted) break

            if (slot.uuid !in characterByUuid) continue
            val character = characterByUuid.getValue(slot.uuid)
            if (momentRepo.commentCountByCharacter(postUuid, character.uuid) > 0) continue
            val isMentioned = slot.uuid in mentionedUuids

            // 本条评论的生成时刻：评论链 1-15min，用 fresh now 让时间/日程上下文与 iOS 每条 fresh Date() 一致
            // （否则跨时段的链尾会沿用入口时刻的时段/「正在做的事」）。
            val genNow = System.currentTimeMillis()
            // AI 回 AI 决策：sleep 完成后做，此时前面 slot 产生的评论已落库可见（nil = 直接评帖子）。
            // 被提醒者直接评帖、不接别人的话（甲 J-2）。
            val replyResolution = if (isMentioned) null else selectReplyTargetForSlot(postUuid, character.uuid, characterNames)
            val replyTargetForLlm = replyResolution?.let { res ->
                refetchReplyTargetIfAlive(res.commentUuid)?.let { target ->
                    CommentReplyTarget(
                        timeDescription = DateFormatters.momentTimeDescription(target.timestamp, genNow, zone),
                        authorName = res.authorName,
                        content = target.content,
                    )
                }
            }

            // LLM 调用前取槽，满则跳过该条评论（不阻塞整个方法）；finally 保证释放（=iOS defer）。
            // 被提醒者不跳过：等槽约 60 秒，仍满则入待互动队列（甲 J-3）。
            val gotSlot = llmSlot.tryAcquire() ||
                (isMentioned && llmSlot.acquireWaiting(MomentMentionInteractor.MENTION_SLOT_ATTEMPTS, MomentMentionInteractor.MENTION_SLOT_POLL_MS))
            if (!gotSlot) {
                if (isMentioned) mentionInteractor.defer(post, character.uuid, genNow)
                continue
            }
            try {
                val commentContent = commentGenerator.generate(
                    character = character,
                    post = post,
                    replyTarget = replyTargetForLlm,
                    userNickname = userNickname,
                    characterNames = characterNames,
                    config = config,
                    strings = strings,
                    scheduleSystemEnabled = settings.scheduleSystemEnabled,
                    nowMillis = genNow,
                    zone = zone,
                    mentioned = isMentioned,
                )
                if (!commentContent.isNullOrBlank()) {
                    // 落库前再校验目标存活（cascade 下 parent 被删会连带新评论消失，宁可降级为普通评论）。
                    val finalTarget = replyResolution?.let { refetchReplyTargetIfAlive(it.commentUuid) }
                    val finalReplyToName = if (finalTarget != null) replyResolution.authorName else null
                    // 每条评论 Room insert 即时提交（=iOS 每条独立 save，防中途被杀丢前面的）。
                    momentRepo.addComment(
                        postUuid = postUuid,
                        content = commentContent,
                        authorType = MomentAuthorType.CHARACTER,
                        characterUuid = character.uuid,
                        replyToName = finalReplyToName,
                        parentCommentUuid = finalTarget?.uuid,
                    )
                    // 互动通知：角色评论了用户的帖子（AI 回 AI 时 preview 加「回复 XX：」前缀，避免失去上下文）。
                    if (postByUser) {
                        val preview = if (finalReplyToName != null) {
                            context.getString(R.string.moment_notif_comment_reply_prefix, finalReplyToName, commentContent)
                        } else {
                            commentContent
                        }
                        notifications.create(MomentNotificationType.COMMENT_ON_USER_POST, character.uuid, post, preview, genNow)
                    }
                    // 评论者自动点赞（冗余保障；selection 在 autoLike=true 时已加入，保留防 selection 路径变更）。
                    if (settings.momentAutoLikeEnabled && !momentRepo.hasLiked(postUuid, character.uuid)) {
                        momentRepo.addLike(postUuid, MomentAuthorType.CHARACTER, character.uuid)
                        if (postByUser) {
                            notifications.create(MomentNotificationType.LIKE_ON_USER_POST, character.uuid, post, nowMillis = genNow)
                        } else if (momentRepo.hasUserLike(postUuid)) {
                            notifications.create(MomentNotificationType.CO_LIKE, character.uuid, post, nowMillis = genNow)
                        }
                    }
                } else if (isMentioned) {
                    mentionInteractor.defer(post, character.uuid, genNow) // 被提醒者生成为空 → 入队下次再试（甲 J-4）
                }
            } catch (e: CancellationException) {
                throw e // 取消传播 → 退出协程（=iOS catch CancellationError break）
            } catch (e: Exception) {
                Log.w(TAG, "朋友圈评论生成失败 post=${postUuid.take(6)}，跳过该条", e)
                if (isMentioned) mentionInteractor.defer(post, character.uuid, genNow)
            } finally {
                llmSlot.release()
            }
        }
    }

    /**
     * AI 回复用户的评论（用户在详情页评论后延迟触发，1:1 iOS `generateReplyToComment`）。
     * 业务键真实查库；评论必须属于该帖（一致性校验）。回复角色：被回的是 AI 评论 → 该 AI；否则帖主是
     * AI → 帖主；都不是 → 随机一个角色。不取 LLM 槽（=iOS，仅 autoInteract 评论链限流）。
     */
    suspend fun generateReplyToComment(
        commentUuid: String,
        postUuid: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val userComment = momentRepo.getComment(commentUuid) ?: return
        val post = momentRepo.getPost(postUuid) ?: return
        if (post.isSoftDeleted) return
        // 一致性：评论必须属于该帖（防调用方传参错位，iOS userComment.post?.uuid == postUUID）。
        if (userComment.postUuid != postUuid) return
        val config = apiConfigRepo.resolveConfigValues(ApiFunction.MOMENT_GENERATION) ?: return

        val allCharacters = characterRepo.getAll()
        val parentComment = userComment.parentCommentUuid?.let { momentRepo.getComment(it) }
        val replyCharacterUuid: String? = when {
            parentComment != null && parentComment.authorTypeRaw == MomentAuthorType.CHARACTER.raw -> parentComment.characterUuid
            post.authorTypeRaw == MomentAuthorType.CHARACTER.raw -> post.characterUuid
            else -> allCharacters.randomOrNull()?.uuid
        }
        val character = replyCharacterUuid?.let { characterRepo.get(it) } ?: return

        val userNickname = commentGenerator.userNickname()
        val strings = MomentCommentPromptStrings.from(PromptStrings(context))
        val characterNames = allCharacters.associate { it.uuid to it.name }
        val scheduleSystemEnabled = settingsRepo.getAppSettings().scheduleSystemEnabled

        // 被回的是用户评论 → 用户昵称；是角色评论 → 该角色名（兜底「朋友」）。
        val userCommentByUser = userComment.authorTypeRaw == MomentAuthorType.USER.raw
        val replyTargetAuthorName = if (userCommentByUser) {
            userNickname
        } else {
            userComment.characterUuid?.let { characterNames[it] }?.takeIf { it.isNotEmpty() } ?: strings.friendFallback
        }
        val replyTarget = CommentReplyTarget(
            timeDescription = DateFormatters.momentTimeDescription(userComment.timestamp, nowMillis, zone),
            authorName = replyTargetAuthorName,
            content = userComment.content,
        )

        try {
            val content = commentGenerator.generate(
                character = character,
                post = post,
                replyTarget = replyTarget,
                userNickname = userNickname,
                characterNames = characterNames,
                config = config,
                strings = strings,
                scheduleSystemEnabled = scheduleSystemEnabled,
                nowMillis = nowMillis,
                zone = zone,
            ) ?: return
            momentRepo.addComment(
                postUuid = postUuid,
                content = content,
                authorType = MomentAuthorType.CHARACTER,
                characterUuid = character.uuid,
                replyToName = if (userCommentByUser) userNickname else null,
                parentCommentUuid = userComment.uuid,
            )
            // 互动通知：角色回复了用户的评论。
            if (userCommentByUser) {
                notifications.create(MomentNotificationType.REPLY_TO_USER_COMMENT, character.uuid, post, content, nowMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "朋友圈回复生成失败 post=${postUuid.take(6)} comment=${commentUuid.take(6)}", e)
        }
    }

    // ---- 待互动队列 drain（睡眠角色醒后补，7.2.5）----

    /** 处理待互动队列——委托 [MomentPendingDrain.drain]（朋友圈发布页重构·甲 chunk 1 只搬不改拆出·回前台调用）。 */
    suspend fun processPendingInteractions(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) = pendingDrain.drain(nowMillis, zone)

    // ---- 调度（延迟触发，经 [MomentDelayedTaskRegistry]）----

    /**
     * AI 自动发帖后排互动（发帖后 30~120s 触发，1:1 iOS `MomentGenerationActor.swift:128-147` 内联 Task）。
     * 由 [MomentGenerationService] 落帖后调用（b-3 接线）。注册表去重 + 删帖可取消 + 被杀后靠恢复重建。
     */
    fun scheduleGeneratedPostInteraction(postUuid: String) {
        MomentDelayedTaskRegistry.register(postUuid, MomentDelayedTaskRegistry.Purpose.AutoInteraction) {
            delay(Random.nextLong(GENERATED_INTERACTION_MIN_MS, GENERATED_INTERACTION_MAX_MS + 1))
            autoInteractWithPost(postUuid)
        }
    }

    /**
     * 用户发布动态后排 AI 互动（延迟 delayMinutes*60 + random(0..60)s，1:1 iOS `scheduleAIInteraction`）。
     * 供 7.2.7 发布 UI 调用（当前无调用方）。
     */
    fun scheduleAIInteraction(postUuid: String, delayMinutes: Int) {
        MomentDelayedTaskRegistry.register(postUuid, MomentDelayedTaskRegistry.Purpose.AutoInteraction) {
            delay(delayMinutes * 60_000L + Random.nextLong(0, USER_INTERACTION_JITTER_MS + 1))
            autoInteractWithPost(postUuid)
        }
    }

    /**
     * 用户评论后排 AI 回复（延迟 delayMinutes*60 + random(0..30)s，1:1 iOS `scheduleAIReply`）。
     * 供 7.2.8 详情 UI 调用（当前无调用方）。
     */
    fun scheduleAIReply(commentUuid: String, postUuid: String, delayMinutes: Int) {
        MomentDelayedTaskRegistry.register(postUuid, MomentDelayedTaskRegistry.Purpose.Reply(commentUuid)) {
            delay(delayMinutes * 60_000L + Random.nextLong(0, USER_REPLY_JITTER_MS + 1))
            generateReplyToComment(commentUuid, postUuid)
        }
    }

    /** 取消某帖的全部在途延迟互动任务（删帖时调，1:1 iOS `cancelPendingInteractions`）。供 7.2.7 删帖调用。 */
    fun cancelPendingInteractions(postUuid: String) {
        MomentDelayedTaskRegistry.cancelAll(postUuid)
    }

    // ---- AI 回 AI 评论目标决策 ----

    /**
     * 为某 slot 决策「回前面某条评论还是直接评帖子」。仅考虑顶级 character 评论（A 版 UI 最多 2 级缩进）。
     * 每次新鲜查库（看得到前面 slot 刚落的评论）。非 null = 回某条评论；null = 直接评帖子（1:1 iOS）。
     */
    private suspend fun selectReplyTargetForSlot(
        postUuid: String,
        speakerCharacterUuid: String,
        characterNames: Map<String, String>,
    ): ReplyTargetResolution? {
        val aiComments = momentRepo.commentsForPost(postUuid)
            .filter { it.authorTypeRaw == MomentAuthorType.CHARACTER.raw }
        // 分离顶级评论 vs 回复，并统计每条顶级评论已被回几次。
        val topLevel = mutableListOf<MomentCommentEntity>()
        val replyCountByParent = HashMap<String, Int>()
        for (c in aiComments) {
            val parentUuid = c.parentCommentUuid
            if (parentUuid != null) replyCountByParent[parentUuid] = (replyCountByParent[parentUuid] ?: 0) + 1
            else topLevel.add(c)
        }
        if (topLevel.isEmpty()) return null

        val candidates = topLevel.map { c ->
            MomentRelevanceScorer.ReplyCandidate(
                commentUuid = c.uuid,
                authorCharacterUuid = c.characterUuid ?: "",
                timestamp = c.timestamp,
                existingReplyCount = replyCountByParent[c.uuid] ?: 0,
            )
        }
        val targetUuid = MomentRelevanceScorer.selectReplyTarget(
            existingComments = candidates,
            currentCharacterUuid = speakerCharacterUuid,
            config = MomentRelevanceScorer.ReplyConfig(),
            random = { Random.nextDouble() },
        ) ?: return null

        // 拿作者名；查不到就放弃（降级为直接评帖子，比设 replyToName=「朋友」更干净）。
        val target = topLevel.firstOrNull { it.uuid == targetUuid } ?: return null
        val authorUuid = target.characterUuid ?: return null
        val name = characterNames[authorUuid]?.takeIf { it.isNotEmpty() } ?: return null
        return ReplyTargetResolution(commentUuid = targetUuid, authorName = name)
    }

    /** 落库前再查目标评论是否仍存在（LLM 生成期间用户可能删它，cascade 会连带回复消失）。null → 降级。 */
    private suspend fun refetchReplyTargetIfAlive(targetUuid: String): MomentCommentEntity? =
        momentRepo.getComment(targetUuid)

    // ---- 共用辅助 ----

    /** 某角色最近 7 天与用户的非 system 消息数（活跃度维度数据源，iOS `fetchRecentMessageCount`）。 */
    private suspend fun recentMessageCount(characterUuid: String, nowMillis: Long): Int =
        messageDao.countRecentNonSystemForCharacter(characterUuid, nowMillis - ACTIVITY_WINDOW_MS)

    /** 决策结果：要回复的评论 uuid + 该评论作者显示名（落库前会再 refetch 做存活校验）。 */
    private data class ReplyTargetResolution(val commentUuid: String, val authorName: String)

    /** 一个评论候选的延迟槽位（uuid + 相对评论阶段起点的延迟毫秒）。 */
    private data class CommentSlot(val uuid: String, val delayMs: Long)

    companion object {
        private const val TAG = "MomentInteract"

        /** 互动通知稳定 id（(类型,帖,角色) 复合）；P1-44 删角色撤已弹与发出处共用单源，改拼法必须同步。 */
        internal fun interactionNotificationId(type: MomentNotificationType, postUuid: String, characterUuid: String): Int =
            "moment:${type.raw}:$postUuid:$characterUuid".hashCode()

        /** 每帖 AI 点赞上限（硬编码，iOS `+Interactions.swift` likeUpperBound=5）。 */
        const val LIKE_UPPER_BOUND = 5

        /** AI 发帖后排互动延迟窗口 30~120s（iOS `Actor.swift:138` random(30...120)）。 */
        const val GENERATED_INTERACTION_MIN_MS = 30_000L
        const val GENERATED_INTERACTION_MAX_MS = 120_000L

        /** 用户发帖→互动的随机抖动上限 60s（iOS `+Interaction.swift:57` random(0...60)）。 */
        const val USER_INTERACTION_JITTER_MS = 60_000L

        /** 用户评论→回复的随机抖动上限 30s（iOS `+Interaction.swift:85` random(0...30)）。 */
        const val USER_REPLY_JITTER_MS = 30_000L

        /** 活跃度统计窗口：最近 7 天（iOS `fetchRecentMessageCount` 默认 days=7）。 */
        const val ACTIVITY_WINDOW_MS = 7L * 24 * 3600 * 1000
    }
}
