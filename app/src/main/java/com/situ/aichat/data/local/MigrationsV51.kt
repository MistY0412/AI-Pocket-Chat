package com.situ.aichat.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.situ.aichat.diagnostics.LogTrend
import java.time.ZoneId

/** v50→v51（时间感知四期·图纸三 §3.1）：`log_entries` 加 10 列（对话 / 轮次 / 实际发送 / 形状 / 失败分类）+ 新表 `log_daily_stats` 并用现有日志行回填。
 *  放新文件：`Migrations.kt` 已近 800 行红线；注册仍在 `ALL_MIGRATIONS`。 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `conversationUuid` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `characterUuid` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `turnId` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `anchorMessageUuid` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `providerType` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `sendAdaptationJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `requestJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `shapeJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `failureKind` TEXT")
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `httpStatus` INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS `log_daily_stats` (`dayKey` TEXT NOT NULL, `modelName` TEXT NOT NULL, `source` TEXT NOT NULL, `calls` INTEGER NOT NULL, `failures` INTEGER NOT NULL, `promptTokens` INTEGER NOT NULL, `completionTokens` INTEGER NOT NULL, `cacheHitTokens` INTEGER NOT NULL, `cacheMissTokens` INTEGER NOT NULL, PRIMARY KEY(`dayKey`, `modelName`, `source`))")
        backfillDailyStats(db, ZoneId.systemDefault())
    }
}

/** 回填（internal 供测试钉时区）：读全部现有日志行 → [LogTrend.backfill] 聚合 → 逐行 INSERT。 */
internal fun backfillDailyStats(db: SupportSQLiteDatabase, zone: ZoneId) {
    val rows = mutableListOf<LogTrend.BackfillRow>()
    db.query(
        "SELECT timestampMillis, modelName, source, isSuccess, promptTokens, completionTokens, cacheHitTokens, cacheMissTokens FROM log_entries",
    ).use { c ->
        while (c.moveToNext()) {
            rows += LogTrend.BackfillRow(
                timestampMillis = c.getLong(0),
                modelName = c.getString(1),
                source = c.getString(2),
                isSuccess = c.getInt(3) != 0,
                promptTokens = c.getLong(4),
                completionTokens = c.getLong(5),
                cacheHitTokens = c.getLong(6),
                cacheMissTokens = c.getLong(7),
            )
        }
    }
    for (stat in LogTrend.backfill(rows, zone)) {
        db.execSQL(
            "INSERT INTO `log_daily_stats` (`dayKey`, `modelName`, `source`, `calls`, `failures`, `promptTokens`, `completionTokens`, `cacheHitTokens`, `cacheMissTokens`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                stat.dayKey, stat.modelName, stat.source, stat.calls, stat.failures,
                stat.promptTokens, stat.completionTokens, stat.cacheHitTokens, stat.cacheMissTokens,
            ),
        )
    }
}
