package com.situ.aichat.ui.liuli.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.glass.LiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.liuliLensUsesHaze
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T2-7（琉璃 2.0 卷四 §4.5 / §7）：分段条选中片 = 画出来的透镜。深灰底上（放大透镜的明暗差）量选中片（第 0 段）：
 * ① 上 1/4 处比下 1/4 处亮（上亮下透）；② 左缘内 0.5dp（xhdpi 下恰 1px）比片中亮（0.5dp 白 rim）。
 * Glass 样式在无宿主（测试 = 内容层）时同样走画出来的透镜：不崩且同样上亮下透。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
class LiuliSegmentedLensTest {

    @get:Rule
    val compose = createComposeRule()

    private class Shot(val bmp: Bitmap, val origin: Offset, val density: Float) {
        fun sum(xDp: Float, yDp: Float): Int {
            val p = bmp.getPixel((origin.x + xDp * density).toInt(), (origin.y + yDp * density).toInt())
            return ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
        }
    }

    private fun render(style: LiuliSegmentedStyle): Shot {
        var view: View? = null
        var origin = Offset.Zero
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF303030))) {
                        Box(Modifier.align(Alignment.Center).width(300.dp).padding(vertical = 12.dp)) {
                            LiuliSegmented(
                                modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() },
                                options = listOf("甲", "乙", "丙"),
                                selected = "甲",
                                label = { it },
                                onSelect = {},
                                style = style,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return Shot(bmp, origin, v.resources.displayMetrics.density)
    }

    @Test fun paper_thumbIsPaintedLens_topBrighter_withRim() {
        val s = render(LiuliSegmentedStyle.Paper)
        // 纸面：轨 36 高、内边 3 → 片 = (3, 3) 起、高 30、宽 (300 − 6) / 3 = 98。避开文字取片右侧 1/4 宽处。
        val x = 3f + 98f * 0.85f
        val top = s.sum(x, 3f + 30f * 0.25f)
        val bottom = s.sum(x, 3f + 30f * 0.75f)
        assertTrue("上 1/4 ($top) 应比下 1/4 ($bottom) 亮", top > bottom + 15)
        val rim = s.sum(3f + 0.25f, 18f)
        val mid = s.sum(x, 18f)
        assertTrue("左缘内 0.5dp ($rim) 应比片中 ($mid) 亮", rim > mid)
    }

    @Test fun glass_withoutHost_fallsBackToPaintedLens() {
        assertFalse(liuliLensUsesHaze(hasHost = false, engine = LiuliGlassEngine.HAZE))
        val s = render(LiuliSegmentedStyle.Glass)
        // 玻璃态：40 高、内边 4 → 片 = (4, 4) 起、高 32、宽 (300 − 8) / 3 ≈ 97.3。
        val x = 4f + 97f * 0.85f
        val top = s.sum(x, 4f + 32f * 0.25f)
        val bottom = s.sum(x, 4f + 32f * 0.75f)
        assertTrue("玻璃态无宿主：上 1/4 ($top) 应比下 1/4 ($bottom) 亮（画出来的透镜）", top > bottom + 15)
    }
}
