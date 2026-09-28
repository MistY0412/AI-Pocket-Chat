package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogTrend
import java.time.LocalDate
import kotlin.math.min

enum class TrendRange(val days: Int) { WEEK(7), MONTH(30) }

/** 趋势一天：柱 = 全部来源的 token（[hitTokens] 夹在 total 内）；折线点 = 「对话」分类的命中率（没报 = null）。 */
data class LogTrendDay(val dayKey: String, val dayOfMonth: Int, val totalTokens: Long, val hitTokens: Long, val chatHitRatePercent: Int?)

data class LogTrendState(
    val range: TrendRange = TrendRange.WEEK,
    val days: List<LogTrendDay> = emptyList(),
    val models: List<LogTrend.Totals> = emptyList(),
    val empty: Boolean = true,
)

/** 趋势装配（四期·图纸四 §3.5 锁定）：全取按天汇总表；按模型表 = 全部来源；不显示金额。 */
fun buildTrend(stats: List<LogDailyStatEntity>, range: TrendRange, todayKey: String): LogTrendState {
    val from = LogTrend.daysBefore(todayKey, range.days - 1)
    val inRange = stats.filter { it.dayKey in from..todayKey } // yyyy-MM-dd 字典序 = 日期序
    val all = LogTrend.perDay(inRange, from, todayKey)
    val chat = LogTrend.perDay(inRange, from, todayKey, LogCategory.CHAT.sources.toSet())
    val days = all.indices.map { i ->
        LogTrendDay(
            dayKey = all[i].key,
            dayOfMonth = LocalDate.parse(all[i].key).dayOfMonth,
            totalTokens = all[i].totalTokens,
            hitTokens = min(all[i].cacheHitTokens, all[i].totalTokens),
            chatHitRatePercent = chat[i].cacheRatePercent,
        )
    }
    return LogTrendState(range = range, days = days, models = LogTrend.perModel(inRange), empty = all.all { it.calls == 0 })
}
