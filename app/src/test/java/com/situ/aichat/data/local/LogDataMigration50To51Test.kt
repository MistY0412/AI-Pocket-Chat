package com.situ.aichat.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * T2-1（时间感知四期·图纸三 §3.1 / E17–E19）：`MIGRATION_50_51` 在**真 SQLite** 上跑一遍（照 [Migration47To48Test]）。
 *
 * 证：① 老日志行一条不丢、原列值不变（K4）；② 10 个新列落 NULL / ''；③ `log_daily_stats` 按「本机时区日期 × 模型 × 来源」
 * 回填、失败只进 failures（数值手算）；④ 空表迁移后汇总为空。v50 `log_entries` 建表语句逐字取自 50.json；种子 INSERT
 * 列清单（15 列 = NOT NULL 且无 SQL DEFAULT）**由脚本从 50.json 机器推导**（PITFALLS §1a）。
 * 迁移内部用 `ZoneId.systemDefault()`，本测试把默认时区钉成 Asia/Shanghai（@After 复原）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogDataMigration50To51Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var savedZone: TimeZone

    @Before fun pinZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After fun tearDown() {
        if (::helper.isInitialized) helper.close()
        TimeZone.setDefault(savedZone)
    }

    /** v50 的 `log_entries` 建表语句，逐字取自 app/schemas/com.situ.aichat.data.local.AppDatabase/50.json。 */
    private val logEntriesV50Sql =
        "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMillis` INTEGER NOT NULL, " +
            "`characterName` TEXT NOT NULL, `modelName` TEXT NOT NULL, `isSuccess` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
            "`messageCount` INTEGER NOT NULL, `durationMillis` INTEGER, `errorMessage` TEXT, `fullContext` TEXT NOT NULL, " +
            "`responseContent` TEXT, `contextSegmentsJson` TEXT NOT NULL, `toolInfoJson` TEXT NOT NULL DEFAULT '', " +
            "`promptTokens` INTEGER NOT NULL, `completionTokens` INTEGER NOT NULL, `reasoningTokens` INTEGER NOT NULL, " +
            "`cacheHitTokens` INTEGER NOT NULL, `cacheMissTokens` INTEGER NOT NULL, `isTokenEstimated` INTEGER NOT NULL)"

    private fun openV50(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(50) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(logEntriesV50Sql)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_timestampMillis` ON `log_entries` (`timestampMillis`)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null) // 内存库
                .callback(callback)
                .build(),
        )
        return helper.writableDatabase
    }

    /** 15 个 NOT NULL 无默认列（脚本从 50.json 推导）+ 三个可空列给值，便于验「原值不变」。 */
    private fun insertLegacy(
        db: SupportSQLiteDatabase, id: Long, ts: Long, model: String, source: String, ok: Boolean,
        prompt: Int, completion: Int, reasoning: Int, hit: Int, miss: Int, error: String?,
    ) {
        db.execSQL(
            "INSERT INTO log_entries (" +
                "id, timestampMillis, characterName, modelName, isSuccess, source, messageCount, fullContext, contextSegmentsJson, " +
                "promptTokens, completionTokens, reasoningTokens, cacheHitTokens, cacheMissTokens, isTokenEstimated, " +
                "durationMillis, errorMessage, responseContent" +
                ") VALUES (?, ?, '林晚', ?, ?, ?, 7, 'ctx-sentinel-$id', 'seg-sentinel-$id', ?, ?, ?, ?, ?, 0, 1500, ?, 'resp-$id')",
            arrayOf<Any?>(id, ts, model, if (ok) 1 else 0, source, prompt, completion, reasoning, hit, miss, error),
        )
    }

    // 北京时间 09-27 10:00 / 09-27 23:59:59 / 09-28 00:00:00（UTC 下后两者同一天）
    private val sep27At10 = 1_790_474_400_000L
    private val sep27At235959 = 1_790_524_799_000L
    private val sep28At0000 = 1_790_524_800_000L

    private fun seedThree(db: SupportSQLiteDatabase) {
        insertLegacy(db, 1, sep27At10, "deepseek-chat", "chat", ok = true, prompt = 1000, completion = 200, reasoning = 30, hit = 600, miss = 400, error = null)
        insertLegacy(db, 2, sep27At235959, "deepseek-chat", "chat", ok = false, prompt = 0, completion = 0, reasoning = 0, hit = 0, miss = 0, error = "API 错误 (429) - rate limit")
        insertLegacy(db, 3, sep28At0000, "glm-4", "memory_summary", ok = true, prompt = 500, completion = 50, reasoning = 0, hit = 0, miss = 0, error = null)
    }

    @Test
    fun migrate50To51_keepsLegacyRows_originalValuesUnchanged() {
        val db = openV50()
        seedThree(db)

        MIGRATION_50_51.migrate(db)

        db.query(
            "SELECT timestampMillis, characterName, modelName, isSuccess, source, messageCount, fullContext, contextSegmentsJson, " +
                "promptTokens, completionTokens, reasoningTokens, cacheHitTokens, cacheMissTokens, isTokenEstimated, durationMillis, " +
                "errorMessage, responseContent, toolInfoJson FROM log_entries WHERE id = 2",
        ).use { c ->
            assertTrue("老行必须还在", c.moveToFirst())
            assertEquals(sep27At235959, c.getLong(0))
            assertEquals("林晚", c.getString(1))
            assertEquals("deepseek-chat", c.getString(2))
            assertEquals(0, c.getInt(3))
            assertEquals("chat", c.getString(4))
            assertEquals(7, c.getInt(5))
            assertEquals("ctx-sentinel-2", c.getString(6))
            assertEquals("seg-sentinel-2", c.getString(7))
            assertEquals(0, c.getInt(8))
            assertEquals(0, c.getInt(9))
            assertEquals(0, c.getInt(10))
            assertEquals(0, c.getInt(11))
            assertEquals(0, c.getInt(12))
            assertEquals(0, c.getInt(13))
            assertEquals(1500L, c.getLong(14))
            assertEquals("API 错误 (429) - rate limit", c.getString(15))
            assertEquals("resp-2", c.getString(16))
            assertEquals("", c.getString(17))
        }
        db.query("SELECT promptTokens, reasoningTokens, cacheHitTokens FROM log_entries WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1000, c.getInt(0))
            assertEquals(30, c.getInt(1))
            assertEquals(600, c.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM log_entries").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("行不丢也不多", 3, c.getInt(0))
        }
    }

    @Test
    fun migrate50To51_newColumns_nullOrEmpty() {
        val db = openV50()
        seedThree(db)
        MIGRATION_50_51.migrate(db)

        db.query(
            "SELECT conversationUuid IS NULL, characterUuid IS NULL, turnId IS NULL, anchorMessageUuid IS NULL, providerType IS NULL, " +
                "sendAdaptationJson, requestJson, shapeJson, failureKind IS NULL, httpStatus IS NULL FROM log_entries ORDER BY id",
        ).use { c ->
            var n = 0
            while (c.moveToNext()) {
                n++
                for (i in 0..4) assertEquals("可空新列第 $i 列落 NULL", 1, c.getInt(i))
                assertEquals("", c.getString(5))
                assertEquals("", c.getString(6))
                assertEquals("", c.getString(7))
                assertEquals("v50 老失败行 failureKind = NULL（读时现算·E17）", 1, c.getInt(8))
                assertEquals(1, c.getInt(9))
            }
            assertEquals(3, n)
        }
        // 新列可写
        db.execSQL("UPDATE log_entries SET turnId = 't-1', httpStatus = 429, shapeJson = '{}' WHERE id = 2")
        db.query("SELECT turnId, httpStatus, shapeJson FROM log_entries WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("t-1", c.getString(0))
            assertEquals(429, c.getInt(1))
            assertEquals("{}", c.getString(2))
        }
    }

    @Test
    fun migrate50To51_backfillsDailyStats_byLocalDayModelSource() {
        val db = openV50()
        seedThree(db)
        MIGRATION_50_51.migrate(db)

        val rows = mutableListOf<String>()
        db.query(
            "SELECT dayKey, modelName, source, calls, failures, promptTokens, completionTokens, cacheHitTokens, cacheMissTokens " +
                "FROM log_daily_stats ORDER BY dayKey, modelName, source",
        ).use { c ->
            while (c.moveToNext()) {
                rows += (0..8).joinToString("|") { c.getString(it) }
            }
        }
        // 手算：09-27 两条 deepseek/chat（一成一败）合一行；09-28 00:00 单独一天（按北京时间）
        assertEquals(
            listOf(
                "2026-09-27|deepseek-chat|chat|2|1|1000|200|600|400",
                "2026-09-28|glm-4|memory_summary|1|0|500|50|0|0",
            ),
            rows,
        )
    }

    @Test
    fun migrate50To51_emptyLogTable_emptyStats() {
        val db = openV50()
        MIGRATION_50_51.migrate(db)
        db.query("SELECT COUNT(*) FROM log_daily_stats").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("空日志表 → 汇总表为空（E18）", 0, c.getInt(0))
        }
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(log_daily_stats)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(1))
        }
        assertEquals(
            listOf("dayKey", "modelName", "source", "calls", "failures", "promptTokens", "completionTokens", "cacheHitTokens", "cacheMissTokens"),
            columns,
        )
    }
}
