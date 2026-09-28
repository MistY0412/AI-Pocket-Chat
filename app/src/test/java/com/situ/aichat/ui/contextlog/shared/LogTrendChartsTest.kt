package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.contextlog.model.LogTrendDay
import com.situ.aichat.ui.theme.AIPocketChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-15（四期·图纸四 §4.8·E26）：两图读屏文案逐字、30 天日标签个数、纵轴取整。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LogTrendChartsTest {

    @get:Rule val compose = createComposeRule()

    private fun day(d: Int, total: Long, hit: Long, rate: Int?) = LogTrendDay("2026-09-%02d".format(d), d, total, hit, rate)

    private val week = listOf(
        day(21, 0, 0, null), day(22, 100_000, 40_000, 40), day(23, 382_000, 200_000, 52), day(24, 0, 0, null),
        day(25, 50_000, 0, 0), day(26, 90_000, 60_000, 66), day(27, 120_000, 72_000, 60),
    )

    private fun show(days: List<LogTrendDay>) = compose.setContent {
        AIPocketChatTheme {
            Column {
                LogUsageBarChart(days, Color.Red, Color.Green, Color.Gray, Color.DarkGray, chineseUnits = true, modifier = Modifier.fillMaxWidth().height(160.dp))
                LogHitRateLineChart(days, Color.Red, Color.Gray, Color.DarkGray, Color.White, modifier = Modifier.fillMaxWidth().height(104.dp))
            }
        }
    }

    @Test
    fun a11y_week() {
        show(week)
        compose.onNodeWithContentDescription("近 7 天每天用量柱状图，最多的一天 38.2万").assertExists() // 读屏带单位（复核 R1）
        compose.onNodeWithContentDescription("近 7 天对话缓存命中率折线图，最近一天 60%").assertExists()
    }

    @Test
    fun a11y_month_allRatesNull_dash() {
        show((1..30).map { day(it, 0, 0, null) })
        compose.onNodeWithContentDescription("近 30 天每天用量柱状图，最多的一天 0").assertExists()
        compose.onNodeWithContentDescription("近 30 天对话缓存命中率折线图，最近一天 —").assertExists()
    }

    @Test
    fun dayLabels_everyDayForWeek_everyFifthIncludingTodayForMonth() {
        assertEquals((0..6).toList(), dayLabelIndices(7))
        assertEquals("30 天：含今天在内每 5 天一个 = 6 个（E26）", listOf(4, 9, 14, 19, 24, 29), dayLabelIndices(30))
    }

    @Test
    fun niceCeil_3_6_15_thirdsAreWhole() {
        assertEquals(3L, niceCeil(0))
        assertEquals(3L, niceCeil(3))
        assertEquals(6L, niceCeil(4))
        assertEquals(15L, niceCeil(7))
        assertEquals("11.7 万 → 15 万（刻度 5 / 10 / 15，不再是 6.7 / 13.3）", 150_000L, niceCeil(117_000))
        assertEquals(600_000L, niceCeil(382_000))
        assertEquals(1_500_000L, niceCeil(1_000_000))
        assertEquals(3_000_000L, niceCeil(1_500_001))
        for (m in listOf(0L, 5L, 99L, 117_000L, 382_000L, 2_380_000L)) assertEquals("三等分恒整除", 0L, niceCeil(m) % 3)
    }

    @Test
    fun barTopLabel_zeroHidden_tinyShownAsLessThan() {
        assertEquals(null, barTopLabel(0, chineseUnits = true))
        assertEquals("不足 0.05 万按万显示会是 0", "<0.1", barTopLabel(300, chineseUnits = true))
        assertEquals("38.2", barTopLabel(382_000, chineseUnits = true))
        assertEquals("300", barTopLabel(300, chineseUnits = false))
    }

    @Test
    fun isolatedPoint_noNeighbours() {
        val d = listOf(day(1, 0, 0, 50), day(2, 0, 0, null), day(3, 0, 0, 40), day(4, 0, 0, 30))
        assertEquals(true, isolatedPoint(d, 0))
        assertEquals(false, isolatedPoint(d, 1))
        assertEquals(false, isolatedPoint(d, 2))
        assertEquals(false, isolatedPoint(d, 3))
    }
}
