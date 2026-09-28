package com.situ.aichat.moments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.data.local.dao.ConversationDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.moments.MomentPendingInteractionStore.PendingInteraction
import com.situ.aichat.prompt.schedule.CharacterSleepChecker
import com.situ.aichat.util.StringListJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-1（朋友圈发布页·乙 §7）：待互动 drain 的重入锁（L-1）、被提醒项重试间隔与上限（L-2）、回写保留新来的（L-3）、
 * 老队列 JSON 兼容。真路径 = 真 [MomentPendingDrain] + 真 [MomentPendingInteractionStore]（Robolectric SharedPreferences）；
 * 依赖用 MockK 假掉（构造照甲卷 `MomentMentionInteractionTest`）。数值从图纸 §3.1 独立反推：间隔 20 分钟、上限 6 次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentPendingDrainRetryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private lateinit var momentRepo: MomentRepository
    private lateinit var characterRepo: CharacterRepository
    private lateinit var apiConfigRepo: ApiConfigRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sleepChecker: CharacterSleepChecker
    private lateinit var conversationDao: ConversationDao
    private lateinit var generator: MomentCommentGenerator

    private val c1 = CharacterEntity(uuid = "c1", name = "小满", creationDate = 0L)

    @Before
    fun setUp() {
        momentRepo = mockk(relaxed = true)
        coEvery { momentRepo.getPost("p1") } returns MomentPostEntity(
            uuid = "p1", content = "今天好开心", timestamp = now - 1_000L, authorTypeRaw = "user",
            mentionedCharacterUuidsJson = StringListJson.encode(listOf("c1")),
        )
        coEvery { momentRepo.commentCountByCharacter(any(), any()) } returns 0
        characterRepo = mockk(relaxed = true)
        coEvery { characterRepo.getAll() } returns listOf(c1)
        coEvery { characterRepo.get("c1") } returns c1
        apiConfigRepo = mockk(relaxed = true)
        coEvery { apiConfigRepo.resolveConfigValues(any()) } returns mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        coEvery { settingsRepo.getAppSettings() } returns AppSettings(momentAutoCommentFrequency = 0)
        sleepChecker = mockk()
        coEvery { sleepChecker.isSleeping(any(), any(), any(), any()) } returns false
        conversationDao = mockk()
        coEvery { conversationDao.latestActiveForCharacter(any()) } returns null
        generator = mockk(relaxed = true)
        coEvery { generator.userNickname() } returns "我"
        MomentPendingInteractionStore.save(context, emptyList())
    }

    private fun drain() = MomentPendingDrain(
        context = context, momentRepo = momentRepo, characterRepo = characterRepo, apiConfigRepo = apiConfigRepo,
        settingsRepo = settingsRepo, sleepChecker = sleepChecker, conversationDao = conversationDao,
        commentGenerator = generator, notifications = mockk(relaxed = true),
    )

    private fun stubGenerate(answer: suspend () -> String?) {
        coEvery {
            generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers { answer() }
    }

    private fun verifyGenerated(times: Int) = coVerify(exactly = times) {
        generator.generate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    private fun mentionItem(failedAttempts: Int = 0, lastAttemptAtMillis: Long = 0L) = PendingInteraction(
        postUuid = "p1", postTimestampMillis = now - 1_000L, postAuthorUuid = null, characterUuid = "c1",
        queuedAtMillis = now - 30 * minute, failedAttempts = failedAttempts, lastAttemptAtMillis = lastAttemptAtMillis,
    )

    private fun queue() = MomentPendingInteractionStore.load(context)

    /** L1（E23）：第一次 drain 卡在生成里时，第二次 drain 立即返回（不等闸门）；生成只被调 1 次。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun l1_重叠时后来的立即返回() = runTest {
        val gate = CompletableDeferred<Unit>()
        stubGenerate { gate.await(); "好" }
        MomentPendingInteractionStore.save(context, listOf(mentionItem()))
        val d = drain()

        val first = launch { d.drain(nowMillis = now) }
        runCurrent()
        verifyGenerated(1) // 正向证据：第一次确已进到生成里、正卡着
        d.drain(nowMillis = now)
        assertFalse("第二次 drain 没等闸门就返回了", gate.isCompleted)
        verifyGenerated(1)
        assertEquals(listOf("p1" to "c1"), queue().map { it.postUuid to it.characterUuid })

        gate.complete(Unit)
        first.join()
        verifyGenerated(1)
        assertTrue(queue().isEmpty())
    }

    /** L2（E24）：被提醒项上一次尝试在 5 分钟前（< 20 分钟）→ 原样留队、生成 0 次。 */
    @Test
    fun l2_二十分钟内不重试_原样留队() = runTest {
        stubGenerate { "好" }
        val item = mentionItem(failedAttempts = 1, lastAttemptAtMillis = now - 5 * minute)
        MomentPendingInteractionStore.save(context, listOf(item))
        drain().drain(nowMillis = now)

        verifyGenerated(0)
        assertEquals(listOf(item), queue())
    }

    /** L3（E25）：上一次尝试在 21 分钟前、生成为空 → 留队，失败次数 +1、尝试时刻 = now。 */
    @Test
    fun l3_过了间隔再失败_计次并记时刻() = runTest {
        stubGenerate { null }
        MomentPendingInteractionStore.save(context, listOf(mentionItem(failedAttempts = 2, lastAttemptAtMillis = now - 21 * minute)))
        drain().drain(nowMillis = now)

        verifyGenerated(1)
        val kept = queue().single()
        assertEquals(3, kept.failedAttempts)
        assertEquals(now, kept.lastAttemptAtMillis)
    }

    /** L3′：从没试过的被提醒项（lastAttempt = 0）第一次抛错 → 留队，次数 1。 */
    @Test
    fun l3b_首次抛错_次数记1() = runTest {
        stubGenerate { throw IllegalStateException("boom") }
        MomentPendingInteractionStore.save(context, listOf(mentionItem()))
        drain().drain(nowMillis = now)

        verifyGenerated(1)
        val kept = queue().single()
        assertEquals(1, kept.failedAttempts)
        assertEquals(now, kept.lastAttemptAtMillis)
    }

    /**
     * L4（E25·复核 R1 修订）：已失败 5 次再失败（第 6 次）→ 放弃，但**留队占位**（次数 6）——恢复场景 D 只补不在队列里的
     * 被提醒者（`MomentMentionInteractionTest` c：在队列 → takeAwakeOwed 不返回），出队就会被 D 从 0 次重来。
     */
    @Test
    fun l4_满六次放弃_留队占位() = runTest {
        stubGenerate { null }
        MomentPendingInteractionStore.save(context, listOf(mentionItem(failedAttempts = 5, lastAttemptAtMillis = now - 21 * minute)))
        drain().drain(nowMillis = now)

        verifyGenerated(1)
        val kept = queue().single()
        assertEquals(6, kept.failedAttempts)
        assertEquals(now, kept.lastAttemptAtMillis)
    }

    /** L4″（复核 R1）：已放弃的占位项（次数 6·上次尝试早已过 20 分钟）→ 不再调 AI、原样留队。 */
    @Test
    fun l4c_已放弃不再调AI_原样留队() = runTest {
        stubGenerate { "好" }
        val tomb = mentionItem(failedAttempts = 6, lastAttemptAtMillis = now - 3 * 60 * minute)
        MomentPendingInteractionStore.save(context, listOf(tomb))
        drain().drain(nowMillis = now)

        verifyGenerated(0)
        assertEquals(listOf(tomb), queue())
    }

    /** L4‴（复核 R1）：占位项入队满 24 小时 → 随过期清理出队（不调 AI）。 */
    @Test
    fun l4d_占位满24小时出队() = runTest {
        stubGenerate { "好" }
        val tomb = mentionItem(failedAttempts = 6, lastAttemptAtMillis = now - 60 * minute).copy(queuedAtMillis = now - 25 * 60 * minute)
        MomentPendingInteractionStore.save(context, listOf(tomb))
        drain().drain(nowMillis = now)

        verifyGenerated(0)
        assertTrue(queue().isEmpty())
    }

    /** L4′ 对照：已失败 4 次再失败（第 5 次）→ 仍留队（次数 5）。 */
    @Test
    fun l4b_第五次失败仍留队() = runTest {
        stubGenerate { null }
        MomentPendingInteractionStore.save(context, listOf(mentionItem(failedAttempts = 4, lastAttemptAtMillis = now - 21 * minute)))
        drain().drain(nowMillis = now)

        assertEquals(5, queue().single().failedAttempts)
    }

    /** L5（E26）：drain 生成过程中别处入队 (p2, c9) → drain 回写后它仍在；处理完的 (p1, c1) 已出队。 */
    @Test
    fun l5_期间新入队的保住() = runTest {
        stubGenerate {
            MomentPendingInteractionStore.add(
                context = context, postUuid = "p2", postTimestampMillis = now, postAuthorUuid = null,
                characterUuid = "c9", nowMillis = now,
            )
            "好"
        }
        MomentPendingInteractionStore.save(context, listOf(mentionItem()))
        drain().drain(nowMillis = now)

        verifyGenerated(1)
        assertEquals(listOf("p2" to "c9"), queue().map { it.postUuid to it.characterUuid })
    }

    /** L5′：无接口配置提前结束时也按「保留 + 新来」回写（过期项被清、快照外新项保住）。 */
    @Test
    fun l5b_无配置提前结束_也保留新来的() = runTest {
        coEvery { apiConfigRepo.resolveConfigValues(any()) } coAnswers {
            MomentPendingInteractionStore.add(
                context = context, postUuid = "p2", postTimestampMillis = now, postAuthorUuid = null,
                characterUuid = "c9", nowMillis = now,
            )
            null
        }
        val stale = mentionItem().copy(postUuid = "p0", queuedAtMillis = now - 25 * 60 * minute)
        MomentPendingInteractionStore.save(context, listOf(stale, mentionItem()))
        drain().drain(nowMillis = now)

        assertEquals(listOf("p1" to "c1", "p2" to "c9"), queue().map { it.postUuid to it.characterUuid })
        assertEquals(0, queue().first().failedAttempts) // 无配置不算一次尝试
    }

    /** L6（E27）：不含新字段的老 JSON → 读出 failedAttempts = 0、lastAttemptAtMillis = 0。 */
    @Test
    fun l6_老队列JSON_新字段按0读() {
        context.getSharedPreferences("moment_pending_interactions", Context.MODE_PRIVATE).edit()
            .putString(
                "MomentPendingInteractions",
                """[{"postUuid":"p1","postTimestampMillis":1,"postAuthorUuid":null,"characterUuid":"c1","queuedAtMillis":2}]""",
            )
            .commit()

        val item = queue().single()
        assertEquals("p1" to "c1", item.postUuid to item.characterUuid)
        assertEquals(0, item.failedAttempts)
        assertEquals(0L, item.lastAttemptAtMillis)
    }
}
