package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.contextlog.model.LogMiniMapModel
import com.situ.aichat.ui.contextlog.model.LogMiniPart
import com.situ.aichat.ui.contextlog.model.LogMiniStyle
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.LogMapColors
import com.situ.aichat.ui.designsystem.LogMapHatchColor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 「每轮会变」斜纹（效果图 `.hatch` = `repeating-linear-gradient(135deg, transparent 0 4px, 白45% 4px 7px)`）：
 * 沿右下方向每 7dp 一个周期、其中 3dp 是白条。调用方先把块裁成自己的形状（本函数不裁）。
 */
internal fun DrawScope.drawLogHatch() {
    val period = 7.dp.toPx()
    val stripe = 3.dp.toPx()
    val diag = sqrt(2f)
    // 条纹 = 直线 x + y = c；白条中心在 t = 4dp + 1.5dp（t 为沿 (1,1) 方向的投影长度）。
    var t = 4.dp.toPx() + stripe / 2
    val limit = (size.width + size.height) / diag + period
    while (t < limit) {
        val c = t * diag
        drawLine(LogMapHatchColor, Offset(c, 0f), Offset(c - size.height, size.height), strokeWidth = stripe)
        t += period
    }
}

/** 地图色块：先按 [shape] 裁、铺底色，要斜纹时叠在底色上。 */
fun Modifier.logMapFill(color: Color, hatched: Boolean, shape: Shape): Modifier =
    clip(shape).drawBehind {
        drawRect(color)
        if (hatched) drawLogHatch()
    }

internal fun miniColor(style: LogMiniStyle, colors: LogMapColors): Color = when (style) {
    LogMiniStyle.PREFIX -> colors.prefix
    LogMiniStyle.VARIABLE -> colors.variable
    LogMiniStyle.HISTORY -> colors.history
    LogMiniStyle.TAIL -> colors.tail
}

private val MINI_GAP = 2.dp

private fun miniWeight(part: LogMiniPart): Float = max(part.fraction, 0.01f)

/**
 * 红线横坐标（复核 R1）：照横条 `Row` 的真实排法算——各部分按 [miniWeight] 分掉「总宽 − 间隙」，部分间隔 [gapPx]；
 * [cutFraction] 落在某部分内部按比例取点，恰在两部分交界时画在间隙正中。原来直接 `宽 × cutFraction`，
 * 最小权重 / 间隙 / 权重归一都会让红线偏离它该指的那道缝。
 */
internal fun miniCutX(parts: List<LogMiniPart>, cutFraction: Float, width: Float, gapPx: Float): Float {
    if (parts.isEmpty()) return width * cutFraction
    val weights = parts.map(::miniWeight)
    val avail = width - gapPx * (parts.size - 1)
    val unit = avail / weights.sum()
    var x = 0f
    var acc = 0f
    parts.forEachIndexed { i, part ->
        val w = weights[i] * unit
        if (i > 0 && cutFraction <= acc + 1e-6f) return x - gapPx / 2 // 交界：前一部分已排完，x 在本部分起点
        if (cutFraction < acc + part.fraction) return x + w * ((cutFraction - acc) / part.fraction)
        acc += part.fraction
        x += w + gapPx
    }
    return width
}

/**
 * 迷你地图横条（§4.8 锁定）：外框 22dp、横条 14dp（上下各留 4dp）整体 7dp 圆角裁切，各部分按占比横排（最小权重 0.01）、
 * 部分间 2dp；红框位置画一条 2dp 红虚线（3dp / 3dp）贯穿外框——**在裁切层之外**，上下各伸出横条 4dp。
 */
@Composable
fun LogMiniMap(model: LogMiniMapModel, colors: LogMapColors, modifier: Modifier = Modifier) {
    val cutColor = AppTheme.colors.status.onError
    Box(modifier.fillMaxWidth().height(22.dp)) {
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
            horizontalArrangement = Arrangement.spacedBy(MINI_GAP),
        ) {
            model.parts.forEach { part ->
                Box(
                    Modifier.weight(miniWeight(part)).fillMaxHeight()
                        .logMapFill(miniColor(part.style, colors), part.style == LogMiniStyle.VARIABLE, RoundedCornerShape(0.dp)),
                )
            }
        }
        model.cutFraction?.let { f ->
            Canvas(Modifier.matchParentSize()) {
                val x = miniCutX(model.parts, f, size.width, MINI_GAP.toPx())
                drawLine(
                    cutColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

/** 图例一项：9dp 圆角 3dp 色块（+ 可选斜纹）+ 10.5sp 次要字。 */
@Composable
fun LogLegendItem(color: Color, hatched: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).logMapFill(color, hatched, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary)
    }
}

/** 迷你地图图例四项（前置区 / 每轮会变〔斜纹〕/ 聊天记录 / 末尾块）；间距取效果图 `.leg`（行 4dp、列 10dp）。 */
@Composable
fun LogMapLegend(colors: LogMapColors, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LogLegendItem(colors.prefix, false, stringResource(R.string.clog_legend_prefix))
        LogLegendItem(colors.variable, true, stringResource(R.string.clog_legend_variable))
        LogLegendItem(colors.history, false, stringResource(R.string.clog_legend_history))
        LogLegendItem(colors.tail, false, stringResource(R.string.clog_legend_tail))
    }
}
