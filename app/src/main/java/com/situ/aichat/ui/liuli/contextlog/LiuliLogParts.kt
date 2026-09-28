package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.contextlog.LogNoticeTone
import com.situ.aichat.ui.contextlog.LogPillTone
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliChip
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliCardMaterial
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliRowTitleColumn
import com.situ.aichat.ui.liuli.page.LiuliRowValue
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/*
 * 日志页琉璃小零件（四期·图纸四 §4.0 词汇表琉璃列）：组内自由内容行、药丸、提示框、失败红条独立卡、芯片行。
 * 边界：不 import 暖陶 App* 组件（只借 AppTheme 色 / AppTypography / 两张脸共用的色调枚举）；间距逐项自带 padding，不用 spacedBy。
 */

/** 组内自由内容行（首行不画分隔线）。 */
@Composable
fun LiuliLogFreeRow(first: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    LiuliRowBase(modifier = modifier, onClick = onClick, divider = !first, verticalPadding = AppSpacing.m, content = content)
}

/**
 * 键值行（§4.0「键值行」琉璃列）：键与值行同一套标题 / 右值落值，但**值吃剩余宽度、一行省略**（同暖陶 `LogKvRow`）。
 * TODO(图纸未覆盖)：图纸映射到 `LiuliValueRow`，但它的标题占 weight、右值不设宽——长值（角色行带引用串）会把
 * 「角色」两字挤成竖排（装机 T4 实测）；这里换成值占 weight，登记图纸 §11 D-x。
 */
@Composable
fun LiuliLogKvRow(title: String, value: String, divider: Boolean = true) {
    LiuliRowBase(divider = divider) {
        LiuliRowTitleColumn(title, subtitle = null)
        Spacer(Modifier.width(LiuliPageGeometry.tileGap))
        LiuliRowValue(value, warning = false, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** 状态药丸（同暖陶 token·胶囊形）。 */
@Composable
fun LiuliLogPill(text: String, tone: LogPillTone, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val (bg, fg) = when (tone) {
        LogPillTone.SUCCESS -> c.status.successContainer to c.status.onSuccess
        LogPillTone.ERROR -> c.status.errorContainer to c.status.onError
        LogPillTone.NEUTRAL -> c.surface.sunken to c.text.secondary
        LogPillTone.USER -> c.accent.container to c.accent.onContainer
    }
    Box(modifier.clip(LiuliShapes.pill).background(bg).padding(horizontal = AppSpacing.s, vertical = 2.dp)) {
        Text(text, style = AppTypography.caption.copy(fontWeight = FontWeight(520)), color = fg)
    }
}

/** 提示框（同暖陶 token·小圆角·左右留页边·下留 12）；[modifier] 挂在圆角裁切之后（可点时涟漪不出角）。 */
@Composable
fun LiuliLogNotice(tone: LogNoticeTone, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors.status
    val (bg, fg) = if (tone == LogNoticeTone.WARNING) c.warningContainer to c.onWarning else c.infoContainer to c.onInfo
    val shape = LiuliShapes.small
    Row(
        Modifier.padding(horizontal = LiuliPageGeometry.gutter).padding(bottom = LiuliLogItemGap).fillMaxWidth()
            .clip(shape).then(modifier).background(bg).border(1.dp, fg.copy(alpha = 0.14f), shape).padding(AppSpacing.m),
    ) {
        Icon(
            if (tone == LogNoticeTone.WARNING) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            contentDescription = null, tint = fg, modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(AppSpacing.s))
        Column(Modifier.weight(1f)) { content() }
    }
}

/**
 * 独立卡（时间线的轮卡 / 后台行）：页边 20 + 组圆角材质；失败时卡内左缘 3dp 红条（画在材质之后 = 在卡内·PITFALLS §1d）；
 * 整卡可点时 clickable 排在材质的圆角裁切之后。
 */
@Composable
fun LiuliLogCard(
    failed: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(LiuliPageGeometry.groupCorner)
    val click = if (onClick != null) Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.padding(horizontal = LiuliPageGeometry.gutter).fillMaxWidth()
            .liuliCardMaterial(shape, dark = LocalIsDarkTheme.current).then(click).height(IntrinsicSize.Min),
    ) {
        if (failed) Box(Modifier.width(3.dp).fillMaxHeight().background(AppTheme.colors.status.onError))
        Row(Modifier.weight(1f).padding(contentPadding), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** 芯片行（横滑·页边 20·芯片间 8）。 */
@Composable
fun <T> LiuliLogChipRow(options: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = LiuliPageGeometry.gutter),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s),
    ) {
        options.forEach { option -> LiuliChip(selected = selected(option), onClick = { onSelect(option) }, label = label(option)) }
    }
}

/** 页边内的一段说明文字（空态 / 说明行）。 */
@Composable
fun LiuliLogCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = AppTypography.caption, color = AppTheme.colors.text.secondary,
        modifier = modifier.padding(horizontal = LiuliPageGeometry.gutter).padding(bottom = AppSpacing.m),
    )
}

/** 列表底的留白（页底 + 导航栏由页壳给，这里只是卡与卡之间的逐项间距常量）。 */
val LiuliLogItemGap = AppSpacing.m

/** 组内细分隔之外的纵向空隙（统计卡 / 提示框之后）。 */
@Composable
fun LiuliLogGap() = Spacer(Modifier.height(LiuliLogItemGap))
