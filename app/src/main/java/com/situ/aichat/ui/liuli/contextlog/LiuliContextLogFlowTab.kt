package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.CacheSummary
import com.situ.aichat.ui.contextlog.ContextLogHomeUiState
import com.situ.aichat.ui.contextlog.StatusGlyph
import com.situ.aichat.ui.contextlog.formatDuration
import com.situ.aichat.ui.contextlog.formatLogTime
import com.situ.aichat.ui.contextlog.primaryName
import com.situ.aichat.ui.contextlog.shared.LogFlowText
import com.situ.aichat.ui.contextlog.shared.failureCopy
import com.situ.aichat.ui.contextlog.shared.flowFailureLine
import com.situ.aichat.ui.contextlog.showSourceBadgeResolved
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmentPosition
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliCardSegment
import com.situ.aichat.ui.liuli.designsystem.liuliSegmentPosition
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/*
 * 「全部流水」分段（四期·图纸四 §4.1b·琉璃）：内容 / 顺序同暖陶 `ContextLogFlowTab`（分类芯片 → 原因芯片 → 告警条 → 命中率卡 →
 * 调用卡 → 详细关提示；空态文字逐字同）；外壳按 §4.0 琉璃词汇重写，调用卡 = 分段卡列表（liuliCardSegment）。
 */

private val ROW_DIVIDER = 0.5.dp

internal fun LazyListScope.liuliFlowTabItems(
    state: ContextLogHomeUiState,
    onCategory: (LogCategory) -> Unit,
    onReason: (LlmFailureKind?) -> Unit,
    onShowFailures: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
) {
    val flow = state.flow
    item(key = "flow-chips") {
        LiuliLogChipRow(LogCategory.entries.toList(), { it == flow.category }, { it.displayName }, onCategory, Modifier.padding(bottom = AppSpacing.m))
    }
    if (flow.category == LogCategory.FAILED && state.reasonChips.isNotEmpty()) {
        item(key = "flow-reasons") {
            val all = stringResource(R.string.clog_reason_all)
            val options: List<LlmFailureKind?> = listOf<LlmFailureKind?>(null) + state.reasonChips.map { it.first }
            val counts = state.reasonChips.toMap()
            LiuliLogChipRow(
                options, { it == state.reason },
                { k -> if (k == null) stringResource(R.string.clog_reason_chip, all, counts.values.sum()) else stringResource(R.string.clog_reason_chip, stringResource(failureCopy(k).name), counts.getValue(k)) },
                onReason, Modifier.padding(bottom = AppSpacing.m),
            )
        }
    }
    if (flow.alerts.isNotEmpty()) {
        item(key = "failure-alerts") { LiuliLogAlertNotice(flow.alerts, state.alertKinds, onShowFailures) }
    }
    if (state.flowEntries.isEmpty()) {
        if (flow.loaded) {
            item(key = "flow-empty") {
                LiuliLogEmpty(if (flow.category == LogCategory.ALL) LogFlowText.EMPTY_ALL else LogFlowText.emptyCategory(flow.category.displayName))
            }
            if (!flow.detailEnabled) item(key = "detail-off") { DetailOffHint(Modifier.padding(top = AppSpacing.l)) }
        }
        return
    }
    flow.cacheSummary?.let { summary -> item(key = "cache-summary") { CacheSummaryGroup(summary) } }
    itemsIndexed(state.flowEntries, key = { _, e -> e.id }) { i, entry ->
        FlowRow(entry, liuliSegmentPosition(i, state.flowEntries.size), showDivider = i > 0) { onOpenEntry(entry.id, !entry.isSuccess) }
    }
    if (!flow.detailEnabled) item(key = "detail-off") { DetailOffHint(Modifier.padding(top = AppSpacing.l)) }
}

@Composable
private fun CacheSummaryGroup(summary: CacheSummary) {
    val label = stringResource(R.string.contextlog_cache_rate_label)
    val countText = stringResource(R.string.contextlog_cache_rate_count, summary.entryCount)
    val rateText = stringResource(R.string.contextlog_cache_rate_inline, summary.ratePercent)
    LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
        LiuliLogFreeRow(first = true, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label：$rateText，$countText" }) {
            Text(label, style = AppTypography.caption, color = AppTheme.colors.text.secondary, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${summary.ratePercent}%", style = AppTypography.titleSmall.copy(fontFeatureSettings = "tnum"), color = AppTheme.colors.text.primary)
                Text(countText, style = AppTypography.caption, color = AppTheme.colors.text.secondary)
            }
        }
    }
}

/** 分段卡一行：失败左缘红条（在卡内）+ 状态圈 + 名字 / 来源徽标 + 模型 · 时刻 + 成功行或「类名 · 一句话」。 */
@Composable
private fun FlowRow(entry: LogListRow, position: LiuliSegmentPosition, showDivider: Boolean, onClick: () -> Unit) {
    val dark = LocalIsDarkTheme.current
    val r = LiuliPageGeometry.groupCorner
    val shape = when (position) {
        LiuliSegmentPosition.Single -> RoundedCornerShape(r)
        LiuliSegmentPosition.Top -> RoundedCornerShape(topStart = r, topEnd = r)
        LiuliSegmentPosition.Bottom -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        LiuliSegmentPosition.Middle -> RoundedCornerShape(0.dp)
    }
    Column(Modifier.padding(horizontal = LiuliPageGeometry.gutter).liuliCardSegment(position, dark)) {
        if (showDivider) {
            Box(Modifier.fillMaxWidth().padding(start = LiuliPageGeometry.groupPadH).height(ROW_DIVIDER).background(LiuliMaterials.divider(dark)))
        }
        Row(Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button, onClick = onClick).height(IntrinsicSize.Min)) {
            if (!entry.isSuccess) Box(Modifier.width(3.dp).fillMaxHeight().background(AppTheme.colors.status.onError))
            Row(Modifier.padding(horizontal = LiuliPageGeometry.groupPadH, vertical = AppSpacing.m)) {
                StatusGlyph(entry.isSuccess)
                Spacer(Modifier.width(LiuliPageGeometry.tileGap))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            primaryName(entry), style = AppTypography.bodyEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (entry.characterName.isBlank()) AppTheme.colors.accent.text else AppTheme.colors.text.primary,
                        )
                        if (showSourceBadgeResolved(entry)) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                entry.source, style = AppTypography.caption, color = AppTheme.colors.accent.text,
                                modifier = Modifier.background(AppTheme.colors.surface.sunken, LiuliShapes.pill).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text("${entry.modelName} · ${formatLogTime(entry.timestampMillis)}", style = AppTypography.caption, color = AppTheme.colors.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    if (entry.isSuccess) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                LogFlowText.sentLine(entry.messageCount, formatDuration(entry.durationMillis)), style = AppTypography.caption,
                                color = AppTheme.colors.text.secondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(LogTokenFormat.withEstimatePrefix(entry.promptTokens + entry.completionTokens, entry.isTokenEstimated) + " tk", style = AppTypography.captionNumeric, color = AppTheme.colors.economy.gold)
                        }
                    } else {
                        Text(flowFailureLine(entry), style = AppTypography.caption, color = AppTheme.colors.status.onError, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** 详细关提示（原文案逐字）：一组里的一行说明字。 */
@Composable
private fun DetailOffHint(modifier: Modifier = Modifier) {
    LiuliGroup(modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
        LiuliLogFreeRow(first = true) { Text(LogFlowText.DETAIL_OFF_HINT, style = AppTypography.caption, color = AppTheme.colors.text.secondary) }
    }
}
