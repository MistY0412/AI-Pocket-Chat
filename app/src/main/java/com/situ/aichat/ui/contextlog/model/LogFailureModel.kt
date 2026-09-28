package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.LlmFailureClassifier
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogListRow
import java.time.ZoneId

/** 失败详情（四期·图纸四 §3.8）：类别、状态码、原始报错、同一天同类失败的次数与前 3 个时刻、那天对今天。 */
data class LogFailureView(
    val kind: LlmFailureKind,
    val httpStatus: Int?,
    val raw: String,
    val sameKindCount: Int,
    val sameKindTimes: List<Long>,
    val dayKind: LogDayKind,
)

private const val SAME_KIND_TIMES_SHOWN = 3

/**
 * 失败详情装配（§3.8 锁定）：成功 → null。同类 = [rows] 里与 entry 同一本地日期、失败、分类相同的行（含 entry 自己；
 * entry 已不在最近 500 条快照里时补算它自己一条）。
 */
fun buildFailureView(entry: LogEntryEntity, rows: List<LogListRow>, nowMillis: Long, zone: ZoneId): LogFailureView? {
    if (entry.isSuccess) return null
    val kind = LlmFailureClassifier.kindOfRow(false, entry.failureKind, entry.errorMessage)!!
    val day = LogFormat.localDate(entry.timestampMillis, zone)
    val same = rows.filter {
        !it.isSuccess && LogFormat.localDate(it.timestampMillis, zone) == day &&
            LlmFailureClassifier.kindOfRow(false, it.failureKind, it.errorMessage) == kind
    }
    val times = (same.map { it.timestampMillis } + if (same.any { it.id == entry.id }) emptyList() else listOf(entry.timestampMillis)).sorted()
    return LogFailureView(
        kind = kind,
        httpStatus = entry.httpStatus,
        raw = (entry.httpStatus?.let { "HTTP $it · " } ?: "") + entry.errorMessage.orEmpty(),
        sameKindCount = times.size,
        sameKindTimes = times.take(SAME_KIND_TIMES_SHOWN),
        dayKind = LogFormat.dayKind(entry.timestampMillis, nowMillis, zone),
    )
}

/** 失败详情页「去 API 设置」按钮只给这三类（§4.6）。 */
fun failureOffersApiSettings(kind: LlmFailureKind): Boolean =
    kind == LlmFailureKind.INVALID_KEY || kind == LlmFailureKind.INSUFFICIENT_BALANCE || kind == LlmFailureKind.BAD_FORMAT
