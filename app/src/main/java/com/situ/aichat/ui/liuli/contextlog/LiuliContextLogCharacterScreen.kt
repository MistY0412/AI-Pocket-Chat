package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.contextlog.ContextLogCharacterUiState
import com.situ.aichat.ui.contextlog.ContextLogCharacterViewModel
import com.situ.aichat.ui.contextlog.formatLogTime
import com.situ.aichat.ui.contextlog.model.LogBackgroundRow
import com.situ.aichat.ui.contextlog.model.LogConversationChip
import com.situ.aichat.ui.contextlog.model.LogKid
import com.situ.aichat.ui.contextlog.model.LogTurnCard
import com.situ.aichat.ui.contextlog.shared.dayHeaderText
import com.situ.aichat.ui.contextlog.shared.kidTailText
import com.situ.aichat.ui.contextlog.shared.quoteText
import com.situ.aichat.ui.contextlog.shared.sourceIcon
import com.situ.aichat.ui.contextlog.shared.timeTokensText
import com.situ.aichat.ui.contextlog.shared.turnFailedText
import com.situ.aichat.ui.contextlog.shared.turnMetaText
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliSectionHeader
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed

/** 角色页（四期·图纸四 §4.2·琉璃）：与暖陶共用 [ContextLogCharacterViewModel]；会话芯片 + 按天分节的独立卡。 */
@Composable
fun LiuliContextLogCharacterScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    viewModel: ContextLogCharacterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiuliContextLogCharacterContent(state, onBack, onOpenEntry, viewModel::selectConversation)
}

@Composable
internal fun LiuliContextLogCharacterContent(
    state: ContextLogCharacterUiState,
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onSelectConversation: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val title = if (state.isSystem) stringResource(R.string.clog_system_tasks) else state.name
    val bottomInset = LiuliPageGeometry.pageBottom + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LiuliPage(title = title, onBack = onBack, collapsed = rememberLargeTitleCollapsed(listState)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = bottomInset),
        ) {
            item(key = "large-title") { LiuliLargeTitle(title) }
            if (state.conversations.size >= 2) {
                item(key = "chips") {
                    val untitled = stringResource(R.string.clog_conversation_untitled)
                    LiuliLogChipRow<LogConversationChip>(
                        state.conversations, { it.uuid == state.selectedConversation },
                        { stringResource(R.string.clog_conversation_chip, it.title.ifEmpty { untitled }) },
                        { onSelectConversation(it.uuid) }, Modifier.padding(bottom = AppSpacing.m),
                    )
                }
            }
            if (state.loaded && state.sections.isEmpty()) {
                item(key = "empty") { LiuliLogEmpty(stringResource(R.string.clog_character_empty)) }
            }
            state.sections.forEach { section ->
                item(key = "day:${section.date}") { LiuliSectionHeader(dayHeaderText(section.dayKind, section.date)) }
                items(section.items, key = { it.key }) { item ->
                    when (item) {
                        is LogTurnCard -> TurnCard(item, onOpenEntry)
                        is LogBackgroundRow -> BackgroundRow(item, onOpenEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnCard(card: LogTurnCard, onOpenEntry: (Long, Boolean) -> Unit) {
    LiuliLogCard(
        failed = card.openFailed,
        contentPadding = PaddingValues(horizontal = AppSpacing.m, vertical = AppSpacing.m),
        modifier = Modifier.padding(bottom = LiuliLogItemGap),
        onClick = { onOpenEntry(card.openId, card.openFailed) },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatLogTime(card.sortMillis), style = AppTypography.settingsRowValue.copy(fontFeatureSettings = "tnum"), color = AppTheme.colors.text.primary)
                Spacer(Modifier.width(AppSpacing.s))
                Text(
                    card.quote?.let { quoteText(it) } ?: card.mainSource,
                    style = AppTypography.secondary,
                    color = if (card.quote != null) AppTheme.colors.text.primary else AppTheme.colors.text.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val failure = card.failureKind
            Text(
                if (card.openFailed && failure != null) turnFailedText(failure) else turnMetaText(card),
                style = AppTypography.caption,
                color = if (card.openFailed) AppTheme.colors.status.onError else AppTheme.colors.text.secondary,
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
            if (card.kids.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.s))
                val stroke = AppTheme.colors.surface.stroke
                Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(
                        stroke, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }
                card.kids.forEach { KidRow(it, onOpenEntry) }
            }
        }
    }
}

@Composable
private fun KidRow(kid: LogKid, onOpenEntry: (Long, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = LiuliPageGeometry.touchTarget).clickable(role = Role.Button, onClickLabel = kid.source) { onOpenEntry(kid.id, kid.failed) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SubdirectoryArrowRight, contentDescription = null, tint = AppTheme.colors.text.secondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(kid.source, style = AppTypography.caption, color = AppTheme.colors.text.primary)
        Spacer(Modifier.width(AppSpacing.s))
        Text(timeTokensText(kid.timeMillis, kid.tokens, kid.tokensEstimated), style = AppTypography.captionNumeric, color = AppTheme.colors.text.secondary)
        if (kid.failed) Text(kidTailText(), style = AppTypography.caption, color = AppTheme.colors.status.onError)
    }
}

@Composable
private fun BackgroundRow(row: LogBackgroundRow, onOpenEntry: (Long, Boolean) -> Unit) {
    LiuliLogCard(
        failed = row.failed,
        contentPadding = PaddingValues(horizontal = AppSpacing.m, vertical = AppSpacing.s),
        // 触达 ≥ 48：最小高把下方逐项间距一并算进去（卡本体 ≥ 48）。
        modifier = Modifier.heightIn(min = LiuliPageGeometry.touchTarget + LiuliLogItemGap).padding(bottom = LiuliLogItemGap),
        onClick = { onOpenEntry(row.id, row.failed) },
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.surface.sunken), contentAlignment = Alignment.Center) {
            Icon(sourceIcon(row.source), contentDescription = null, tint = AppTheme.colors.accent.text, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(AppSpacing.m))
        Column(Modifier.weight(1f)) {
            Text(row.source, style = AppTypography.settingsRowTitle, color = AppTheme.colors.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timeTokensText(row.sortMillis, row.tokens, row.tokensEstimated), style = AppTypography.captionNumeric, color = AppTheme.colors.text.secondary)
        }
        Text(
            stringResource(if (row.failed) R.string.clog_kid_failed else R.string.clog_bg_caption),
            style = AppTypography.caption,
            color = if (row.failed) AppTheme.colors.status.onError else AppTheme.colors.text.secondary,
        )
    }
}
