package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogListRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** T1-9（四期·图纸四 §3.8）：原始报错拼接、同类只数同一天、前 3 个时刻、老记录按文字现算类别。 */
class LogFailureModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(27, 15)

    private fun row(id: Long, ts: Long, ok: Boolean = false, kind: String? = "insufficient_balance", error: String? = null) =
        LogListRow(id = id, timestampMillis = ts, isSuccess = ok, failureKind = kind, errorMessage = error)

    @Test
    fun success_null() {
        assertNull(buildFailureView(LogEntryEntity(id = 1, isSuccess = true), emptyList(), now, zone))
    }

    @Test
    fun sameKind_sameDayOnly_firstThreeTimes_includesSelf() {
        val entry = LogEntryEntity(id = 5, timestampMillis = at(27, 14), isSuccess = false, failureKind = "insufficient_balance", httpStatus = 402, errorMessage = "Insufficient Balance")
        val rows = listOf(
            row(1, at(27, 9)), row(2, at(27, 10)), row(3, at(27, 11)), row(5, at(27, 14)),
            row(6, at(27, 12), kind = "timeout"), // 别的类
            row(7, at(27, 13), ok = true, kind = null), // 成功
            row(8, at(26, 23, 59)), // 昨天
        )
        val v = buildFailureView(entry, rows, now, zone)!!
        assertEquals(LlmFailureKind.INSUFFICIENT_BALANCE, v.kind)
        assertEquals(402, v.httpStatus)
        assertEquals("HTTP 402 · Insufficient Balance", v.raw)
        assertEquals(4, v.sameKindCount)
        assertEquals(listOf(at(27, 9), at(27, 10), at(27, 11)), v.sameKindTimes)
        assertEquals(LogDayKind.TODAY, v.dayKind)
    }

    @Test
    fun entryOutsideSnapshot_stillCountsItself() {
        val entry = LogEntryEntity(id = 99, timestampMillis = at(25, 8), isSuccess = false, failureKind = "timeout")
        val v = buildFailureView(entry, listOf(row(1, at(25, 9), kind = "timeout")), now, zone)!!
        assertEquals(2, v.sameKindCount)
        assertEquals(listOf(at(25, 8), at(25, 9)), v.sameKindTimes)
        assertEquals(LogDayKind.EARLIER, v.dayKind)
        assertEquals("无状态码不加前缀", "", v.raw)
    }

    @Test
    fun oldRecord_kindFromText() {
        val entry = LogEntryEntity(id = 3, timestampMillis = at(26, 20), isSuccess = false, failureKind = null, errorMessage = "鉴权失败 (401)，请检查 API Key 是否正确。")
        val v = buildFailureView(entry, listOf(row(3, at(26, 20), kind = null, error = "鉴权失败 (401)，请检查 API Key 是否正确。")), now, zone)!!
        assertEquals(LlmFailureKind.INVALID_KEY, v.kind)
        assertEquals(1, v.sameKindCount)
        assertNull(v.httpStatus)
        assertEquals(LogDayKind.YESTERDAY, v.dayKind)
    }
}
