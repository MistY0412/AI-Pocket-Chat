package com.situ.aichat.ui.liuli.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 条内距：左右 8 · 上下 4（内件是 48 触达的 40 视觉钮 → 条高恰 56）；内件缝 4 = 暖陶写日记动作条同值；圆角 28 = 条高一半。 */
private val FLOATING_BAR_PAD_H = 8.dp
private val FLOATING_BAR_PAD_V = 4.dp
private val FLOATING_BAR_ITEM_GAP = 4.dp
private val FLOATING_BAR_SHAPE = RoundedCornerShape(28.dp)

/**
 * 悬浮玻璃底条（琉璃 2.0 卷六·一 §4·三块共用：写日记工具条 / 动态详情评论条）：一片 Bar 玻璃浮在内容之上，
 * 跟着键盘升起（`navigationBarsPadding` + `imePadding`·同暖陶动作条的做法），左右离屏 12、离导航栏 / 键盘 12、最小高 56。
 * 放进 `LiuliPage(bottomBar = …)` 槽（overlay → 真玻璃）。内容区要给它让出 [LiuliPageGeometry.floatingBarReserve]。
 */
@Composable
fun LiuliFloatingBar(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                start = LiuliPageGeometry.floatingBarInset,
                end = LiuliPageGeometry.floatingBarInset,
                bottom = LiuliPageGeometry.floatingBarGap,
            )
            .heightIn(min = LiuliPageGeometry.floatingBar)
            .liuliGlass(FLOATING_BAR_SHAPE, dark = dark, role = LiuliGlassRole.Bar)
            .padding(horizontal = FLOATING_BAR_PAD_H, vertical = FLOATING_BAR_PAD_V),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(FLOATING_BAR_ITEM_GAP),
        content = content,
    )
}
