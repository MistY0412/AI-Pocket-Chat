package com.situ.aichat.ui.liuli.glass

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
 * 卷一复核 R1 🔴-1：「只着色兜底」的玻璃必须**整片不透明**——它画在形状下面的投影才透不上来（扫码页实拍：圆钮里
 * 八角亮斑、底条里亮内框）。量法：兜底玻璃压在纯红底上，量玻璃正中（高光带只到 42% 高，正中不受它影响）。
 *
 * 期望从规格反推（不引用实现常量）：浅 = 白 88% 覆白垫底 = 纯白；深 = #1E1A30 86% 覆 #1D1A2E 垫底 ≈ (30, 26, 48)。
 * 修复前浅色会是 88% 白 + 12% 红 ≈ (255, 224, 224)——红通道之外的两个通道就是判据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LiuliGlassFallbackPixelTest {

    @get:Rule
    val compose = createComposeRule()

    private fun centerPixelOfFallbackGlassOnRed(dark: Boolean): Int {
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                // 不在任何宿主里 → 只着色兜底（E5）。
                Box(Modifier.align(Alignment.Center).size(200.dp, 100.dp).liuliGlass(RoundedCornerShape(24.dp), dark = dark))
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp.getPixel(v.width / 2, v.height / 2)
    }

    private fun assertRgb(expected: Triple<Int, Int, Int>, pixel: Int, label: String) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val ok = abs(r - expected.first) <= 3 && abs(g - expected.second) <= 3 && abs(b - expected.third) <= 3
        assertTrue("$label：实测 ($r, $g, $b)，期望 ≈ $expected（身后的红不许透上来）", ok)
    }

    @Test fun lightFallbackGlass_isOpaqueWhite_noRedBleed() {
        assertRgb(Triple(255, 255, 255), centerPixelOfFallbackGlassOnRed(dark = false), "浅色兜底玻璃正中")
    }

    @Test fun darkFallbackGlass_isOpaqueDuskFrost_noRedBleed() {
        assertRgb(Triple(30, 26, 48), centerPixelOfFallbackGlassOnRed(dark = true), "深色兜底玻璃正中")
    }
}
