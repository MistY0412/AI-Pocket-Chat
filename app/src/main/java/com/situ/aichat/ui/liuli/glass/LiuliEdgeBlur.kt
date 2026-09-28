@file:OptIn(ExperimentalHazeApi::class)

package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.animation.core.EaseInOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/** 屏边模糊最浓处的模糊半径 = 自研毛玻璃同一个半径（卷四复核 R1·Haze 玻璃默认 14dp 压不住状态栏下的字）。 */
internal val EDGE_BLUR_RADIUS: Dp = LiuliGlassSpec.blurRadius

/** 此处玻璃走哪种画法：在宿主 overlay 里按生效档，否则只着色（与 [liuliGlass] 同一判定）。 */
@Composable
internal fun currentLiuliGlassEngine(): LiuliGlassEngine =
    if (LocalLiuliGlassHost.current == null) {
        LiuliGlassEngine.TINT_ONLY
    } else {
        resolveGlassEngine(LocalGlassTier.current, Build.VERSION.SDK_INT)
    }

/**
 * 屏边模糊（卷四复核 R1·用户 09-26 附图「从最顶上往下由糊到清、没有分界」）：只画身后内容的模糊——不着色、不提白、
 * 无光影 / 折射 / 色散 / 投影，遮挡带的底色与渐隐由调用方叠。
 * - Haze 引擎：**真渐进模糊**——模糊半径在 [fadeFromPx] 以上恒为 [EDGE_BLUR_RADIUS]，往下沿缓入缓出曲线递减，到 [fadeToPx]
 *   归零（按行变半径，不是「一层匀糊 + 透明度渐隐」的叠影）；
 * - 自研毛玻璃：整片匀糊（同半径），渐隐靠调用方的透明度遮罩。**片子不许伸出屏外**：屏外取不到内容，边缘模糊会掺进一圈
 *   透明、把身后清晰的字透出来（复核装机实测：状态栏最上一行字没糊掉）；
 * - 只着色 / 不在宿主里：原样返回（调用方自己画颜色渐隐）。
 *
 * [blurAtEnd] = true：模糊在 [fadeToPx] 最浓、往 [fadeFromPx] 递减到零（屏底带·琉璃 2.0 卷六·一）。
 */
internal fun Modifier.liuliEdgeBlur(fadeFromPx: Float, fadeToPx: Float, blurAtEnd: Boolean = false): Modifier = composed {
    val host = LocalLiuliGlassHost.current ?: return@composed this
    when (resolveGlassEngine(LocalGlassTier.current, Build.VERSION.SDK_INT)) {
        LiuliGlassEngine.HAZE -> hazeEdgeBlur(host.haze, fadeFromPx, fadeToPx, blurAtEnd)
        LiuliGlassEngine.FROSTED_BLUR -> frostedEdgeBlur(host.backdrop)
        LiuliGlassEngine.TINT_ONLY -> this
    }
}

@Composable
private fun Modifier.hazeEdgeBlur(haze: HazeState, fadeFromPx: Float, fadeToPx: Float, blurAtEnd: Boolean): Modifier {
    val style = remember(fadeFromPx, fadeToPx, blurAtEnd) {
        GlassStyle.regular.then {
            shape(LiuliShapes.rect)
            tint(Color.Transparent)
            optics(
                refractionStrength = 0f,
                refractionDetailIntensity = 0f,
                blurRadius = EDGE_BLUR_RADIUS,
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseInOut,
                    startY = fadeFromPx,
                    startIntensity = if (blurAtEnd) 0f else 1f,
                    endY = fadeToPx,
                    endIntensity = if (blurAtEnd) 1f else 0f,
                ),
            )
            specularIntensity(0f)
            edgeShadow(Color.Transparent)
            ambientResponse(0f)
            chromaticAberrationStrength(0f)
            contrast(0f)
            whitePoint(0f)
            chromaMultiplier(1f)
        }
    }
    val input = remember(haze) { HazeInput.Sources(haze) }
    return hazeGlass(input = input, style = style)
}

/** 自研毛玻璃的匀糊切片（同 [liuliFrostedGlass] 的取景与切片偏移，去掉染色 / 高光 / 细边 / 饱和度）。 */
@Composable
private fun Modifier.frostedEdgeBlur(backdrop: BackdropState): Modifier {
    val blurLayer = rememberGraphicsLayer()
    var sliceOffset by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { sliceOffset = liuliSliceOffset(it, backdrop) }
        .drawBehind {
            // 读 tick：内容层每有新帧，本片重画（读而不写·不成环）。
            @Suppress("UNUSED_VARIABLE")
            val frame = backdrop.tick
            val off = sliceOffset
            val radiusPx = EDGE_BLUR_RADIUS.toPx()
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            blurLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                translate(-off.x, -off.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(blurLayer)
        }
}
