package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier

/**
 * 跨窗口取景源（卷二图纸 §0.2-5）：宿主 `active` 时对 content 与 overlay 两边都提供；**只给独立窗口里的
 * 三种弹层壳读**（底部面板 / 对话框 / 弹出菜单）。同窗口的玻璃仍只读 overlay 专用的 [LocalLiuliGlassHost]。
 */
val LocalLiuliWindowGlassSource = staticCompositionLocalOf<LiuliGlassHostState?> { null }

/**
 * 独立窗口弹层的大面板玻璃（卷二）：Haze 两档且拿得到取景源 → 真玻璃（Panel 配方）；
 * 否则（毛玻璃档 / 安卓 13 以下 / 不在宿主里）→ 卷一「只着色兜底」（不透明）。
 */
fun Modifier.liuliWindowGlass(shape: RoundedCornerShape, dark: Boolean): Modifier = composed {
    val source = LocalLiuliWindowGlassSource.current
    val tier = LocalGlassTier.current
    if (source != null && resolveGlassEngine(tier, Build.VERSION.SDK_INT) == LiuliGlassEngine.HAZE) {
        liuliHazeGlass(shape, dark, LiuliGlassRole.Panel, tier, source.haze)
    } else {
        liuliFrostedGlass(shape, dark, LiuliGlassRole.Panel, backdrop = null)
    }
}
