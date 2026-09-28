package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.Palette

/** 晨光柔光底的落值（琉璃 2.0 卷一图纸 §4.3·模拟器实拍台同款）。 */
internal object LiuliAmbientSpec {
    val glowsLight = listOf(Palette.DawnGlowPeach, Palette.DawnGlowLilac, Palette.DawnGlowSky, Palette.DawnGlowRose)
    val glowsDark = listOf(Palette.DuskGlowViolet, Palette.DuskGlowPlum, Palette.DuskGlowNavy, Palette.DuskGlowIndigo)

    /** 四个光晕中心（占宽 / 高比例）：左上 · 右中上 · 左中下 · 右下。 */
    val centers = listOf(Offset(0.12f, 0.10f), Offset(0.88f, 0.34f), Offset(0.08f, 0.66f), Offset(0.82f, 0.92f))

    /** 光晕半径 = 0.78 × 画布宽。 */
    const val RADIUS_FRACTION = 0.78f

    fun glows(dark: Boolean): List<Color> = if (dark) glowsDark else glowsLight
}

/**
 * 画柔光底：先铺 [base]（覆盖本次绘制区域），再按 [centers] 画四个径向光晕（中心满色 → 半径处透明）。
 * [canvas] = 光晕坐标的参照尺寸（默认本次绘制区域；导航行纸面带传整页尺寸，好与页面底严丝合缝）。
 */
internal fun DrawScope.drawLiuliAmbient(
    base: Color,
    glows: List<Color>,
    centers: List<Offset> = LiuliAmbientSpec.centers,
    canvas: Size = size,
) {
    drawRect(base)
    val radius = LiuliAmbientSpec.RADIUS_FRACTION * canvas.width
    glows.forEachIndexed { i, c ->
        val center = Offset(centers[i].x * canvas.width, centers[i].y * canvas.height)
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(c, c.copy(alpha = 0f)), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
}

/** 琉璃页面的柔光氛围底（不透明·可直接当宿主内容层的底）。 */
@Composable
fun LiuliAmbientBackground(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val glows = LiuliAmbientSpec.glows(colors.isDark)
    Canvas(modifier) { drawLiuliAmbient(colors.surface.base, glows) }
}
