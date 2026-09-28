package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.formatLogTime
import com.situ.aichat.ui.contextlog.model.LogContextMap
import com.situ.aichat.ui.contextlog.model.LogMapBlock
import com.situ.aichat.ui.contextlog.model.LogMapCut
import com.situ.aichat.ui.contextlog.model.LogMapHistory
import com.situ.aichat.ui.contextlog.model.LogMapZone
import com.situ.aichat.ui.contextlog.model.LogMapZoneLabel
import com.situ.aichat.ui.contextlog.model.LogMarkerKind
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.LogMapColors

private val BAR_WIDTH = 28.dp
private val BAR_SHAPE = RoundedCornerShape(4.dp)
private val W640 = FontWeight(640)
private val W520 = FontWeight(520)

/** 完整地图纵列（四期·图纸四 §4.8·两张脸共用）：区标签 / 色块行 / 聊天记录行（白短线 = 时间标记）/ 红框行，项间 2dp。 */
@Composable
fun LogContextMapColumn(map: LogContextMap, colors: LogMapColors, modifier: Modifier = Modifier) {
    val lastTail = map.items.lastOrNull { it is LogMapBlock && it.zone == LogMapZone.TAIL }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        map.items.forEach { item ->
            when (item) {
                is LogMapZoneLabel -> ZoneLabel(item.zone)
                is LogMapBlock -> BlockRow(item, colors, isLastTail = item === lastTail)
                is LogMapHistory -> HistoryRow(item, colors)
                is LogMapCut -> LogCutFrame(item)
            }
        }
    }
}

@Composable
private fun ZoneLabel(zone: LogMapZone) {
    val res = when (zone) {
        LogMapZone.PREFIX -> R.string.clog_zone_prefix
        LogMapZone.HISTORY -> R.string.clog_zone_history
        LogMapZone.TAIL -> R.string.clog_zone_tail
    }
    Text(
        stringResource(res),
        style = AppTheme.typography.settingsRowSubtitle.copy(fontWeight = W640),
        color = AppTheme.colors.text.secondary,
        modifier = Modifier.padding(start = 40.dp, top = AppSpacing.s, bottom = AppSpacing.xs),
    )
}

@Composable
private fun BlockRow(block: LogMapBlock, colors: LogMapColors, isLastTail: Boolean) {
    val variable = block.variable || block.savedBlock
    val color = when {
        variable -> colors.variable
        block.zone == LogMapZone.TAIL -> colors.tail
        else -> colors.prefix
    }
    val more = if (block.moreCount > 0) stringResource(R.string.clog_map_more_modules, block.moreCount) else null
    val name = block.label?.let { stringResource(mapLabelRes(it)) } ?: (block.names + listOfNotNull(more)).joinToString(" · ")
    Row(Modifier.fillMaxWidth().heightIn(min = block.heightDp.dp).height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(BAR_WIDTH).fillMaxHeight().logMapFill(color, variable, BAR_SHAPE))
        Spacer(Modifier.width(AppSpacing.m))
        Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
            NameAndTokens(name, block.tokens)
            val extra = when {
                block.savedBlock -> stringResource(R.string.clog_saved_block_extra)
                isLastTail -> stringResource(R.string.clog_tail_note)
                else -> null
            }
            extra?.let { Text(it, style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary) }
        }
    }
}

@Composable
private fun HistoryRow(h: LogMapHistory, colors: LogMapColors) {
    val primary = AppTheme.colors.text.primary
    val secondary = AppTheme.colors.text.secondary
    val tickText = stringResource(R.string.clog_marker_tick)
    val sceneText = stringResource(R.string.clog_marker_scene)
    Row(Modifier.fillMaxWidth().heightIn(min = h.heightDp.dp).height(IntrinsicSize.Min)) {
        Spacer(
            Modifier.width(BAR_WIDTH).fillMaxHeight().logMapFill(colors.history, false, BAR_SHAPE).drawBehind {
                val inset = 3.dp.toPx()
                val tickH = 2.dp.toPx()
                for (f in h.ticks) {
                    val y = (f * size.height).coerceIn(0f, size.height - tickH)
                    drawRoundRect(colors.tick, Offset(inset, y), Size(size.width - inset * 2, tickH), CornerRadius(1.dp.toPx()))
                }
            },
        )
        Spacer(Modifier.width(AppSpacing.m))
        Column(Modifier.weight(1f)) {
            NameAndTokens(stringResource(R.string.clog_history_row, h.messageCount), h.tokens)
            if (h.ticks.isNotEmpty()) {
                Text(stringResource(R.string.clog_history_ticks_note), style = AppTheme.typography.caption, color = secondary)
            }
            h.markers.forEach { m ->
                val detail = when (m.kind) {
                    LogMarkerKind.PAUSE -> m.detail.orEmpty()
                    LogMarkerKind.TICK -> tickText
                    LogMarkerKind.SCENE -> sceneText
                }
                Text(
                    buildAnnotatedString {
                        withStyle(AppTheme.typography.captionNumeric.copy(fontWeight = W520, color = primary).toSpanStyle()) { append(m.label) }
                        append(" ")
                        withStyle(SpanStyle(color = secondary)) { append(detail) }
                    },
                    style = AppTheme.typography.caption,
                    color = secondary,
                )
            }
            if (h.moreMarkers > 0) {
                Text(stringResource(R.string.clog_markers_more, h.moreMarkers), style = AppTheme.typography.caption, color = secondary)
            }
        }
    }
}

@Composable
private fun NameAndTokens(name: String, tokens: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = AppTheme.typography.settingsRowValue,
            color = AppTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AppSpacing.s))
        Text(LogTokenFormat.compact(tokens), style = AppTheme.typography.captionNumeric, color = AppTheme.colors.text.secondary)
    }
}

/**
 * 红框行：虚线描边（1dp·4dp / 4dp）的红底框；次行 =「和上一轮（HH:mm）相比，」+ 该类正文 +「约 N tk 按原价计费」。
 * 正文与计费句之间英文补一个空格（中文句号后不空格·§11 D-4）。
 */
@Composable
internal fun LogCutFrame(cut: LogMapCut) {
    val onError = AppTheme.colors.status.onError
    val body = buildString {
        cut.previousTimeMillis?.let { append(stringResource(R.string.clog_cut_prev, formatLogTime(it))) }
        append(cutBodyText(cut.kind, cut.moduleName))
        if (cut.uncachedTokens > 0) {
            if (!chineseUnits()) append(" ")
            append(stringResource(R.string.clog_cut_cost, LogTokenFormat.compact(cut.uncachedTokens)))
        }
    }
    val shape = AppTheme.shapes.small
    Column(
        Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs)
            .clip(shape)
            .background(AppTheme.colors.status.errorContainer)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    onError, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(8.dp.toPx()), // = AppShapes.small 的圆角
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = AppSpacing.m, vertical = AppSpacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = onError, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(AppSpacing.xs))
            Text(stringResource(R.string.clog_cut_title), style = AppTheme.typography.caption.copy(fontWeight = W640), color = onError)
        }
        Text(body, style = AppTheme.typography.caption, color = onError)
    }
}
