package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.prompt.ContextSegment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 图纸三 §3.7 读侧（补 T1-2：NO_PREVIOUS / NO_DATA 两种只由 [LogAnalysisReader] 产出）：解码容错 + 缓存断点查上一条的接线。
 */
class LogAnalysisReaderTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }
    private val logDao = mockk<LogDao>()
    private val reader = LogAnalysisReader(logDao, json)

    private val prevShape = LogRequestShape("su", listOf("S", "U1"), listOf(10, 5))
    private val curShape = LogRequestShape("suau", listOf("S", "U1", "A1", "U2"), listOf(10, 5, 7, 3))
    private val segs = listOf(ContextSegment("人设", null, 20, 10, "prefix", "fp"))
    private val segsJson = json.encodeToString(ListSerializer(ContextSegment.serializer()), segs)

    private fun entry(conversation: String? = "conv-1", shape: String = LogRequestShape.encode(json, curShape)) = LogEntryEntity(
        id = 7, timestampMillis = 1000, source = "chat", conversationUuid = conversation, shapeJson = shape, contextSegmentsJson = segsJson,
    )

    @Test
    fun noConversation_noPrevious() = runBlocking {
        assertEquals(CacheBreak(CacheBreakKind.NO_PREVIOUS), reader.cacheBreakOf(entry(conversation = null)))
    }

    @Test
    fun oldRowWithoutShape_noData() = runBlocking {
        assertEquals(CacheBreak(CacheBreakKind.NO_DATA), reader.cacheBreakOf(entry(shape = "")))
    }

    @Test
    fun noPreviousRow_noPrevious_withCurrentTotal() = runBlocking {
        coEvery { logDao.previousComparable("conv-1", "chat", 1000, 7) } returns null
        assertEquals(CacheBreak(CacheBreakKind.NO_PREVIOUS, totalTokensEstimate = 25), reader.cacheBreakOf(entry()))
    }

    @Test
    fun previousShapeUndecodable_noData() = runBlocking {
        coEvery { logDao.previousComparable(any(), any(), any(), any()) } returns LogShapeRow(6, 900, "", segsJson)
        assertEquals(CacheBreak(CacheBreakKind.NO_DATA), reader.cacheBreakOf(entry()))
    }

    @Test
    fun withPrevious_delegatesToAnalyzer_sameConversationSourceAndPosition() = runBlocking {
        coEvery { logDao.previousComparable(any(), any(), any(), any()) } returns LogShapeRow(6, 900, LogRequestShape.encode(json, prevShape), segsJson)
        assertEquals(CacheBreak(CacheBreakKind.TAIL, messageIndex = 2, cachedTokensEstimate = 15, totalTokensEstimate = 25), reader.cacheBreakOf(entry()))
        coVerify(exactly = 1) { logDao.previousComparable("conv-1", "chat", 1000, 7) }
    }

    // ── 四期·图纸四 T1-10：compareWithPrevious 带回上一条的时刻与分段；没有上一条的 NO_* 分支两者为 null / 空表 ──

    @Test
    fun compareWithPrevious_carriesPreviousTimeAndSegments() = runBlocking {
        val prevSegs = listOf(ContextSegment("人设", null, 20, 10, "prefix", "old-fp"))
        val prevSegsJson = json.encodeToString(ListSerializer(ContextSegment.serializer()), prevSegs)
        coEvery { logDao.previousComparable(any(), any(), any(), any()) } returns
            LogShapeRow(6, 900, LogRequestShape.encode(json, prevShape), prevSegsJson)
        val c = reader.compareWithPrevious(entry())
        assertEquals(900L, c.previousTimestampMillis)
        assertEquals(prevSegs, c.previousSegments)
        assertEquals("断点与 cacheBreakOf 同一结果", reader.cacheBreakOf(entry()), c.cacheBreak)
    }

    @Test
    fun compareWithPrevious_noPreviousBranches_nullTimeEmptySegments() = runBlocking {
        coEvery { logDao.previousComparable("conv-1", "chat", 1000, 7) } returns null
        for ((e, kind) in listOf(
            entry(conversation = null) to CacheBreakKind.NO_PREVIOUS,
            entry(shape = "") to CacheBreakKind.NO_DATA,
            entry() to CacheBreakKind.NO_PREVIOUS,
        )) {
            val c = reader.compareWithPrevious(e)
            assertEquals(kind, c.cacheBreak.kind)
            assertNull(c.previousTimestampMillis)
            assertEquals(emptyList<ContextSegment>(), c.previousSegments)
        }
    }

    @Test
    fun compareWithPrevious_previousShapeUndecodable_noDataButKeepsPreviousFacts() = runBlocking {
        coEvery { logDao.previousComparable(any(), any(), any(), any()) } returns LogShapeRow(6, 900, "", segsJson)
        val c = reader.compareWithPrevious(entry())
        assertEquals(CacheBreak(CacheBreakKind.NO_DATA), c.cacheBreak)
        assertEquals("查到了上一条就带回它的时刻（§3.4：只有「没有上一条」时才为 null）", 900L, c.previousTimestampMillis)
        assertEquals(segs, c.previousSegments)
    }

    @Test
    fun decodeHelpers_tolerateEmptyAndBad() {
        assertEquals(emptyList<ContextSegment>(), reader.segmentsOf(""))
        assertEquals(emptyList<ContextSegment>(), reader.segmentsOf("[{bad"))
        assertEquals(segs, reader.segmentsOf(segsJson))
        assertNull(reader.shapeOf(LogEntryEntity(shapeJson = "")))
        assertNull(reader.adaptationOf(LogEntryEntity(sendAdaptationJson = "")))
        assertEquals(LogSendAdaptation(asIs = true), reader.adaptationOf(LogEntryEntity(sendAdaptationJson = "{\"asIs\":true}")))
    }

    @Test
    fun failureKindOf_row() {
        assertNull(reader.failureKindOf(LogListRow(id = 1, timestampMillis = 0, isSuccess = true)))
        assertEquals(LlmFailureKind.RATE_LIMITED, reader.failureKindOf(LogListRow(id = 1, timestampMillis = 0, isSuccess = false, failureKind = "rate_limited")))
        assertEquals(LlmFailureKind.INVALID_KEY, reader.failureKindOf(LogListRow(id = 1, timestampMillis = 0, isSuccess = false, errorMessage = "鉴权失败 (401)，请检查 API Key 是否正确。")))
    }
}
