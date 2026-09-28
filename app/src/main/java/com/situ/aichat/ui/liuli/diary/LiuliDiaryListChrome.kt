package com.situ.aichat.ui.liuli.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.diary.DiaryEntryFilter
import com.situ.aichat.ui.diary.DiaryViewMode
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmented
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmentedStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliCardMaterial
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.liuli.page.LiuliPageCircleAction
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

// 琉璃日记本的导航行件 / 筛选条 / 卡与信封材质 / 本卷常量（琉璃 2.0 卷六·一 §4.1）。

/** 票根卡圆角 16（= 暖陶卡圆角·设计稿 `.tk` 16）。 */
private val DIARY_CARD_SHAPE = RoundedCornerShape(16.dp)
/** 时间线卡左右 16 / 上下 5（= 暖陶）。 */
internal val DIARY_CARD_GUTTER = 16.dp
internal val DIARY_CARD_GAP_V = 5.dp
/** 交换信封底（设计稿 `.env`）：浅 = 白 28%、深 = 白 4%。 */
private const val ENVELOPE_FILL_LIGHT = 0.28f
private const val ENVELOPE_FILL_DARK = 0.04f
/** 连续天数胶囊横内距（设计稿 `.btxt`）。 */
private val STREAK_PAD_H = 14.dp
/** 纸面筛选条内距（= 暖陶筛选条下 8 + 列表顶 4）。 */
private val FILTER_PAD_H = 16.dp
private val FILTER_PAD_V = 12.dp
/** 导航行菜单落在圆钮下方 8（`Popup(TopEnd)` 是右上角贴锚点右上角·PITFALLS §1d）。详情页同用。 */
internal val DIARY_MENU_OFFSET = DpOffset(0.dp, LiuliPageGeometry.backButton + 8.dp)
/** 日记本屏底渐进带在导航栏之上伸出 = 写一笔距导航栏 24 + 胶囊高 48 + 尾巴 12 = 84。 */
internal val DIARY_LIST_BOTTOM_EDGE = LiuliPageGeometry.fabBottom + LiuliPageGeometry.fabPill + LiuliPageGeometry.edgeTail

/** 琉璃票根卡 / 那年今天的卡面：半透明卡（16 圆角）。 */
internal fun Modifier.liuliDiaryCard(dark: Boolean) = liuliCardMaterial(DIARY_CARD_SHAPE, dark)

/** 交换信封的卡面：比内容卡更淡的一层白（虚线信封边由暖陶件自己画在其后）。 */
internal fun Modifier.liuliEnvelopeSurface(dark: Boolean) =
    clip(DIARY_CARD_SHAPE).background(Color.White.copy(alpha = if (dark) ENVELOPE_FILL_DARK else ENVELOPE_FILL_LIGHT))

/** 连续记录胶囊（R5 印章·发布才算）：导航行里一片小玻璃，「N 天」主色字；不可点（同暖陶）。 */
@Composable
internal fun LiuliDiaryStreakPill(days: Int) {
    val colors = AppTheme.colors
    val dark = LocalIsDarkTheme.current
    val cd = stringResource(R.string.a11y_diary_streak, days)
    Box(
        modifier = Modifier
            .height(LiuliPageGeometry.backButton)
            .liuliGlass(LiuliShapes.pill, dark = dark, role = LiuliGlassRole.Button)
            .padding(horizontal = STREAK_PAD_H)
            .clearAndSetSemantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.diary_streak_days, days),
            style = AppTypography.captionNumeric,
            color = colors.accent.text,
            maxLines = 1,
        )
    }
}

/** 视图菜单：日期圆钮 + 玻璃菜单（三模式带勾 + 「回顾与统计」）。 */
@Composable
internal fun LiuliDiaryViewMenuAction(
    viewMode: DiaryViewMode,
    onViewModeChange: (DiaryViewMode) -> Unit,
    onShowStats: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        LiuliPageCircleAction(
            onClick = { expanded = true },
            contentDescription = stringResource(R.string.diary_view_mode),
            icon = Icons.Filled.DateRange,
        )
        LiuliPopupMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            items = DiaryViewMode.entries.map { mode ->
                LiuliMenuEntry(text = stringResource(mode.labelRes), selected = mode == viewMode, onClick = { onViewModeChange(mode) })
            } + LiuliMenuEntry(text = stringResource(R.string.diary_menu_insights), onClick = onShowStats),
            offset = DIARY_MENU_OFFSET,
        )
    }
}

/** 作者筛选条（全部 / 我的 / TA 的信）：列表头里是纸面分段，收起后住进玻璃顶栏的 subBar 换玻璃分段。 */
@Composable
internal fun LiuliDiaryFilterStrip(selected: DiaryEntryFilter, onSelect: (DiaryEntryFilter) -> Unit, glass: Boolean) {
    LiuliSegmented(
        options = DiaryEntryFilter.entries,
        selected = selected,
        label = { stringResource(it.labelRes) },
        onSelect = onSelect,
        modifier = if (glass) Modifier else Modifier.padding(horizontal = FILTER_PAD_H, vertical = FILTER_PAD_V),
        style = if (glass) LiuliSegmentedStyle.Glass else LiuliSegmentedStyle.Paper,
    )
}
