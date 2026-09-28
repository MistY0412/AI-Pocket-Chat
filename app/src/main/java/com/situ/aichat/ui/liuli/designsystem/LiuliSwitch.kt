package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 药丸几何（§3.2）：轨 44×26 · 拇指 20 · 边距 3 · 触达 48 居中外溢不占版。 */
private val TRACK_WIDTH = 44.dp
private val TRACK_HEIGHT = 26.dp
private val THUMB = 20.dp
private val THUMB_INSET = 3.dp
private val TOUCH = 48.dp

/** 开态顶沿迎光（白 35%）；关态轨色见 [LiuliMaterials.offTrack]（琉璃 2.0 卷二 §4.5-2）。 */
private const val SPECULAR_ALPHA = 0.35f
/** 拇指影（卷二 §4.5-2：1 → 2dp）。 */
private val THUMB_SHADOW = 2.dp

/** 禁用态透明度（与 [LiuliButton] 同值·结构恒定不提前 return）。 */
private const val DISABLED_ALPHA = 0.38f

/**
 * 琉璃开关（图纸 2026-09-05 卷二C §4.11 · 落值 §3.2 · A-15）。
 *
 * 自绘，**禁 M3 `Switch`**（§9 ⑤）：开态 = 主色三段 135° 渐变 [LiuliMaterials.accentBrush] + 顶沿 1px 白 35%
 * 迎光；关态 = [LiuliMaterials.offTrack] 平轨。拇指恒 20dp 纯白正圆 + 2dp 影（两态同大小——「关态拇指小一圈」
 * 是 M3 的语汇，不是琉璃的）。
 *
 * 动效：拇指位移走位移轴 [AppMotion].calmSpring，[rememberReduceMotion] 时 `snap()` 直落。
 * 交互：`toggleable(role = Role.Switch)` + `indication = null`（轨上不铺涟漪·反馈由拇指与触觉承担），
 * 开 `haptics.light()`（脆）/ 关 `haptics.soft()`（柔）——与暖陶 `AppSwitch` 同一套触觉分支。
 * 触达 48 用 `size + requiredSize` 居中外溢（同 `liuliFootprint` 思路的矩形版），版位恒 44×26。
 */
@Composable
fun LiuliSwitch(
    checked: Boolean,
    /** null = **纯视觉**（不挂 toggleable、不撑 48 触达）——整行可点的开关行用（复核 R1 🔴-2：两层都可点时点药丸被内层吃掉、行不翻）。 */
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - THUMB - THUMB_INSET else THUMB_INSET,
        animationSpec = if (reduceMotion) snap() else AppMotion.calmSpring(),
        label = "liuliSwitchThumb",
    )
    val trackOff = LiuliMaterials.offTrack(dark)

    Box(
        modifier = modifier
            .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .requiredSize(TOUCH)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            interactionSource = interaction,
                            indication = null,
                            onValueChange = { next ->
                                if (next) haptics.light() else haptics.soft()
                                onCheckedChange(next)
                            },
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
                .drawBehind {
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    if (checked) {
                        // 135° 三段渐变：起点左上、终点右下（与 Prominent 钮同一条）。
                        drawRoundRect(brush = LiuliMaterials.accentBrush, cornerRadius = corner)
                        drawRoundRect(
                            color = Color.White.copy(alpha = SPECULAR_ALPHA),
                            size = size.copy(height = 1f),
                            cornerRadius = corner,
                        )
                    } else {
                        drawRoundRect(color = trackOff, cornerRadius = corner)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = thumbOffset)
                    .shadow(THUMB_SHADOW, CircleShape)
                    .size(THUMB)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
