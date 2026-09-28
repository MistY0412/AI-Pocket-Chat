package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * T1-5（四期·图纸三 §3.9 / E19–E20）：[LogTrend.backfill] 与日键。时区钉 `Asia/Shanghai`（UTC+8）——
 * 23:59:59 与次日 00:00:00 在 UTC 下是同一天（15:59:59Z / 16:00:00Z），只有按本机时区算才分成两天，
 * 这一对就是「没按 zone 算」的判别例。期望值全部手算。
 */
class LogTrendBackfillTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    // 北京时间：09-27 10:00 / 09-27 23:59:59 / 09-28 00:00:00（epoch 毫秒按 UTC+8 手算）
    private val sep27At10 = 1_790_474_400_000L
    private val sep27At235959 = 1_790_524_799_000L
    private val sep28At0000 = 1_790_524_800_000L

    private fun row(
        at: Long, model: String = "m", source: String = "chat", ok: Boolean = true,
        prompt: Long = 0, completion: Long = 0, hit: Long = 0, miss: Long = 0,
    ) = LogTrend.BackfillRow(at, model, source, ok, prompt, completion, hit, miss)

    @Test
    fun dayKey_splitsAtLocalMidnight_notUtc() {
        assertEquals("2026-09-27", LogTrend.dayKey(sep27At235959, shanghai))
        assertEquals("2026-09-28", LogTrend.dayKey(sep28At0000, shanghai))
        assertEquals("UTC 下两者同一天（判别前提）", "2026-09-27", LogTrend.dayKey(sep28At0000, ZoneId.of("UTC")))
    }

    @Test
    fun daysBefore_subtractsCalendarDays() {
        assertEquals("2026-06-30", LogTrend.daysBefore("2026-09-28", 90))
        assertEquals("2026-02-28", LogTrend.daysBefore("2026-03-01", 1))
    }

    @Test
    fun backfill_235959And000000_goToDifferentDays() {
        val out = LogTrend.backfill(listOf(row(sep27At235959), row(sep28At0000)), shanghai)
        assertEquals(listOf("2026-09-27", "2026-09-28"), out.map { it.dayKey })
        assertEquals(listOf(1, 1), out.map { it.calls })
    }

    @Test
    fun backfill_aggregatesSameKey_failureOnlyCountsIntoFailures() {
        val out = LogTrend.backfill(
            listOf(
                row(sep27At10, prompt = 1000, completion = 200, hit = 600, miss = 400),
                row(sep27At235959, ok = false),
                row(sep27At10, prompt = 10, completion = 5, hit = 1, miss = 2),
            ),
            shanghai,
        )
        assertEquals(
            listOf(LogDailyStatEntity("2026-09-27", "m", "chat", calls = 3, failures = 1, promptTokens = 1010, completionTokens = 205, cacheHitTokens = 601, cacheMissTokens = 402)),
            out,
        )
    }

    @Test
    fun backfill_sortedByDayThenModelThenSource() {
        val out = LogTrend.backfill(
            listOf(
                row(sep28At0000, model = "a", source = "chat"),
                row(sep27At10, model = "b", source = "chat"),
                row(sep27At10, model = "a", source = "story"),
                row(sep27At10, model = "a", source = "chat"),
            ),
            shanghai,
        )
        assertEquals(
            listOf("2026-09-27|a|chat", "2026-09-27|a|story", "2026-09-27|b|chat", "2026-09-28|a|chat"),
            out.map { "${it.dayKey}|${it.modelName}|${it.source}" },
        )
    }

    @Test
    fun backfill_empty_isEmpty() {
        assertEquals(emptyList<LogDailyStatEntity>(), LogTrend.backfill(emptyList(), shanghai))
    }
}
