package com.situ.aichat.ui.contextlog

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.TrendRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** T2-1（四期·图纸四 §3.2）：首页纯装配——分段覆盖 / 记住 / 未知串、原因芯片、告警最多一类、flow 与原函数相等（K2）、清空后空。 */
class ContextLogHomeUiStateTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 9, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(27, 15)
    private val today = "2026-09-27"

    private var id = 1L
    private fun row(ts: Long, source: String, ok: Boolean, kind: String? = null) =
        LogListRow(id = id++, timestampMillis = ts, source = source, isSuccess = ok, failureKind = kind, characterName = "林晚")

    private val rows = listOf(
        row(at(27, 10), LogSource.STORY_GENERATION, false, "timeout"),
        row(at(27, 11), LogSource.STORY_GENERATION, false, "rate_limited"),
        row(at(27, 12), LogSource.STORY_GENERATION, false, "rate_limited"),
        row(at(27, 13), LogSource.STORY_GENERATION, false, "timeout"),
        row(at(27, 14), LogSource.STORY_GENERATION, true),
        row(at(27, 9), LogSource.CHAT, false, "insufficient_balance"),
        row(at(27, 9), LogSource.CHAT, false, "insufficient_balance"),
        row(at(27, 9), LogSource.CHAT, false, "insufficient_balance"),
        row(at(25, 9), LogSource.CHAT, false, "network"), // 24h 外：告警与 alertKinds 都不算
    )
    private val stats = listOf(LogDailyStatEntity(today, "m", LogSource.CHAT, calls = 9, failures = 7, promptTokens = 100, completionTokens = 20, cacheHitTokens = 0, cacheMissTokens = 0))

    private fun build(
        rows: List<LogListRow> = this.rows, remembered: String = "", override: LogHomeTab? = null,
        category: LogCategory = LogCategory.ALL, reason: LlmFailureKind? = null,
    ) = buildLogHomeUiState(rows, stats, emptyList(), AppSettings(), remembered, override, category, reason, TrendRange.WEEK, today, now, zone)

    @Test
    fun tab_overrideBeatsRemembered_unknownFallsBack() {
        assertEquals(LogHomeTab.TREND, build(remembered = "trend").tab)
        assertEquals(LogHomeTab.FLOW, build(remembered = "trend", override = LogHomeTab.FLOW).tab)
        assertEquals("未知串（E30）", LogHomeTab.CONVERSATION, build(remembered = "乱写").tab)
        assertEquals("从没选过", LogHomeTab.CONVERSATION, build(remembered = "").tab)
        assertTrue(build().loaded)
    }

    @Test
    fun flow_equalsOriginalBuilder() {
        for (cat in listOf(LogCategory.ALL, LogCategory.FAILED, LogCategory.STORY)) {
            assertEquals("K2：$cat", buildContextLogUiState(rows, cat, AppSettings(), now), build(category = cat).flow)
        }
    }

    @Test
    fun alertKinds_mostFrequent_tieByEnumOrder_24hOnly() {
        val s = build()
        assertEquals(listOf(LogSource.STORY_GENERATION, LogSource.CHAT).toSet(), s.flow.alerts.map { it.source }.toSet())
        assertEquals("timeout 2 与 rate_limited 2 并列 → 枚举序靠前的 TIMEOUT", LlmFailureKind.TIMEOUT, s.alertKinds[LogSource.STORY_GENERATION])
        assertEquals(LlmFailureKind.INSUFFICIENT_BALANCE, s.alertKinds[LogSource.CHAT])
        assertEquals(s.flow.alerts, s.conversationAlerts)
        // 复核 R1：全部流水停在「失败」时原列表语义隐去告警，但按对话的告警照旧（恒对全量算），类别后缀也还在
        val failed = build(category = LogCategory.FAILED)
        assertTrue(failed.flow.alerts.isEmpty())
        assertEquals(s.conversationAlerts, failed.conversationAlerts)
        assertEquals(LlmFailureKind.INSUFFICIENT_BALANCE, failed.alertKinds[LogSource.CHAT])
    }

    @Test
    fun reasonChips_onlyForFailed_countDescThenEnumOrder_andFilter() {
        assertEquals(emptyList<Pair<LlmFailureKind, Int>>(), build(category = LogCategory.ALL).reasonChips)
        val failed = build(category = LogCategory.FAILED)
        assertEquals(
            listOf(
                LlmFailureKind.INSUFFICIENT_BALANCE to 3,
                LlmFailureKind.TIMEOUT to 2,
                LlmFailureKind.RATE_LIMITED to 2,
                LlmFailureKind.NETWORK to 1,
            ),
            failed.reasonChips,
        )
        assertEquals(8, failed.flowEntries.size)
        val onlyRate = build(category = LogCategory.FAILED, reason = LlmFailureKind.RATE_LIMITED)
        assertEquals(2, onlyRate.flowEntries.size)
        assertTrue(onlyRate.flowEntries.all { it.failureKind == "rate_limited" })
        assertEquals(LlmFailureKind.RATE_LIMITED, onlyRate.reason)
        assertEquals("原因只在「失败」里生效", 9, build(category = LogCategory.ALL, reason = LlmFailureKind.RATE_LIMITED).flowEntries.size)
    }

    @Test
    fun todayStatsFromDailyTable_andClearedIsEmpty() {
        val s = build()
        val stats = s.conversation.today!!
        assertEquals(9, stats.calls)
        assertEquals(7, stats.failures)
        assertNull(stats.cacheRatePercent)
        val cleared = buildLogHomeUiState(emptyList(), emptyList(), emptyList(), AppSettings(), "", null, LogCategory.ALL, null, TrendRange.WEEK, today, now, zone)
        assertTrue("E35", cleared.conversation.empty)
        assertTrue(cleared.flowEntries.isEmpty())
        assertTrue(cleared.trend.empty)
        assertEquals(0, cleared.conversation.today!!.calls)
    }
}
