package com.situ.aichat.story

import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.data.repository.StoryRepository
import com.situ.aichat.testutil.PrefsThreadRecordingContext
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动主线程读盘清零 ②（图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）：本类是 AppViewModel 的依赖、在主线程构造，
 * 构造期不许碰 SharedPreferences；防抖时间戳的读写只在回前台那一趟的后台协程里发生。
 * （「首跑必重排 / 防抖」语义由 [StoryAutoSerializeUnlockRefreshTest] 原样锁着。）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryAutoSerializePrefsLazyTest {

    private val context = PrefsThreadRecordingContext(ApplicationProvider.getApplicationContext())
    private val storyRepository = mockk<StoryRepository>(relaxed = true)

    @Test
    fun 构造不碰prefs_回前台那一趟在后台线程取() {
        val passFinished = AtomicBoolean(false) // 第三步拉 serializing 列表 = 这一趟真跑到了底（同 UnlockRefreshTest 的判据）
        coEvery { storyRepository.getStoriesByStatus(StoryStatus.SERIALIZING) } answers {
            passFinished.set(true)
            emptyList()
        }
        val caller = Thread.currentThread()

        val service = StoryAutoSerializeService(
            context, storyRepository, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        )
        assertTrue("构造期不许取 prefs（= 主线程读盘）", context.threadsFor(PREFS_NAME).isEmpty())

        service.onAppForeground()
        repeat(400) { if (!passFinished.get()) Thread.sleep(5) }
        assertTrue("等待超时：前台 pass 没跑完", passFinished.get())

        val touched = context.threadsFor(PREFS_NAME)
        assertTrue("前台 pass 应当读过防抖时间戳", touched.isNotEmpty())
        assertTrue("prefs 不许在调用方线程上取：$touched", touched.none { it === caller })
    }

    private companion object {
        /** 与 [StoryAutoSerializeService] 的文件名同值（它是 private，这里照抄；改名会让「应当读过」那条先红）。 */
        const val PREFS_NAME = "story_schedule_prefs"
    }
}
