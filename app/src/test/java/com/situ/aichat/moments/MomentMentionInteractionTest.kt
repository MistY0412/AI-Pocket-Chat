package com.situ.aichat.moments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.R
import com.situ.aichat.data.local.dao.ConversationDao
import com.situ.aichat.data.local.dao.MessageDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentCommentEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.model.MomentNotificationType
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.prompt.schedule.CharacterSleepChecker
import com.situ.aichat.util.StringListJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-2 / T2-3 / T2-4（朋友圈发布页重构·甲 §7）：被提醒者的保底互动、待互动 drain 的提醒口径、恢复场景 D。
 * 真路径 = 真 [MomentInteractionService] / [MomentPendingDrain] / [MomentMentionInteractor] / [MomentRecoveryService]；
 * 依赖（仓库 / 评论生成器 / 通知 / 可用性）用 MockK 假掉，[MomentLlmSlot] 默认用真件。断言从图纸 §3.3 / §5 E1–E13 / E31 独立反推。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentMentionInteractionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L

    private lateinit var momentRepo: MomentRepository
    private lateinit var characterRepo: CharacterRepository
    private lateinit var apiConfigRepo: ApiConfigRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sleepChecker: CharacterSleepChecker
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private lateinit var generator: MomentCommentGenerator
    private lateinit var notifications: MomentInteractionNotifications
    private lateinit var availability: MomentMentionAvailability

    private fun ch(uuid: String, name: String) = CharacterEntity(uuid = uuid, name = name, creationDate = 0L)
    private val chars = listOf(ch("c1", "小雨"), ch("c2", "阿哲"), ch("c3", "林晚"), ch("c4", "小周"))

    @Before
    fun setUp() {
        momentRepo = mockk(relaxed = true)
        characterRepo = mockk(relaxed = true)
        coEvery { characterRepo.getAll() } returns chars
        chars.forEach { c -> coEvery { characterRepo.get(c.uuid) } returns c }
        apiConfigRepo = mockk(relaxed = true)
        coEvery { apiConfigRepo.resolveConfigValues(any()) } returns mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        settings()
        sleepChecker = mockk()
        coEvery { sleepChecker.isSleeping(any(), any(), any(), any()) } returns false
        conversationDao = mockk()
        coEvery { conversationDao.latestActiveForCharacter(any()) } returns null
        messageDao = mockk(relaxed = true)
        generator = mockk(relaxed = true)
        coEvery { generator.userNickname() } returns "我"
        coEvery { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "好耶"
        notifications = mockk(relaxed = true)
        availability = mockk()
        coEvery { availability.of(any(), any(), any(), any()) } returns MentionAvailability.AVAILABLE
        MomentPendingInteractionStore.save(context, emptyList())
    }

    private fun settings(frequency: Int = 0, autoLike: Boolean = false, schedule: Boolean = false) {
        coEvery { settingsRepo.getAppSettings() } returns AppSettings(
            momentAutoCommentFrequency = frequency,
            momentAutoLikeEnabled = autoLike,
            scheduleSystemEnabled = schedule,
        )
    }

    private fun userPost(vararg mentions: String) = MomentPostEntity(
        uuid = "p1", content = "今天好开心", timestamp = now - 1_000L, authorTypeRaw = "user",
        mentionedCharacterUuidsJson = StringListJson.encode(mentions.toList()),
    )

    private fun aiComment(by: String) = MomentCommentEntity(
        uuid = "cm-$by", content = "早", authorTypeRaw = "character", characterUuid = by, postUuid = "p1",
    )

    private fun interactor(slot: MomentLlmSlot) = MomentMentionInteractor(
        context = context, momentRepo = momentRepo, characterRepo = characterRepo, apiConfigRepo = apiConfigRepo,
        settingsRepo = settingsRepo, availability = availability, generator = generator,
        notifications = notifications, llmSlot = slot,
    )

    private fun drain() = MomentPendingDrain(
        context = context, momentRepo = momentRepo, characterRepo = characterRepo, apiConfigRepo = apiConfigRepo,
        settingsRepo = settingsRepo, sleepChecker = sleepChecker, conversationDao = conversationDao,
        commentGenerator = generator, notifications = notifications,
    )

    private fun service(slot: MomentLlmSlot = MomentLlmSlot()) = MomentInteractionService(
        context = context, momentRepo = momentRepo, characterRepo = characterRepo, apiConfigRepo = apiConfigRepo,
        settingsRepo = settingsRepo, sleepChecker = sleepChecker, messageDao = messageDao, llmSlot = slot,
        conversationDao = conversationDao, commentGenerator = generator, notifications = notifications,
        pendingDrain = drain(), mentionInteractor = interactor(slot),
    )

    private fun queued() = MomentPendingInteractionStore.load(context).map { it.postUuid to it.characterUuid }

    private fun verifyGeneratedFor(uuid: String, mentioned: Boolean, times: Int = 1) = coVerify(exactly = times) {
        generator.generate(match { it.uuid == uuid }, any(), any(), any(), any(), any(), any(), any(), any(), any(), mentioned)
    }

    // ── T2-2 autoInteractWithPost ──

    /** a（E2）：醒着的被提醒者——评论名额 0、自动点赞关，也必赞必评；直接评帖（replyTarget = null）；其他角色 0 评论。 */
    @Test
    fun t2_2a_醒着被提醒_名额0点赞关_也必赞必评() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        service().autoInteractWithPost("p1", nowMillis = now)

        coVerify(exactly = 1) { momentRepo.addLike("p1", MomentAuthorType.CHARACTER, "c1", any()) }
        coVerify(exactly = 1) {
            generator.generate(match { it.uuid == "c1" }, any(), isNull(), any(), any(), any(), any(), any(), any(), any(), true)
        }
        coVerify(exactly = 1) { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { momentRepo.addComment("p1", "好耶", MomentAuthorType.CHARACTER, "c1", any(), any(), any()) }
        coVerify(exactly = 1) { momentRepo.addComment(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { notifications.create(MomentNotificationType.COMMENT_ON_USER_POST, "c1", any(), "好耶", any()) }
        assertTrue(queued().isEmpty())
    }

    /** b（E3）：被提醒的在见面 → 进待互动队列，本轮不赞不评。 */
    @Test
    fun t2_2b_被提醒者见面中_入队不赞不评() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c2")
        coEvery { availability.of("c2", any(), any(), any()) } returns MentionAvailability.IN_MEETING
        service().autoInteractWithPost("p1", nowMillis = now)

        assertEquals(listOf("p1" to "c2"), queued())
        coVerify(exactly = 0) { momentRepo.addLike(any(), any(), "c2", any()) }
        verifyGeneratedFor("c2", mentioned = true, times = 0)
    }

    /** c（E4）：日程开、被提醒的在睡 → 进待互动队列。 */
    @Test
    fun t2_2c_被提醒者在睡_入队() = runTest {
        settings(schedule = true)
        coEvery { momentRepo.getPost("p1") } returns userPost("c3")
        coEvery { availability.of("c3", true, any(), any()) } returns MentionAvailability.SLEEPING
        service().autoInteractWithPost("p1", nowMillis = now)

        assertEquals(listOf("p1" to "c3"), queued())
        coVerify(exactly = 0) { momentRepo.addLike(any(), any(), "c3", any()) }
        verifyGeneratedFor("c3", mentioned = true, times = 0)
    }

    /** d（E1·K-1）：没提醒任何人的帖——生成从未收到 mentioned = true、队列仍空、可用性一次都没问；打分链照常跑（正向证据）。 */
    @Test
    fun t2_2d_无提醒帖_行为同现在() = runTest {
        settings(frequency = 2, autoLike = true)
        coEvery { momentRepo.getPost("p1") } returns userPost()
        service().autoInteractWithPost("p1", nowMillis = now)

        coVerify(exactly = 0) { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), true) }
        coVerify(exactly = 0) { availability.of(any(), any(), any(), any()) }
        assertTrue(queued().isEmpty())
        coVerify(exactly = 4) { messageDao.countRecentNonSystemForCharacter(any(), any()) }
    }

    /** e（E5）：被提醒者生成返回空 → 入队。 */
    @Test
    fun t2_2e_生成为空_入队() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        service().autoInteractWithPost("p1", nowMillis = now)

        assertEquals(listOf("p1" to "c1"), queued())
        coVerify(exactly = 0) { momentRepo.addComment(any(), any(), any(), any(), any(), any(), any()) }
    }

    /** e′（E5）：被提醒者生成抛错 → 入队。 */
    @Test
    fun t2_2e2_生成抛错_入队() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery {
            generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("boom")
        service().autoInteractWithPost("p1", nowMillis = now)

        assertEquals(listOf("p1" to "c1"), queued())
    }

    /** f（E6）：被提醒者已评论过（恢复重跑）→ 不再处理。 */
    @Test
    fun t2_2f_已评论过_不再处理() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery { momentRepo.commentsForPost("p1") } returns listOf(aiComment("c1"))
        service().autoInteractWithPost("p1", nowMillis = now)

        coVerify(exactly = 0) { momentRepo.addLike(any(), any(), "c1", any()) }
        verifyGeneratedFor("c1", mentioned = true, times = 0)
        assertTrue(queued().isEmpty())
    }

    /** g（E7）：已有 2 条非提醒者评论、名额 2 → 非提醒者预算 0；被提醒的 c1 照评，除 c1 外 0 次落评论。 */
    @Test
    fun t2_2g_不占名额_非提醒者预算扣满也照评() = runTest {
        settings(frequency = 2)
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery { momentRepo.commentsForPost("p1") } returns listOf(aiComment("c2"), aiComment("c3"))
        service().autoInteractWithPost("p1", nowMillis = now)

        coVerify(exactly = 1) { momentRepo.addComment("p1", "好耶", MomentAuthorType.CHARACTER, "c1", any(), any(), any()) }
        coVerify(exactly = 1) { momentRepo.addComment(any(), any(), any(), any(), any(), any(), any()) }
    }

    /** h（E8）：LLM 槽一直满 → 等槽（31 次 × 2 秒）仍拿不到 → c1 入队、0 次生成。 */
    @Test
    fun t2_2h_槽满等不到_入队() = runTest {
        val slot = mockk<MomentLlmSlot>(relaxed = true)
        every { slot.tryAcquire() } returns false
        coEvery { slot.acquireWaiting(any(), any()) } returns false
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        service(slot).autoInteractWithPost("p1", nowMillis = now)

        coVerify(exactly = 1) { slot.acquireWaiting(31, 2_000L) }
        assertEquals(listOf("p1" to "c1"), queued())
        coVerify(exactly = 0) { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /** i（E31）：新提示词一句中英成对，逐字等于图纸 §4 锁定串。 */
    @Test
    @Config(qualifiers = "zh-rCN")
    fun t2_2i_提示词一句_中文() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("「%1\$s」发这条时特意提醒了你来看。", ctx.getString(R.string.moment_comment_mentioned_you))
    }

    @Test
    fun t2_2i_提示词一句_英文() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "“%1\$s” specifically tagged you when posting this, hoping you would see it.",
            ctx.getString(R.string.moment_comment_mentioned_you),
        )
    }

    // ── T2-3 待互动 drain ──

    private fun queueOne(characterUuid: String = "c1") = MomentPendingInteractionStore.add(
        context = context, postUuid = "p1", postTimestampMillis = now - 1_000L,
        postAuthorUuid = null, characterUuid = characterUuid, nowMillis = now,
    )

    /** a（E9）：提醒项、已赞未评、名额 0 → 照评（mentioned = true）、不重复点赞、出队。 */
    @Test
    fun t2_3a_提醒项已赞未评名额0_照评出队() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery { momentRepo.hasLiked("p1", "c1") } returns true
        queueOne()
        drain().drain(nowMillis = now)

        verifyGeneratedFor("c1", mentioned = true)
        coVerify(exactly = 1) { momentRepo.addComment("p1", "好耶", MomentAuthorType.CHARACTER, "c1", any(), any(), any()) }
        coVerify(exactly = 0) { momentRepo.addLike(any(), any(), any(), any()) }
        assertTrue(queued().isEmpty())
    }

    /** b（E10）：提醒项生成为空 → 留在队列。 */
    @Test
    fun t2_3b_提醒项生成失败_留队() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        queueOne()
        drain().drain(nowMillis = now)

        verifyGeneratedFor("c1", mentioned = true)
        assertEquals(listOf("p1" to "c1"), queued())
    }

    /** b′（E10）：提醒项生成抛错 → 留在队列。 */
    @Test
    fun t2_3b2_提醒项生成抛错_留队() = runTest {
        coEvery { momentRepo.getPost("p1") } returns userPost("c1")
        coEvery {
            generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("boom")
        queueOne()
        drain().drain(nowMillis = now)

        assertEquals(listOf("p1" to "c1"), queued())
    }

    /** c（E11）：非提醒项已赞 → 丢弃（同现在），不生成。 */
    @Test
    fun t2_3c_非提醒项已赞_丢弃() = runTest {
        settings(frequency = 2)
        coEvery { momentRepo.getPost("p1") } returns userPost()
        coEvery { momentRepo.hasLiked("p1", "c1") } returns true
        queueOne()
        drain().drain(nowMillis = now)

        coVerify(exactly = 0) { generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertTrue(queued().isEmpty())
    }

    // ── T2-4 恢复场景 D ──

    private fun stubRecoveryScans() {
        coEvery { momentRepo.recentUserComments(any(), any()) } returns emptyList()
        coEvery { momentRepo.recentUserPostsInWindow(any(), any(), any()) } returns emptyList()
        coEvery { momentRepo.recentCharacterPostsInWindow(any(), any(), any()) } returns emptyList()
        coEvery { momentRepo.recentUserPostsWithMentionsInWindow(any(), any(), any()) } returns listOf(userPost("c1"))
    }

    private fun recovery(mention: MomentMentionInteractor) =
        MomentRecoveryService(momentRepo = momentRepo, interactionService = mockk(relaxed = true), mentionInteractor = mention)

    /** a（E12）：c1 欠评、醒着、不在队列、无在途 → settle 1 次；窗口 = (now − 24h, now − 5min)、LIMIT = 5 × 2。 */
    @Test
    fun t2_4a_欠评醒着_调settle() = runTest {
        stubRecoveryScans()
        val mention = mockk<MomentMentionInteractor>(relaxed = true)
        coEvery { mention.takeAwakeOwed(any(), any(), any()) } returns listOf(chars[0])
        recovery(mention).recoverIfNeeded(nowMillis = now)

        coVerify(exactly = 1) { momentRepo.recentUserPostsWithMentionsInWindow(now - 86_400_000L, now - 300_000L, 10) }
        coVerify(exactly = 1) { mention.settle("p1", match { list -> list.map { it.uuid } == listOf("c1") }, any()) }
    }

    /** b（E13）：该帖有在途延迟任务 → 连欠账都不算、不 settle。 */
    @Test
    fun t2_4b_在途任务_跳过() = runTest {
        stubRecoveryScans()
        val mention = mockk<MomentMentionInteractor>(relaxed = true)
        MomentDelayedTaskRegistry.register("p1", MomentDelayedTaskRegistry.Purpose.AutoInteraction) { awaitCancellation() }
        try {
            recovery(mention).recoverIfNeeded(nowMillis = now)
        } finally {
            MomentDelayedTaskRegistry.cancelAll("p1")
        }

        coVerify(exactly = 1) { momentRepo.recentUserPostsWithMentionsInWindow(any(), any(), any()) }
        coVerify(exactly = 0) { mention.takeAwakeOwed(any(), any(), any()) }
        coVerify(exactly = 0) { mention.settle(any(), any(), any()) }
    }

    /** c（E13）：真 takeAwakeOwed——c1 已在队列 → 不返回，不 settle（settle 第一步取帖从未发生）。 */
    @Test
    fun t2_4c_已在队列_不返回() = runTest {
        stubRecoveryScans()
        queueOne()
        val real = interactor(MomentLlmSlot())
        assertTrue(real.takeAwakeOwed(userPost("c1"), now, java.time.ZoneId.of("Asia/Shanghai")).isEmpty())
        recovery(real).recoverIfNeeded(nowMillis = now)

        coVerify(exactly = 0) { momentRepo.getPost(any()) }
        assertEquals(listOf("p1" to "c1"), queued())
    }

    /** d（E13）：真 takeAwakeOwed——c1 在睡 → 不返回、顺手入队，不 settle。 */
    @Test
    fun t2_4d_在睡_不返回且入队() = runTest {
        stubRecoveryScans()
        coEvery { availability.of("c1", any(), any(), any()) } returns MentionAvailability.SLEEPING
        recovery(interactor(MomentLlmSlot())).recoverIfNeeded(nowMillis = now)

        coVerify(exactly = 0) { momentRepo.getPost(any()) }
        assertEquals(listOf("p1" to "c1"), queued())
    }
}
