package com.situ.aichat.ui.moments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.offline.MeetingSky
import com.situ.aichat.ui.offline.SkyBucket
import com.situ.aichat.ui.offline.drawMoonDisc
import kotlin.math.min
import kotlin.random.Random

// 发布页头顶的天色（朋友圈发布页·乙 §4.2·两张脸同色）：本色三停渐变 → 星 → 月 → 暖阳 → 霞带 →（琉璃）底部渐隐。
// 静态画无动画（reduceMotion 无差）；键盘收起进度 p 只在绘制 / graphicsLayer 阶段读（§4.1）。

/** 大字时间（Y-4·44sp / 48 行高 / 字重 300·等宽数字；越出字阶上限 = 语言进化，见设计语言字阶例外）。 */
private val TIME_STYLE = AppTypography.titleLarge.copy(
    fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.W300, fontFeatureSettings = "tnum",
)
private val DATE_STYLE = AppTypography.kaiQuote.copy(letterSpacing = 1.sp)
private val SLIM_STYLE = AppTypography.secondary.copy(fontFeatureSettings = "tnum")

/** 天上字色：白天深墨，其余暖白（= 见面回忆口径）。 */
internal fun composeSkyInk(moment: ComposeSkyMoment): Color = if (moment.lightSky) MeetingSky.Ink else MeetingSky.WarmWhite

/** 渐变在高度分数 [fraction] 处的颜色（三停均布 0 / 0.5 / 1·sRGB 线性插值 = 画布渐变口径）。 */
internal fun composeSkyColorAt(stops: List<Color>, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    val (a, b, t) = if (f <= 0.5f) Triple(stops[0], stops[1], f * 2f) else Triple(stops[1], stops[2], (f - 0.5f) * 2f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
    )
}

/** 天色画布：高度由骨架 `composeSkyHeight` 给；[fadeOut]（琉璃）= 底部 0.86 起渐隐。 */
@Composable
internal fun ComposeMomentSkyBackdrop(
    moment: ComposeSkyMoment,
    fadeOut: Boolean,
    status: WindowInsets,
    ime: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val stops = remember(moment.bucket) { MeetingSky.baseStops(moment.bucket) }
    val layer = if (fadeOut) Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) else Modifier
    Canvas(modifier.then(layer)) {
        drawComposeSky(moment, stops, statusTop = status.getTop(this).toFloat(), collapse = composeCollapse(ime))
        if (fadeOut) {
            drawRect(Brush.verticalGradient(0.86f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }
    }
}

private fun DrawScope.drawComposeSky(moment: ComposeSkyMoment, stops: List<Color>, statusTop: Float, collapse: Float) {
    drawRect(Brush.verticalGradient(stops))
    val rnd = Random(moment.starSeed)
    repeat(composeStarCount(moment.bucket)) {
        val x = size.width * (0.05f + 0.90f * rnd.nextFloat())
        val y = statusTop + 8.dp.toPx() + 140.dp.toPx() * rnd.nextFloat()
        val r = (0.9f + 0.7f * rnd.nextFloat()).dp.toPx()
        val a = min(0.85f, 0.40f + 0.35f * rnd.nextFloat())
        drawCircle(MeetingSky.WarmWhite.copy(alpha = a), radius = r, center = Offset(x, y))
    }
    val fade = 1f - collapse // 月亮 / 暖阳 / 霞带随键盘收起淡出（Y-2）
    moment.moon?.let { moon ->
        val center = Offset(size.width - 48.dp.toPx(), statusTop + 92.dp.toPx())
        drawMoonDisc(center, 11.dp.toPx(), fade, moon, skyAtMoon = composeSkyColorAt(stops, center.y / size.height))
    }
    if (moment.bucket == SkyBucket.DAY) {
        val center = Offset(size.width - 64.dp.toPx(), statusTop + 76.dp.toPx())
        val radius = 64.dp.toPx()
        drawCircle(
            Brush.radialGradient(
                listOf(MeetingSky.SunWarm.copy(alpha = 0.60f), MeetingSky.SunWarm.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
            alpha = fade,
        )
    }
    if (moment.bucket == SkyBucket.DAWN || moment.bucket == SkyBucket.DUSK) {
        // 霞带压在日期行下方（Y-3·保日期小字对比度）。
        glowBand(0.02f, 0.57f, statusTop + 150.dp.toPx(), 10.dp.toPx(), 0.26f * fade)
        glowBand(0.24f, 0.86f, statusTop + 164.dp.toPx(), 9.dp.toPx(), 0.18f * fade)
    }
}

/** 圆头霞带：横向两端渐隐（0 透明 · 0.28 满 · 0.72 满 · 1 透明）。 */
private fun DrawScope.glowBand(fromF: Float, toF: Float, top: Float, height: Float, alpha: Float) {
    val left = size.width * fromF
    val right = size.width * toF
    val full = MeetingSky.GlowWarm.copy(alpha = alpha)
    val clear = MeetingSky.GlowWarm.copy(alpha = 0f)
    drawRoundRect(
        brush = Brush.horizontalGradient(0f to clear, 0.28f to full, 0.72f to full, 1f to clear, startX = left, endX = right),
        topLeft = Offset(left, top),
        size = Size(right - left, height),
        cornerRadius = CornerRadius(height / 2f, height / 2f),
    )
}

/** 天上文字：满态（大字时间 + 日期行·左对齐）与窄态（一行小字·居中）叠放，alpha 按 p 交替。 */
@Composable
internal fun ComposeMomentSkyText(moment: ComposeSkyMoment, status: WindowInsets, ime: WindowInsets) {
    val ink = composeSkyInk(moment)
    val dayPart = stringResource(moment.dayPart.label)
    Box(Modifier.fillMaxWidth().windowInsetsPadding(status.only(WindowInsetsSides.Top))) {
        Column(
            Modifier
                .padding(start = AppSpacing.screenGutter, top = STAMP_TOP)
                .graphicsLayer { alpha = 1f - composeCollapse(ime) },
        ) {
            Text(moment.time, style = TIME_STYLE, color = ink)
            Spacer(Modifier.height(STAMP_DATE_GAP))
            Text(
                stringResource(R.string.moment_compose_date_line, moment.monthDay, moment.weekday, dayPart),
                style = DATE_STYLE,
                color = ink,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(SKY_SLIM)
                .padding(horizontal = SLIM_TEXT_SIDE)
                .graphicsLayer { alpha = composeCollapse(ime) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.moment_compose_slim_line, moment.time, moment.weekday, dayPart),
                style = SLIM_STYLE,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
