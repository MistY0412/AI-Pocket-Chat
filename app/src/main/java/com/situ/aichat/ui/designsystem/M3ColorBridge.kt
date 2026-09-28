package com.situ.aichat.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Phase 0 全局换装桥：把 Fable-5 暖中性 + 陶土玫 token 灌进 M3 [ColorScheme] 槽位，让现有 110 文件
 * `MaterialTheme.*` 读取点一夜换暖装（未迁移屏也不再是工程师风 Monet），压缩阴阳脸窗口。
 * 已迁移组件直接读 [AppTheme].colors，不经 M3。本桥属设计系统基建（lint 围栏白名单），非 feature 代码。
 */
internal fun brandLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Palette.Clay,
    onPrimary = Palette.OnClayInk, // 深墨字 on 陶土填充（微信式·白字 2.96:1 不达 4.5·WCAG 决议）
    primaryContainer = Palette.ClayWhisper,
    onPrimaryContainer = Palette.ClayInk,
    secondary = Palette.ClayDeep, // 陶土功能深档（白字 5.3:1·中间调 #A8765F 死区不可作文字底）
    onSecondary = Palette.White,
    secondaryContainer = Palette.Linen,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Gold,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.WarnContainer,
    onTertiaryContainer = Palette.OnWarn,
    background = Palette.Porcelain,
    onBackground = Palette.Ink,
    // 沉浸决议（2026-06-13 用户拍板）：surface=background 同色——顶栏/列表等铬面与底无缝（微信式整屏一底），
    // 白色只留给「内容纸张」（AI 气泡/卡片走 AppTheme.colors.surface.raised，不经此槽位）。
    surface = Palette.Porcelain,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Linen,
    onSurfaceVariant = Palette.InkSoft,
    surfaceTint = Palette.Clay,
    outline = Palette.InkFaint,
    outlineVariant = Palette.LinenDeep,
    error = Palette.OnError,
    onError = Palette.White,
    errorContainer = Palette.ErrorContainer,
    onErrorContainer = Palette.OnError,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Porcelain,
    inversePrimary = Palette.ClayLight,
    scrim = Palette.Scrim,
    // M3 tonal 面阶（NavigationBar/Sheet/Menu/SearchBar 等读 surfaceContainer 族——不映射会落回
    // 基线薰衣草灰）：暖中性等感知步长，仅桥接用不进 Palette/semantic 层。
    surfaceBright = Palette.Porcelain,
    surfaceDim = Color(0xFFE0D9CF),
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Color(0xFFF6F1EA),
    surfaceContainer = Palette.Linen,
    surfaceContainerHigh = Color(0xFFECE5DB),
    surfaceContainerHighest = Color(0xFFE7E0D5),
)

internal fun brandDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Palette.Clay, // 深档填充也保浅陶配深墨字（#A8765F 死区配深字仅 3.71:1）
    onPrimary = Palette.OnClayInk,
    primaryContainer = Palette.ClayWhisperDark,
    onPrimaryContainer = Palette.ClayCream,
    secondary = Palette.ClayLight,
    onSecondary = Palette.OnClayInk,
    secondaryContainer = Palette.Bark,
    onSecondaryContainer = Palette.Cream,
    tertiary = Palette.GoldDark,
    onTertiary = Palette.Espresso,
    tertiaryContainer = Palette.WarnContainerDark,
    onTertiaryContainer = Palette.OnWarnDark,
    background = Palette.Espresso,
    onBackground = Palette.Cream,
    // 沉浸决议：深档同口径 surface=background（深暖灰一底到边）。
    surface = Palette.Espresso,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.Bark,
    onSurfaceVariant = Palette.Sand,
    surfaceTint = Palette.ClayDark,
    outline = Palette.Taupe,
    outlineVariant = Palette.BarkLine,
    error = Palette.OnErrorDark,
    onError = Palette.Espresso,
    errorContainer = Palette.ErrorContainerDark,
    onErrorContainer = Palette.OnErrorDark,
    inverseSurface = Palette.Cream,
    inverseOnSurface = Palette.Espresso,
    inversePrimary = Palette.Clay,
    scrim = Palette.Scrim,
    surfaceBright = Color(0xFF3A342C),
    surfaceDim = Palette.Espresso,
    surfaceContainerLowest = Color(0xFF0F0C0A),
    surfaceContainerLow = Palette.Coffee,
    surfaceContainer = Color(0xFF211D18),
    surfaceContainerHigh = Palette.BarkLine,
    surfaceContainerHighest = Color(0xFF36312A),
)

