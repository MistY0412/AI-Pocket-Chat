package com.situ.aichat.ui.liuli.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-1 / T2-2：琉璃统一玻璃宿主（琉璃 2.0 卷一图纸 2026-09-25 §3.3 / §7·取代旧的宿主测试）。
 *
 * 钉：① 三档下 content 与 overlay 里三种角色的玻璃片同时渲染（sdk 34 ≥ 33 → 标准 / 通透走 Haze 分支）；
 * ② overlay 拿得到宿主、content 拿不到；③ 宿主外 / 显式关模糊的玻璃退着色不崩（E5 / E6）；④ 安卓 11 下
 * 标准档照常渲染（E4）；⑤ 开着页面切档不丢内容（E8）；⑥ 宿主关门后 overlay 拿到 null、两槽照常（E7）。
 * Robolectric 不跑真 draw 合成，「看起来对不对」归装机 T4。真机尺寸 qualifiers 防节点被推出可视区（PITFALLS §1e）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliGlassHostTest {

    @get:Rule
    val compose = createComposeRule()

    /** overlay 里三种角色各一片玻璃、各带一行字。 */
    @Composable
    private fun ThreeRoles(dark: Boolean = false) {
        Column {
            Box(Modifier.size(width = 200.dp, height = 44.dp).liuliGlass(LiuliShapes.pill, dark = dark)) {
                Text("条上的字")
            }
            Box(Modifier.size(200.dp).liuliGlass(LiuliShapes.overlay, dark = dark, role = LiuliGlassRole.Panel)) {
                Text("面板上的字")
            }
            Box(Modifier.size(60.dp).liuliGlass(CircleShape, dark = dark, role = LiuliGlassRole.Button)) {
                Text("钮")
            }
        }
    }

    private fun assertAllFourShown() {
        compose.onNodeWithText("内容").assertIsDisplayed()
        compose.onNodeWithText("条上的字").assertIsDisplayed()
        compose.onNodeWithText("面板上的字").assertIsDisplayed()
        compose.onNodeWithText("钮").assertIsDisplayed()
    }

    private fun showTier(tier: GlassTier) {
        compose.setContent {
            CompositionLocalProvider(LocalGlassTier provides tier) {
                LiuliGlassHost(
                    modifier = Modifier.fillMaxSize(),
                    content = { Text("内容") },
                    overlay = { ThreeRoles() },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun frosted_contentAndThreeRolesRender() {
        showTier(GlassTier.FROSTED)
        assertAllFourShown()
    }

    @Test fun standard_contentAndThreeRolesRender() {
        showTier(GlassTier.STANDARD)
        assertAllFourShown()
    }

    @Test fun sheer_contentAndThreeRolesRender() {
        showTier(GlassTier.SHEER)
        assertAllFourShown()
    }

    @Test fun overlaySeesHost_contentDoesNot() {
        var inOverlay: LiuliGlassHostState? = null
        var inContent: LiuliGlassHostState? = null
        var contentRan = false
        compose.setContent {
            LiuliGlassHost(
                modifier = Modifier.fillMaxSize(),
                content = {
                    inContent = LocalLiuliGlassHost.current
                    contentRan = true
                    Text("内容")
                },
                overlay = {
                    inOverlay = LocalLiuliGlassHost.current
                    Text("浮层")
                },
            )
        }
        compose.waitForIdle()
        assertTrue("content 槽必须真的组合过", contentRan)
        assertNotNull("overlay 里的玻璃片靠它取景", inOverlay)
        assertNull("content 里不该拿到宿主（放进 content 会取到自己）", inContent)
    }

    @Test fun glassOutsideHost_lightAndDark_degradeWithoutCrashing() {
        compose.setContent {
            Column {
                Box(Modifier.size(80.dp).liuliGlass(CircleShape, dark = false)) { Text("无宿主浅") }
                Box(Modifier.size(80.dp).liuliGlass(CircleShape, dark = true)) { Text("无宿主深") }
            }
        }
        compose.onNodeWithText("无宿主浅").assertIsDisplayed()
        compose.onNodeWithText("无宿主深").assertIsDisplayed()
    }

    @Test fun blurDisabledGlassInOverlay_stillShowsText() {
        compose.setContent {
            LiuliGlassHost(
                modifier = Modifier.fillMaxSize(),
                content = { Text("内容") },
                overlay = {
                    Box(
                        Modifier.size(200.dp)
                            .liuliGlass(LiuliShapes.overlay, dark = false, role = LiuliGlassRole.Panel, blurEnabled = false),
                    ) { Text("关模糊的面板") }
                },
            )
        }
        compose.onNodeWithText("内容").assertIsDisplayed()
        compose.onNodeWithText("关模糊的面板").assertIsDisplayed()
    }

    @Config(sdk = [30], qualifiers = "zh-rCN-w411dp-h891dp")
    @Test fun android11_standardTier_rendersAsTintOnly() {
        showTier(GlassTier.STANDARD)
        assertAllFourShown()
    }

    @Test fun switchingTierWhileShown_keepsContentAndGlass() {
        var tier by mutableStateOf(GlassTier.FROSTED)
        compose.setContent {
            CompositionLocalProvider(LocalGlassTier provides tier) {
                LiuliGlassHost(
                    modifier = Modifier.fillMaxSize(),
                    content = { Text("内容") },
                    overlay = { ThreeRoles(dark = true) },
                )
            }
        }
        compose.waitForIdle()
        assertAllFourShown()
        listOf(GlassTier.STANDARD, GlassTier.SHEER, GlassTier.FROSTED).forEach { next ->
            compose.runOnIdle { tier = next }
            compose.waitForIdle()
            assertAllFourShown()
        }
    }

    @Test fun closingTheGate_givesOverlayNullHost_andBothSlotsStillRender() {
        var active by mutableStateOf(true)
        var seen: LiuliGlassHostState? = null
        compose.setContent {
            LiuliGlassHost(
                modifier = Modifier.fillMaxSize(),
                active = active,
                content = { Text("内容") },
                overlay = {
                    seen = LocalLiuliGlassHost.current
                    Box(Modifier.size(60.dp).liuliGlass(CircleShape, dark = false, role = LiuliGlassRole.Button)) {
                        Text("钮")
                    }
                },
            )
        }
        compose.waitForIdle()
        assertNotNull("开门时 overlay 拿得到宿主", seen)
        compose.runOnIdle { active = false }
        compose.waitForIdle()
        assertNull("关门后 overlay 拿到 null 宿主（玻璃退着色）", seen)
        compose.onNodeWithText("内容").assertIsDisplayed()
        compose.onNodeWithText("钮").assertIsDisplayed()
    }
}
