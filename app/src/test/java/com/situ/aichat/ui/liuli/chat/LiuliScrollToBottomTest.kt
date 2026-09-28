package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
 * T2-5（琉璃 2.0 卷四 §4.2 / §7）：回底钮住 overlay 后由 `liftPx`（面板 / 键盘高度）整体抬升。mdpi（1dp = 1px）：
 * 钮视觉底 = 容器底 − 68dp − liftPx；点击回调恰一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
class LiuliScrollToBottomTest {

    @get:Rule
    val compose = createComposeRule()

    private var clicks = 0

    private fun show(lift: Int) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize().testTag("host")) {
                        LiuliScrollToBottom(
                            visible = true,
                            reduceMotion = true,
                            bottomPadding = 68.dp,
                            liftPx = { lift },
                            onClick = { clicks++ },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** 容器底 − 钮**视觉**底：语义节点是 48dp 触达框（居中外溢），视觉 40dp 圆的底 = 触达框中心 + 20。 */
    private fun gap(): Float {
        val host = compose.onNodeWithTag("host").getUnclippedBoundsInRoot()
        val fab = compose.onNodeWithContentDescription("滚动到底部").getUnclippedBoundsInRoot()
        val visualBottom = (fab.top.value + fab.bottom.value) / 2f + 20f
        return host.bottom.value - visualBottom
    }

    @Test fun lifted_bottomIsHostBottomMinusPaddingMinusLift() {
        show(lift = 300)
        assertEquals(68f + 300f, gap(), 0.5f)
    }

    @Test fun notLifted_bottomIsHostBottomMinusPadding() {
        show(lift = 0)
        assertEquals(68f, gap(), 0.5f)
    }

    @Test fun click_firesOnce() {
        show(lift = 300)
        compose.onNodeWithContentDescription("滚动到底部").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
