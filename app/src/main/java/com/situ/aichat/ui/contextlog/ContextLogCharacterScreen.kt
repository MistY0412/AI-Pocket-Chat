package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.contextlog.model.LogBackgroundRow
import com.situ.aichat.ui.contextlog.model.LogKid
import com.situ.aichat.ui.contextlog.model.LogTurnCard
import com.situ.aichat.ui.contextlog.shared.dayHeaderText
import com.situ.aichat.ui.contextlog.shared.kidTailText
import com.situ.aichat.ui.contextlog.shared.quoteText
import com.situ.aichat.ui.contextlog.shared.sourceIcon
import com.situ.aichat.ui.contextlog.shared.timeTokensText
import com.situ.aichat.ui.contextlog.shared.turnFailedText
import com.situ.aichat.ui.contextlog.shared.turnMetaText
import com.situ.aichat.ui.designsystem.AppChoiceChip
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar

/** 角色页（四期·图纸四 §4.2·暖陶）：会话芯片（≥ 2 个才出）+ 按天分节的一轮一轮卡 + 后台小方块。 */
@Composable
fun ContextLogCharacterScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    viewModel: ContextLogCharacterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContextLogCharacterContent(state, onBack, onOpenEntry, viewModel::selectConversation)
}

@Composable
internal fun ContextLogCharacterContent(
    state: ContextLogCharacterUiState,
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onSelectConversation: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = AppTheme.colors.surface.base,
        topBar = {
            AppTopBar(
                title = if (state.isSystem) stringResource(R.string.clog_system_tasks) else state.name,
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppSpacing.screenGutter, end = AppSpacing.screenGutter, top = AppSpacing.s, bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGapInGroup),
        ) {
            if (state.conversations.size >= 2) {
                item(key = "chips") {
                    val untitled = stringResource(R.string.clog_conversation_untitled)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
                        items(state.conversations, key = { it.uuid }) { chip ->
                            AppChoiceChip(
                                selected = chip.uuid == state.selectedConversation,
                                onClick = { onSelectConversation(chip.uuid) },
                                label = stringResource(R.string.clog_conversation_chip, chip.title.ifEmpty { untitled }),
                            )
                        }
                    }
                }
            }
            if (state.loaded && state.sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.clog_character_empty),
                        style = AppTheme.typography.secondary,
                        color = AppTheme.colors.text.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.hero),
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "day:${section.date}") { LogGroupLabel(dayHeaderText(section.dayKind, section.date)) }
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
    LogStripCard(
        failed = card.openFailed,
        contentPadding = PaddingValues(horizontal = AppSpacing.m, vertical = AppSpacing.m),
        onClick = { onOpenEntry(card.openId, card.openFailed) },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatLogTime(card.sortMillis),
                    style = AppTheme.typography.settingsRowValue.copy(fontFeatureSettings = "tnum"),
                    color = AppTheme.colors.text.primary,
                )
                Spacer(Modifier.width(AppSpacing.s))
                Text(
                    card.quote?.let { quoteText(it) } ?: card.mainSource,
                    style = AppTheme.typography.secondary,
                    color = if (card.quote != null) AppTheme.colors.text.primary else AppTheme.colors.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val failure = card.failureKind
            Text(
                if (card.openFailed && failure != null) turnFailedText(failure) else turnMetaText(card),
                style = AppTheme.typography.caption,
                color = if (card.openFailed) AppTheme.colors.status.onError else AppTheme.colors.text.secondary,
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
            if (card.kids.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.s))
                DashedDivider()
                card.kids.forEach { KidRow(it, onOpenEntry) }
            }
        }
    }
}

@Composable
private fun DashedDivider() {
    val color = AppTheme.colors.surface.stroke
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun KidRow(kid: LogKid, onOpenEntry: (Long, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = kid.source) { onOpenEntry(kid.id, kid.failed) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SubdirectoryArrowRight, contentDescription = null, tint = AppTheme.colors.text.secondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(kid.source, style = AppTheme.typography.caption, color = AppTheme.colors.text.primary)
        Spacer(Modifier.width(AppSpacing.s))
        Text(timeTokensText(kid.timeMillis, kid.tokens, kid.tokensEstimated), style = AppTheme.typography.captionNumeric, color = AppTheme.colors.text.secondary)
        if (kid.failed) Text(kidTailText(), style = AppTheme.typography.caption, color = AppTheme.colors.status.onError)
    }
}

@Composable
private fun BackgroundRow(row: LogBackgroundRow, onOpenEntry: (Long, Boolean) -> Unit) {
    LogStripCard(
        failed = row.failed,
        contentPadding = PaddingValues(horizontal = AppSpacing.m, vertical = AppSpacing.s),
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = { onOpenEntry(row.id, row.failed) },
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.surface.sunken), contentAlignment = Alignment.Center) {
            Icon(sourceIcon(row.source), contentDescription = null, tint = AppTheme.colors.accent.text, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(AppSpacing.m))
        Column(Modifier.weight(1f)) {
            Text(row.source, style = AppTheme.typography.settingsRowTitle, color = AppTheme.colors.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timeTokensText(row.sortMillis, row.tokens, row.tokensEstimated), style = AppTheme.typography.captionNumeric, color = AppTheme.colors.text.secondary)
        }
        Text(
            stringResource(if (row.failed) R.string.clog_kid_failed else R.string.clog_bg_caption),
            style = AppTheme.typography.caption,
            color = if (row.failed) AppTheme.colors.status.onError else AppTheme.colors.text.secondary,
        )
    }
}
