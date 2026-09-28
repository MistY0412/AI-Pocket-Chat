package com.situ.aichat.ui.chat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.OnGlass

/**
 * 泡下时间戳在壁纸上的色调（卷四 §0.2-7·两张脸共用）：[color] = 时间字与勾的颜色，[halo] = 描边（文字阴影）颜色。
 * null（默认）= 没有壁纸 → 时间戳照旧 `text.secondary` / 勾照旧，逐像素不变。
 */
@Immutable
data class BubbleStampTone(val color: Color, val halo: Color)

/** 由有壁纸的消息列表 / 飞入覆盖层在外面提供；[BubbleInlineTimestamp] 读它。 */
val LocalBubbleStampTone = staticCompositionLocalOf<BubbleStampTone?> { null }

/**
 * 壁纸中段亮度（Rec.601）低于此值 → 白字，否则 → 墨字。取白字 / 墨字对比度的交点 = 灰 126 / 255（两张脸的主字同一个
 * 交点；无描边最坏 暖陶 3.54 / 琉璃 3.63）。卷四复核 R1：图纸原写 0.53 算错了交点（灰 126–135 会错选白字、最坏 3.11）。
 */
const val STAMP_WALLPAPER_DARK_LUMA = 0.494f
/** 描边：暗壁纸上的白字配黑 55% 描边、亮壁纸上的墨字配白 80% 描边；模糊半径 4dp。 */
internal val STAMP_HALO_ON_DARK = Color.Black.copy(alpha = 0.55f)
internal val STAMP_HALO_ON_LIGHT = Color.White.copy(alpha = 0.80f)
val STAMP_HALO_BLUR: Dp = 4.dp

/** 按壁纸中段亮度选色调（纯函数·T1）：[onDark] / [onLight] 由各张脸给自己的「玻璃主字」色。 */
fun bubbleStampTone(midLuma: Float, onDark: Color, onLight: Color): BubbleStampTone =
    if (midLuma < STAMP_WALLPAPER_DARK_LUMA) BubbleStampTone(onDark, STAMP_HALO_ON_DARK) else BubbleStampTone(onLight, STAMP_HALO_ON_LIGHT)

/** 暖陶的色调（字色 = 暖陶玻璃主字 [OnGlass]）。 */
internal fun warmBubbleStampTone(wallpaper: ChatWallpaper): BubbleStampTone =
    bubbleStampTone(wallpaper.midLuma, OnGlass.PrimaryOnDark, OnGlass.PrimaryOnLight)
