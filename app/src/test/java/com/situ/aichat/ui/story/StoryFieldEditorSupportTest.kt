package com.situ.aichat.ui.story

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.R
import com.situ.aichat.story.StoryEditableField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 字段编辑页共用件（琉璃 2.0 卷六·三·上 §3.5·T1-4）：返回判据 / 三态文案 / 内容区副标题。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class StoryFieldEditorSupportTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(field: StoryEditableField?, bookTitle: String = "对面楼的灯", dirty: Boolean = false) = StoryFieldEditorState(
        field = field,
        bookTitle = bookTitle,
        mode = StoryFieldMode.CUSTOM,
        text = "",
        inheritedText = null,
        factoryDefault = null,
        dirty = dirty,
    )

    @Test fun 返回判据_只有未保存改动才问() {
        assertFalse(storyFieldEditorNeedsConfirm(null))
        assertFalse(storyFieldEditorNeedsConfirm(state(StoryEditableField.WRITER_IDENTITY, dirty = false)))
        assertTrue(storyFieldEditorNeedsConfirm(state(StoryEditableField.WRITER_IDENTITY, dirty = true)))
    }

    @Test fun 三态文案() {
        assertEquals(R.string.story_field_mode_follow, storyFieldModeLabelRes(StoryFieldMode.FOLLOW))
        assertEquals(R.string.story_field_mode_custom, storyFieldModeLabelRes(StoryFieldMode.CUSTOM))
        assertEquals(R.string.story_field_mode_off, storyFieldModeLabelRes(StoryFieldMode.OFF))
    }

    @Test fun 副标题_全局无_档案用书名_空白书名无_写法字段是本书() {
        val got = arrayOfNulls<String>(4)
        compose.setContent {
            got[0] = storyFieldEditorSubtitle(state(null))
            got[1] = storyFieldEditorSubtitle(state(StoryEditableField.OUTLINE, bookTitle = "对面楼的灯"))
            got[2] = storyFieldEditorSubtitle(state(StoryEditableField.OUTLINE, bookTitle = "  "))
            got[3] = storyFieldEditorSubtitle(state(StoryEditableField.WRITER_IDENTITY))
        }
        compose.waitForIdle()
        assertNull(got[0])
        assertEquals("对面楼的灯", got[1])
        assertNull(got[2])
        assertEquals("本书", got[3])
    }
}
