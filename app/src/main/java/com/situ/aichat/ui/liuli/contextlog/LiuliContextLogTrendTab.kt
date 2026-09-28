package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.situ.aichat.ui.contextlog.rangeText
import com.situ.aichat.ui.contextlog.shared.LogHitRateLineChart
import com.situ.aichat.ui.contextlog.shared.LogLegendItem
import com.situ.aichat.ui.contextlog.shared.LogUsageBarChart
import com.situ.aichat.ui.contextlog.shared.chineseUnits
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.logMapColors
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 「趋势」分段（四期·图纸四 §4.1c·琉璃）：内容同暖陶——范围芯片 → 用量柱图 → 对话命中率折线 → 按模型表（组脚注 = 不显示金额）。 */
internal fun LazyListScope.liuliTrendTabItems(trend: LogTrendState, onRange: (TrendRange) -> Unit) {
    item(key = "trend-range") {
        LiuliLogChipRow(TrendRange.entries.toList(), { it == trend.range }, { rangeText(it) }, onRange, Modifier.padding(bottom = AppSpacing.m))
    }
    if (trend.empty) {
        item(key = "trend-empty") { LiuliLogEmpty(stringResource(R.string.clog_trend_empty)) }
        return
    }
    item(key = "trend-usage") {
        LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter), header = stringResource(R.string.clog_usage_title)) {
            Column(Modifier.padding(LiuliPageGeometry.groupPadH)) {
                LogUsageBarChart(
                    trend.days, hitColor = AppTheme.colors.accent.text, restColor = logMapColors().history,
                    gridColor = AppTheme.colors.surface.stroke, labelColor = AppTheme.colors.text.secondary,
                    chineseUnits = chineseUnits(), modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                Row(Modifier.padding(top = AppSpacing.s), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogLegendItem(AppTheme.colors.accent.text, false, stringResource(R.string.clog_legend_hit))
                    LogLegendItem(logMapColors().history, false, stringResource(R.string.clog_legend_full))
                }
            }
        }
    }
    item(key = "trend-rate") {
        LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter), header = stringResource(R.string.clog_hit_rate_title)) {
            LogHitRateLineChart(
                trend.days, lineColor = AppTheme.colors.accent.text, gridColor = AppTheme.colors.surface.stroke,
                labelColor = AppTheme.colors.text.secondary, dotFill = AppTheme.colors.surface.raised,
                modifier = Modifier.padding(LiuliPageGeometry.groupPadH).fillMaxWidth().height(104.dp),
            )
        }
    }
    item(key = "trend-models") {
        LiuliGroup(
            Modifier.padding(horizontal = LiuliPageGeometry.gutter),
            header = stringResource(R.string.clog_by_model_title, rangeText(trend.range)),
            footer = stringResource(R.string.clog_no_money),
        ) {
            Column(Modifier.padding(LiuliPageGeometry.groupPadH)) { ModelTable(trend.models) }
        }
    }
}

@Composable
private fun ModelTable(models: List<LogTrend.Totals>) {
    val head = AppTypography.settingsRowSubtitle
    val cell = AppTypography.secondary.copy(fontFeatureSettings = "tnum")
    val chinese = chineseUnits()
    TableRow(head, stringResource(R.string.clog_col_model), stringResource(R.string.clog_col_calls), stringResource(R.string.clog_col_tokens), stringResource(R.string.clog_col_hits), header = true)
    val dark = LocalIsDarkTheme.current
    models.forEach { m ->
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(LiuliMaterials.divider(dark)))
        TableRow(
            cell, m.key, m.calls.toString(), LogFormat.bigTokens(m.totalTokens, chinese),
            m.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_no_cache), hitMuted = m.cacheRatePercent == null,
        )
    }
}

@Composable
private fun TableRow(style: TextStyle, model: String, calls: String, tokens: String, hits: String, header: Boolean = false, hitMuted: Boolean = false) {
    val primary = if (header) AppTheme.colors.text.secondary else AppTheme.colors.text.primary
    Row(Modifier.fillMaxWidth().padding(vertical = if (header) 0.dp else AppSpacing.s).padding(bottom = if (header) AppSpacing.xs else 0.dp)) {
        Box(Modifier.weight(1f)) {
            Text(model, style = style, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
        Text(calls, style = style, color = primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
        Text(tokens, style = style, color = primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
        Text(hits, style = style, color = if (hitMuted) AppTheme.colors.text.secondary else primary, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}
