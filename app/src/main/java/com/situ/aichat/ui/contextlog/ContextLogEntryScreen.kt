package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.LogShareFormat
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.shared.LogMapLegend
import com.situ.aichat.ui.contextlog.shared.LogMiniMap
import com.situ.aichat.ui.contextlog.shared.LogToolCallContent
import com.situ.aichat.ui.contextlog.shared.breakNavText
import com.situ.aichat.ui.contextlog.shared.entryCaptionText
import com.situ.aichat.ui.contextlog.shared.failureCopy
import com.situ.aichat.ui.contextlog.shared.kidTailText
import com.situ.aichat.ui.contextlog.shared.sentSubtitleText
import com.situ.aichat.ui.contextlog.shared.timeTokensText
import com.situ.aichat.ui.contextlog.shared.tokensExtraText
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.logMapColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 一轮详情 / 调用详情（四期·图纸四 §4.3·暖陶·取代原 `ContextLogDetailScreen` + 分段页）：说明行 → 信息卡 → Token 卡 →
 * （失败兜底卡）→ 迷你地图卡 → 链接卡 → 工具调用 → 这一轮带出的后台调用 → 复制全文 / 导出可重放请求。
 */
@Composable
fun ContextLogEntryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenSent: (Long) -> Unit,
    onOpenReply: (Long) -> Unit,
    viewModel: ContextLogEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ContextLogEntryContent(
        state, onBack, onOpenEntry, onOpenMap, onOpenSent, onOpenReply,
        // 拼 20 万字级大串 → Default 调度器（原详情页同款）。
        onCopyAll = { e ->
            scope.launch(Dispatchers.Default) {
                LogShareActions.copyOrExport(context, LogShareFormat.entryText(e), LogShareFormat.exportFileName(e.source, e.timestampMillis))
            }
        },
        onExportReplay = { e ->
            scope.launch(Dispatchers.Default) {
                viewModel.replayExport(e)?.let { (text, fileName) -> LogShareActions.exportWithFeedback(context, text, fileName) }
            }
        },
    )
}

