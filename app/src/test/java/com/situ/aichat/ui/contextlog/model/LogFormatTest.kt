package com.situ.aichat.ui.contextlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** T1-1（四期·图纸四 §3.9）：数字 / 日期格式。期望值从规格手算，不照抄实现。 */
class LogFormatTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(shanghai).toInstant().toEpochMilli()

    @Test
    fun bigTokens_chinese() {
        assertEquals("9876", LogFormat.bigTokens(9_876, chineseUnits = true))
        assertEquals("38.2万", LogFormat.bigTokens(382_000, chineseUnits = true))
        assertEquals("去掉 .0", "41万", LogFormat.bigTokens(410_000, chineseUnits = true))
        assertEquals("≥ 100 万四舍五入到整万", "238万", LogFormat.bigTokens(2_380_000, chineseUnits = true))
        assertEquals("1万", LogFormat.bigTokens(10_000, chineseUnits = true))
    }

    @Test
    fun bigTokens_english() {
        assertEquals("999", LogFormat.bigTokens(999, chineseUnits = false))
        assertEquals("1.5k", LogFormat.bigTokens(1_500, chineseUnits = false))
        assertEquals("去掉 .0", "2k", LogFormat.bigTokens(2_000, chineseUnits = false))
        assertEquals("2.4M", LogFormat.bigTokens(2_380_000, chineseUnits = false))
    }

    @Test
    fun axisTokens_chineseIsPlainWanNumber_englishIsBigTokens() {
        assertEquals("0", LogFormat.axisTokens(0, chineseUnits = true))
        assertEquals("5", LogFormat.axisTokens(50_000, chineseUnits = true))
        assertEquals("2.5", LogFormat.axisTokens(25_000, chineseUnits = true))
        assertEquals("200", LogFormat.axisTokens(2_000_000, chineseUnits = true))
        assertEquals("50k", LogFormat.axisTokens(50_000, chineseUnits = false))
    }

    @Test
    fun groupedSecondsAndCallTokens() {
        assertEquals("5,760", LogFormat.grouped(5_760))
        assertEquals("1,234,567", LogFormat.grouped(1_234_567))
        assertEquals("6.8", LogFormat.seconds(6_800))
        assertEquals("0.0", LogFormat.seconds(0))
        assertEquals("≈1.2k tk", LogFormat.callTokens(1_234, isEstimated = true))
        assertEquals("980 tk", LogFormat.callTokens(980, isEstimated = false))
    }

    @Test
    fun cacheRate_nullWhenProviderDidNotReport() {
        assertNull("hit + miss = 0 → null，绝不当 0%", LogFormat.cacheRate(0, 0))
        assertEquals(0, LogFormat.cacheRate(0, 10))
        assertEquals("2 / 3 = 66.67 → 67", 67, LogFormat.cacheRate(2, 1))
    }

    @Test
    fun dayKind_midnightSplitsTodayAndYesterday() {
        val now = at(2026, 9, 27, 0, 0, 0)
        assertEquals(LogDayKind.TODAY, LogFormat.dayKind(at(2026, 9, 27, 0, 0, 0), now, shanghai))
        assertEquals("23:59:59 是前一天", LogDayKind.YESTERDAY, LogFormat.dayKind(at(2026, 9, 26, 23, 59, 59), now, shanghai))
        assertEquals(LogDayKind.EARLIER, LogFormat.dayKind(at(2026, 9, 25, 23, 59, 59), now, shanghai))
        val later = at(2026, 9, 27, 23, 59, 59)
        assertEquals(LogDayKind.TODAY, LogFormat.dayKind(at(2026, 9, 27, 0, 0, 0), later, shanghai))
    }

    @Test
    fun timesAndProvider() {
        val t = at(2026, 9, 26, 21, 5, 9)
        assertEquals("21:05:09", LogFormat.timeWithSeconds(t, shanghai))
        assertEquals("2026-09-26 21:05:09", LogFormat.fullTime(t, shanghai))
        assertEquals("DeepSeek", LogFormat.providerName("deepseek"))
        assertEquals("自定义 (OpenAI 协议)", LogFormat.providerName("没见过的"))
    }
}
