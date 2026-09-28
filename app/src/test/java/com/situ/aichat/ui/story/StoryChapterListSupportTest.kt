package com.situ.aichat.ui.story

import com.situ.aichat.data.local.entity.StoryChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** 章节页共用件（琉璃 2.0 卷六·三·上 §3.3·T1-2）：六个派生量 / 继续阅读目标 / 节点四态判据。 */
class StoryChapterListSupportTest {

    private val ch1 = StoryChapterEntity(id = "c1", chapterNumber = 1, hasChoice = true, userChoice = "打开门")
    private val ch2 = StoryChapterEntity(id = "c2", chapterNumber = 2, hasChoice = true, userChoice = null)

    @Test fun 六个派生量() {
        val s = storyChapterListSummary(listOf(ch1, ch2), lastReadChapterId = "c1", advancedFromChapterNumber = null)
        assertSame(ch2, s.pending)
        assertSame(ch2, s.resume)
        assertEquals(listOf(ch2, ch1), s.newestFirst)
        assertEquals("c2", s.latestId)
        assertEquals(1, s.lastReadNumber)
        assertEquals(1, s.choiceCount)
    }

    @Test fun 上次阅读是未知id_已读话数为0() {
        assertEquals(0, storyChapterListSummary(listOf(ch1, ch2), "unknown", null).lastReadNumber)
    }

    @Test fun 继续阅读目标_同待选章不重复给() {
        assertNull(storyChapterContinueTarget(ch2, ch2))
        assertSame(ch1, storyChapterContinueTarget(null, ch1))
        assertSame(ch1, storyChapterContinueTarget(ch2, ch1))
    }

    @Test fun 节点_解锁边界_到点即解锁() {
        val now = 1_000_000L
        assertEquals(StoryChapterNodeKind.LOCK, storyChapterNodeKind(StoryChapterEntity(chapterNumber = 1, unlockAt = now + 1), now, 5))
        val atNow = storyChapterNodeKind(StoryChapterEntity(chapterNumber = 1, unlockAt = now), now, 5)
        assertEquals(StoryChapterNodeKind.DOT, atNow)
    }

    @Test fun 节点_有选择优先于已读_无选择按已读分实心与描边() {
        val now = 5L
        assertEquals(StoryChapterNodeKind.DIAMOND, storyChapterNodeKind(StoryChapterEntity(chapterNumber = 1, hasChoice = true), now, lastReadNumber = 3))
        assertEquals(StoryChapterNodeKind.DOT, storyChapterNodeKind(StoryChapterEntity(chapterNumber = 3), now, lastReadNumber = 3))
        assertEquals(StoryChapterNodeKind.RING, storyChapterNodeKind(StoryChapterEntity(chapterNumber = 4), now, lastReadNumber = 3))
    }
}