@Composable
internal fun ContextLogEntryContent(
    state: ContextLogEntryUiState,
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenSent: (Long) -> Unit,
    onOpenReply: (Long) -> Unit,
    onCopyAll: (LogEntryEntity) -> Unit,
    onExportReplay: (LogEntryEntity) -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = AppTheme.colors.surface.base,
        topBar = {
            AppTopBar(
                title = stringResource(if (state.isTurn) R.string.clog_turn_title else R.string.clog_entry_title),
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
            if (!state.loaded) return@LazyColumn
            val e = state.entry
            if (e == null) {
                item(key = "missing") { MissingText() }
                return@LazyColumn
            }
            item(key = "caption") {
                Text(entryCaptionText(e, state.conversationTitle, state.quote), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
            }
            item(key = "info") { InfoCard(e, state.providerLabel) }
            if (e.isSuccess) item(key = "tokens") { TokenCard(e, state.cacheRatePercent) }
            state.failure?.let { f ->
                item(key = "failure") {
                    val copy = failureCopy(f.kind)
                    LogCard {
                        Text(stringResource(copy.name), style = AppTheme.typography.label, color = AppTheme.colors.status.onError)
                        Text(stringResource(copy.short), style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary)
                        LogNavRow(stringResource(R.string.clog_open_failure), onClick = { onOpenEntry(e.id, true) })
                    }
                }
            }
            item(key = "minimap") { MiniMapCard(state, onOpenMap = { onOpenMap(e.id) }) }
            item(key = "links") {
                LogCard {
                    LogNavRow(stringResource(R.string.clog_sent_title), onClick = { onOpenSent(e.id) }, subtitle = sentSubtitleText(state.adaptation))
                    if (!e.responseContent.isNullOrEmpty()) LogNavRow(stringResource(R.string.clog_reply_full), onClick = { onOpenReply(e.id) })
                }
            }
            state.toolInfo?.let { info -> item(key = "tools") { LogCard(title = "工具调用") { LogToolCallContent(info) } } }
            if (state.kids.isNotEmpty()) {
                item(key = "kids") {
                    LogCard(title = stringResource(R.string.clog_turn_kids_title)) {
                        state.kids.forEach { kid ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = kid.source) { onOpenEntry(kid.id, kid.failed) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(kid.source, style = AppTheme.typography.secondary, color = AppTheme.colors.text.primary, modifier = Modifier.weight(1f))
                                Text(
                                    timeTokensText(kid.timeMillis, kid.tokens, kid.tokensEstimated) + (if (kid.failed) kidTailText() else "") + " ›",
                                    style = AppTheme.typography.secondary,
                                    color = if (kid.failed) AppTheme.colors.status.onError else AppTheme.colors.text.primary,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "buttons") { ActionButtons(state, e, onCopyAll, onExportReplay) }
        }
    }
}

@Composable
internal fun MissingText() {
    Text(
        stringResource(R.string.clog_entry_missing),
        style = AppTheme.typography.secondary,
        color = AppTheme.colors.text.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.hero),
    )
}

@Composable
private fun InfoCard(e: LogEntryEntity, providerLabel: String?) {
    LogCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.isSuccess) LogPill(stringResource(R.string.clog_status_ok), LogPillTone.SUCCESS)
            else LogPill(stringResource(R.string.clog_status_failed), LogPillTone.ERROR)
            Spacer(Modifier.weight(1f))
            Text(LogFormat.fullTime(e.timestampMillis, java.time.ZoneId.systemDefault()), style = AppTheme.typography.captionNumeric, color = AppTheme.colors.text.secondary)
        }
        Spacer(Modifier.size(AppSpacing.s))
        LogKvRow(stringResource(R.string.clog_kv_source), e.source)
        LogKvRow(stringResource(R.string.clog_kv_model), e.modelName)
        LogKvRow(stringResource(R.string.clog_kv_provider), providerLabel ?: stringResource(R.string.clog_old_value))
        e.durationMillis?.let { LogKvRow(stringResource(R.string.clog_kv_duration), stringResource(R.string.clog_turn_duration, LogFormat.seconds(it))) }
        LogKvRow(stringResource(R.string.clog_kv_messages), stringResource(R.string.clog_messages_count, e.messageCount))
    }
}

@Composable
private fun TokenCard(e: LogEntryEntity, cacheRatePercent: Int?) {
    LogCard(title = stringResource(R.string.clog_tokens_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
            TokenCell(LogFormat.grouped(e.promptTokens.toLong()), stringResource(R.string.clog_tokens_input), Modifier.weight(1f))
            TokenCell(LogFormat.grouped(e.completionTokens.toLong()), stringResource(R.string.clog_tokens_output), Modifier.weight(1f))
            TokenCell(cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_rate_none), stringResource(R.string.clog_tokens_cache), Modifier.weight(1f))
        }
        Spacer(Modifier.size(AppSpacing.s))
        Text(tokensExtraText(e), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
        if (e.isTokenEstimated) Text(stringResource(R.string.clog_tokens_estimated), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
    }
}

@Composable
private fun TokenCell(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(AppTheme.shapes.small).background(AppTheme.colors.surface.sunken).padding(vertical = AppSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = AppTheme.typography.bodyEmphasis.copy(fontFeatureSettings = "tnum"), color = AppTheme.colors.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary)
    }
}

@Composable
private fun MiniMapCard(state: ContextLogEntryUiState, onOpenMap: () -> Unit) {
    val mini = state.miniMap
    if (mini == null) {
        LogCard { Text(stringResource(R.string.clog_map_old), style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary) }
        return
    }
    val colors = logMapColors()
    LogCard(title = stringResource(R.string.clog_map_title)) {
        LogMiniMap(mini, colors)
        Spacer(Modifier.size(AppSpacing.s))
        LogMapLegend(colors)
        val cut = state.comparison?.cacheBreak
        if (cut != null) {
            LogNavRow(breakNavText(cut.kind, cut.moduleName), onClick = onOpenMap, subtitle = stringResource(R.string.clog_break_nav_sub))
        }
    }
}

@Composable
private fun ActionButtons(state: ContextLogEntryUiState, e: LogEntryEntity, onCopyAll: (LogEntryEntity) -> Unit, onExportReplay: (LogEntryEntity) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
            AppButton(onClick = { onCopyAll(e) }, style = AppButtonStyle.Tonal, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AppSpacing.s))
                Text(stringResource(R.string.contextlog_copy_all))
            }
            AppButton(onClick = { onExportReplay(e) }, style = AppButtonStyle.Primary, enabled = state.canExportReplay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AppSpacing.s))
                Text(stringResource(R.string.clog_export_replay), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            stringResource(
                when {
                    state.canExportReplay -> R.string.clog_export_note
                    !state.detailEnabled -> R.string.clog_export_needs_detail
                    else -> R.string.clog_export_none
                },
            ),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.text.secondary,
            modifier = Modifier.padding(top = AppSpacing.s),
        )
    }
}
