package com.situ.aichat.ui.liuli.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes

/** 两行文字之间的呼吸（照暖陶 `ChatRow` / `ContactRow` 的 6dp·行高由头像 54 决定，不受它影响）。 */
private val LINE_GAP = 6.dp

/**
 * 琉璃列表行骨架（图纸 2026-09-06 卷三 A-15 / §4.3）——聊天列表与联系人**共用同一具骨架**，
 * 各自只往三个槽里填内容。
 *
 * 落值：头像槽 54 · 行内距 上下 12 / 左右 16（卷三：行住在分段卡里·卡外另留 20·行高 = 54 + 24 = 78）· 头像→文字 12 ·
 * **行不铺底**（卷三 §4.8：底由列表项的分段卡 `liuliCardSegment` 画）。
 *
 * **分隔发丝不在行内**（由列表项画在本段顶端·卷三），否则行的点击面会把发丝一起吃进去、且随左滑一起动。
 */
@Composable
fun LiuliListRow(
    avatar: @Composable () -> Unit,
    primary: @Composable RowScope.() -> Unit,
    secondary: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = LiuliHomeGeometry.rowPadH,
                vertical = LiuliHomeGeometry.rowPadV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(LiuliHomeGeometry.rowGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically, content = primary)
            Row(verticalAlignment = Alignment.CenterVertically, content = secondary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(LiuliHomeGeometry.rowGap))
            trailing()
        }
    }
}

/**
 * 计数丸（底栏徽章与列表未读丸的**单源**·M3 `Badge` 禁用·§9 ⑤）：卷三起 = 主色三段渐变底
 * （[LiuliMaterials.accentBrush]）+ 白字（白字压渐变三站 ≥ 4.6·卷二已测）。>99 显 "99+"；0 不画。
 */
@Composable
internal fun LiuliCountPill(
    count: Int,
    height: Dp,
    sidePadding: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(LiuliShapes.pill)
            .background(LiuliMaterials.accentBrush)
            .padding(horizontal = sidePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = AppTypography.caption.copy(fontSize = fontSize, fontWeight = FontWeight.W600),
            color = Palette.White,
            maxLines = 1,
        )
    }
}

/** 列表未读丸：20 高、最小宽 20、左右 7、字 12/600（§3.2「列表行」）。 */
@Composable
fun LiuliUnreadPill(count: Int, modifier: Modifier = Modifier) {
    LiuliCountPill(
        count = count,
        height = LiuliHomeGeometry.unreadHeight,
        sidePadding = LiuliHomeGeometry.unreadSidePadding,
        fontSize = 12.sp,
        modifier = modifier,
    )
}
