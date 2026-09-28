package com.situ.aichat.ui.story

import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.story.StoryGenPhase
import com.situ.aichat.story.StoryGenerationTaskManager.GenerationProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 琉璃 2.0 卷六·三·下甲 T1-3：生成完成后「翻不翻章」协作者（图纸 §3.6 [StoryReaderGenerationWatcher]）。
 * G1 = 暖陶现行为（自动跳最新章）；G2–G7 = 琉璃 R2「写好了 · 翻开」与清空时机。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryReaderGenerationWatcherTest {

    private val c1 = StoryChapterEntity(id = "c1", storyId = "s1", chapterNumber = 1)
    private val c2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2)
    private val c3 = StoryChapterEntity(id = "c3", storyId = "s1", chapterNumber = 3)

    private fun writing(n: Int) = GenerationProgress(
        progress = 0.3, genPhase = StoryGenPhase.WRITING, phase = "正在写正文", storyTitle = "对面楼的灯", chapterNumber = n,
    )

    private class Rig(
        val gen: MutableStateFlow<GenerationProgress?>,
        val current: MutableStateFlow<String>,
        val storyId: MutableStateFlow<String?>,
        var latest: StoryChapterEntity?,
    ) {
        lateinit var watcher: StoryReaderGenerationWatcher
    }

    private fun TestScope.rig(current: String, latest: StoryChapterEntity?, autoJump: Boolean, storyId: String? = "s1"): Rig {
        val r = Rig(MutableStateFlow(null), MutableStateFlow(current), MutableStateFlow(storyId), latest)
        r.watcher = StoryReaderGenerationWatcher(backgroundScope, r.gen, r.storyId, { r.latest }, r.current)
        r.watcher.autoJump = autoJump
        r.watcher.start()
        runCurrent()
        return r
    }

    /** 在写第 [n] 章 → 写完时最新章变成 [after]（null = 不变）→ 活跃生成清空。 */
    private fun TestScope.finishWriting(r: Rig, n: Int, after: StoryChapterEntity?) {
        r.gen.value = writing(n)
        runCurrent()
        if (after != null) r.latest = after
        r.gen.value = null
        runCurrent()
    }

    @Test
    fun g1_autoJump_jumpsToLatest_noReady() = runTest {
        val r = rig(current = "c2", latest = c2, autoJump = true)
        finishWriting(r, 3, after = c3)
        assertEquals("c3", r.current.value)
        assertNull(r.watcher.ready.value)
    }

    @Test
    fun g2_noAutoJump_staysAndOffersReady() = runTest {
        val r = rig(current = "c2", latest = c2, autoJump = false)
        finishWriting(r, 3, after = c3)
        assertEquals("c2", r.current.value)
        assertEquals(StoryReadyChapter("c3", 3), r.watcher.ready.value)
    }

    @Test
    fun g3_failedWrite_noReady() = runTest {
        val same = rig(current = "c2", latest = c2, autoJump = false)
        finishWriting(same, 3, after = null)
        assertNull(same.watcher.ready.value)
        assertEquals("c2", same.current.value)

        // 最新章 ≠ 当前章但章号对不上（在写第 3 章失败，最新仍是第 2 章）→ 不出钮、也不跳。
        val mismatch = rig(current = "c1", latest = c2, autoJump = false)
        finishWriting(mismatch, 3, after = null)
        assertNull(mismatch.watcher.ready.value)
        assertEquals("c1", mismatch.current.value)
    }

    @Test
    fun g4_openReady_jumpsAndClears_noopWhenEmpty() = runTest {
        val r = rig(current = "c2", latest = c2, autoJump = false)
        r.watcher.openReady()
        runCurrent()
        assertEquals("没有写好的章时点翻开不动", "c2", r.current.value)

        finishWriting(r, 3, after = c3)
        r.watcher.openReady()
        runCurrent()
        assertEquals("c3", r.current.value)
        assertNull(r.watcher.ready.value)
    }

    @Test
    fun g5_userNavigatesToReadyChapter_clears() = runTest {
        val r = rig(current = "c2", latest = c2, autoJump = false)
        finishWriting(r, 3, after = c3)
        assertEquals(StoryReadyChapter("c3", 3), r.watcher.ready.value)
        r.current.value = "c3"
        runCurrent()
        assertNull(r.watcher.ready.value)
    }

    @Test
    fun g6_newRoundOfWriting_clears() = runTest {
        val r = rig(current = "c2", latest = c2, autoJump = false)
        finishWriting(r, 3, after = c3)
        assertEquals(StoryReadyChapter("c3", 3), r.watcher.ready.value)
        r.gen.value = writing(4)
        runCurrent()
        assertNull(r.watcher.ready.value)
    }

    @Test
    fun g7_noStoryId_doesNothing() = runTest {
        val jump = rig(current = "c2", latest = c2, autoJump = true, storyId = null)
        finishWriting(jump, 3, after = c3)
        assertEquals("c2", jump.current.value)

        val ready = rig(current = "c2", latest = c2, autoJump = false, storyId = null)
        finishWriting(ready, 3, after = c3)
        assertNull(ready.watcher.ready.value)
    }
}
