package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.diagnostics.FailureRateAlert
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.shared.LogFlowText
import com.situ.aichat.ui.contextlog.shared.alertLineTexts
import com.situ.aichat.ui.contextlog.shared.failureCopy
import com.situ.aichat.ui.contextlog.shared.flowFailureLine
import com.situ.aichat.ui.designsystem.AppChoiceChip
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme

/*
 * 「全部流水」分段（四期·图纸四 §4.1b·K2 = 原列表页行为）：原 `ContextLogListScreen.kt` 的 CategoryChips / CacheSummaryCard /
 * LogListCard / SourceBadge / EmptyState / DetailOffHint 只搬不改到这里（gutter 14dp 孤值改由页壳的 screenGutter 统一给）；
 * 有意变化：① LogListCard 失败行改「类名 · 一句话」（原始报错进失败详情页）；② 选中「失败」时多一排原因芯片；
 * ③ 告警条换 §4.0 提示框外壳 + 类名后缀（与「按对话」同一件·§11 D-2）。中文字面量收在 [LogFlowText]。
 */

internal fun LazyListScope.flowTabItems(
    state: ContextLogHomeUiState,
    onCategory: (LogCategory) -> Unit,
    onReason: (LlmFailureKind?) -> Unit,
    onShowFailures: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
) {
    val flow = state.flow
    item(key = "flow-chips") { CategoryChips(selected = flow.category, onSelect = onCategory) }
    if (flow.category == LogCategory.FAILED && state.reasonChips.isNotEmpty()) {
        item(key = "flow-reasons") { ReasonChips(state.reasonChips, state.reason, onReason) }
    }
    // 空筛选态也渲染告警条（复核 R1-🟡2 原注释）：健康是全局体检，不随当前筛选恰好无条目而消失。
    if (flow.alerts.isNotEmpty()) {
        item(key = "failure-alerts") { LogAlertNotice(flow.alerts, state.alertKinds, onShowFailures) }
    }
    if (state.flowEntries.isEmpty()) {
        item(key = "flow-empty") { EmptyState(loaded = flow.loaded, category = flow.category, detailEnabled = flow.detailEnabled) }
        return
    }
    flow.cacheSummary?.let { summary -> item(key = "cache-summary") { CacheSummaryCard(summary) } }
    items(state.flowEntries, key = { it.id }) { entry -> LogListCard(entry = entry, onClick = { onOpenEntry(entry.id, !entry.isSuccess) }) }
    if (!flow.detailEnabled) {
        item(key = "detail-off") { Spacer(Modifier.height(2.dp)); DetailOffHint() }
    }
}

@Composable
private fun CategoryChips(selected: LogCategory, onSelect: (LogCategory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(LogCategory.entries.toList()) { cat ->
            val on = cat == selected
            val brush = Brush.linearGradient(
                listOf(AppTheme.colors.accent.gradientStart, AppTheme.colors.accent.gradientEnd),
            )
            Box(
                modifier = Modifier
                    .clip(AppTheme.shapes.full)
                    .then(
                        if (on) Modifier.background(brush, AppTheme.shapes.full)
                        else Modifier.background(AppTheme.colors.surface.sunken, AppTheme.shapes.full),
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    cat.displayName,
                    style = AppTheme.typography.secondary,
                    color = if (on) AppTheme.colors.text.onAccent else AppTheme.colors.text.secondary,
                )
            }
        }
    }
}

/** 原因芯片（§4.1b·新增）：「全部原因 总数」+ 各类「类名 数」。 */
@Composable
private fun ReasonChips(chips: List<Pair<LlmFailureKind, Int>>, selected: LlmFailureKind?, onSelect: (LlmFailureKind?) -> Unit) {
    val all = stringResource(R.string.clog_reason_all)
    val labels = chips.map { (kind, n) -> kind to stringResource(R.string.clog_reason_chip, stringResource(failureCopy(kind).name), n) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
        item(key = "all") {
            AppChoiceChip(selected = selected == null, onClick = { onSelect(null) }, label = stringResource(R.string.clog_reason_chip, all, chips.sumOf { it.second }))
        }
        items(labels, key = { it.first.raw }) { (kind, label) ->
            AppChoiceChip(selected = selected == kind, onClick = { onSelect(kind) }, label = label)
        }
    }
}

/**
 * 近 24h 失败告警条（原 FailureAlertBanner：标题 / 每来源一行 / 「查看失败 ›」/ 整条一个语义节点不变；
 * 换 §4.0 提示框外壳 + 每行接「 · 类名」后缀）。「按对话」与「全部流水」共用这一件。
 */
