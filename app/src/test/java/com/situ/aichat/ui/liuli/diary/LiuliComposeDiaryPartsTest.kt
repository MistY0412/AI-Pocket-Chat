package com.situ.aichat.ui.liuli.diary

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.toSize
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.diary.ComposeDiaryState
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-C1–C5（琉璃 2.0 卷六·一 §4.4）：写日记底条的三态门控（同暖陶动作条：空稿 / 有字 / TA 的信 / 九张图）+
 * 稿纸框与底条的几何（Robolectric 状态栏 / 导航栏 / 键盘恒 0）+ 录音卡浮在底条上方。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliComposeDiaryPartsTest {

    @get:Rule
    val compose = createComposeRule()

    private var records = 0
    private var drafts = 0

    @Composable
    private fun bar(state: ComposeDiaryState) = LiuliComposeDiaryBar(
        state = state,
        onAddImage = {},
        onToggleVisibility = {},
        onAiAssist = {},
        onSaveDraft = { drafts++ },
        onRecord = { records++ },
        onStartVoice = {},
        onVoiceDrag = {},
        onFinishVoice = {},
    )

    private fun host(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) { content() }
            }
        }
        compose.waitForIdle()
    }

    @Test fun C1_空稿记下禁用无先存着无AI改写() {
        host { bar(ComposeDiaryState(content = "")) }
        compose.onNodeWithText("记下").assertIsNotEnabled()
        compose.onAllNodesWithText("先存着").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("AI 帮写").assertCountEquals(0)
    }

    @Test fun C2_有字三件都在且回调() {
        host { bar(ComposeDiaryState(content = "今天风很大")) }
        compose.onNodeWithText("记下").assertIsEnabled().performClick()
        compose.onNodeWithText("先存着").performClick()
        compose.waitForIdle()
        assertEquals(1, records)
        assertEquals(1, drafts)
        compose.onNodeWithContentDescription("AI 帮写").assertExists()
    }

    @Test fun C2_编辑TA的信没有AI改写() {
        host { bar(ComposeDiaryState(content = "他今天说风好大", isExchangeLetter = true)) }
        compose.onAllNodesWithContentDescription("AI 帮写").assertCountEquals(0)
    }

    @Test fun C3_九张图加图钮禁用() {
        host { bar(ComposeDiaryState(content = "有图", images = List(9) { "/img/$it.jpg" })) }
        compose.onNodeWithContentDescription("添加").assertIsNotEnabled()
    }

    /** 稿纸的真边框：`liuliPaperMaterial` 里的裁形状层（graphicsLayer）住在「左右 12 · 顶 56 · 底 80」的内距之后。 */
    private fun paperBounds(): Rect {
        val info = compose.onNodeWithTag("paper").fetchSemanticsNode().layoutInfo.getModifierInfo()
        val clip = info.first { it.modifier::class.java.simpleName.contains("GraphicsLayer") }
        val px = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val topLeft = clip.coordinates.positionInRoot()
        val size = clip.coordinates.size.toSize()
        return Rect(topLeft.x / px, topLeft.y / px, (topLeft.x + size.width) / px, (topLeft.y + size.height) / px)
    }

    @Test fun C4_稿纸框几何且不压底条() {
        host {
            LiuliPage(
                title = "日记",
                onBack = {},
                collapsed = false,
                bottomBar = { Box(Modifier.testTag("bar")) { bar(ComposeDiaryState(content = "今天风很大")) } },
            ) {
                LiuliComposePaper(Modifier.testTag("paper")) { Text("纸上的字") }
            }
        }
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val paper = paperBounds()
        assertEquals(12f, paper.left - root.left.value, 0.5f)
        assertEquals(12f, root.right.value - paper.right, 0.5f)
        assertEquals("顶 = 导航行 44 + 12", 56f, paper.top - root.top.value, 0.5f)
        assertEquals("底 = 底条占位 68 + 12", 80f, root.bottom.value - paper.bottom, 0.5f)
        val barBox = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        assertTrue("纸底 ${paper.bottom} 应在底条顶 ${barBox.top} 之上", paper.bottom <= barBox.top.value + 0.5f)
    }

    @Test fun C5_录音卡浮在底条上方() {
        host {
            LiuliPage(
                title = "日记",
                onBack = {},
                collapsed = false,
                bottomBar = {
                    LiuliComposeDiaryBottom(voiceRecording = true, voiceLevel = 0.5f, voiceDurationMs = 3_000L, voiceCancelling = false) {
                        Box(Modifier.testTag("bar")) { bar(ComposeDiaryState(content = "")) }
                    }
                },
            ) {}
        }
        val hint = compose.onNodeWithText("上滑取消").getUnclippedBoundsInRoot()
        val barBox = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        assertTrue("录音文案底 ${hint.bottom} 应不低于底条顶 ${barBox.top}", hint.bottom.value <= barBox.top.value + 0.5f)
    }
}
