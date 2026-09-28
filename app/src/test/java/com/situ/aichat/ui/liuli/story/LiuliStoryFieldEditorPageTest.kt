package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryEditableField
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryFieldEditorState
import com.situ.aichat.ui.story.StoryFieldMode
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-F1–F2（琉璃 2.0 卷六·三·上 §7）：保存栏两钮条件与回调 / 返回 / 三态段三种正文区。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryFieldEditorPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var restores = 0
    private var saves = 0
    private var leaves = 0
    private val texts = mutableListOf<String>()

    private fun state(field: StoryEditableField, mode: StoryFieldMode, factoryDefault: String?) = StoryFieldEditorState(
        field = field, bookTitle = "对面楼的灯", mode = mode, text = "", inheritedText = "出厂节拍全文",
        factoryDefault = factoryDefault, dirty = false,
    )

    private fun show(s: StoryFieldEditorState, saving: Boolean = false) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryFieldEditorPage(
                        title = "场面节拍", state = s, saving = saving, onLeave = { leaves++ }, onSetMode = {}, onSetText = { texts += it },
                        onApplyPreset = {}, onRequestRestore = { restores++ }, onSave = { saves++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun F1_无出厂值不出恢复默认() {
        show(state(StoryEditableField.WRITER_IDENTITY, StoryFieldMode.CUSTOM, factoryDefault = null))
        compose.onAllNodesWithText("恢复默认").assertCountEquals(0)
    }

    @Test fun F1_有出厂值出恢复默认_保存与返回各回调() {
        show(state(StoryEditableField.WRITER_IDENTITY, StoryFieldMode.CUSTOM, factoryDefault = "出厂身份"))
        compose.onNodeWithText("恢复默认").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1), listOf(restores, saves, leaves))
    }

    @Test fun F1_保存中保存钮禁用() {
        show(state(StoryEditableField.WRITER_IDENTITY, StoryFieldMode.CUSTOM, factoryDefault = null), saving = true)
        compose.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test fun F2_跟随全局_出继承预览且无输入框() {
        show(state(StoryEditableField.SCENE_BEATS, StoryFieldMode.FOLLOW, factoryDefault = null))
        compose.onNodeWithText("跟随全局").assertExists()
        compose.onNodeWithText("当前生效（全局 / 出厂默认）").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun F2_本书自定义_输入框可写() {
        show(state(StoryEditableField.SCENE_BEATS, StoryFieldMode.CUSTOM, factoryDefault = null))
        compose.onNode(hasSetTextAction()).performTextInput("甜")
        compose.waitForIdle()
        assertTrue(texts.any { it.contains("甜") })
    }

    @Test fun F2_本书关闭_出说明句() {
        show(state(StoryEditableField.SCENE_BEATS, StoryFieldMode.OFF, factoryDefault = null))
        compose.onNodeWithText("已关闭——本书不注入这一段").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
