package com.situ.aichat.ui.liuli.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * T2-2（卷二图纸 2026-09-25 §7）：半透明卡片的柔影**只在形状外**——卡内像素 = 卡底覆背景，影不许透进来
 * （卷一复核 R1 教训：Compose `shadow()` 画在形状下面，半透明面会把它透出来）。
 *
 * 量法：卡片压在纯红底上，量卡正中（顶沿高光只 1px、白边只在边缘，正中不受影响）。期望从规格反推：
 * 浅 = 白 52% 覆红 ≈ (255, 132, 132)；深 = #241F3A 58% 覆红 ≈ (128, 18, 34)。容差 ±3。
 * 另量卡底边下 4dp（柔影下移 8dp 的覆盖区）必须偏离纯红——证明影确实画出来了（全否定断言配正向证据）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LiuliSoftShadowPixelTest {

    @get:Rule
    val compose = createComposeRule()

    /** 画一张 200×100dp 卡在红底正中，返回（卡正中像素, 卡底边正下方 4dp 像素）。 */
    private fun cardOnRed(dark: Boolean): Pair<Int, Int> {
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                Box(Modifier.align(Alignment.Center).size(200.dp, 100.dp).liuliCardMaterial(RoundedCornerShape(20.dp), dark))
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val density = v.resources.displayMetrics.density
        val cx = v.width / 2
        val cy = v.height / 2
        val belowY = cy + ((50f + 4f) * density).toInt()
        return bmp.getPixel(cx, cy) to bmp.getPixel(cx, belowY)
    }

    private fun rgb(pixel: Int) = Triple((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

    private fun assertRgb(expected: Triple<Int, Int, Int>, pixel: Int, label: String) {
        val (r, g, b) = rgb(pixel)
        val ok = abs(r - expected.first) <= 3 && abs(g - expected.second) <= 3 && abs(b - expected.third) <= 3
        assertTrue("$label：实测 ($r, $g, $b)，期望 ≈ $expected（柔影不许透进卡内）", ok)
    }

    @Test fun lightCard_centerIsTranslucentWhiteOverRed_shadowOutsideOnly() {
        val (center, below) = cardOnRed(dark = false)
        assertRgb(Triple(255, 132, 132), center, "浅色半透明卡正中")
        val (r, g, b) = rgb(below)
        assertTrue("浅色卡底下 4dp 应落在柔影里（实测 ($r, $g, $b)，纯红 = 无影）", r < 250 || g > 4 || b > 4)
    }

    @Test fun darkCard_centerIsTranslucentDuskOverRed_shadowOutsideOnly() {
        val (center, below) = cardOnRed(dark = true)
        assertRgb(Triple(128, 18, 34), center, "深色半透明卡正中")
        val (r, _, _) = rgb(below)
        assertTrue("深色卡底下 4dp 应落在柔影里（实测 r = $r，纯红 = 255）", r < 250)
    }
}
