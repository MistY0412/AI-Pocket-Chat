package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 自研毛玻璃的录层状态（原样搬自旧宿主文件，去掉只有旧调试台用的 `invalidate()`）。 */
@Stable
class BackdropState internal constructor(internal val layer: GraphicsLayer) {
    /** 宿主左上角在根坐标系的位置（未就绪兜底用）。 */
    internal var hostOrigin by mutableStateOf(Offset.Zero)

    /** 宿主自身的布局坐标（切片偏移用 `hostCoords.localPositionOf(自身)`·不受整页缩放影响）。 */
    internal var hostCoords: LayoutCoordinates? = null

    /** content 每画一帧 +1；毛玻璃片在 draw 里读它 → 身后有新帧时重画。 */
    internal var tick by mutableIntStateOf(0)
}

/** 琉璃玻璃宿主状态：自研录层 + Haze 取景各一份，按生效档只启用其一（两者绝不同时取景）。 */
@Stable
class LiuliGlassHostState internal constructor(
    internal val backdrop: BackdropState,
    internal val haze: HazeState,
)

/** overlay 里的玻璃片从这里拿宿主；null = 不在宿主 overlay 里（或宿主关门）→ 玻璃退成着色。 */
val LocalLiuliGlassHost = staticCompositionLocalOf<LiuliGlassHostState?> { null }

@Composable
fun rememberLiuliGlassHostState(): LiuliGlassHostState {
    val layer = rememberGraphicsLayer()
    val haze = rememberHazeState()
    return remember(layer, haze) { LiuliGlassHostState(BackdropState(layer), haze) }
}

/**
 * 琉璃统一玻璃宿主（琉璃 2.0 卷一·取代旧的普通宿主与门控宿主两份）。
 *
 * - [content]：被透过去看的内容（**必须自画不透明底**，否则玻璃影从片内透出·PITFALLS §1d）；
 * - [overlay]：玻璃片住这里（放进 content 会取到自己）。
 * - 生效档是 Haze → content 挂 `hazeSource`；是自研毛玻璃且系统 ≥ 12 → content 每帧录层；否则都不做。
 * - [active] = false（门控宿主在非 Tab 路由、扫码页）：不取景，overlay 拿到 null 宿主。
 * - `active` 时另向 content 与 overlay 提供 [LocalLiuliWindowGlassSource]（跨窗口弹层取景·卷二）。
 */
@Composable
fun LiuliGlassHost(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    state: LiuliGlassHostState = rememberLiuliGlassHostState(),
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val engine = if (active) {
        resolveGlassEngine(LocalGlassTier.current, Build.VERSION.SDK_INT)
    } else {
        LiuliGlassEngine.TINT_ONLY
    }
    val backdrop = state.backdrop
    Box(
        modifier.onGloballyPositioned { coords ->
            backdrop.hostOrigin = coords.positionInRoot()
            backdrop.hostCoords = coords
        },
    ) {
        val capture = when (engine) {
            LiuliGlassEngine.HAZE -> Modifier.hazeSource(state.haze)
            LiuliGlassEngine.FROSTED_BLUR -> Modifier
                // 独立渲染层：兄弟玻璃片失效时不重跑本层的录制（否则 tick 互相触发成环）。
                .graphicsLayer()
                .drawWithContent {
                    backdrop.layer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop.layer)
                    // 写在画完之后；本 lambda 不读 tick，不成环。
                    backdrop.tick++
                }
            LiuliGlassEngine.TINT_ONLY -> Modifier
        }
        CompositionLocalProvider(LocalLiuliWindowGlassSource provides (if (active) state else null)) {
            Box(Modifier.matchParentSize().then(capture)) {
                content()
            }
            CompositionLocalProvider(LocalLiuliGlassHost provides (if (active) state else null)) {
                overlay()
            }
        }
    }
}
