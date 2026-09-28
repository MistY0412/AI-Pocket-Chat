package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-S1–S4（琉璃 2.0 卷六·三·下乙 §7）：琉璃自由输入 / 上一版两弹层（经 [LiuliStoryReaderSheetFace] 入口）。
 * 期望值从暖陶两弹层行为与 zh 原文独立反推：trim 后为空置灰 / 交出 trim 文本并关 / 标题拼法 / 正文过清洗 / 换回二段式。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val confirmed = mutableListOf<String>()
    private var dismissCount = 0
    private var restoreCount = 0

    private fun host(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun showCustom() = host {
        LiuliStoryReaderSheetFace.CustomChoiceSheet(prompt = "她在等你开口。", hint = "你可以输入任何会影响剧情发展的内容。", onConfirm = { confirmed += it }, onDismiss = { dismissCount++ })
    }

    private fun showDraft(draft: StoryChapterDraft) = host {
        LiuliStoryReaderSheetFace.PreviousDraftSheet(draft = draft, onRestore = { restoreCount++ }, onDismiss = { dismissCount++ })
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun button(text: String) = compose.onNode(hasText(text) and isButton)

    @Test fun S1_自由输入_空文本确认置灰_trim后交出并关() {
        showCustom()
        compose.onNodeWithText("自由输入").assertExists()
        compose.onNodeWithContentDescription("输入自定义选择").assertExists()
        button("确定").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("  跟上去  ")
        compose.waitForIdle()
        button("确定").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf("跟上去"), confirmed)
        assertEquals(1, dismissCount)
    }

    @Test fun S2_自由输入_取消只关不交() {
        showCustom()
        button("取消").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissCount)
        assertEquals(emptyList<String>(), confirmed)
    }

    @Test fun S3_上一版_标题带旧稿标题_正文过清洗() {
        showDraft(StoryChapterDraft(title = "旧的七楼", content = "电梯门开了一半。[mood:tense]"))
        compose.onNodeWithText("上一版 · 旧的七楼").assertExists()
        compose.onNodeWithText("重写前保留的版本，可随时换回").assertExists()
        compose.onNodeWithText("电梯门开了一半。").assertExists()
        // 生肉零泄漏：沉浸标签一个字都不许上屏。
        compose.onAllNodesWithText("[mood", substring = true).assertCountEquals(0)
    }

    @Test fun S3_上一版_旧稿标题空白_只剩上一版() {
        showDraft(StoryChapterDraft(title = "  ", content = "电梯门开了一半。"))
        compose.onNodeWithText("上一版").assertExists()
        compose.onAllNodesWithText("上一版 · ", substring = true).assertCountEquals(0)
    }

    @Test fun S4_上一版_换回先确认_确认后才发() {
        showDraft(StoryChapterDraft(title = "旧的七楼", content = "电梯门开了一半。"))
        button("换回这一版").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("换回上一版？").assertExists()
        assertEquals(0, restoreCount)
        button("换回").performClick()
        compose.waitForIdle()
        assertEquals(1, restoreCount)
    }
}
