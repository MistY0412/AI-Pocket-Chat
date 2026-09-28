package com.situ.aichat.ui.liuli.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/**
 * 玻璃片在宿主内的切片偏移（原样搬自旧 `LiuliGlass.kt:58–62`·T2 锁「整页缩放下偏移不变」）。
 * 宿主坐标就绪时用 `localPositionOf`——两者同在被缩放的页面里，相对位置不受祖先 `graphicsLayer` 缩放影响
 * （导航 2.10 左滑返回 `scaleOut` 0.7 时按根坐标差会取错切片）；未就绪（首帧回调次序）时退回根坐标差。
 */
internal fun liuliSliceOffset(self: LayoutCoordinates, host: BackdropState?): Offset {
    val hc = host?.hostCoords
    return if (hc != null && hc.isAttached) hc.localPositionOf(self, Offset.Zero)
    else self.positionInRoot() - (host?.hostOrigin ?: Offset.Zero)
}

/**
 * 自研毛玻璃（[backdrop] 非空 = 身后实时模糊）/ 只着色兜底（[backdrop] 为空）。
 * 顺序：影（形状外）→ 独立渲染层 + 按形状裁 → 模糊切片（兜底时换成不透明垫底）→ 染色 → 顶部高光带 → 内容
 * → 顶沿 1px 高光 → 白色细边。
 */
@Composable
internal fun Modifier.liuliFrostedGlass(
    shape: RoundedCornerShape,
    dark: Boolean,
    role: LiuliGlassRole,
    backdrop: BackdropState?,
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    var sliceOffset by remember { mutableStateOf(Offset.Zero) }
    val tint = liuliFrostedTint(dark, blurred = backdrop != null)
    val underlay = liuliFallbackUnderlay(dark)
    val sheen = if (dark) LiuliGlassSpec.sheenDark else LiuliGlassSpec.sheenLight
    val specular = if (dark) LiuliGlassSpec.specularDark else LiuliGlassSpec.specularLight
    val rim = if (dark) LiuliGlassSpec.rimDark else LiuliGlassSpec.rimLight
    val satFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(LiuliGlassSpec.SATURATION) })
    }
    return this
        .shadow(liuliGlassElevation(role), shape, clip = false)
        .onGloballyPositioned { if (backdrop != null) sliceOffset = liuliSliceOffset(it, backdrop) }
        .graphicsLayer {
            this.shape = shape
            clip = true
        }
        .drawWithContent {
            if (backdrop != null) {
                // 读 tick：内容层每有新帧，本片重画（读而不写·不成环）。
                @Suppress("UNUSED_VARIABLE")
                val frame = backdrop.tick
                val off = sliceOffset
                val radiusPx = LiuliGlassSpec.blurRadius.toPx()
                blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
                blurLayer.colorFilter = satFilter
                blurLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                    translate(-off.x, -off.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurLayer)
            } else {
                // 兜底：先铺不透明垫底，否则 12% 的透明会把形状下面的投影透上来（卷一复核 R1 🔴-1）。
                drawRect(color = underlay)
            }
            drawRect(color = tint)
            drawRect(brush = Brush.verticalGradient(0f to sheen, LiuliGlassSpec.SHEEN_STOP to Color.Transparent))
            drawContent()
            drawRect(color = specular, topLeft = Offset.Zero, size = size.copy(height = 1f))
            // 细边：沿形状描 2 × rimWidth 宽，裁后可见 rimWidth。
            drawOutline(
                shape.createOutline(size, layoutDirection, this),
                color = rim,
                style = Stroke(LiuliGlassSpec.rimWidth.toPx() * 2),
            )
        }
}
