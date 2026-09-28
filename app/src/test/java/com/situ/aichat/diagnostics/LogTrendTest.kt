package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1-4（四期·图纸三 §3.9）：按天补零升序、来源过滤、缓存率（null ≠ 0%）与舍入、按模型排序。期望值全部手算。
 */
class LogTrendTest {

    private fun stat(day: String, model: String = "m", source: String = "chat", calls: Int = 1, failures: Int = 0, prompt: Long = 0, completion: Long = 0, hit: Long = 0, miss: Long = 0) =
        LogDailyStatEntity(day, model, source, calls, failures, prompt, completion, hit, miss)

    private fun totals(key: String, calls: Int = 0, failures: Int = 0, prompt: Long = 0, completion: Long = 0, hit: Long = 0, miss: Long = 0) =
        LogTrend.Totals(key, calls, failures, prompt, completion, hit, miss)

    @Test
    fun perDay_fillsMissingDaysWithZero_ascending_ignoresOutOfRange() {
        val rows = listOf(
            stat("2026-09-28", calls = 3, failures = 1, prompt = 300, completion = 30, hit = 100, miss = 200),
            stat("2026-09-26", calls = 2, prompt = 200, completion = 20),
            stat("2026-09-26", model = "n", calls = 1, prompt = 50, completion = 5),
            stat("2026-09-20", calls = 9),
            stat("2026-09-29", calls = 9),
        )
        assertEquals(
            listOf(
                totals("2026-09-25"),
                totals("2026-09-26", calls = 3, prompt = 250, completion = 25),
                totals("2026-09-27"),
                totals("2026-09-28", calls = 3, failures = 1, prompt = 300, completion = 30, hit = 100, miss = 200),
            ),
            LogTrend.perDay(rows, "2026-09-25", "2026-09-28"),
        )
    }

    @Test
    fun perDay_crossesMonthBoundary() {
        assertEquals(listOf("2026-09-30", "2026-10-01"), LogTrend.perDay(emptyList(), "2026-09-30", "2026-10-01").map { it.key })
    }

    @Test
    fun perDay_sourcesFilter() {
        val rows = listOf(stat("2026-09-28", source = "chat", calls = 2), stat("2026-09-28", source = "story", calls = 5), stat("2026-09-28", source = "diary", calls = 7))
        assertEquals(listOf(2), LogTrend.perDay(rows, "2026-09-28", "2026-09-28", sources = setOf("chat")).map { it.calls })
        assertEquals(listOf(5), LogTrend.perDay(rows, "2026-09-28", "2026-09-28", sources = setOf("story", "voice")).map { it.calls })
        assertEquals(listOf(14), LogTrend.perDay(rows, "2026-09-28", "2026-09-28").map { it.calls })
    }

    @Test
    fun cacheRatePercent_nullWhenUnreported_andRounding() {
        assertNull("服务商没报 ≠ 0%", totals("k").cacheRatePercent)
        assertEquals(68, totals("k", hit = 680, miss = 320).cacheRatePercent)
        assertEquals(0, totals("k", hit = 0, miss = 10).cacheRatePercent)
        assertEquals(33, totals("k", hit = 1, miss = 2).cacheRatePercent)
        assertEquals(67, totals("k", hit = 2, miss = 1).cacheRatePercent)
        assertEquals("12.5 向上取 13", 13, totals("k", hit = 1, miss = 7).cacheRatePercent)
        assertEquals(330L, totals("k", prompt = 300, completion = 30).totalTokens)
    }

    @Test
    fun perModel_sortedByCallsDesc_thenKeyAsc_withSourcesFilter() {
        val rows = listOf(
            stat("2026-09-27", model = "b", calls = 2, prompt = 10),
            stat("2026-09-28", model = "b", calls = 1, prompt = 5),
            stat("2026-09-28", model = "c", calls = 3),
            stat("2026-09-28", model = "a", calls = 3),
            stat("2026-09-28", model = "z", source = "story", calls = 9),
        )
        val chatOnly = LogTrend.perModel(rows, sources = setOf("chat"))
        assertEquals(listOf("a", "b", "c"), chatOnly.map { it.key })
        assertEquals(listOf(3, 3, 3), chatOnly.map { it.calls })
        assertEquals(15L, chatOnly[1].promptTokens)
        assertEquals(listOf("z", "a", "b", "c"), LogTrend.perModel(rows).map { it.key })
    }
}
