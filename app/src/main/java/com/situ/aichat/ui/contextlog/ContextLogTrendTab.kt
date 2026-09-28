package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.diagnostics.LogTrend
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogTrendState
import com.situ.aichat.ui.contextlog.model.TrendRange
import com.situ.aichat.ui.contextlog.shared.LogHitRateLineChart
import com.situ.aichat.ui.contextlog.shared.LogLegendItem
import com.situ.aichat.ui.contextlog.shared.LogUsageBarChart
import com.situ.aichat.ui.contextlog.shared.chineseUnits
import com.situ.aichat.ui.designsystem.AppChoiceChip
import com.situ.aichat.ui.designsystem.AppListDivider
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.logMapColors

/** 「趋势」分段（四期·图纸四 §4.1c·暖陶）：范围芯片 → 每天用量柱图 → 对话命中率折线 → 按模型表（不显示金额）。 */
internal fun LazyListScope.trendTabItems(trend: LogTrendState, onRange: (TrendRange) -> Unit) {
    item(key = "trend-range") { RangeChips(trend.range, onRange) }
    if (trend.empty) {
        item(key = "trend-empty") {
            Text(
                stringResource(R.string.clog_trend_empty),
                style = AppTheme.typography.secondary,
                color = AppTheme.colors.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.hero),
            )
        }
        return
    }
    item(key = "trend-usage") {
        LogCard(title = stringResource(R.string.clog_usage_title)) {
            LogUsageBarChart(
                trend.days,
                hitColor = AppTheme.colors.accent.text,
                restColor = logMapColors().history,
                gridColor = AppTheme.colors.surface.stroke,
                labelColor = AppTheme.colors.text.secondary,
                chineseUnits = chineseUnits(),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            Spacer(Modifier.height(AppSpacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogLegendItem(AppTheme.colors.accent.text, false, stringResource(R.string.clog_legend_hit))
                LogLegendItem(logMapColors().history, false, stringResource(R.string.clog_legend_full))
            }
        }
    }
    item(key = "trend-rate") {
        LogCard(title = stringResource(R.string.clog_hit_rate_title)) {
            LogHitRateLineChart(
                trend.days,
                lineColor = AppTheme.colors.accent.text,
                gridColor = AppTheme.colors.surface.stroke,
                labelColor = AppTheme.colors.text.secondary,
                dotFill = AppTheme.colors.surface.raised,
                modifier = Modifier.fillMaxWidth().height(104.dp),
            )
        }
    }
    item(key = "trend-models") {
        LogCard(title = stringResource(R.string.clog_by_model_title, rangeText(trend.range))) {
            ModelTable(trend.models)
            Text(
                stringResource(R.string.clog_no_money),
                style = AppTheme.typography.caption,
                color = AppTheme.colors.text.secondary,
                modifier = Modifier.padding(top = AppSpacing.s),
            )
        }
    }
}

@Composable
internal fun rangeText(range: TrendRange): String =
    stringResource(if (range == TrendRange.WEEK) R.string.clog_range_7 else R.string.clog_range_30)

@Composable
private fun RangeChips(selected: TrendRange, onRange: (TrendRange) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s)) {
        TrendRange.entries.forEach { r ->
            item(key = r.name) { AppChoiceChip(selected = selected == r, onClick = { onRange(r) }, label = rangeText(r)) }
        }
    }
}

/** 按模型表：模型（左·最宽 130dp 省略）/ 次数 / token / 命中（右）；命中没报 →「不缓存」。 */
@Composable
private fun ModelTable(models: List<LogTrend.Totals>) {
    val head = AppTheme.typography.settingsRowSubtitle
    val cell = AppTheme.typography.secondary.copy(fontFeatureSettings = "tnum")
    val chinese = chineseUnits()
    TableRow(
        head,
        stringResource(R.string.clog_col_model), stringResource(R.string.clog_col_calls),
        stringResource(R.string.clog_col_tokens), stringResource(R.string.clog_col_hits), headColor = true,
    )
    models.forEach { m ->
        AppListDivider(startInset = 0.dp)
        TableRow(
            cell, m.key, m.calls.toString(), LogFormat.bigTokens(m.totalTokens, chinese),
            m.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_no_cache), hitMuted = m.cacheRatePercent == null,
        )
    }
}

@Composable
private fun TableRow(style: TextStyle, model: String, calls: String, tokens: String, hits: String, headColor: Boolean = false, hitMuted: Boolean = false) {
    val primary = if (headColor) AppTheme.colors.text.secondary else AppTheme.colors.text.primary
    Row(Modifier.fillMaxWidth().padding(vertical = if (headColor) 0.dp else AppSpacing.s).padding(bottom = if (headColor) AppSpacing.xs else 0.dp)) {
        Box(Modifier.weight(1f)) {
            Text(model, style = style, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
        Text(calls, style = style, color = primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
        Text(tokens, style = style, color = primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
        Text(hits, style = style, color = if (hitMuted) AppTheme.colors.text.secondary else primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}
