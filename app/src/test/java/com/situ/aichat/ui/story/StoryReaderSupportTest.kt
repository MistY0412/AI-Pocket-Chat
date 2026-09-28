package com.situ.aichat.ui.story

import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryArcPlanning
import com.situ.aichat.story.StoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 琉璃 2.0 卷六·三·下甲 T1-1：阅读器两张脸共用的纯派生量（图纸 §3.1）。
 * 期望值从原 StoryReaderScreen 的式子独立反推（导航门 / 收尾进度钳位 / 拖动映射）。
 */
class StoryReaderSupportTest {

    private val c1 = StoryChapterEntity(id = "c1", storyId = "s1", chapterNumber = 1)
    private val c2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2)
    private val c3 = StoryChapterEntity(id = "c3", storyId = "s1", chapterNumber = 3)
    private val three = listOf(c1, c2, c3)

    private fun nav(
        current: StoryChapterEntity?,
        chapters: List<StoryChapterEntity> = three,
        currentId: String = current?.id ?: "",
        status: String? = StoryStatus.SERIALIZING,
        generating: Boolean = false,
        hasPrevDraft: Boolean = false,
    ) = storyReaderNav(chapters, currentId, current, status, generating, hasPrevDraft)

    // ── storyReaderNav ──

    @Test
    fun middleChapter_hasBothNeighbours_notLatest_cannotRewrite() {
        val n = nav(c2)
        assertEquals(1, n.chapterIndex)
        assertTrue(n.hasPrev)
        assertTrue(n.hasNext)
        assertFalse(n.isLatestChapter)
        assertFalse(n.canRewrite)
    }

    @Test
    fun lastChapter_notGenerating_isLatest_canRewrite_noNext() {
        val n = nav(c3)
        assertTrue(n.isLatestChapter)
        assertTrue(n.canRewrite)
        assertFalse(n.hasNext)
        assertTrue(n.hasPrev)
    }

    @Test
    fun lastChapter_generating_blocksRewriteAndPreviousDraft() {
        val n = nav(c3, generating = true, hasPrevDraft = true)
        assertFalse(n.canRewrite)
        assertFalse(n.canViewPreviousDraft)
    }

    @Test
    fun previousDraft_onlyOnLatestNotGenerating() {
        assertTrue(nav(c3, hasPrevDraft = true).canViewPreviousDraft)
        assertFalse(nav(c2, hasPrevDraft = true).canViewPreviousDraft)
        assertFalse(nav(c3, hasPrevDraft = false).canViewPreviousDraft)
    }

    @Test
    fun continueArc_onlyCompletedBookOnLastChapter() {
        assertTrue(nav(c3, status = StoryStatus.COMPLETED).showContinueArc)
        assertFalse(nav(c2, status = StoryStatus.COMPLETED).showContinueArc)
        assertFalse(nav(c3, status = StoryStatus.WAITING_CHOICE).showContinueArc)
    }

    @Test
    fun unknownId_indexMinusOne_noNeighbours() {
        val n = nav(c2, currentId = "ghost")
        assertEquals(-1, n.chapterIndex)
        assertFalse(n.hasPrev)
        assertFalse(n.hasNext)
    }

    @Test
    fun emptyList_nullChapter_isLatestByNullEquality_butCannotRewrite() {
        val n = nav(null, chapters = emptyList(), currentId = "")
        // 原式 currentChapter?.id == chapters.lastOrNull()?.id：null == null → true（原式如此）。
        assertTrue(n.isLatestChapter)
        assertFalse(n.canRewrite)
        assertFalse(n.hasPrev)
        assertFalse(n.hasNext)
    }

    // ── storyFinaleProgress ──

    @Test
    fun finale_nullStory_orNoEndingType_isNull() {
        assertNull(storyFinaleProgress(null))
        assertNull(storyFinaleProgress(StoryEntity(id = "s1", finaleEndingType = null)))
    }

    @Test
    fun finale_outlineNotYetWritten_showsFirstChapter() {
        val nullOutline = storyFinaleProgress(StoryEntity(id = "s1", finaleEndingType = "happy", storyOutline = null, cachedLatestChapterNumber = 40))
        val emptyOutline = storyFinaleProgress(StoryEntity(id = "s1", finaleEndingType = "happy", storyOutline = "", cachedLatestChapterNumber = 40))
        assertEquals(1, nullOutline!!.current)
        assertEquals(1, emptyOutline!!.current)
        // 大纲没自报章数 → 终章弧回退长度。
        assertEquals(StoryArcPlanning.FINALE_LENGTH_FALLBACK, nullOutline.total)
    }

    @Test
    fun finale_farPastArcEnd_clampsToTotal() {
        val p = storyFinaleProgress(
            StoryEntity(id = "s1", finaleEndingType = "happy", storyOutline = "终章弧大纲", currentArcStartChapter = 1, cachedLatestChapterNumber = 99),
        )!!
        assertEquals(p.total, p.current)
    }

    // ── storyReaderSeekIndex ──

    @Test
    fun seek_mapsFractionToTopAlignedItem_andClamps() {
        assertEquals(5, storyReaderSeekIndex(0.5f, 10))
        assertEquals(0, storyReaderSeekIndex(0f, 10))
        assertEquals(9, storyReaderSeekIndex(1f, 10))
        assertEquals(9, storyReaderSeekIndex(0.999f, 10))
        assertEquals(3, storyReaderSeekIndex(2f, 4))
        assertEquals(0, storyReaderSeekIndex(-1f, 4))
        assertEquals(0, storyReaderSeekIndex(Float.NaN, 4))
        assertEquals(-1, storyReaderSeekIndex(0.5f, 0))
    }
}
