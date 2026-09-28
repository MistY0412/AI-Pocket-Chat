package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import com.situ.aichat.diagnostics.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-5（四期·图纸四 §3.5）：7 / 30 天起止补零、hit 夹在 total 内、折线只算「对话」分类、按模型全来源、empty。 */
class LogTrendModelTest {

    private val today = "2026-09-27"

    private fun stat(day: String, model: String, source: String, calls: Int, prompt: Long, completion: Long, hit: Long, miss: Long) =
        LogDailyStatEntity(day, model, source, calls = calls, failures = 0, promptTokens = prompt, completionTokens = completion, cacheHitTokens = hit, cacheMissTokens = miss)

    private val stats = listOf(
        stat("2026-09-27", "m1", LogSource.CHAT, 3, 1_000, 200, 600, 400),
        stat("2026-09-27", "m2", LogSource.MEMORY_SUMMARY, 2, 500, 100, 0, 0),
        stat("2026-09-26", "m5", LogSource.DIARY_GENERATION, 1, 100, 0, 5_000, 0), // hit 超过 total（异常数据）→ 夹住
        stat("2026-09-25", "m1", LogSource.IMAGE_UNDERSTANDING, 1, 100, 10, 0, 100), // 图片理解属「对话」分类
        stat("2026-09-20", "m3", LogSource.CHAT, 5, 10, 10, 0, 0), // 7 天外、30 天内
        stat("2026-08-01", "m4", LogSource.CHAT, 9, 10, 10, 0, 0), // 30 天外（E28）
    )

    @Test
    fun week() {
        val s = buildTrend(stats, TrendRange.WEEK, today)
        assertEquals(TrendRange.WEEK, s.range)
        assertEquals((21..27).toList(), s.days.map { it.dayOfMonth })
        assertEquals("2026-09-21", s.days.first().dayKey)
        val d27 = s.days[6]
        assertEquals(1_800L, d27.totalTokens)
        assertEquals(600L, d27.hitTokens)
        assertEquals("只算对话分类：600 / 1000", 60, d27.chatHitRatePercent)
        val d26 = s.days[5]
        assertEquals(100L, d26.totalTokens)
        assertEquals("hit 夹在 total 内", 100L, d26.hitTokens)
        assertNull("日记不属于对话分类 → 折线没这点", d26.chatHitRatePercent)
        assertEquals("报了 0 就是 0%", 0, s.days[4].chatHitRatePercent)
        assertNull("补零的天 → null（E27）", s.days[0].chatHitRatePercent)
        assertEquals(0L, s.days[0].totalTokens)
        assertEquals("按模型全来源、calls 降序", listOf("m1", "m2", "m5"), s.models.map { it.key })
        assertEquals(4, s.models[0].calls)
        assertEquals(false, s.empty)
    }

    @Test
    fun month_onlyThirtyDays() {
        val s = buildTrend(stats, TrendRange.MONTH, today)
        assertEquals(30, s.days.size)
        assertEquals("2026-08-29", s.days.first().dayKey)
        assertEquals(29, s.days.first().dayOfMonth)
        assertEquals(listOf("m3", "m1", "m2", "m5"), s.models.map { it.key })
        assertTrue("m4 在 30 天外不取", s.models.none { it.key == "m4" })
    }

    @Test
    fun empty_whenNothingInRange() {
        val s = buildTrend(listOf(stat("2026-08-01", "m4", LogSource.CHAT, 9, 1, 1, 0, 0)), TrendRange.WEEK, today)
        assertTrue(s.empty)
        assertEquals(7, s.days.size)
        assertEquals(emptyList<Any>(), s.models)
    }
}
