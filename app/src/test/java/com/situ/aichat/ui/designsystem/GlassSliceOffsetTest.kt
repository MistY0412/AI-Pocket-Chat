package com.situ.aichat.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import com.situ.aichat.ui.liuli.glass.LiuliGlassHost
import com.situ.aichat.ui.liuli.glass.LiuliGlassHostState
import com.situ.aichat.ui.liuli.glass.liuliSliceOffset
import com.situ.aichat.ui.liuli.glass.rememberLiuliGlassHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 玻璃切片偏移在「整页缩放」下必须不变（工具链轮三复核 R1 🔴-1·2026-09-24）。
 *
 * 场景复刻导航 2.10 的预测性返回：页面祖先挂 `graphicsLayer` 缩到 0.5。玻璃在页面里的布局位置是固定的
 * (40dp, 120dp)，切片偏移（暖陶按参照框、琉璃按宿主）必须恒等于这个布局位置；按根坐标算的旧口径会被缩放带偏——
 * 同一场景下断言旧口径 ≠ 布局位置，证明本测试抓得住回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassSliceOffsetTest {

    @get:Rule
    val compose = createComposeRule()

    // 300dp 落在 Robolectric 默认 320×470 屏内（记忆「屏太小假绿」：超出会被压小）。
    private val pageModifier = Modifier.size(300.dp).graphicsLayer { scaleX = 0.5f; scaleY = 0.5f }
    private val glassPos = Pair(40.dp, 120.dp)

    @Composable
    private fun GlassProbe(onCoords: (LayoutCoordinates) -> Unit) {
        Box(Modifier.offset(glassPos.first, glassPos.second).size(10.dp).onGloballyPositioned(onCoords))
    }

    private fun expected(): Offset = with(compose.density) {
        Offset(glassPos.first.toPx(), glassPos.second.toPx())
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }

    @Test
    fun 暖陶_参照框内偏移不受整页缩放影响_尺寸取框() {
        var frame: GlassFrameState? = null
        var self: LayoutCoordinates? = null
        compose.setContent {
            Box(pageModifier) {
                GlassFrame(Modifier.fillMaxSize()) {
                    frame = LocalGlassFrame.current
                    GlassProbe { self = it }
                }
            }
        }
        compose.waitForIdle()

        val glass = self!!
        val (off, refSize) = glassSliceOf(glass, frame!!.coords)
        assertOffset(expected(), off)
        val side = with(compose.density) { 300.dp.roundToPx() }
        assertEquals(side, refSize.width)
        assertEquals(side, refSize.height)

        // 旧口径（不给参照框 = 按窗口根坐标）在缩放下被带偏：这正是修复前的错位。
        val rootBased = glassSliceOf(glass, null).first
        assertNotEquals(expected().y, rootBased.y, 0.5f)
    }

    @Test
    fun 琉璃_宿主内偏移不受整页缩放影响() {
        var state: LiuliGlassHostState? = null
        var self: LayoutCoordinates? = null
        compose.setContent {
            // 切片偏移只在自研毛玻璃档用得上（琉璃 2.0 卷一图纸 §7 T2-4）。
            CompositionLocalProvider(LocalGlassTier provides GlassTier.FROSTED) {
                Box(pageModifier) {
                    val st = rememberLiuliGlassHostState().also { state = it }
                    LiuliGlassHost(modifier = Modifier.fillMaxSize(), state = st, content = {}, overlay = { GlassProbe { self = it } })
                }
            }
        }
        compose.waitForIdle()

        val glass = self!!
        val host = state!!.backdrop
        assertOffset(expected(), liuliSliceOffset(glass, host))

        // 旧口径 = 自身根坐标 - 宿主根坐标，在缩放下被带偏。
        val oldWay = glass.positionInRoot() - host.hostOrigin
        assertTrue("旧口径应被缩放带偏（${oldWay.y} vs ${expected().y}）", kotlin.math.abs(oldWay.y - expected().y) > 1f)
    }
}
