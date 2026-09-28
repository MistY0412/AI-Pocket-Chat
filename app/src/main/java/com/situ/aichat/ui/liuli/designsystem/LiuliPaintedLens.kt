package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 底栏当前 Tab 的「玻璃透镜」丸落值（用户 2026-09-06 拍板「丸甲」·对版稿 `liuli_tab_pill_mockup.html` D 表）。
 *
 * iOS 26 的选中态不是主题色平涂，而是比底栏再亮一层的玻璃：上亮下透的白 + 迎光顶沿 + 0.5dp 白 rim +
 * 底沿一点内影（厚度）+ 很轻的影；钴蓝只落在图标和字上（[LiuliTabBar] 的 `accent.text`）。
 */
internal object LiuliPaintedLensSpec {
    const val FILL_TOP_LIGHT = 0.92f
    const val FILL_BOTTOM_LIGHT = 0.55f
    const val FILL_TOP_DARK = 0.16f
    const val FILL_BOTTOM_DARK = 0.07f
    val shadeHeight = 6.dp
    const val SHADE_LIGHT = 0.06f // 昼：钴蓝 6%
    const val SHADE_DARK = 0.25f // 夜：黑 25%
    const val SPECULAR_LIGHT = 1f
    const val SPECULAR_DARK = 0.22f
    const val RIM_LIGHT = 0.90f
    const val RIM_DARK = 0.18f
    val rimWidth = 0.5.dp
}

/**
 * 画出来的透镜（卷四：自 `LiuliTabPill.liuliTabLens` 搬家·原值）：影（只画在形状外）→ 裁 [shape] → 底渐变 →
 * 底沿内影 → 顶沿 1px 迎光 → 0.5dp rim。底栏在非 Haze 引擎下、页面里的分段条滑块都用它。
 */
fun Modifier.liuliPaintedLens(shape: Shape, dark: Boolean): Modifier = composed {
    val colors = AppTheme.colors
    val fillTop = Color.White.copy(alpha = if (dark) LiuliPaintedLensSpec.FILL_TOP_DARK else LiuliPaintedLensSpec.FILL_TOP_LIGHT)
    val fillBottom = Color.White.copy(alpha = if (dark) LiuliPaintedLensSpec.FILL_BOTTOM_DARK else LiuliPaintedLensSpec.FILL_BOTTOM_LIGHT)
    val shade = if (dark) Color.Black.copy(alpha = LiuliPaintedLensSpec.SHADE_DARK) else colors.accent.primary.copy(alpha = LiuliPaintedLensSpec.SHADE_LIGHT)
    val specular = Color.White.copy(alpha = if (dark) LiuliPaintedLensSpec.SPECULAR_DARK else LiuliPaintedLensSpec.SPECULAR_LIGHT)
    val rim = Color.White.copy(alpha = if (dark) LiuliPaintedLensSpec.RIM_DARK else LiuliPaintedLensSpec.RIM_LIGHT)
    this
        .liuliSoftShadow(shape, LiuliMaterials.cardShadow(dark), LiuliMaterials.thumbShadowOffsetY, LiuliMaterials.thumbShadowBlur)
        .clip(shape)
        .drawBehind {
            drawRect(brush = Brush.verticalGradient(listOf(fillTop, fillBottom)))
            val shadeH = LiuliPaintedLensSpec.shadeHeight.toPx()
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 1f to shade, startY = size.height - shadeH, endY = size.height),
                topLeft = Offset(0f, size.height - shadeH),
                size = Size(size.width, shadeH),
            )
            drawRect(color = specular, size = Size(size.width, 1f))
            // 描 1dp、裁后可见 0.5dp（同 liuliGlass 发丝口径）。
            drawOutline(shape.createOutline(size, layoutDirection, this), color = rim, style = Stroke(LiuliPaintedLensSpec.rimWidth.toPx() * 2))
        }
}
