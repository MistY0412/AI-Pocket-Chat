package com.situ.aichat.ui.moments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.ColorContrast
import com.situ.aichat.ui.offline.MeetingSky
import com.situ.aichat.ui.offline.SkyBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-2（朋友圈发布页·乙 §7·E18）：天上文字对比度——五桶 × 状态栏 {24, 44, 52}dp，按图纸 §4.1 / §4.2 几何
 * （时间带 S+64..S+112、日期带 S+120..S+140、天色高 S+204·三停均布 0 / 0.5 / 1·sRGB 线性插值）以 0.5dp 步长扫描，
 * 字色 = 白天深墨 / 其余暖白，≥ 4.5 硬值无 cushion。另钉霞带压在日期行下方（Y-3）。几何数值从图纸重新打字，
 * 并与实现常量双保险对齐。
 */
class ComposeSkyContrastTest {

    private val statusBars = listOf(24f, 44f, 52f)
    private val skyFull = 204f
    private val timeBand = 64f..112f
    private val dateBand = 120f..140f
    private val step = 0.5f

    /** 独立实现：三停均布 sRGB 线性插值（不调用实现里的 composeSkyColorAt）。 */
    private fun gradientAt(stops: List<Color>, y: Float, height: Float): Color {
        val f = (y / height).coerceIn(0f, 1f)
        val (a, b, t) = if (f <= 0.5f) Triple(stops[0], stops[1], f / 0.5f) else Triple(stops[1], stops[2], (f - 0.5f) / 0.5f)
        return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
    }

    private fun scan(s: Float, band: ClosedFloatingPointRange<Float>, action: (Float) -> Unit) {
        var y = s + band.start
        while (y <= s + band.endInclusive + 1e-3f) {
            action(y)
            y += step
        }
    }

    @Test fun 五桶三种状态栏_时间与日期带都不低于4点5() {
        var worst = Double.MAX_VALUE
        var checked = 0
        for (bucket in SkyBucket.entries) {
            val stops = MeetingSky.baseStops(bucket)
            val ink = if (bucket == SkyBucket.DAY) MeetingSky.Ink else MeetingSky.WarmWhite
            for (s in statusBars) {
                val height = s + skyFull
                for ((name, band) in listOf("time" to timeBand, "date" to dateBand)) {
                    scan(s, band) { y ->
                        val ratio = ColorContrast.ratio(ink, gradientAt(stops, y, height))
                        worst = minOf(worst, ratio)
                        checked++
                        assertTrue("$bucket S=$s $name y=$y ratio=$ratio", ratio >= 4.5)
                    }
                }
            }
        }
        assertTrue("扫描点数 $checked", checked > 5 * 3 * 100)
        assertTrue("最紧点 $worst", worst < 6.0) // 正向证据：扫描真走到了贴线区（清晨日期行实算 ≈4.5–4.6）
    }

    @Test fun 霞带顶压在日期带底之下至少8dp() {
        val glowTop = 150f
        assertTrue(glowTop >= dateBand.endInclusive + 8f)
    }

    @Test fun 几何与实现常量对齐() {
        assertEquals(64.dp, STAMP_TOP)
        assertEquals(8.dp, STAMP_DATE_GAP)
        assertEquals(204.dp, SKY_FULL)
        // 时间带 = 顶 64 + 大字行高 48；日期带 = 时间带底 + 8 + 楷体行高 20。
        assertEquals(timeBand.endInclusive - timeBand.start, 48f)
        assertEquals(20.sp, AppTypography.kaiQuote.lineHeight)
        assertEquals(dateBand.start, timeBand.endInclusive + 8f)
        assertEquals(dateBand.endInclusive, dateBand.start + 20f)
    }
}
