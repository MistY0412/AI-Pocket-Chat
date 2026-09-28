package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.data.remote.llm.ApiConfigValues
import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.LlmClient
import com.situ.aichat.data.remote.llm.LlmRequestCapture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * T1-6（四期·图纸三 §3.8 / E2 / E3 / E28 / E29）：请求体消毒存库 + 导出文本 + 文件名。
 * 替身文字与 KB 数按 [LogContextFormat] 口径手算（base64 长度 × 3 / 4 / 1024，至少 1）。
 */
class LogReplayRequestTest {

    /** 与 App 注入的 Json 同配置（di/NetworkModule）。 */
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }

    private val imageUrl = "data:image/jpeg;base64," + "A".repeat(4096) // 4096 × 3 / 4 / 1024 = 3 KB
    private val audio = "B".repeat(8192) // 6 KB

    private val mediaRequest = ChatRequestDto(
        model = "claude-sonnet-4-5",
        messages = listOf(
            ChatMessageDto(role = "system", content = "人设"),
            ChatMessageDto(role = "user", contentParts = listOf(ChatContentPart.Text("看这张"), ChatContentPart.ImageUrl(imageUrl), ChatContentPart.InputAudio(audio))),
        ),
        stream = true,
        temperature = 0.8,
    )

    @Test
    fun sanitized_replacesMediaWithPlaceholders_restUntouched() {
        val out = LogReplayRequest.sanitized(mediaRequest)
        assertEquals(
            listOf(ChatContentPart.Text("看这张"), ChatContentPart.Text("[图片 · 约 3 KB]"), ChatContentPart.Text("[语音 · 约 6 KB]")),
            out.messages[1].contentParts,
        )
        assertEquals(mediaRequest.messages[0], out.messages[0])
        assertEquals(mediaRequest.copy(messages = out.messages), out)
    }

    @Test
    fun encodeForStore_noBase64_andOverLimitIsEmpty() {
        val stored = LogReplayRequest.encodeForStore(json, mediaRequest)
        assertTrue(stored.contains("[图片 · 约 3 KB]"))
        assertTrue(stored.contains("[语音 · 约 6 KB]"))
        assertFalse(stored.contains("base64,"))
        assertFalse(stored.contains("AAAAAAAA"))
        assertFalse(stored.contains("BBBBBBBB"))

        val huge = ChatRequestDto(model = "m", messages = listOf(ChatMessageDto(role = "user", content = "字".repeat(LogContextFormat.STORED_TEXT_HARD_LIMIT))), stream = false)
        assertEquals("超 20 万字不存截断的坏 JSON（E3）", "", LogReplayRequest.encodeForStore(json, huge))
    }

    @Test
    fun exportText_emptyRequestJson_isNull() {
        assertNull(LogReplayRequest.exportText(LogEntryEntity(requestJson = ""), 1L))
        // 复核 R1 裁决 T-1：库里坏 JSON 同「导不出」，不抛、不导半截
        assertNull(LogReplayRequest.exportText(LogEntryEntity(requestJson = "{\"model\":"), 1L))
    }

    @Test
    fun exportText_structureAndMeta_fromRealCapturedRequest_noKeyNoBaseUrl() = runBlocking {
        val config = ApiConfigValues(providerType = ApiProviderType.ANTHROPIC, apiKey = "sk-test-secret-123", baseUrl = "https://relay.example.com/v1", modelName = "claude-sonnet-4-5")
        val client = LlmClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"choices":[{"message":{"content":"好"},"finish_reason":"stop"}]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            json,
        )
        val capture = LlmRequestCapture()
        withContext(capture) { client.completion(messages = mediaRequest.messages, config = config) }
        val requestJson = LogReplayRequest.encodeForStore(json, capture.request!!)
        val entry = LogEntryEntity(
            id = 42, timestampMillis = 1_790_555_400_000L, source = "chat", characterName = "林晚", modelName = "claude-sonnet-4-5",
            providerType = "anthropic", requestJson = requestJson,
        )

        val text = LogReplayRequest.exportText(entry, exportedAtMillis = 1_790_600_000_000L)!!
        assertFalse(text.contains("sk-test-secret-123"))
        assertFalse(text.contains("relay.example.com"))
        assertTrue("prettyPrint 四格缩进", text.contains("\n    \"meta\": {"))

        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("meta", "request"), root.keys)
        val meta = root.getValue("meta").jsonObject
        assertEquals(
            listOf("app", "kind", "loggedAtMillis", "exportedAtMillis", "source", "characterName", "modelName", "providerType", "note"),
            meta.keys.toList(),
        )
        assertEquals("AI Pocket Chat", meta.getValue("app").jsonPrimitive.content)
        assertEquals("replayable_request", meta.getValue("kind").jsonPrimitive.content)
        assertEquals(1_790_555_400_000L, meta.getValue("loggedAtMillis").jsonPrimitive.long)
        assertEquals(1_790_600_000_000L, meta.getValue("exportedAtMillis").jsonPrimitive.long)
        assertEquals("chat", meta.getValue("source").jsonPrimitive.content)
        assertEquals("林晚", meta.getValue("characterName").jsonPrimitive.content)
        assertEquals("claude-sonnet-4-5", meta.getValue("modelName").jsonPrimitive.content)
        assertEquals("anthropic", meta.getValue("providerType").jsonPrimitive.content)
        assertEquals("不含 API key 与请求地址；图片 / 语音已换成占位文字。", meta.getValue("note").jsonPrimitive.content)
        assertEquals("request 原样放入", Json.parseToJsonElement(requestJson), root.getValue("request"))
    }

    @Test
    fun exportText_nullProviderType_isJsonNull() {
        val text = LogReplayRequest.exportText(LogEntryEntity(requestJson = "{\"model\":\"m\"}", providerType = null), 1L)!!
        assertEquals(JsonNull, Json.parseToJsonElement(text).jsonObject.getValue("meta").jsonObject.getValue("providerType"))
    }

    @Test
    fun fileName_localTimeAndId() {
        // 北京时间 2026-09-28 08:30:00
        val entry = LogEntryEntity(id = 42, timestampMillis = 1_790_555_400_000L)
        assertEquals("replay-20260928-083000-42.json", LogReplayRequest.fileName(entry, ZoneId.of("Asia/Shanghai")))
        assertEquals("replay-20260928-003000-42.json", LogReplayRequest.fileName(entry, ZoneId.of("UTC")))
    }
}
