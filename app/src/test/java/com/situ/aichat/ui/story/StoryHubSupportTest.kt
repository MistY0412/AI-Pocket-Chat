package com.situ.aichat.ui.story

import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.CustomStoryPrompts
import com.situ.aichat.story.StoryEditableField
import com.situ.aichat.story.StoryFieldKind
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.story.StoryUpdateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 书页共用件（琉璃 2.0 卷六·三·上 §3.8·T1-5）：写法七行 / NEW / 两开关当前值 / 追更 / 解锁时间 / 提醒文案 / 连载操作 / Tab 文案。 */
class StoryHubSupportTest {

    private fun draft(mode: String = StoryUpdateMode.FREE, hour: Int = 7, minute: Int = 5) = StorySettingsDraft(
        updateMode = mode, unlockHour = hour, unlockMinute = minute, genre = "都市", writingStyle = "细腻",
        narrativePerson = "second", chapterLengthPreference = 1500, chatInfluenceWeight = "medium", worldSetting = "", plotDirection = "",
    )

    @Test fun 写法七行_非档案类按注册表序一个不漏() {
        val expected = listOf(
            StoryEditableField.WRITER_IDENTITY, StoryEditableField.GENRE_TECHNIQUES, StoryEditableField.WRITING_RULES,
            StoryEditableField.BANNED_OVERRIDE, StoryEditableField.PACING, StoryEditableField.SCENE_BEATS, StoryEditableField.TASTE_PROFILE,
        )
        assertEquals(expected, storyHubCraftFields())
        assertTrue(storyHubCraftFields().none { it.kind == StoryFieldKind.ARCHIVE })
        assertEquals(StoryEditableField.entries.count { it.kind != StoryFieldKind.ARCHIVE }, storyHubCraftFields().size)
    }

    @Test fun 写法行NEW只挂场面节拍与口味画像() {
        assertTrue(storyHubCraftFieldIsNew(StoryEditableField.SCENE_BEATS))
        assertTrue(storyHubCraftFieldIsNew(StoryEditableField.TASTE_PROFILE))
        assertFalse(storyHubCraftFieldIsNew(StoryEditableField.WRITER_IDENTITY))
        assertFalse(storyHubCraftFieldIsNew(StoryEditableField.PACING))
    }

    @Test fun 两开关当前值_没解出prompts时章末选项关场景快照开_显式值跟随() {
        assertFalse(storyChapterChoicesOn(null))
        assertTrue(storySceneSnapshotOn(null))
        assertTrue(storyChapterChoicesOn(CustomStoryPrompts(chapterChoicesEnabled = true)))
        assertFalse(storySceneSnapshotOn(CustomStoryPrompts(sceneSnapshotEnabled = false)))
    }

    @Test fun 追更模式读写_其余字段不变() {
        assertTrue(storyDraftChase(draft(StoryUpdateMode.CHASE)))
        assertFalse(storyDraftChase(draft(StoryUpdateMode.FREE)))
        val on = storyDraftWithChase(draft(StoryUpdateMode.FREE), true)
        assertEquals(StoryUpdateMode.CHASE, on.updateMode)
        assertEquals(draft(StoryUpdateMode.FREE).copy(updateMode = StoryUpdateMode.CHASE), on)
        assertEquals(StoryUpdateMode.FREE, storyDraftWithChase(draft(StoryUpdateMode.CHASE), false).updateMode)
    }

    @Test fun 解锁时间两位补零_提醒文案() {
        assertEquals("07:05", storyUnlockTimeText(draft(hour = 7, minute = 5)))
        assertEquals(R.string.story_settings_reminder_on, storyReminderLabelRes(true))
        assertEquals(R.string.action_close, storyReminderLabelRes(false))
    }

    @Test fun 连载操作钮_完结与暂停才出() {
        assertEquals(StorySerialOps.COMPLETED, storySerialOps(StoryEntity(status = StoryStatus.COMPLETED)))
        assertEquals(StorySerialOps.PAUSED, storySerialOps(StoryEntity(status = StoryStatus.PAUSED)))
        assertNull(storySerialOps(StoryEntity(status = StoryStatus.SERIALIZING)))
        assertNull(storySerialOps(StoryEntity(status = StoryStatus.WAITING_CHOICE)))
    }

    @Test fun 双Tab文案() {
        assertEquals(R.string.story_hub_tab_archive, storyHubTabLabelRes(0))
        assertEquals(R.string.story_hub_tab_settings, storyHubTabLabelRes(1))
    }
}
