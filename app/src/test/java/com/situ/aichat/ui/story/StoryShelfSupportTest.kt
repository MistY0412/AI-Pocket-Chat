package com.situ.aichat.ui.story

import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 书架共用件（琉璃 2.0 卷六·三·上 §3.1·T1-1）：两区拆分 / 菜单兜底 / 按 id 解析目标 / 菜单分派。 */
class StoryShelfSupportTest {

    private fun story(id: String, status: String) = StoryEntity(id = id, title = id, status = status)

    @Test fun 两区拆分_已完结进档案_其余进在读_各区保持原序() {
        val a = story("a", StoryStatus.SERIALIZING)
        val b = story("b", StoryStatus.COMPLETED)
        val c = story("c", StoryStatus.PAUSED)
        val d = story("d", StoryStatus.GENERATION_FAILED)
        val e = story("e", "someFutureRaw")
        val f = story("f", StoryStatus.COMPLETED)
        val split = storyShelfSplit(listOf(a, b, c, d, e, f))
        assertEquals(listOf(a, c, d, e), split.active)
        assertEquals(listOf(b, f), split.archived)
    }

    @Test fun 菜单兜底_无菜单不清_书在不清_书没了才清() {
        val stories = listOf(story("a", StoryStatus.SERIALIZING))
        assertFalse(storyMenuStale(null, stories))
        assertFalse(storyMenuStale("a", stories))
        assertTrue(storyMenuStale("gone", stories))
    }

    @Test fun 按id解析_在读与已完结互斥() {
        val live = story("x", StoryStatus.SERIALIZING)
        val done = story("y", StoryStatus.COMPLETED)
        val stories = listOf(live, done)
        assertSame(live, storyActiveById(stories, "x"))
        assertNull(storyActiveById(stories, "y"))
        assertNull(storyActiveById(stories, "nope"))
        assertSame(done, storyArchivedById(stories, "y"))
        assertNull(storyArchivedById(stories, "x"))
    }

    @Test fun 菜单分派_暂停与恢复同走切换暂停_其余各走各的() {
        StoryCardMenuAction.entries.forEach { action ->
            val calls = mutableListOf<String>()
            action.dispatch(
                onTogglePause = { calls += "pause" },
                onArchive = { calls += "archive" },
                onOpenSettings = { calls += "settings" },
                onDelete = { calls += "delete" },
            )
            val expected = when (action) {
                StoryCardMenuAction.PAUSE, StoryCardMenuAction.RESUME -> "pause"
                StoryCardMenuAction.ARCHIVE -> "archive"
                StoryCardMenuAction.SETTINGS -> "settings"
                StoryCardMenuAction.DELETE -> "delete"
            }
            assertEquals("$action", listOf(expected), calls)
        }
    }
}
