package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryTextSheetSpec
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-X1（琉璃 2.0 卷六·三·上 §7）：文本弹层上限截断 + 计数 / 填入默认 / 确认交回当前文本。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryTextEditorSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun X1_上限截断计数_填入默认_确认() {
        val confirmed = mutableListOf<String>()
        var dismissed = 0
        val spec = StoryTextSheetSpec(
            title = "写作身份", subtitle = null, placeholder = "写点什么", initialText = "", maxLength = 5,
            fillDefaultLabel = "填入默认", fillDefault = { "默认" }, onConfirm = { confirmed += it },
        )
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryTextEditorSheet(spec) { dismissed++ }
                }
            }
        }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("一二三四五六七八")
        compose.waitForIdle()
        field.assertTextEquals("一二三四五", includeEditableText = true)
        compose.onNodeWithText("5/5").assertExists()
        compose.onNodeWithText("填入默认").performClick()
        compose.waitForIdle()
        field.assertTextEquals("默认", includeEditableText = true)
        compose.onNodeWithText("确定").performClick()
        compose.waitForIdle()
        assertEquals(listOf("默认"), confirmed)
        assertEquals(1, dismissed)
    }
}
