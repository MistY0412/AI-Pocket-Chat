package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogTrendDay
import com.situ.aichat.ui.designsystem.AppTypography

/*
 * 趋势两图（四期·图纸四 §4.8·两张脸共用·只用 foundation Canvas + AppTypography，不引任何图表库）。
 * 几何锁定值：左轴区 28dp、底部日标签区 16dp、顶部留 12dp；刻度字 10.5sp。
 * 图纸没写的小偏移取过审效果图的 SVG：刻度字右缘距绘图区 5dp、柱顶数字离柱 3dp、末点百分比右缘在点左 5dp / 基线在点上 7dp。
 */

private val AXIS = 28.dp
private val BOTTOM = 16.dp
private val TOP = 12.dp
private val TICK_GAP = 5.dp

/** 日标签画在哪几列：7 天每天；30 天只在 (n − 1 − i) % 5 == 0 的列（含今天）。 */
internal fun dayLabelIndices(n: Int): List<Int> =
    if (n <= 7) (0 until n).toList() else (0 until n).filter { (n - 1 - it) % 5 == 0 }

/**
 * 纵轴上限：3 / 6 / 15 × 10ᵏ 里第一个 ≥ [max] 的；≤ 3（含全 0）→ 3。
 * 复核 R1：原 1 / 2 / 5 × 10ᵏ 配「0、⅓、⅔、1」四条网格会出 6.7 / 13.3 这种刻度；换成 3 的倍数，三等分恒为整数
 * （15 万 → 5 / 10 / 15；6 万 → 2 / 4 / 6）。
 */
internal fun niceCeil(max: Long): Long {
    if (max <= 3L) return 3L
    var p = 1L
    while (true) {
        for (m in longArrayOf(3, 6, 15)) if (m * p >= max) return m * p
        p *= 10
    }
}

/** 7 天柱顶字：0 不写（没用量的日子不标「0」）；不为 0 却按万显示成「0」的写「<0.1」（中文轴以万为单位·复核 R1）。 */
internal fun barTopLabel(total: Long, chineseUnits: Boolean): String? {
    if (total <= 0L) return null
    val text = LogFormat.axisTokens(total, chineseUnits)
    return if (text == "0") "<0.1" else text
}

/** 每天用量柱状图：下段命中缓存（[hitColor]）、上段原价（[restColor]）；7 天时柱顶写总量。 */
@Composable
fun LogUsageBarChart(
    days: List<LogTrendDay>,
    hitColor: Color,
    restColor: Color,
    gridColor: Color,
    labelColor: Color,
    chineseUnits: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = AppTypography.settingsRowSubtitle.copy(color = labelColor)
    val dayLabels = days.map { stringResource(R.string.clog_day_short, it.dayOfMonth) }
    val maxTotal = days.maxOfOrNull { it.totalTokens } ?: 0L
    // 读屏要带单位（「38.2万」而非「38.2」·复核 R1）：图上的万由标题交代，读屏听不到标题
    val a11y = stringResource(R.string.clog_usage_a11y, days.size, LogFormat.bigTokens(maxTotal, chineseUnits))
    Canvas(modifier.clearAndSetSemantics { contentDescription = a11y }) {
        val axis = AXIS.toPx()
        val top = TOP.toPx()
        val plotH = size.height - BOTTOM.toPx() - top
        val yMax = niceCeil(maxTotal)
        fun y(v: Long) = top + plotH * (1f - v.toFloat() / yMax)
        for (i in 0..3) {
            val v = yMax * i / 3
            gridLine(y(v), gridColor)
            tickLabel(measurer, LogFormat.axisTokens(v, chineseUnits), y(v), style)
        }
        if (days.isEmpty()) return@Canvas
        val week = days.size <= 7
        val cw = (size.width - axis) / days.size
        val barW = cw * (if (week) 0.6f else 0.7f)
        val radius = CornerRadius(2.dp.toPx())
        days.forEachIndexed { i, d ->
            val left = axis + i * cw + (cw - barW) / 2
            if (d.totalTokens > 0L) {
                val bar = Path().apply {
                    addRoundRect(RoundRect(left, y(d.totalTokens), left + barW, y(0), topLeftCornerRadius = radius, topRightCornerRadius = radius))
                }
                clipPath(bar) {
                    drawRect(restColor, Offset(left, y(d.totalTokens)), Size(barW, y(0) - y(d.totalTokens)))
                    drawRect(hitColor, Offset(left, y(d.hitTokens)), Size(barW, y(0) - y(d.hitTokens)))
                }
            }
            if (week) barTopLabel(d.totalTokens, chineseUnits)?.let { label ->
                val t = measurer.measure(label, style)
                drawText(t, topLeft = Offset(left + barW / 2 - t.size.width / 2f, y(d.totalTokens) - 3.dp.toPx() - t.size.height))
            }
        }
        dayLabels(measurer, dayLabels, cw, style)
    }
}

