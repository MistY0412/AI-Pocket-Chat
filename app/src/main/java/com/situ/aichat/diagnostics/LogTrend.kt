package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogDailyStatEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 上下文日志趋势（四期·图纸三 §3.9）：日键 + 按天 / 按模型聚合 + 迁移回填聚合（趋势不显示金额）。数据源 = 按天汇总表 `log_daily_stats`
 * （写日志时同锁累加，不受条数轮转截断）；日期一律按本机时区在 Kotlin 里算（不用 SQLite `localtime`，测试可钉时区）。
 */
object LogTrend {
    /** 汇总表只留这么多天（轮转时删更早的）。 */
    const val STATS_KEEP_DAYS = 90

    /** 本机时区的日期键 `yyyy-MM-dd`（= [LocalDate.toString]）。 */
    fun dayKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    /** [dayKey] 往前推 [days] 天的日期键。 */
    fun daysBefore(dayKey: String, days: Int): String =
        LocalDate.parse(dayKey).minusDays(days.toLong()).toString()

    /** 一段汇总：[key] = 日键（[perDay]）或模型名（[perModel]）。 */
    data class Totals(
        val key: String,
        val calls: Int,
        val failures: Int,
        val promptTokens: Long,
        val completionTokens: Long,
        val cacheHitTokens: Long,
        val cacheMissTokens: Long,
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens

        /** hit + miss = 0 → null（服务商没报，≠ 命中 0%）；否则 (hit * 100.0 / (hit + miss)).roundToInt()。 */
        val cacheRatePercent: Int?
            get() {
                val sum = cacheHitTokens + cacheMissTokens
                return if (sum == 0L) null else (cacheHitTokens * 100.0 / sum).roundToInt()
            }
    }

    /** [fromDayKey]..[toDayKey] 每天一行（缺的天补零·升序）；[sources] 非 null 时只计这些来源。key = 日键。 */
    fun perDay(rows: List<LogDailyStatEntity>, fromDayKey: String, toDayKey: String, sources: Set<String>? = null): List<Totals> {
        val byDay = rows.filter { sources == null || it.source in sources }.groupBy { it.dayKey }
        val out = mutableListOf<Totals>()
        var day = LocalDate.parse(fromDayKey)
        val end = LocalDate.parse(toDayKey)
        while (!day.isAfter(end)) {
            val key = day.toString()
            out += sum(key, byDay[key].orEmpty())
            day = day.plusDays(1)
        }
        return out
    }

    /** 按模型汇总（key = modelName）：calls 降序，同 calls 按 key 升序。 */
    fun perModel(rows: List<LogDailyStatEntity>, sources: Set<String>? = null): List<Totals> =
        rows.filter { sources == null || it.source in sources }
            .groupBy { it.modelName }
            .map { (model, group) -> sum(model, group) }
            .sortedWith(compareByDescending<Totals> { it.calls }.thenBy { it.key })

    private fun sum(key: String, group: List<LogDailyStatEntity>) = Totals(
        key = key,
        calls = group.sumOf { it.calls },
        failures = group.sumOf { it.failures },
        promptTokens = group.sumOf { it.promptTokens },
        completionTokens = group.sumOf { it.completionTokens },
        cacheHitTokens = group.sumOf { it.cacheHitTokens },
        cacheMissTokens = group.sumOf { it.cacheMissTokens },
    )

    /** 迁移回填读的一行（v50 日志表投影）。 */
    data class BackfillRow(
        val timestampMillis: Long,
        val modelName: String,
        val source: String,
        val isSuccess: Boolean,
        val promptTokens: Long,
        val completionTokens: Long,
        val cacheHitTokens: Long,
        val cacheMissTokens: Long,
    )

    /** 迁移回填：按 (dayKey, modelName, source) 聚合；结果按 dayKey、modelName、source 升序。 */
    fun backfill(rows: List<BackfillRow>, zone: ZoneId): List<LogDailyStatEntity> =
        rows.groupBy { Triple(dayKey(it.timestampMillis, zone), it.modelName, it.source) }
            .map { (key, group) ->
                LogDailyStatEntity(
                    dayKey = key.first,
                    modelName = key.second,
                    source = key.third,
                    calls = group.size,
                    failures = group.count { !it.isSuccess },
                    promptTokens = group.sumOf { it.promptTokens },
                    completionTokens = group.sumOf { it.completionTokens },
                    cacheHitTokens = group.sumOf { it.cacheHitTokens },
                    cacheMissTokens = group.sumOf { it.cacheMissTokens },
                )
            }
            .sortedWith(compareBy({ it.dayKey }, { it.modelName }, { it.source }))
}
