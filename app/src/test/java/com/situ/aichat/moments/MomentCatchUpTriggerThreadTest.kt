package com.situ.aichat.moments

import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import com.situ.aichat.testutil.PrefsThreadRecordingContext
import com.situ.aichat.work.BackgroundScheduler
import com.situ.aichat.work.MomentCatchUpWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * 聊天一轮收尾的欠帖补发（稳定性防线 B·StrictMode 基线登记的 `MomentOwedPostStore` 两行）：欠帖标记
 * （`moment_owed_posts`）不再在调用方线程（生产里 = viewModelScope 的 Main.immediate）上读；「查 → 清 → 排」
 * 顺序与排任务的各项取值照旧。
 *
 * 真路径 = 真 [MomentGenerationService.triggerCatchUpPostIfNeeded] + 真 [MomentOwedPostStore]（Robolectric
 * SharedPreferences）；只假掉 [BackgroundScheduler]。预置数据一律经**未包装**的 Application 写 / 读，不污染线程记录。
 * 线程按对象比，不按名字比（协程调试模式会改线程名）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentCatchUpTriggerThreadTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val context = PrefsThreadRecordingContext(app)
    private val scheduler = mockk<BackgroundScheduler>(relaxed = true)
    private val now = System.currentTimeMillis()

    @Test
    fun 当天有欠帖_不在调用方线程读_先清后排补发任务() = runBlocking {
        MomentOwedPostStore.markOwedPost(app, "c1", now)
        var owedWhenScheduled: Boolean? = null
        every {
            scheduler.scheduleOneShot(any(), MomentCatchUpWorker::class.java, any(), any(), any(), any())
        } answers { owedWhenScheduled = MomentOwedPostStore.hasOwedPost(app, "c1", now) }
        val caller = Thread.currentThread()

        service().triggerCatchUpPostIfNeeded("c1", nowMillis = now)

        val touched = context.threadsFor(PREFS_NAME)
        assertTrue("欠帖标记应当读过", touched.isNotEmpty())
        assertTrue("欠帖标记不许在调用方线程上读：$touched", touched.none { it === caller })
        assertEquals("排补发任务时欠帖标记已清（先清后排）", false, owedWhenScheduled)
        assertFalse("补发后欠帖标记已清", MomentOwedPostStore.hasOwedPost(app, "c1", now))

        val delay = slot<Duration>()
        val input = slot<Data>()
        verify(exactly = 1) {
            scheduler.scheduleOneShot(
                uniqueName = MomentCatchUpWorker.uniqueName("c1"),
                workerClass = MomentCatchUpWorker::class.java,
                initialDelay = capture(delay),
                requireNetwork = true,
                existingPolicy = ExistingWorkPolicy.KEEP,
                inputData = capture(input),
            )
        }
        assertTrue("延迟 40~80 秒：${delay.captured}", delay.captured.seconds in 40L..80L)
        assertEquals("c1", input.captured.getString(MomentCatchUpWorker.KEY_CHARACTER_UUID))
    }

    @Test
    fun 没有欠帖_不在调用方线程读_不排任务() = runBlocking {
        val caller = Thread.currentThread()

        service().triggerCatchUpPostIfNeeded("c1", nowMillis = now)

        val touched = context.threadsFor(PREFS_NAME)
        assertTrue("欠帖标记应当读过", touched.isNotEmpty())
        assertTrue("欠帖标记不许在调用方线程上读：$touched", touched.none { it === caller })
        verify(exactly = 0) { scheduler.scheduleOneShot(any(), any<Class<MomentCatchUpWorker>>(), any(), any(), any(), any()) }
    }

    @Test
    fun 昨天的欠帖_过期不补发_标记原样不清() = runBlocking {
        val yesterday = now - Duration.ofDays(1).toMillis()
        MomentOwedPostStore.markOwedPost(app, "c1", yesterday)

        service().triggerCatchUpPostIfNeeded("c1", nowMillis = now)

        verify(exactly = 0) { scheduler.scheduleOneShot(any(), any<Class<MomentCatchUpWorker>>(), any(), any(), any(), any()) }
        assertTrue("过期标记不清（同旧行为：查不到当天欠帖即直接返回）", MomentOwedPostStore.hasOwedPost(app, "c1", yesterday))
    }

    private fun service() = MomentGenerationService(
        context = context, contextLog = mockk(relaxed = true), apiConfigRepo = mockk(relaxed = true),
        characterRepo = mockk(relaxed = true), momentRepo = mockk(relaxed = true), scheduleDao = mockk(relaxed = true),
        sleepChecker = mockk(relaxed = true), settingsRepo = mockk(relaxed = true), interactionService = mockk(relaxed = true),
        backgroundScheduler = scheduler, giftQueue = mockk(relaxed = true),
        petShopQueue = mockk(relaxed = true), petRepository = mockk(relaxed = true),
        newPostNotifier = mockk(relaxed = true), userProfileDao = mockk(relaxed = true),
        conversationDao = mockk(relaxed = true),
    )

    private companion object {
        /** 与 [MomentOwedPostStore] 的文件名同值（它是 private，这里照抄；改名会让「应当读过」那条先红）。 */
        const val PREFS_NAME = "moment_owed_posts"
    }
}
