package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.dao.LogStatsDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.remote.llm.ApiConfigValues
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.LlmClient
import com.situ.aichat.data.remote.llm.LlmError
import com.situ.aichat.data.remote.llm.UsageDto
import com.situ.aichat.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.net.UnknownHostException

/**
 * T2-3（四期·图纸三 §3.4 / E1 / E11 / E21 / E23 / E24）：记录层落库实体的新列。LLM 走**真 [LlmClient]**（OkHttp 拦截器假服务端），
 * 捕获链与请求体都是真的；落库照 [ContextLogToolInfoRecordingTest] 用 MockK LogDao + coVerify(timeout)（fire-and-forget）。
 */
class ContextLogServiceTraceTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }
    private val bodies = mutableListOf<String>()
    private val logDao = mockk<LogDao>(relaxed = true)
    private val logStatsDao = mockk<LogStatsDao>(relaxed = true)
    private val config = ApiConfigValues(providerType = ApiProviderType.DEEPSEEK, apiKey = "k", baseUrl = "https://api.deepseek.com/v1", modelName = "deepseek-v4-flash")
    private val messages = listOf(ChatMessageDto(role = "system", content = "人设"), ChatMessageDto(role = "user", content = "在吗"))
    private val trace = LogTrace("conv-1", "char-1", "turn-1", "msg-1")

    private fun service(detail: Boolean, code: Int = 200, body: String = """{"choices":[{"message":{"content":"在呀"},"finish_reason":"stop"}]}"""): ContextLogService {
        val settings = mockk<SettingsRepository>()
        every { settings.appSettings } returns flowOf(AppSettings(logDetailEnabled = detail))
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            bodies.add(Buffer().also { chain.request().body?.writeTo(it) }.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return ContextLogService(LlmClient(http, json), logDao, settings, json, logStatsDao)
    }

    private fun capturedEntry(): LogEntryEntity {
        val slot = slot<LogEntryEntity>()
        coVerify(timeout = 2000) { logDao.insert(capture(slot)) }
        return slot.captured
    }

    @Test
    fun completion_inLogTrace_detailOff_traceColumnsAndMetadata_noBody() = runBlocking {
        withContext(trace) { service(detail = false).completion("chat", "林晚", config, messages) }
        val e = capturedEntry()
        assertEquals("conv-1", e.conversationUuid)
        assertEquals("char-1", e.characterUuid)
        assertEquals("turn-1", e.turnId)
        assertEquals("msg-1", e.anchorMessageUuid)
        assertEquals("deepseek", e.providerType)
        assertEquals("白名单原样（E4）", "{\"asIs\":true}", e.sendAdaptationJson)
        assertEquals(LogRequestShape.of(messages), LogRequestShape.decode(json, e.shapeJson))
        assertEquals("detail 关不存请求体（E1·K3）", "", e.requestJson)
        assertEquals("", e.fullContext)
        assertNull(e.failureKind)
        assertNull(e.httpStatus)
    }

    @Test
    fun completion_detailOn_requestJsonIsWhatWasSent() = runBlocking {
        service(detail = true).completion("chat", "林晚", config, messages)
        val e = capturedEntry()
        assertEquals("无媒体时 = 真实报文", bodies.single(), e.requestJson)
    }

    @Test
    fun completion_failure_classifiedFromThrowable_withStatus() = runBlocking {
        try {
            withContext(trace) { service(detail = false, code = 401, body = "{\"error\":\"bad key\"}").completion("chat", "林晚", config, messages) }
            fail("401 应原样抛出")
        } catch (e: LlmError.Http) {
            assertEquals(401, e.statusCode)
        }
        val e = capturedEntry()
        assertEquals(false, e.isSuccess)
        assertEquals("invalid_key", e.failureKind)
        assertEquals(401, e.httpStatus)
        assertEquals("turn-1", e.turnId)
        assertEquals("deepseek", e.providerType)
        coVerify(timeout = 2000) { logStatsDao.addCall(any(), "deepseek-v4-flash", "chat", true, 0L, 0L, 0L, 0L) }
    }

    @Test
    fun completion_failure_passesThrowableNotText() = runBlocking {
        // 判别例：UnknownHostException 按类型 = network；只看文字「api.deepseek.com」会落 other。
        val llm = mockk<LlmClient>()
        coEvery { llm.completion(any(), any(), any(), any(), any(), any(), any(), any()) } throws UnknownHostException("api.deepseek.com")
        val settings = mockk<SettingsRepository>()
        every { settings.appSettings } returns flowOf(AppSettings())
        try {
            ContextLogService(llm, logDao, settings, json, logStatsDao).completion("chat", "林晚", config, messages)
            fail("应原样抛出")
        } catch (e: UnknownHostException) {
            assertEquals("api.deepseek.com", e.message)
        }
        val e = capturedEntry()
        assertEquals("network", e.failureKind)
        assertEquals("api.deepseek.com", e.errorMessage)
    }

    @Test
    fun recordError_throwableOverload_classifiedFromType() = runBlocking {
        service(detail = false).recordError("voice", "林晚", "m", messages, UnknownHostException("relay.example"))
        assertEquals("network", capturedEntry().failureKind)
    }

    @Test
    fun recordError_stringOverload_classifiedFromText() = runBlocking {
        service(detail = false).recordError("voice", "林晚", "m", messages, "请求超时，请检查网络连接。")
        val e = capturedEntry()
        assertEquals("timeout", e.failureKind)
        assertNull(e.httpStatus)
    }

    @Test
    fun recordSuccess_explicitTrace_noCapture() = runBlocking {
        service(detail = true).recordSuccess("chat", "林晚", "m", messages, "好", 10L, null, trace = trace)
        val e = capturedEntry()
        assertEquals("turn-1", e.turnId)
        assertEquals("msg-1", e.anchorMessageUuid)
        assertNull("没捕获请求 → 服务商未知", e.providerType)
        assertEquals("", e.sendAdaptationJson)
        assertEquals("", e.requestJson)
        assertTrue("形状恒存", e.shapeJson.isNotEmpty())
    }

    @Test
    fun noTrace_fourColumnsNull() = runBlocking {
        service(detail = false).completion("schedule", "林晚", config, messages)
        val e = capturedEntry()
        assertNull(e.conversationUuid)
        assertNull(e.characterUuid)
        assertNull(e.turnId)
        assertNull(e.anchorMessageUuid)
        assertEquals("deepseek", e.providerType)
    }

    @Test
    fun addCall_argumentsByValue_dayKeyFromEntryTimestamp() = runBlocking {
        val usage = UsageDto(promptTokens = 100, completionTokens = 20, promptCacheHitTokens = 60, promptCacheMissTokens = 40)
        service(detail = false).recordSuccess("chat", "林晚", "deepseek-v4-flash", messages, "好", 10L, usage)
        val e = capturedEntry()
        val day = Instant.ofEpochMilli(e.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        coVerify(timeout = 2000) { logStatsDao.addCall(day, "deepseek-v4-flash", "chat", false, 100L, 20L, 60L, 40L) }
    }

    @Test
    fun addCallThrows_logStillInserted_andLockReleased() = runBlocking {
        coEvery { logStatsDao.addCall(any(), any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("disk full")
        val svc = service(detail = false)
        svc.recordSuccess("chat", "林晚", "m", messages, "一", 10L, null)
        coVerify(timeout = 2000, exactly = 1) { logDao.insert(any()) }
        svc.recordSuccess("chat", "林晚", "m", messages, "二", 10L, null)
        coVerify(timeout = 2000, exactly = 2) { logDao.insert(any()) }
    }

    @Test
    fun clearAll_clearsBothTables() = runBlocking {
        service(detail = false).clearAll()
        coVerify(exactly = 1) { logDao.deleteAll() }
        coVerify(exactly = 1) { logStatsDao.deleteAll() }
    }

    @Test
    fun trim_deletesStatsOlderThan90Days() = runBlocking {
        val before = LocalDate.now().minusDays(90).toString()
        service(detail = false).enforceRetentionLimit()
        coVerify(timeout = 2000) { logStatsDao.deleteBefore(match { it == before || it == LocalDate.now().minusDays(90).toString() }) }
    }
}