/** 对话缓存命中率折线：相邻两天都有值才连线，连续段下方淡填；7 天每点画圈、30 天只画最后一个点；末点实心并写百分比。 */
@Composable
fun LogHitRateLineChart(
    days: List<LogTrendDay>,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    dotFill: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = AppTypography.settingsRowSubtitle.copy(color = labelColor)
    val lastStyle = AppTypography.settingsRowSubtitle.copy(color = lineColor, fontWeight = FontWeight(640))
    val dayLabels = days.map { stringResource(R.string.clog_day_short, it.dayOfMonth) }
    val last = days.lastOrNull { it.chatHitRatePercent != null }?.chatHitRatePercent
    val a11y = stringResource(R.string.clog_hitrate_a11y, days.size, last?.let { "$it%" } ?: stringResource(R.string.clog_rate_none))
    Canvas(modifier.clearAndSetSemantics { contentDescription = a11y }) {
        val axis = AXIS.toPx()
        val top = TOP.toPx()
        val plotH = size.height - BOTTOM.toPx() - top
        fun y(pct: Int) = top + plotH * (1f - pct / 100f)
        for (pct in listOf(0, 50, 100)) {
            gridLine(y(pct), gridColor)
            tickLabel(measurer, "$pct%", y(pct), style)
        }
        if (days.isEmpty()) return@Canvas
        val week = days.size <= 7
        val cw = (size.width - axis) / days.size
        fun x(i: Int) = axis + i * cw + cw / 2
        val lastIndex = days.indexOfLast { it.chatHitRatePercent != null }
        // 连续段：淡填 + 折线
        var i = 0
        while (i < days.size) {
            if (days[i].chatHitRatePercent == null) { i++; continue }
            var j = i
            while (j + 1 < days.size && days[j + 1].chatHitRatePercent != null) j++
            if (j > i) {
                val line = Path().apply { for (k in i..j) if (k == i) moveTo(x(k), y(days[k].chatHitRatePercent!!)) else lineTo(x(k), y(days[k].chatHitRatePercent!!)) }
                val fill = Path().apply { addPath(line); lineTo(x(j), y(0)); lineTo(x(i), y(0)); close() }
                drawPath(fill, lineColor.copy(alpha = 0.10f))
                drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            }
            i = j + 1
        }
        days.forEachIndexed { k, d ->
            val pct = d.chatHitRatePercent ?: return@forEachIndexed
            val c = Offset(x(k), y(pct))
            if (k == lastIndex) {
                drawCircle(lineColor, 3.6.dp.toPx(), c)
                val t = measurer.measure("$pct%", lastStyle)
                drawText(t, topLeft = Offset(c.x - 5.dp.toPx() - t.size.width, c.y - 7.dp.toPx() - t.firstBaseline))
            } else if (week || isolatedPoint(days, k)) {
                // 30 天只画末点；但前后都没数的孤点不画就整个看不见（没有线连它·复核 R1）→ 照 7 天的空心圈画
                drawCircle(dotFill, 2.4.dp.toPx(), c)
                drawCircle(lineColor, 2.4.dp.toPx(), c, style = Stroke(width = 1.6.dp.toPx()))
            }
        }
        dayLabels(measurer, dayLabels, cw, style)
    }
}

/** 前后两天都没有命中率的点（不会被任何折线段连到）。 */
internal fun isolatedPoint(days: List<LogTrendDay>, k: Int): Boolean =
    days[k].chatHitRatePercent != null && days.getOrNull(k - 1)?.chatHitRatePercent == null && days.getOrNull(k + 1)?.chatHitRatePercent == null

private fun DrawScope.gridLine(y: Float, color: Color) =
    drawLine(color, Offset(AXIS.toPx(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())

/** 刻度字：右对齐在左轴区（右缘距绘图区 [TICK_GAP]），竖向居中在网格线上。 */
private fun DrawScope.tickLabel(measurer: TextMeasurer, text: String, y: Float, style: TextStyle) {
    val t = measurer.measure(text, style)
    drawText(t, topLeft = Offset(AXIS.toPx() - TICK_GAP.toPx() - t.size.width, y - t.size.height / 2f))
}

/** 底部日标签：列中心居中、竖向居中在底部区。 */
private fun DrawScope.dayLabels(measurer: TextMeasurer, labels: List<String>, cw: Float, style: TextStyle) {
    val bottom = BOTTOM.toPx()
    for (i in dayLabelIndices(labels.size)) {
        val t = measurer.measure(labels[i], style)
        val cx = AXIS.toPx() + i * cw + cw / 2
        drawText(t, topLeft = Offset(cx - t.size.width / 2f, size.height - bottom + (bottom - t.size.height) / 2f))
    }
}
