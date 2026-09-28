package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 胶囊内距 / 图标 / 缝 = 暖陶「写一笔」胶囊原值（E 甲只换材质）。 */
private val FAB_PILL_PAD_H = 20.dp
private val FAB_PILL_ICON = 16.dp
private val FAB_PILL_GAP = 6.dp

/**
 * 玻璃浮动胶囊钮（琉璃 2.0 卷六·一 §4·E 甲·三块共用：写一笔）：一片 pill 玻璃（Button 档）+ 主色图标与字 + 按压缩放 / 叠亮 / 光斑。
 * 修饰链次序同 [LiuliCircleButton]（按压缩放 → 尺寸 → 玻璃 → 叠亮 → 光斑 → 点击）；高 [LiuliPageGeometry.fabPill] = 触达。
 * 放进 `LiuliPage(fab = …)` 槽（overlay 里取得到宿主 → 真玻璃）。圆钮版用 `LiuliCircleButton(size = LiuliPageGeometry.fab)`。
 */
@Composable
fun LiuliFabPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .liuliPressable(interactionSource = interaction, enabled = true)
            .height(LiuliPageGeometry.fabPill)
            .liuliGlass(LiuliShapes.pill, dark = dark, role = LiuliGlassRole.Button)
            .liuliPressBrighten(interaction, enabled = true)
            .liuliPressLight(dark)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = label,
                onClick = {
                    haptics.light()
                    onClick()
                },
            )
            .padding(horizontal = FAB_PILL_PAD_H),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FAB_PILL_GAP),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent.text, modifier = Modifier.size(FAB_PILL_ICON))
        Text(label, style = AppTypography.label, color = colors.accent.text, maxLines = 1)
    }
}
