package com.situ.aichat.ui.liuli.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.designsystem.LiuliFabPill
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
 * T2-P1（琉璃 2.0 卷六·一 §3.10）：页壳的屏边渐进模糊带 + 随内容宽的浮动钮槽。量真件边框（`getUnclippedBoundsInRoot`），
 * Robolectric 状态栏 / 导航栏恒 0，所以量到的是 inset 以外那一段（期望从图纸算式独立复算）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliPageEdgeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun page(
        collapsed: Boolean = false,
        subBar: (@Composable () -> Unit)? = null,
        bottomEdge: Dp? = null,
        fab: (@Composable () -> Unit)? = null,
    ) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliPage(
                        title = "日记",
                        onBack = {},
                        collapsed = collapsed,
                        subBar = subBar,
                        fab = fab,
                        bottomEdge = bottomEdge,
                    ) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = LiuliPageGeometry.navRow)) {
                            item { Box { Text("正文") } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun 收起且有subBar时顶带伸到112() {
        page(collapsed = true, subBar = { Text("筛选条") })
        compose.onAllNodesWithTag(LIULI_NAV_BAND_TAG).assertCountEquals(1)
        val band = compose.onNodeWithTag(LIULI_NAV_BAND_TAG).getUnclippedBoundsInRoot()
        // 胶囊 44 + subBar 56 + 尾巴 12
        assertEquals(112f, (band.bottom - band.top).value, 0.01f)
    }

    @Test fun 不传bottomEdge时没有底带() {
        page()
        compose.onAllNodesWithTag(LIULI_BOTTOM_EDGE_TAG).assertCountEquals(0)
    }

    @Test fun 底带高84且贴屏底() {
        page(bottomEdge = 84.dp)
        compose.onAllNodesWithTag(LIULI_BOTTOM_EDGE_TAG).assertCountEquals(1)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val band = compose.onNodeWithTag(LIULI_BOTTOM_EDGE_TAG).getUnclippedBoundsInRoot()
        assertEquals(84f, (band.bottom - band.top).value, 0.5f)
        assertEquals(root.bottom.value, band.bottom.value, 0.5f)
    }

    @Test fun 胶囊浮动钮贴右下角且高48宽过56() {
        page(fab = { LiuliFabPill(icon = Icons.Filled.Edit, label = "写一笔", onClick = {}) })
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithText("写一笔").getUnclippedBoundsInRoot()
        assertEquals(LiuliPageGeometry.gutter.value, (root.right - pill.right).value, 0.5f)
        assertEquals(LiuliPageGeometry.fabBottom.value, (root.bottom - pill.bottom).value, 0.5f)
        assertEquals(48f, (pill.bottom - pill.top).value, 0.5f)
        assertTrue("胶囊随字宽，应比圆钮 56 宽：${pill.right - pill.left}", (pill.right - pill.left).value > 56f)
    }
}
