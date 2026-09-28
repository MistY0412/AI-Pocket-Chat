package com.situ.aichat.ui.liuli.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import com.situ.aichat.ui.liuli.designsystem.rememberLiuliInstantSheetState
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 三种独立窗口弹层壳（卷二图纸 2026-09-25 §4.7）。 */
enum class WindowShell { Sheet, Dialog, Menu }

/**
 * T2-3（卷二图纸 §7）：三种弹层壳 × 三档玻璃 × {壳放在 [LiuliGlassHost] 的 content 里、壳不在宿主里} 共 18 例——
 * 真玻璃分支（通透 / 标准 + 在宿主里）与兜底分支（毛玻璃档 / 不在宿主里）都能正常组合、壳内文字显示（E1–E4）。
 * 壳放在 **content** 里是最常见的弹出位置（行内菜单、删除确认·E3）：取景源两层都提供才拿得到。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliWindowGlassTest(private val shell: WindowShell, private val tier: GlassTier, private val inHost: Boolean) {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun ShellUnderTest() {
        when (shell) {
            WindowShell.Sheet -> LiuliSheetShell(onDismissRequest = {}, sheetState = rememberLiuliInstantSheetState()) {
                Text(SHELL_TEXT)
            }
            WindowShell.Dialog -> LiuliDialog(onDismissRequest = {}, title = SHELL_TEXT, body = "确认正文")
            WindowShell.Menu -> LiuliPopupMenu(expanded = true, onDismiss = {}, items = listOf(LiuliMenuEntry(text = SHELL_TEXT, onClick = {})))
        }
    }

    @Test fun shellTextIsDisplayed() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(
                    LocalAppHaptics provides mockk<AppHaptics>(relaxed = true),
                    LocalGlassTier provides tier,
                ) {
                    if (inHost) {
                        LiuliGlassHost(
                            modifier = Modifier.fillMaxSize(),
                            content = {
                                Box(Modifier.matchParentSize().background(Color.White)) { Text("身后的页面内容") }
                                ShellUnderTest()
                            },
                            overlay = {},
                        )
                    } else {
                        ShellUnderTest()
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(SHELL_TEXT).assertIsDisplayed()
    }

    companion object {
        private const val SHELL_TEXT = "弹层里的字"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}·{1}·inHost={2}")
        fun cases(): List<Array<Any>> = WindowShell.entries.flatMap { shell ->
            GlassTier.entries.flatMap { tier -> listOf(true, false).map { host -> arrayOf<Any>(shell, tier, host) } }
        }
    }
}

/** T2-3 另一例（§3.3）：宿主 `active` 时 content 与 overlay 都拿得到跨窗口取景源；`active = false` 时两边都为空。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiuliWindowGlassSourceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sourcesSeen(active: Boolean): Pair<LiuliGlassHostState?, LiuliGlassHostState?> {
        var inContent: LiuliGlassHostState? = null
        var inOverlay: LiuliGlassHostState? = null
        compose.setContent {
            LiuliGlassHost(
                modifier = Modifier.fillMaxSize(),
                active = active,
                content = { inContent = LocalLiuliWindowGlassSource.current },
                overlay = { inOverlay = LocalLiuliWindowGlassSource.current },
            )
        }
        compose.waitForIdle()
        return inContent to inOverlay
    }

    @Test fun activeHost_providesSourceToContentAndOverlay() {
        val (content, overlay) = sourcesSeen(active = true)
        assertNotNull("content 里应拿得到跨窗口取景源", content)
        assertNotNull("overlay 里应拿得到跨窗口取景源", overlay)
    }

    @Test fun inactiveHost_providesNoSource() {
        val (content, overlay) = sourcesSeen(active = false)
        assertNull("宿主关门时 content 不应拿到取景源", content)
        assertNull("宿主关门时 overlay 不应拿到取景源", overlay)
    }
}
