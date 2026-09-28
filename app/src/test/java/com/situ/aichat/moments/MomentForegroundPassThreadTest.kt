package com.situ.aichat.moments

import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.testutil.PrefsThreadRecordingContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 启动主线程读盘清零 ④（图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）：朋友圈前台一轮整轮挪到 IO 线程——
 * 待互动队列（SharedPreferences）不再在调用方线程（生产里 = 主线程 viewModelScope）上读，「先 drain 后恢复」顺序不变。
 * 真路径 = 真 [MomentPendingDrain] + 真 [MomentPendingInteractionStore]（Robolectric SharedPreferences）；
 * [MomentInteractionService] 只当转发壳（它的 processPendingInteractions 本来就是一行委托给 drain）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentForegroundPassThreadTest {

    private val context = PrefsThreadRecordingContext(ApplicationProvider.getApplicationContext())
    private val now = System.currentTimeMillis()
    private val momentRepo: MomentRepository = mockk(relaxed = true)

    private val drain = MomentPendingDrain(
        context = context, momentRepo = momentRepo, characterRepo = mockk(relaxed = true),
        apiConfigRepo = mockk(relaxed = true), settingsRepo = mockk<SettingsRepository>().also {
            coEvery { it.getAppSettings() } returns AppSettings()
        },
        sleepChecker = mockk { coEvery { isSleeping(any(), any(), any(), any()) } returns false },
        conversationDao = mockk { coEvery { latestActiveForCharacter(any()) } returns null },
        commentGenerator = mockk(relaxed = true), notifications = mockk(relaxed = true),
    )

    @Test
    fun 整轮不在调用方线程跑_队列照常消费_先drain后恢复() = runBlocking {
        // 帖子已不在 → drain 消费掉该项（不生成、不等随机间隔），足够证明「真读了队列、真走完了」。
        coEvery { momentRepo.getPost("p1") } returns null
        MomentPendingInteractionStore.save(context, emptyList())
        MomentPendingInteractionStore.add(context, "p1", now - 1_000L, null, "c1", nowMillis = now)
        val caller = Thread.currentThread()
        val before = context.threadsFor(PREFS_NAME).size
        val order = mutableListOf<String>()
        var recoveryThread: Thread? = null
        val interaction = mockk<MomentInteractionService> {
            coEvery { processPendingInteractions(any(), any()) } coAnswers {
                order += "drain"
                drain.drain(nowMillis = now)
            }
        }
        val recovery = mockk<MomentRecoveryService> {
            coEvery { recoverIfNeeded(any()) } coAnswers {
                order += "recover"
                recoveryThread = Thread.currentThread()
            }
        }

        runMomentForegroundPass(interaction, recovery)

        val touched = context.threadsFor(PREFS_NAME).drop(before)
        assertTrue("drain 应当读过待互动队列", touched.isNotEmpty())
        assertTrue("待互动队列不许在调用方线程上读：$touched", touched.none { it === caller })
        assertNotNull(recoveryThread)
        assertTrue("恢复也不在调用方线程跑", recoveryThread !== caller)
        assertEquals(listOf("drain", "recover"), order)
        coVerify(exactly = 1) { momentRepo.getPost("p1") }
        assertTrue("帖子已不在 → 该项被消费", MomentPendingInteractionStore.load(context).isEmpty())
    }

    private companion object {
        /** 与 [MomentPendingInteractionStore] 的文件名同值（它是 private，这里照抄；改名会让「应当读过」那条先红）。 */
        const val PREFS_NAME = "moment_pending_interactions"
    }
}
