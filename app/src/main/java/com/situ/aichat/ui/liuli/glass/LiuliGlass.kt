package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier

/**
 * 玻璃加厚（卷三 §0.2-8）：true 时 Haze 两档一律用壁纸加厚配方（regular + [LiuliGlassSpec.hazeThickLight] / Dark），**影高仍按角色**；
 * 自研毛玻璃与只着色兜底本就够厚，不受影响。只由聊天屏有壁纸时的 `LiuliChromeTone` 打开。
 */
val LocalLiuliGlassThick = staticCompositionLocalOf { false }

/**
 * 琉璃玻璃片的**唯一入口**（琉璃 2.0 卷一）。页面只说「什么形状、深浅、什么角色」，三档切换、安卓版本兜底、
 * 投影、光影都在这里处理：
 * - 在 [LiuliGlassHost] 的 overlay 里且 [blurEnabled] → 按生效档走 Haze（安卓 13+ 的标准 / 通透）或自研毛玻璃；
 * - 否则（内容层、独立窗口弹层、宿主关门、显式关模糊）→ 只着色（厚底色压住身后内容）。
 *
 * [shape] 只收圆角矩形（Haze 只认它；直角用 `LiuliShapes.rect`）。
 */
fun Modifier.liuliGlass(
    shape: RoundedCornerShape,
    dark: Boolean,
    role: LiuliGlassRole = LiuliGlassRole.Bar,
    blurEnabled: Boolean = true,
): Modifier = composed {
    val host = if (blurEnabled) LocalLiuliGlassHost.current else null
    val tier = LocalGlassTier.current
    val engine = if (host == null) LiuliGlassEngine.TINT_ONLY else resolveGlassEngine(tier, Build.VERSION.SDK_INT)
    val thick = LocalLiuliGlassThick.current
    when {
        host != null && engine == LiuliGlassEngine.HAZE -> liuliHazeGlass(shape, dark, role, tier, host.haze, thick)
        host != null && engine == LiuliGlassEngine.FROSTED_BLUR -> liuliFrostedGlass(shape, dark, role, host.backdrop)
        else -> liuliFrostedGlass(shape, dark, role, backdrop = null)
    }
}
