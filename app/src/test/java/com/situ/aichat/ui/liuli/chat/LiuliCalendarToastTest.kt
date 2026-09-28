package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-6（琉璃 2.0 卷四 §0.2-11 / §4.3 / §7）：日历提示顶距 = `topPadding`（原来落在窗口顶 8dp、被顶栏盖住）。
 * mdpi（1dp = 1px）：提示文字顶 ≥ 容器顶 + 120，且仍在 120 + 条高 38 之内；`text = null` 不显示。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
class LiuliCalendarToastTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(text: String?) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize().testTag("host")) {
                        LiuliCalendarToast(
                            text = text,
                            isDelete = false,
                            reduceMotion = true,
                            topPadding = 120.dp,
                            onDismiss = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun toastSitsBelowTopPadding() {
        show("已添加到日历")
        val host = compose.onNodeWithTag("host").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("已添加到日历", substring = true).getUnclippedBoundsInRoot()
        val top = label.top.value - host.top.value
        assertTrue("提示文字顶距 $top 应 ≥ 120 − 0.5", top >= 119.5f)
        assertTrue("提示文字顶距 $top 应 < 120 + 38（仍在条内）", top < 158f)
    }

    @Test fun nullText_notShown() {
        show(null)
        compose.onNodeWithText("已添加到日历", substring = true).assertDoesNotExist()
    }
}