@Composable
internal fun LogAlertNotice(alerts: List<FailureRateAlert>, kinds: Map<String, LlmFailureKind>, onShowFailed: () -> Unit) {
    val title = stringResource(R.string.contextlog_alert_title)
    val lines = alertLineTexts(alerts, kinds)
    val action = stringResource(R.string.contextlog_alert_action)
    val fg = AppTheme.colors.status.onWarning
    LogNotice(
        LogNoticeTone.WARNING,
        Modifier
            .clickable(onClickLabel = action, onClick = onShowFailed)
            .semantics(mergeDescendants = true) { contentDescription = "$title：${lines.joinToString("；")}。$action" },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppTheme.typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = fg)
                Spacer(Modifier.height(3.dp))
                lines.forEach { line ->
                    Text(line, style = AppTheme.typography.caption, color = fg.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(9.dp))
            Text("$action ›", style = AppTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun CacheSummaryCard(summary: CacheSummary) {
    val label = stringResource(R.string.contextlog_cache_rate_label)
    val countText = stringResource(R.string.contextlog_cache_rate_count, summary.entryCount)
    val rateText = stringResource(R.string.contextlog_cache_rate_inline, summary.ratePercent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.medium)
            .background(AppTheme.colors.surface.raised)
            .border(1.dp, AppTheme.colors.surface.stroke, AppTheme.shapes.medium)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label：$rateText，$countText" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppTheme.typography.caption,
            color = AppTheme.colors.text.secondary,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${summary.ratePercent}%",
                style = AppTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = AppTheme.colors.text.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(countText, style = AppTheme.typography.caption, color = AppTheme.colors.text.tertiary)
        }
    }
}

@Composable
private fun LogListCard(entry: LogListRow, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.medium)
            .background(AppTheme.colors.surface.raised)
            .border(1.dp, AppTheme.colors.surface.stroke, AppTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (!entry.isSuccess) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(AppTheme.colors.status.onError))
            }
            Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
                StatusGlyph(entry.isSuccess)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            primaryName(entry),
                            style = AppTheme.typography.bodyEmphasis,
                            color = if (entry.characterName.isBlank()) AppTheme.colors.accent.text else AppTheme.colors.text.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showSourceBadgeResolved(entry)) {
                            Spacer(Modifier.width(7.dp))
                            SourceBadge(entry.source)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${entry.modelName} · ${formatLogTime(entry.timestampMillis)}",
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    if (entry.isSuccess) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                LogFlowText.sentLine(entry.messageCount, formatDuration(entry.durationMillis)),
                                style = AppTheme.typography.caption,
                                color = AppTheme.colors.text.secondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                LogTokenFormat.withEstimatePrefix(
                                    entry.promptTokens + entry.completionTokens, entry.isTokenEstimated,
                                ) + " tk",
                                style = AppTheme.typography.captionNumeric,
                                color = AppTheme.colors.economy.gold,
                            )
                        }
                    } else {
                        FailureLine(entry)
                    }
                }
            }
        }
    }
}

/** 失败卡第三行（§4.1b 有意变化）：粗体类名 + 「 · 」+ 一句话（拼法与琉璃同一件 [flowFailureLine]）。 */
@Composable
private fun FailureLine(entry: LogListRow) {
    Text(
        flowFailureLine(entry),
        style = AppTheme.typography.caption,
        color = AppTheme.colors.status.onError,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SourceBadge(source: String) {
    Box(
        Modifier
            .background(AppTheme.colors.surface.sunken, AppTheme.shapes.full)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(source, style = AppTheme.typography.caption, color = AppTheme.colors.accent.text)
    }
}

@Composable
private fun EmptyState(loaded: Boolean, category: LogCategory, detailEnabled: Boolean) {
    Column(
        // 搬进懒列表后高度不受限、「竖向居中」失效，空态会贴在芯片下 12dp——上方留 hero 与按对话 / 琉璃一致（复核 R1）
        Modifier.fillMaxSize().padding(top = AppSpacing.hero).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loaded) {
            Text(
                if (category == LogCategory.ALL) LogFlowText.EMPTY_ALL else LogFlowText.emptyCategory(category.displayName),
                style = AppTheme.typography.body,
                color = AppTheme.colors.text.tertiary,
            )
            if (!detailEnabled) {
                Spacer(Modifier.height(16.dp))
                DetailOffHint()
            }
        }
    }
}

@Composable
private fun DetailOffHint() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.medium)
            .background(AppTheme.colors.surface.sunken)
            .padding(13.dp),
    ) {
        Text(
            LogFlowText.DETAIL_OFF_HINT,
            style = AppTheme.typography.caption,
            color = AppTheme.colors.text.secondary,
        )
    }
}
