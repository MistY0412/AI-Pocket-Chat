package com.situ.aichat.ui.liuli.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * T2-8（琉璃 2.0 卷三 §7）：分段卡三段（顶 / 中 / 底·各 200×80dp·紧贴）压在纯红底上，逐像素量：
 * ① 三段正中 = 卡底覆红（浅 白 52% ≈ (255, 132, 132)·深 #241F3A 58% ≈ (128, 18, 34)）±3；
 * ② 两处接缝上下各 1dp 与段正中同值 ±3（段间无横边、无影）；③ 中段左缘外 6dp 偏离纯红（侧影在）；
 * ④ 底段下缘外 4dp 偏离纯红（底影在）；⑤ 顶段顶沿内 0.5dp 比正中亮（高光在）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LiuliCardSegmentPixelTest {

    @get:Rule
    val compose = createComposeRule()

    private class Shot(val bmp: Bitmap, val left: Float, val top: Float, val density: Float) {
        fun px(xDp: Float, yDp: Float): Int = bmp.getPixel((left + xDp * density).toInt(), (top + yDp * density).toInt())
    }

    private fun segmentsOnRed(dark: Boolean): Shot {
        var view: View? = null
        var origin = Offset.Zero
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                Column(Modifier.align(Alignment.Center).onGloballyPositioned { origin = it.positionInRoot() }) {
                    listOf(LiuliSegmentPosition.Top, LiuliSegmentPosition.Middle, LiuliSegmentPosition.Bottom).forEach {
                        Box(Modifier.size(200.dp, 80.dp).liuliCardSegment(it, dark))
                    }
                }
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return Shot(bmp, origin.x, origin.y, v.resources.displayMetrics.density)
    }

    private fun rgb(pixel: Int) = Triple((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

    private fun assertNear(expected: Triple<Int, Int, Int>, pixel: Int, label: String) {
        val (r, g, b) = rgb(pixel)
        val ok = abs(r - expected.first) <= 3 && abs(g - expected.second) <= 3 && abs(b - expected.third) <= 3
        assertTrue("$label：实测 ($r, $g, $b)，期望 ≈ $expected", ok)
    }

    /** 「偏离纯红」：三通道离 (255, 0, 0) 的总偏差 ≥ 3（纯红底渲染恒为精确 255 / 0 / 0，≥ 3 排除 ±1 舍入噪声）。 */
    private fun assertNotPureRed(pixel: Int, label: String) {
        val (r, g, b) = rgb(pixel)
        assertTrue("$label 应落在柔影里（实测 ($r, $g, $b)，纯红 = 无影）", (255 - r) + g + b >= 3)
    }

    private fun check(dark: Boolean, fill: Triple<Int, Int, Int>) {
        val s = segmentsOnRed(dark)
        val mode = if (dark) "深" else "浅"
        // ① 三段正中。
        listOf(40f, 120f, 200f).forEach { y -> assertNear(fill, s.px(100f, y), "$mode 段正中 y=$y") }
        // ② 两处接缝（80 / 160dp）上下各 1dp：无横边、无影。
        listOf(80f, 160f).forEach { seam ->
            assertNear(fill, s.px(100f, seam - 1f), "$mode 接缝 $seam 上 1dp")
            assertNear(fill, s.px(100f, seam + 1f), "$mode 接缝 $seam 下 1dp")
        }
        // ③ 中段左缘外 6dp：侧影。
        assertNotPureRed(s.px(-6f, 120f), "$mode 中段左缘外 6dp")
        // ④ 底段下缘外 4dp：底影。
        assertNotPureRed(s.px(100f, 244f), "$mode 底段下缘外 4dp")
        // ⑤ 顶段顶沿内 0.5dp 比正中亮（高光）。
        val (tr, tg, tb) = rgb(s.px(100f, 0.5f))
        val (cr, cg, cb) = rgb(s.px(100f, 40f))
        assertTrue("$mode 顶沿 ($tr, $tg, $tb) 应比正中 ($cr, $cg, $cb) 亮", tr + tg + tb > cr + cg + cb + 30)
    }

    @Test fun light_threeSegments_seamless_withOuterShadowAndHighlight() = check(dark = false, fill = Triple(255, 132, 132))

    @Test fun dark_threeSegments_seamless_withOuterShadowAndHighlight() = check(dark = true, fill = Triple(128, 18, 34))
}
