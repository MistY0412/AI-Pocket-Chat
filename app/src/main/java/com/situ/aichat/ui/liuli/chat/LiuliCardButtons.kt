package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliAccentFill
import com.situ.aichat.ui.liuli.designsystem.liuliPressBrighten
import com.situ.aichat.ui.liuli.designsystem.liuliPressable
import com.situ.aichat.ui.liuli.page.liuliTouchHeight

/**
 * 琉璃卡内钮族（图纸 2026-09-05 卷二C §3.2 卡一节·自 `LiuliCardShell.kt` 只搬不改·复核 R1）：
 * 等分主 / 浅染钮 [LiuliCardButton] + 三级文字钮 [LiuliCardTextButton] + 共用的触达壳 [LiuliCardTouchSlot]。
 */

/**
 * 卡内钮（对版稿 `.cbt`·高 34 · pill · `label` W600 归梯 640·§3.2）：
 * [prominent] = 主色渐变实底（[liuliAccentFill]·卷三）/ 否则 = **浅染实底**（`accent.container` + `accent.onContainer`）自画——§3.2 明写
 * 「soft 是浅染实底不是玻璃」，内容层退染色的玻璃在纸卡上糊成一片。
 *
 * **触达 48 不占版**（复核 R1 🔴-1·REDLINES「a11y 48dp」）：版位仍是 34（钮行几何一像素不动），点击面经
 * [liuliTouchHeight] 上下各外溢 7dp——落在卡体 / 卡脚内边距上，不与任何别的触达面相争。点击面与视觉因此分
 * 两层（[LiuliCardTouchSlot]）：`LiuliButton` 的 clickable 与视觉同节点、版位一被压到 34 触达也只剩 34，所以
 * prominent 档在此**自画**钴蓝配方——与 `LiuliButtonStyle.Prominent` / `LiuliSendButton` 同配方同落值。
 */
@Composable
internal fun RowScope.LiuliCardButton(
    text: String,
    prominent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    softContainer: Color = AppTheme.colors.accent.container,
    softContent: Color = AppTheme.colors.accent.onContainer,
) {
    val style = AppTypography.label.copy(fontWeight = BUTTON_WEIGHT)
    LiuliCardTouchSlot(slot = modifier.weight(1f), onClick = onClick) { interaction ->
        val face = Modifier
            .fillMaxWidth()
            .height(LiuliChatGeometry.cardButtonHeight)
            .liuliPressable(interactionSource = interaction, enabled = true)
        if (prominent) {
            Box(
                modifier = face.liuliAccentFill(LiuliShapes.pill).liuliPressBrighten(interaction, enabled = true),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = style, color = Palette.White)
            }
        } else {
            Box(
                modifier = face.clip(LiuliShapes.pill).background(softContainer).liuliPressBrighten(interaction, enabled = true),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = style, color = softContent)
            }
        }
    }
}

/**
 * 卡内三级文字钮（「先不约」「取消约定」·F11 的 `AppButtonStyle.Text` 同档）：不占等分槽、`secondary` 字 ×
 * `accent.text`、无底；触达同 [LiuliCardButton] 48 不占版。
 */
@Composable
internal fun LiuliCardTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiuliCardTouchSlot(slot = modifier, onClick = onClick) { interaction ->
        Box(
            modifier = Modifier
                .height(LiuliChatGeometry.cardButtonHeight)
                .liuliPressable(interactionSource = interaction, enabled = true)
                .padding(horizontal = TEXT_BUTTON_SIDE),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = AppTypography.secondary, color = AppTheme.colors.accent.text)
        }
    }
}

/**
 * 触达壳：版位 34 高（参与钮行定尺）· 点击面 48 居中外溢（[liuliTouchHeight]）· 视觉由 [face] 画在最里，
 * 按压缩放读同一枚 [MutableInteractionSource]；`indication = null`——48 框是隐形的，ripple 画出来会出视觉边。
 */
@Composable
private fun LiuliCardTouchSlot(
    slot: Modifier,
    onClick: () -> Unit,
    face: @Composable (MutableInteractionSource) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = slot
            .height(LiuliChatGeometry.cardButtonHeight)
            .liuliTouchHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { haptics.light(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        face(interaction)
    }
}

/** 落值（§3.2 卡一节 + 对版稿 `.cbt`·孤值即打回）。 */
private val TEXT_BUTTON_SIDE = 12.dp
private val BUTTON_WEIGHT = FontWeight(640)
