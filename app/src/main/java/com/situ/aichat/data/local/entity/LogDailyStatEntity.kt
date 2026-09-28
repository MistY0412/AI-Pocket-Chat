package com.situ.aichat.data.local.entity

import androidx.room.Entity

/** 上下文日志按天汇总（四期·图纸三 §3.1）：天 × 模型 × 来源一行；写日志时同锁累加，不受条数轮转影响，只留 [com.situ.aichat.diagnostics.LogTrend.STATS_KEEP_DAYS] 天。不进备份。 */
@Entity(tableName = "log_daily_stats", primaryKeys = ["dayKey", "modelName", "source"])
data class LogDailyStatEntity(
    /** 本机时区的日期 `yyyy-MM-dd`（[com.situ.aichat.diagnostics.LogTrend.dayKey]）。 */
    val dayKey: String,
    val modelName: String,
    val source: String,
    val calls: Int = 0,
    val failures: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cacheHitTokens: Long = 0,
    val cacheMissTokens: Long = 0,
)
