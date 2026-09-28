package com.situ.aichat.data.local

import androidx.room.Room
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.dao.LogStatsDao
import com.situ.aichat.data.local.entity.LogDailyStatEntity
import com.situ.aichat.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T2-1（时间感知四期·图纸三 §3.1 / §3.10·Robolectric + 真 in-memory Room·照 DayMomentsDaoTest）：
 * v51 的 [LogDao] 新查询 / 列清单 / 去隐私 SQL 与 [LogStatsDao] 累加事务。断言从 §3 规格独立反推。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogDaoV51Test {

    private lateinit var db: AppDatabase
    private lateinit var dao: LogDao
    private lateinit var stats: LogStatsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.logDao()
        stats = db.logStatsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(
        ts: Long, source: String = "chat", ok: Boolean = true, conversation: String? = "conv-1",
        shape: String = "", segments: String = "",
    ) = LogEntryEntity(
        timestampMillis = ts, source = source, isSuccess = ok, conversationUuid = conversation,
        shapeJson = shape, contextSegmentsJson = segments,
    )

    private fun insert(e: LogEntryEntity): Long = runBlocking { dao.insert(e) }

    @Test
    fun recent_carriesNewColumns() = runBlocking {
        insert(
            LogEntryEntity(
                timestampMillis = 100, characterName = "林晚", modelName = "deepseek-chat", isSuccess = false,
                conversationUuid = "conv-1", characterUuid = "char-1", turnId = "turn-1", anchorMessageUuid = "msg-1",
                providerType = "deepseek", failureKind = "rate_limited", httpStatus = 429,
            ),
        )
        val row = dao.recent(10).first().single()
        assertEquals("conv-1", row.conversationUuid)
        assertEquals("char-1", row.characterUuid)
        assertEquals("turn-1", row.turnId)
        assertEquals("msg-1", row.anchorMessageUuid)
        assertEquals("deepseek", row.providerType)
        assertEquals("rate_limited", row.failureKind)
        assertEquals(429, row.httpStatus)
        assertEquals("林晚", row.characterName)
        assertEquals("deepseek-chat", row.modelName)
    }

    @Test
    fun recent_oldStyleRow_newColumnsNull() = runBlocking {
        insert(LogEntryEntity(timestampMillis = 100))
        val row = dao.recent(10).first().single()
        assertNull(row.conversationUuid)
        assertNull(row.turnId)
        assertNull(row.failureKind)
        assertNull(row.httpStatus)
    }

    @Test
    fun recentSuccessBySource_filtersSortsLimits_unchanged() = runBlocking {
        val a = insert(entry(100).copy(promptTokens = 11, cacheHitTokens = 5))
        insert(entry(200, ok = false))
        insert(entry(300, source = "story"))
        val d = insert(entry(300).copy(promptTokens = 44))
        val e = insert(entry(400).copy(promptTokens = 55))

        val two = dao.recentSuccessBySource("chat", 2).first()
        assertEquals(listOf(e, d), two.map { it.id })
        assertEquals(listOf(55, 44), two.map { it.promptTokens })

        val all = dao.recentSuccessBySource("chat", 10).first()
        assertEquals(listOf(e, d, a), all.map { it.id })
        assertEquals(5, all.last().cacheHitTokens)
    }

    @Test
    fun previousComparable_sameMillisById_skipsOtherSourceFailureAndConversation() = runBlocking {
        val a = insert(entry(100, shape = "A", segments = "segA"))
        insert(entry(150, source = "story", shape = "E"))
        insert(entry(180, conversation = "conv-2", shape = "F"))
        insert(entry(200, ok = false, shape = "D"))
        val b = insert(entry(200, shape = "B"))
        val c = insert(entry(200, shape = "C"))

        val beforeC = dao.previousComparable("conv-1", "chat", 200, c)!!
        assertEquals("同毫秒按 id 取更早那条（E27）", b, beforeC.id)
        assertEquals("B", beforeC.shapeJson)

        val beforeB = dao.previousComparable("conv-1", "chat", 200, b)!!
        assertEquals("跨来源 / 失败行 / 别的对话都不取", a, beforeB.id)
        assertEquals("segA", beforeB.contextSegmentsJson)

        assertNull(dao.previousComparable("conv-1", "chat", 100, a))
        assertEquals("story 来源只找 story", "E", dao.previousComparable("conv-1", "story", 999, 999)?.shapeJson)
    }

    @Test
    fun purgeFullText_clearsRequestJson_keepsShapeAndAdaptation() = runBlocking {
        val id = insert(
            LogEntryEntity(
                timestampMillis = 100, requestJson = "{\"messages\":[]}", shapeJson = "{\"roles\":\"su\"}",
                sendAdaptationJson = "{\"asIs\":true}",
            ),
        )
        assertEquals("只有 requestJson 有正文的行也算受影响", 1, dao.purgeFullText())
        val after = dao.getById(id)!!
        assertEquals("", after.requestJson)
        assertEquals("{\"roles\":\"su\"}", after.shapeJson)
        assertEquals("{\"asIs\":true}", after.sendAdaptationJson)
        assertEquals("幂等：再按零行", 0, dao.purgeFullText())
    }

    @Test
    fun addCall_twiceAccumulates_inOneRow() = runBlocking {
        stats.addCall("2026-09-27", "m", "chat", failed = false, promptTokens = 100, completionTokens = 20, cacheHitTokens = 60, cacheMissTokens = 40)
        stats.addCall("2026-09-27", "m", "chat", failed = true, promptTokens = 0, completionTokens = 0, cacheHitTokens = 0, cacheMissTokens = 0)
        stats.addCall("2026-09-27", "m", "story", failed = false, promptTokens = 7, completionTokens = 3, cacheHitTokens = 0, cacheMissTokens = 0)
        assertEquals(
            listOf(
                LogDailyStatEntity("2026-09-27", "m", "chat", calls = 2, failures = 1, promptTokens = 100, completionTokens = 20, cacheHitTokens = 60, cacheMissTokens = 40),
                LogDailyStatEntity("2026-09-27", "m", "story", calls = 1, failures = 0, promptTokens = 7, completionTokens = 3, cacheHitTokens = 0, cacheMissTokens = 0),
            ),
            stats.since("2026-01-01").first(),
        )
    }

    @Test
    fun since_andDeleteBefore_useDayKeyBoundaries() = runBlocking {
        for (day in listOf("2026-06-29", "2026-06-30", "2026-07-01")) {
            stats.addCall(day, "m", "chat", failed = false, promptTokens = 1, completionTokens = 1, cacheHitTokens = 0, cacheMissTokens = 0)
        }
        assertEquals(listOf("2026-06-30", "2026-07-01"), stats.since("2026-06-30").first().map { it.dayKey })
        assertEquals("严格小于：只删 06-29", 1, stats.deleteBefore("2026-06-30"))
        assertEquals(listOf("2026-06-30", "2026-07-01"), stats.since("2000-01-01").first().map { it.dayKey })
        stats.deleteAll()
        assertEquals(emptyList<LogDailyStatEntity>(), stats.since("2000-01-01").first())
    }
}
