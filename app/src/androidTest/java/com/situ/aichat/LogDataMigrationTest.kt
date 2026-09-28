package com.situ.aichat

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.situ.aichat.data.local.AppDatabase
import com.situ.aichat.data.local.MIGRATION_50_51
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T3-1（时间感知四期·图纸三 §7·E17–E19）：v50 插两条旧日志 → 迁到 v51（`runMigrationsAndValidate` 对照 51.json）→
 * ①旧行原值不变、行不丢；②10 个新列落 NULL / ''；③`log_daily_stats` 有回填行（一行·calls 2·failures 1）。
 * 种子 INSERT 列清单 = 50.json `log_entries` 里「NOT NULL 且无 SQL DEFAULT」的全部 15 列（脚本从 50.json 机器推导·
 * PITFALLS §1a）。两条种子只差 1ms，任何时区下都落同一个日期键。逐版本全链校验仍由 [MigrationTest]（LATEST_VERSION = 51）负责。
 */
@RunWith(AndroidJUnit4::class)
class LogDataMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration50To51AddsLogColumnsAndBackfillsDailyStats() {
        helper.createDatabase(TEST_DB, 50).apply {
            for ((id, ok) in listOf(1L to true, 2L to false)) {
                execSQL(
                    "INSERT INTO log_entries (" +
                        "id, timestampMillis, characterName, modelName, isSuccess, source, messageCount, fullContext, contextSegmentsJson, " +
                        "promptTokens, completionTokens, reasoningTokens, cacheHitTokens, cacheMissTokens, isTokenEstimated" +
                        ") VALUES (" +
                        "$id, ${NOON_UTC + id}, '林晚', 'deepseek-chat', ${if (ok) 1 else 0}, 'chat', 5, 'ctx-$id', 'seg-$id', " +
                        "${if (ok) 1000 else 0}, ${if (ok) 200 else 0}, 0, ${if (ok) 600 else 0}, ${if (ok) 400 else 0}, 0" +
                        ")",
                )
            }
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 51, true, MIGRATION_50_51).use { db ->
            db.query(
                "SELECT characterName, modelName, isSuccess, fullContext, contextSegmentsJson, promptTokens, cacheHitTokens, " +
                    "conversationUuid IS NULL, turnId IS NULL, providerType IS NULL, sendAdaptationJson, requestJson, shapeJson, " +
                    "failureKind IS NULL, httpStatus IS NULL FROM log_entries WHERE id = 1",
            ).use { c ->
                assertTrue("旧日志行必须还在", c.moveToFirst())
                assertEquals("林晚", c.getString(0))
                assertEquals("deepseek-chat", c.getString(1))
                assertEquals(1, c.getInt(2))
                assertEquals("ctx-1", c.getString(3))
                assertEquals("seg-1", c.getString(4))
                assertEquals(1000, c.getInt(5))
                assertEquals(600, c.getInt(6))
                assertEquals(1, c.getInt(7))
                assertEquals(1, c.getInt(8))
                assertEquals(1, c.getInt(9))
                assertEquals("", c.getString(10))
                assertEquals("", c.getString(11))
                assertEquals("", c.getString(12))
                assertEquals(1, c.getInt(13))
                assertEquals(1, c.getInt(14))
            }
            db.query("SELECT COUNT(*) FROM log_entries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("行数不变", 2, c.getInt(0))
            }
            db.query(
                "SELECT modelName, source, calls, failures, promptTokens, completionTokens, cacheHitTokens, cacheMissTokens FROM log_daily_stats",
            ).use { c ->
                assertTrue("汇总表有回填行", c.moveToFirst())
                assertEquals("deepseek-chat", c.getString(0))
                assertEquals("chat", c.getString(1))
                assertEquals(2, c.getInt(2))
                assertEquals(1, c.getInt(3))
                assertEquals(1000L, c.getLong(4))
                assertEquals(200L, c.getLong(5))
                assertEquals(600L, c.getLong(6))
                assertEquals(400L, c.getLong(7))
                assertTrue("同一天同模型同来源只一行", !c.moveToNext())
            }
        }
    }

    private companion object {
        const val TEST_DB = "log-data-migration-test.db"
        /** 2026-09-27 12:00:00 UTC——两条种子在其后 1ms / 2ms，任何整点 / 半点时区的午夜都不落在两者之间 ⇒ 必同一日期键。 */
        const val NOON_UTC = 1_790_510_400_000L
    }
}
