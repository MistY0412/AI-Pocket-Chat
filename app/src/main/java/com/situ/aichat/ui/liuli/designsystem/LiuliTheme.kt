package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 琉璃设计系统 token（第二张脸·契约 FABLE5_THEME_LIULI_PROPOSAL.md §3.3/§4.1）。
 *
 * 定位：琉璃**不是换皮**，是与暖陶永久并行的第二套界面树（`ui/liuli` 包下整棵树）。本文件是它的形状 / 玻璃上文字色
 * 与两个选脸 CompositionLocal 的单源；色相仍走共用的 [com.situ.aichat.ui.designsystem.AppTheme].colors
 * （琉璃色板由 [com.situ.aichat.ui.theme.AIPocketChatTheme] 按 [AppSkin] provide）。
 *
 * 边界（图纸 §2.3）：`ui/liuli` 下的文件绝不 import 暖陶 `App*` **组件**；只许读 `AppTheme`（色）、`AppTypography`、
 * `Palette`、`AppMotion`、`LocalAppHaptics`、`rememberReduceMotion`、[LocalIsDarkTheme]。
 */

/** 当前界面「脸」（由 [com.situ.aichat.ui.theme.AIPocketChatTheme] provide；没 provide 时——只有单测 / 预览——兜底暖陶。产品默认脸见 AppSkin.DEFAULT）。 */
val LocalAppSkin = staticCompositionLocalOf { AppSkin.CLAY }

/** 当前玻璃「质感」档（同上 provide·默认通透）。玻璃宿主与玻璃片 [com.situ.aichat.ui.liuli.glass.liuliGlass] 都读它。 */
val LocalGlassTier = staticCompositionLocalOf { GlassTier.SHEER }

/** 琉璃形状阶（契约 §3.3 表「琉璃」列·Telegram iOS 器型口径）。 */
object LiuliShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(20.dp)
    /** 二级屏的内嵌圆角分组（卷四 §4.1 `groupCorner`·琉璃 2.0 卷二 16 → 20）。 */
    val group = RoundedCornerShape(20.dp)
    val bubble = RoundedCornerShape(18.dp)
    val bubbleTailCorner = 5.dp          // 末条尾巴侧角（卷二气泡用）
    val overlay = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    val pill = RoundedCornerShape(percent = 50)
    /** 直角玻璃条（Haze 只认圆角矩形，直角写 0dp）。 */
    val rect = RoundedCornerShape(0.dp)
    /** 对话框卡（琉璃 2.0 卷二 §4.7-2）。 */
    val dialog = RoundedCornerShape(24.dp)
    /** 弹出菜单卡（卷二 §4.7-3）。 */
    val menu = RoundedCornerShape(18.dp)
    /** 悬浮底部面板（卷二 §4.7-1）：四周留 8dp，顶 38 / 底 32。 */
    val sheetFloating = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 32.dp, bottomStart = 32.dp)
    /** 收起态悬浮胶囊顶栏（卷二 §4.8-1）。 */
    val compactPill = RoundedCornerShape(22.dp)
}

/**
 * 玻璃片**之上**的文字 / 图标色（契约 §4.1·琉璃 2.0 卷一图纸 §4.1 取晨光 Palette 值）。玻璃自身已把身后内容
 * 压到近乎中性底，故这两色不走语义 token——它们要对「玻璃合成后的底」达标，而不是对某个 surface 达标。
 * 卷二 §0.2-3：次要字与 `text.secondary` 压深后合并为同一值（卷三 §0.2-5 再调：浅 #544E70 / 深 #C7C3DF）。
 */
@Immutable
data class LiuliOnGlassColors(val primary: Color, val secondary: Color)

val LiuliOnGlassLight = LiuliOnGlassColors(primary = Palette.DawnInk, secondary = Palette.DawnInkSoft)
val LiuliOnGlassDark = LiuliOnGlassColors(primary = Palette.DuskInk, secondary = Palette.DuskInkSoft)

/** 琉璃 token 访问器（用法同暖陶的 `AppTheme`）。 */
object LiuliTheme {
    val skin: AppSkin
        @Composable @ReadOnlyComposable get() = LocalAppSkin.current

    val glassTier: GlassTier
        @Composable @ReadOnlyComposable get() = LocalGlassTier.current

    val shapes: LiuliShapes get() = LiuliShapes

    val onGlass: LiuliOnGlassColors
        @Composable @ReadOnlyComposable get() =
            if (LocalIsDarkTheme.current) LiuliOnGlassDark else LiuliOnGlassLight
}
