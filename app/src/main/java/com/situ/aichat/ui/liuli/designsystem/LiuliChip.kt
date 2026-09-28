package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.page.liuliTouchHeight
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import androidx.compose.foundation.clickable

/** 胶囊几何（§3.2）：32 高 · 左右 12。 */
private val CHIP_HEIGHT = 32.dp
private val CHIP_WEIGHT = FontWeight(520)
private const val DISABLED_ALPHA = 0.45f

/**
 * 琉璃选择标签（图纸 2026-09-05 卷二C §4.11 · 落值 §3.2 · A-15·签名对齐暖陶 `AppChoiceChip`）。
 *
 * 自绘，**禁 M3 `FilterChip`**（§9 ⑤）。琉璃 2.0 卷二 §4.5-1：选中 = 主色渐变 [LiuliMaterials.accentBrush] + 白字
 * + 形状外柔影、无边；未选 = [LiuliMaterials.chipFill] + 1dp [LiuliMaterials.cardRim] 白边 + 玻璃上主文字色。
 * 两态同字号字重 → 切换无重排；过渡由一个「选中度」驱动（渐变按它淡入、边按它淡出、柔影按它浓淡），字色同 spec
 * 动画（[AppMotion].effectMediumSpring 效果轴永不过冲·[rememberReduceMotion] 时瞬时）。
 *
 * 触达 48：版位恒 32 高，`liuliTouchHeight` 把点击面上下各外溢 8dp（`clickable` 必须排在它之后）。
 * a11y：[selectable] + `selected` 语义 + [role]（默认 [Role].RadioButton 单选组；可开可关的独立开关传
 * [Role].Checkbox——节日 chip 就是后者）。
 */
@Composable
fun LiuliChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    /**
     * 视觉胶囊是否撑满可用宽（§2.1「日期胶囊 …… 满宽」）。默认 false = 内容宽（chip 行 / FlowRow 里的常态）。
     * **不能**靠调用方传 `Modifier.fillMaxWidth()` 代替：那只撑大外面的触达框，胶囊本体照旧缩在中间；
     * 而用 `propagateMinConstraints` 传下去又会把 `liuliTouchHeight` 的 48 最小高一并传进来，把版位从
     * 锁定的 32 顶到 48（装机实证）。
     */
    fillWidth: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val spec = if (reduceMotion) snap() else AppMotion.effectMediumSpring<androidx.compose.ui.graphics.Color>()
    val selectedFraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else AppMotion.effectMediumSpring(),
        label = "liuliChipSelected",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Palette.White else LiuliTheme.onGlass.primary,
        animationSpec = spec,
        label = "liuliChipContent",
    )
    val rim = LiuliMaterials.cardRim(dark)
    val shadow = LiuliMaterials.accentShadow

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .height(CHIP_HEIGHT)
            .liuliTouchHeight()
            .then(
                if (role == Role.Button) {
                    // 动作芯片（「加载预设 / 保存为预设」）：是按钮不是选项，读屏不该念「未选中」（卷五复核 R1）。
                    Modifier.clickable(
                        enabled = enabled,
                        role = role,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { haptics.light(); onClick() },
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = role,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { haptics.selection(); onClick() },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(CHIP_HEIGHT)
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                // 柔影画在形状外（半透明未选底不许透影·卷二 §0.2-2），浓淡随选中度。
                .liuliSoftShadow(
                    LiuliShapes.pill,
                    shadow.copy(alpha = shadow.alpha * selectedFraction),
                    LiuliMaterials.chipShadowOffsetY,
                    LiuliMaterials.chipShadowBlur,
                )
                .clip(LiuliShapes.pill)
                .background(LiuliMaterials.chipFill(dark))
                // 渐变底按选中度叠在未选底之上。
                .drawBehind { if (selectedFraction > 0f) drawRect(LiuliMaterials.accentBrush, alpha = selectedFraction) }
                // 白边只属未选态，按选中度淡出（选中是渐变实底·无边）。
                .border(1.dp, rim.copy(alpha = rim.alpha * (1f - selectedFraction)), LiuliShapes.pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (leading != null) {
                CompositionLocalProvider(LocalContentColor provides contentColor) { leading() }
            }
            Text(
                text = label,
                style = AppTypography.secondary.copy(fontWeight = CHIP_WEIGHT),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
