package com.situ.aichat.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppShapes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-4（琉璃 2.0 卷三 §7）：暖陶图片泡（卷三起琉璃也用它）。① 文件没了 → 占位块「图片已失效」② 合并朗读句压成
 * 一个停 ③ 点击 / 长按各恰一次——**缺失态也可点**（三态都可点·卷二C D-3 的旧 TODO 由此结案）。
 * 缩略图经真后台线程读盘（`produceState`）→ 显式 `waitUntil`（PITFALLS §1e）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class ChatImageBubbleTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var clicks = 0
    private var longClicks = 0

    private fun setBubble(a11y: String?) {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                ChatImageBubble(
                    imagePath = "chat_images/does-not-exist.jpg",
                    thumbnailPath = null,
                    shape = AppShapes.bubble,
                    maxWidth = 300.dp,
                    onClick = { clicks++ },
                    onLongClick = { longClicks++ },
                    a11yDescription = a11y,
                )
            }
        }
    }

    @Test fun missingFile_showsQuietPlaceholderLabel() {
        setBubble(a11y = null)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("图片已失效").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("图片已失效").assertIsDisplayed()
    }

    @Test fun mergedSentence_isASingleStop() {
        setBubble(a11y = "你在刚才说：[图片]")
        compose.onAllNodesWithContentDescription("你在刚才说：[图片]").assertCountEquals(1)
        // 整块被压成一个停：占位文案不再单独进语义树。
        compose.onNodeWithText("图片已失效").assertDoesNotExist()
    }

    @Test fun click_firesOnce_evenWhenMissing() {
        setBubble(a11y = "你在刚才说：[图片]")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("你在刚才说：[图片]").performClick()
        assertEquals(1, clicks)
    }

    @Test fun longClick_firesOnce() {
        setBubble(a11y = "你在刚才说：[图片]")
        compose.onNodeWithContentDescription("你在刚才说：[图片]").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals("长按不串到单击", 0, clicks)
    }
}
