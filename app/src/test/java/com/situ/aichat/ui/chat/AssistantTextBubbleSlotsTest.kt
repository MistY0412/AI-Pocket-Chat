package com.situ.aichat.ui.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
 * T2-4（琉璃 2.0 卷三 §3.9）：暖陶 AI 文字泡的两个加法口子——`body`（正文槽）与 `onDoubleClick`。
 * 两个都不传 = 改前原样（正文 `Text` 在、双击无回调）；未显形时双击 / 长按都不回调（原闸）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class AssistantTextBubbleSlotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var doubles = 0
    private var longs = 0

    private fun setBubble(revealed: Boolean = true, withSlots: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                if (withSlots) {
                    AssistantTextBubble(
                        revealed = revealed,
                        text = "原文",
                        quotedContent = null,
                        quotedSender = null,
                        shape = AppShapes.bubble,
                        maxWidth = 300.dp,
                        onLongClick = { longs++ },
                        onDoubleClick = { doubles++ },
                        body = { Text("槽里的正文") },
                    )
                } else {
                    AssistantTextBubble(
                        revealed = revealed,
                        text = "原文",
                        quotedContent = null,
                        quotedSender = null,
                        shape = AppShapes.bubble,
                        maxWidth = 300.dp,
                        onLongClick = { longs++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun body_replacesDefaultText() {
        setBubble(withSlots = true)
        compose.onNodeWithText("槽里的正文").assertIsDisplayed()
        compose.onNodeWithText("原文").assertDoesNotExist()
    }

    @Test fun doubleClick_firesOnce() {
        setBubble(withSlots = true)
        compose.onNodeWithText("槽里的正文").performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(1, doubles)
    }

    @Test fun noSlots_isUnchanged_textPresent_noDoubleCallback() {
        setBubble(withSlots = false)
        compose.onNodeWithText("原文").assertIsDisplayed()
        compose.onNodeWithText("原文").performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(0, doubles)
    }

    /**
     * 未显形：长按不弹菜单（组件内原闸 `if (revealed)`）。双击的「只挂已显形」闸在**调用方**——§3.9 锁定写法把
     * `onDoubleClick` 原样交给 `combinedClickable`，琉璃行只在已显形时才传非 null（`LiuliMessageRow.onDoubleReact`），
     * 那一半由 `LiuliMessageRowTest.unrevealedAi_doubleTap_doesNotReact` 钉（§11 D-3）。
     */
    @Test fun unrevealed_ignoresLongClick() {
        setBubble(revealed = false, withSlots = true)
        compose.onNodeWithContentDescription("正在输入", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("未显形不弹长按菜单", 0, longs)
    }
}
