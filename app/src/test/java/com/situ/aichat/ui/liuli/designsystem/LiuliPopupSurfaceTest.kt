package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-P1：自 [LiuliPopupMenu] 抽出的玻璃弹出层底座 [LiuliPopupSurface]——
 * 展开时渲染调用方给的任意内容，收起时整层不在树上（[LiuliPopupMenu] 原有行为由 `LiuliPopupMenuTest` 原样钉住）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliPopupSurfaceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) { content() }
            }
        }
    }

    @Test
    fun expanded_rendersCallerContent() {
        show { LiuliPopupSurface(expanded = true, onDismiss = {}) { Text("试") } }
        compose.onNodeWithText("试").assertIsDisplayed()
    }

    @Test
    fun collapsed_rendersNothing() {
        show { LiuliPopupSurface(expanded = false, onDismiss = {}) { Text("试") } }
        compose.onNodeWithText("试").assertDoesNotExist()
    }
}
