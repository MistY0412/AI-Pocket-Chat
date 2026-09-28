package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 琉璃按钮三档（契约 FABLE5_THEME_LIULI_PROPOSAL.md §4.1 · 图纸 §4.3）。与暖陶 `AppButtonStyle` 同形，
 * 卷二搬 sheet 内容时可平移替换。
 * - [Glass]：玻璃片本身当面（模糊 + 染色 + 迎光 + 发丝 + 影）——琉璃的默认钮。
 * - [Prominent]：主色三段 135° 渐变实底（[LiuliMaterials.accentBrush]·琉璃 2.0 卷二）+ 顶沿硬亮线 + 一道彩影——主行动 CTA。
 * - [Text]：透明 + 钴蓝字——三级行动。
 */
enum class LiuliButtonStyle { Glass, Prominent, Text }

/**
 * 禁用态透明度（结构恒定·绝不 `if (!enabled) return`·REDLINES §7）。
 *
 * ⚠️ 承重排序：`Modifier.alpha` 只淡化它**之后**（内层）画的东西，所以它必须排在「画底」的那一段
 * （Prominent 的渐变实底 / Glass 的 [liuliGlass] / 圆钮的圆玻璃）**之前**。排到底之后 = 底满色、只有字发灰，
 * 用户看不出钮按不动（卷五 C5 装机取证 `liuli_v5/07_api_config.png` 就是这么翻的车）。
 * 回归钉在 `LiuliButtonTest.禁用态三档底色都必须被淡化`。
 */
private const val DISABLED_ALPHA = 0.38f

/**
 * 琉璃按钮。内容槽 [content] 与暖陶 `AppButton` 同形（同参数名、同 [RowScope] 内容槽），文字色与
 * [AppTypography].label 经 [LocalContentColor]/[LocalTextStyle] 注入，调用方直接写 `Text(...)` 即自动取色取样式。
 *
 * 视觉高 40dp（[defaultMinSize]）、触达 48dp（[minimumInteractiveComponentSize]）；按压走
 * [Modifier.liuliPressable]（缩 0.96）+ [Modifier.liuliPressBrighten]（Glass/Prominent 形状内叠亮）+ 官方品牌 ripple（[LocalIndication]）+
 * `haptics.light()`。[enabled]=false 时降透明且不可点，**结构恒定**（禁用态提前 return 是动画杀手）。
 * [danger]=true 时 Text 档内容走 error 色（与暖陶同语义）；Glass 档 + [danger] **不走玻璃**，改淡红实底
 * [LiuliMaterials.dangerFill] + 红字（琉璃 2.0 卷二 §4.4）。
 */
@Composable
fun LiuliButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LiuliButtonStyle = LiuliButtonStyle.Glass,
    enabled: Boolean = true,
    danger: Boolean = false,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val shape = LiuliShapes.pill

    // R1 🔵-1：danger 只染 Glass / Text 两档的字（红字 on 玻璃 / on 表面）；Prominent 是钴蓝实底，红字压蓝底
    // 既不达对比也不是任何已过审长相，故忽略 danger 保持白字。
    val contentColor: Color = when {
        style == LiuliButtonStyle.Prominent -> Palette.White
        danger -> colors.status.onError
        style == LiuliButtonStyle.Text -> colors.accent.text
        else -> LiuliTheme.onGlass.primary
    }
    val resolvedPadding = contentPadding ?: PaddingValues(
        horizontal = if (style == LiuliButtonStyle.Text) 12.dp else 18.dp,
    )
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides AppTypography.label.copy(color = contentColor),
    ) {
        Row(
            modifier = modifier
                .liuliPressable(interactionSource = interaction, enabled = enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = {
                        haptics.light()
                        onClick()
                    },
                )
                .minimumInteractiveComponentSize()
                // alpha 必须在「画底」之前：它只作用于内层绘制（见 [DISABLED_ALPHA] 承重排序注释）。
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(
                    when (style) {
                        // 危险玻璃钮（卷二 §4.4）：不走玻璃，淡红实底 + 红字（字色见上方 contentColor）。
                        LiuliButtonStyle.Glass -> if (danger) {
                            Modifier.clip(shape).background(LiuliMaterials.dangerFill(colors.status.onError, dark))
                        } else {
                            Modifier.liuliGlass(shape, dark = dark, role = LiuliGlassRole.Button).liuliPressLight(dark, enabled)
                        }
                        // 渐变实底 + 6dp 彩影 + 顶沿迎光（与发送钮 / 麦克风 / 卡片主钮共用·卷三 §0.2-14）。
                        LiuliButtonStyle.Prominent -> Modifier.liuliAccentFill(shape)
                        LiuliButtonStyle.Text -> Modifier.clip(shape)
                    },
                )
                // 提亮只亮形状内（Glass / Prominent；卷四复核 R1：原先画满触达框、按下亮出方角）。
                .liuliPressBrighten(interaction, enabled = enabled && style != LiuliButtonStyle.Text)
                .defaultMinSize(minHeight = 40.dp)
                .padding(resolvedPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * 琉璃圆钮（顶栏 / 输入排的图标钮）：一片圆玻璃 + 居中内容。[size] 为视觉直径（默认 40dp），
 * 触达由 [minimumInteractiveComponentSize] 保 48dp。[contentDescription] 同时进 `onClickLabel` 与 semantics。
 *
 * 长相 = 用户 2026-09-06 拍板「圆钮甲·iOS 26 淡玻璃钮」：玻璃走 [com.situ.aichat.ui.liuli.glass.LiuliGlassRole.Button]
 * （2dp 小影·光影随档由玻璃入口决定·琉璃 2.0 卷一），图标 / 内容用 `accent.text`（原 `onGlass.primary` 墨色太重）。
 */
@Composable
fun LiuliCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val interaction = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalContentColor provides AppTheme.colors.accent.text) {
        Box(
            modifier = modifier
                .liuliPressable(interactionSource = interaction, enabled = enabled)
                .minimumInteractiveComponentSize()
                .size(size)
                // 同上：alpha 排在圆玻璃之前，否则禁用态只有图标发灰、玻璃片仍满色。
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .liuliGlass(CircleShape, dark = dark, role = LiuliGlassRole.Button)
                .liuliPressBrighten(interaction, enabled)
                .liuliPressLight(dark, enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = {
                        haptics.light()
                        onClick()
                    },
                )
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
