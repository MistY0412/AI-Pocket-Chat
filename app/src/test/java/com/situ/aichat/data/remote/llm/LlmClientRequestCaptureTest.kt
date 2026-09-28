package com.situ.aichat.data.remote.llm

import android.util.Log
import com.situ.aichat.data.model.ApiProviderType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T2-2（时间感知四期·图纸三 §3.2 / E4–E7）：「实际发出去的样子」捕获的**接线**——断言一律落在真实发出的报文上
 * （手法照 [LlmClientProviderAdaptWiringTest]）：捕获对象按客户端同一 Json 编码后与报文逐字相同；只留首发；
 * 放不放捕获元素，报文逐字节不变（K1）。
 */
class LlmClientRequestCaptureTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val bodies = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        bodies.clear()
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    private fun cfg(type: ApiProviderType, baseUrl: String, model: String) =
        ApiConfigValues(providerType = type, apiKey = "sk-secret-key", baseUrl = baseUrl, modelName = model)

    private val deepseek = cfg(ApiProviderType.DEEPSEEK, "https://api.deepseek.com/v1", "deepseek-v4-flash")
    private val claude = cfg(ApiProviderType.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-5")

    private val paramRejection =
        """{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. """ +
            """Use 'max_completion_tokens' instead.","type":"invalid_request_error","param":"max_tokens"}}"""

    private fun completionJson(content: String, finishReason: String = "stop") =
        """{"choices":[{"message":{"content":"$content"},"finish_reason":"$finishReason"}]}"""

    private fun sseBody(content: String) =
        "data: {\"choices\":[{\"delta\":{\"content\":\"$content\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: [DONE]\n\n"

    private fun clientResponding(vararg responses: Pair<Int, String>): LlmClient {
        val queue = responses.toMutableList()
        val ok = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            bodies.add(Buffer().also { req.body?.writeTo(it) }.readUtf8())
            val (code, body) = queue.removeAt(0)
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "Bad Request")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return LlmClient(ok, json)
    }

    private fun sys(t: String) = ChatMessageDto(role = "system", content = t)
    private fun user(t: String) = ChatMessageDto(role = "user", content = t)
    private fun asst(t: String) = ChatMessageDto(role = "assistant", content = t)

    /** 前置 1 条 + 中途 1 个时间标记 + 末尾 2 张卡（非白名单改写：leading 0 / mid 1 / tail 2）。 */
    private val chat = listOf(
        sys("P"), user("u1"), asst("a1"),
        sys("【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"), user("u2"),
        sys("C1"), sys("C2"),
    )

    private fun encode(request: ChatRequestDto) = json.encodeToString(ChatRequestDto.serializer(), request)

    @Test
    fun `流式_DeepSeek_捕获与报文逐字相同_白名单adaptation为null`() = runBlocking {
        val client = clientResponding(200 to sseBody("嗯"))
        val capture = LlmRequestCapture()
        withContext(capture) { client.streamChat(messages = chat, config = deepseek).toList() }
        assertEquals(bodies.single(), encode(assertNotNullAndGet(capture.request)))
        assertEquals("deepseek", capture.providerTypeRaw)
        assertNull("白名单原样发送", capture.adaptation)
        assertTrue(capture.request!!.stream)
    }

    @Test
    fun `非流式_Claude_捕获与报文逐字相同_三计数等于adapt`() = runBlocking {
        val client = clientResponding(200 to completionJson("好"))
        val capture = LlmRequestCapture()
        withContext(capture) { client.completion(messages = chat, config = claude) }
        assertEquals(bodies.single(), encode(assertNotNullAndGet(capture.request)))
        assertEquals("anthropic", capture.providerTypeRaw)
        val expected = ProviderMessageAdapter.adapt(chat)
        assertEquals(SendAdaptationCounts(expected.leadingMerged, expected.midMerged, expected.tailMerged), capture.adaptation)
        assertEquals("手算：前置 1 条不合并 / 中途 1 / 末尾 2", SendAdaptationCounts(0, 1, 2), capture.adaptation)
    }

    @Test
    fun `流式首发400自愈重发_捕获仍是首发`() = runBlocking {
        val client = clientResponding(400 to paramRejection, 200 to sseBody("她推开门"))
        val capture = LlmRequestCapture()
        withContext(capture) { client.streamChat(messages = chat, config = claude, maxTokens = 1_000).toList() }
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], encode(capture.request!!))
        assertEquals(1_000, capture.request!!.maxTokens)
        assertNull("未改名的 max_tokens 版", capture.request!!.maxCompletionTokens)
        assertTrue(bodies[1].contains("\"max_completion_tokens\":1000"))
    }

    @Test
    fun `非流式首发400换名_撞限升额_捕获仍是首发`() = runBlocking {
        val client = clientResponding(400 to paramRejection, 200 to completionJson("半截", "length"), 200 to completionJson("完整"))
        val capture = LlmRequestCapture()
        val result = withContext(capture) { client.completion(messages = chat, config = claude, maxTokens = 1_000) }
        assertEquals("完整", result)
        assertEquals(3, bodies.size)
        assertEquals(bodies[0], encode(capture.request!!))
        assertEquals(1_000, capture.request!!.maxTokens)
    }

    @Test
    fun `放不放捕获元素_报文逐字节不变_K1`() = runBlocking {
        val plain = clientResponding(200 to sseBody("嗯"), 200 to completionJson("好"))
        plain.streamChat(messages = chat, config = claude, maxTokens = 500, temperature = 0.8).toList()
        plain.completion(messages = chat, config = claude, maxTokens = 500, temperature = 0.8)
        val withoutCapture = bodies.toList()
        bodies.clear()

        val captured = clientResponding(200 to sseBody("嗯"), 200 to completionJson("好"))
        withContext(LlmRequestCapture()) { captured.streamChat(messages = chat, config = claude, maxTokens = 500, temperature = 0.8).toList() }
        withContext(LlmRequestCapture()) { captured.completion(messages = chat, config = claude, maxTokens = 500, temperature = 0.8) }
        assertEquals(withoutCapture, bodies)
        assertTrue("请求体本来就不含 key", bodies.none { it.contains("sk-secret-key") })
    }

    private fun assertNotNullAndGet(request: ChatRequestDto?): ChatRequestDto {
        assertNotNull("应捕获到首发请求", request)
        return request!!
    }
}