/**
 * 琉璃主题（第二张脸·琉璃 2.0 晨光·卷一图纸 §4.1 表 C）M3 换装桥·昼档（晨光）。
 * 让未迁移屏的 `MaterialTheme.*` 也变琉璃色（D-15 甲：琉璃色 + 暖陶器型）；已迁移组件直接读 [AppTheme].colors。
 * tonal 面阶为紫中性等感知步长（仅桥接用·不进 Palette/semantic）。
 */
internal fun brandLiuliLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Palette.DawnIris,
    onPrimary = Palette.White, // 白字 on 鸢尾紫填充（4.9·达标）
    primaryContainer = Palette.DawnIrisContainer,
    onPrimaryContainer = Palette.DawnIrisOnContainer,
    secondary = Palette.DawnIrisText, // 鸢尾紫功能深档（白字达标·on 晨光底作文字达 4.5）
    onSecondary = Palette.White,
    secondaryContainer = Palette.DawnSunken,
    onSecondaryContainer = Palette.DawnInk,
    tertiary = Palette.Gold,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.WarnContainer,
    onTertiaryContainer = Palette.OnWarn,
    background = Palette.DawnBase,
    onBackground = Palette.DawnInk,
    // 沉浸决议：surface=background 同色（晨光整屏一底·白色只留内容纸张走 AppTheme.colors.surface.raised）。
    surface = Palette.DawnBase,
    onSurface = Palette.DawnInk,
    surfaceVariant = Palette.DawnSunken,
    onSurfaceVariant = Palette.DawnInkSoft,
    surfaceTint = Palette.DawnIris,
    outline = Palette.DawnInkFaint,
    outlineVariant = Palette.DawnStroke,
    error = Palette.OnError,
    onError = Palette.White,
    errorContainer = Palette.ErrorContainer,
    onErrorContainer = Palette.OnError,
    inverseSurface = Palette.DawnInk,
    inverseOnSurface = Palette.DawnBase,
    inversePrimary = Palette.DuskIris,
    scrim = Palette.Scrim,
    surfaceBright = Palette.DawnBase,
    surfaceDim = Palette.DawnStroke,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Color(0xFFF2F0F6), // 墨 3% over 底
    surfaceContainer = Palette.DawnSunken,
    surfaceContainerHigh = Color(0xFFE8E5ED), // 墨 8% over 底
    surfaceContainerHighest = Palette.DawnStroke,
)

/**
 * 琉璃主题 M3 换装桥·夜档（晨光·夜：深紫近黑底 + 提亮鸢尾紫）。
 */
internal fun brandLiuliDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Palette.DawnIris, // 渐变起点鸢尾紫配白字 4.9（M3 实底钮·DuskIris 留给 surfaceTint/装饰）
    onPrimary = Palette.White,
    primaryContainer = Palette.DuskIrisContainer,
    onPrimaryContainer = Palette.DuskIrisOnContainer,
    secondary = Palette.DuskIris,
    onSecondary = Palette.DuskBase,
    secondaryContainer = Palette.DuskSunken,
    onSecondaryContainer = Palette.DuskInk,
    tertiary = Palette.GoldDark,
    // 深字配金 / 红实底：同角色的晨光夜底（卷一 §11 D-1·复核 R1 核准）。
    onTertiary = Palette.DuskBase,
    tertiaryContainer = Palette.WarnContainerDark,
    onTertiaryContainer = Palette.OnWarnDark,
    background = Palette.DuskBase,
    onBackground = Palette.DuskInk,
    surface = Palette.DuskBase,
    onSurface = Palette.DuskInk,
    surfaceVariant = Palette.DuskSunken,
    onSurfaceVariant = Palette.DuskInkSoft,
    surfaceTint = Palette.DuskIris,
    outline = Palette.DuskInkFaint,
    outlineVariant = Palette.DuskStroke,
    error = Palette.OnErrorDark,
    // 深字配金 / 红实底：同角色的晨光夜底（卷一 §11 D-1·复核 R1 核准）。
    onError = Palette.DuskBase,
    errorContainer = Palette.ErrorContainerDark,
    onErrorContainer = Palette.OnErrorDark,
    inverseSurface = Palette.DuskInk,
    inverseOnSurface = Palette.DuskBase,
    inversePrimary = Palette.DawnIris,
    scrim = Palette.Scrim,
    surfaceBright = Color(0xFF35333E), // 白 14% over 底
    surfaceDim = Palette.DuskBase,
    surfaceContainerLowest = Color(0xFF0E0D15), // 底向黑 30%
    surfaceContainerLow = Palette.DuskRaised,
    surfaceContainer = Color(0xFF201E29), // 白 5% over 底
    surfaceContainerHigh = Palette.DuskStroke,
    surfaceContainerHighest = Color(0xFF35333E), // 白 14% over 底
)
