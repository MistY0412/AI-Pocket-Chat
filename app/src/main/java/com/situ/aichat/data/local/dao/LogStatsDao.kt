package com.situ.aichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.situ.aichat.data.local.entity.LogDailyStatEntity
import kotlinx.coroutines.flow.Flow

/**
 * 上下文日志按天汇总 DAO（四期·图纸三 §3.1）：累加 / 查询 / 清理。
 * 累加 = 「INSERT OR IGNORE 保行 + 列级 UPDATE」同事务（minSdk 29 的 SQLite 3.22 没有 `ON CONFLICT DO UPDATE`）。
 */
@Dao
abstract class LogStatsDao {
    /** 只给 [addCall] 用：保证这一行存在（已存在则忽略）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(row: LogDailyStatEntity): Long

    /** 只给 [addCall] 用：列级累加（minSdk 29 的 SQLite 没有 UPSERT）。 */
    @Query(
        "UPDATE log_daily_stats SET calls = calls + 1, failures = failures + :failed, " +
            "promptTokens = promptTokens + :promptTokens, completionTokens = completionTokens + :completionTokens, " +
            "cacheHitTokens = cacheHitTokens + :cacheHitTokens, cacheMissTokens = cacheMissTokens + :cacheMissTokens " +
            "WHERE dayKey = :dayKey AND modelName = :modelName AND source = :source",
    )
    abstract suspend fun increment(dayKey: String, modelName: String, source: String, failed: Int, promptTokens: Long, completionTokens: Long, cacheHitTokens: Long, cacheMissTokens: Long): Int

    /** 记一次调用（事务：先保行再累加）。 */
    @Transaction
    open suspend fun addCall(dayKey: String, modelName: String, source: String, failed: Boolean, promptTokens: Long, completionTokens: Long, cacheHitTokens: Long, cacheMissTokens: Long) {
        insertIfAbsent(LogDailyStatEntity(dayKey, modelName, source))
        increment(dayKey, modelName, source, if (failed) 1 else 0, promptTokens, completionTokens, cacheHitTokens, cacheMissTokens)
    }

    @Query("SELECT * FROM log_daily_stats WHERE dayKey >= :fromDayKey ORDER BY dayKey ASC, modelName ASC, source ASC")
    abstract fun since(fromDayKey: String): Flow<List<LogDailyStatEntity>>

    @Query("DELETE FROM log_daily_stats WHERE dayKey < :beforeDayKey")
    abstract suspend fun deleteBefore(beforeDayKey: String): Int

    @Query("DELETE FROM log_daily_stats")
    abstract suspend fun deleteAll()
}
