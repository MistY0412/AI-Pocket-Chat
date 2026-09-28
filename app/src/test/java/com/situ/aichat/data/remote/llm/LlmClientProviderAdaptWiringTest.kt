package com.situ.aichat.data.remote.llm

import android.util.Log
import com.situ.aichat.data.model.ApiProviderType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T2-1（时间感知四期·图纸一 §3.2 / §3.2b·E1 / E2 / E10 / E11 / E42）：发送前改写与会话头的**接线**。
 *
 * 纯函数测试（[ProviderMessageAdapterTest] / [ProviderFamilyTest]）证不了「某一跳漏传 sessionKey」或
 * 「重试那发绕过了改写」——断言一律落在真实发出的报文与请求头上。手法照搬 [LlmClientDialectWiringTest]。
 */
class LlmClientProviderAdaptWiringTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val bodies = mutableListOf<String>()
    private val headers = mutableListOf<Headers>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        bodies.clear()
        headers.clear()
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    private fun cfg(type: ApiProviderType, baseUrl: String, model: String) =
        ApiConfigValues(providerType = type, apiKey = "k", baseUrl = baseUrl, modelName = model)

    private val deepseek = cfg(ApiProviderType.DEEPSEEK, "https://api.deepseek.com/v1", "deepseek-v4-flash")
    private val claude = cfg(ApiProviderType.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-5")
    private val openRouterClaude = cfg(ApiProviderType.OPENROUTER, "https://openrouter.ai/api/v1", "anthropic/claude-sonnet-4-5")
    private val grok = cfg(ApiProviderType.OPENAI_COMPATIBLE, "https://api.x.ai/v1", "grok-4")
    private val miniMax = cfg(ApiProviderType.MINIMAX, "https://api.minimaxi.com/v1", "MiniMax-M2")

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
            headers.add(req.headers)
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
    private fun asst(t: String, reasoning: String? = null) = ChatMessageDto(role = "assistant", content = t, reasoningContent = reasoning)

    /** 典型聊天形状：前置 1 条 + 中途 1 个时间标记 + 末尾 2 张卡。 */
    private val chat = listOf(
        sys("P"), user("u1"), asst("a1", reasoning = "想了想"),
        sys("【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"), user("u2"),
        sys("C1"), sys("C2"),
    )

    /** 手算改写结果（§3.2 E2 形状·文本重新打字）。 */
    private val note = "（说明：对方消息开头或结尾用【系统说明】框起来的内容，是 App 附上的时间与规则信息，不是对方打的字，不要回应它本身，也不要在回复里提到它。）"
    private val adaptedChat = listOf(
        sys("P\n\n$note"), user("u1"), asst("a1", reasoning = "想了想"),
        user("【系统说明】【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】【/系统说明】\nu2\n\n【系统说明】\nC1\n\nC2\n【/系统说明】"),
    )

    private fun messagesOf(body: String) = json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
    private fun encoded(list: List<ChatMessageDto>) =
        json.parseToJsonElement(json.encodeToString(ListSerializer(ChatMessageDto.serializer()), list)).jsonArray

    @Test
    fun `E1_DeepSeek官方_请求体messages与输入逐字相同`() = runBlocking {
        val client = clientResponding(200 to completionJson("好"))
        client.completion(messages = chat, config = deepseek)
        assertEquals(encoded(chat), messagesOf(bodies.single()))
    }

    @Test
    fun `E2_Claude_请求体为改写形状`() = runBlocking {
        val client = clientResponding(200 to sseBody("嗯"))
        client.streamChat(messages = chat, config = claude).toList()
        assertEquals(encoded(adaptedChat), messagesOf(bodies.single()))
    }

    @Test
    fun `E10_OpenRouter带sessionKey_加x-session-id`() = runBlocking {
        val client = clientResponding(200 to sseBody("嗯"))
        client.streamChat(messages = chat, config = openRouterClaude, sessionKey = "conv-1").toList()
        assertEquals("apc-conv-1", headers.single()["x-session-id"])
        assertNull(headers.single()["x-grok-conv-id"])
    }

    @Test
    fun `E10_xAI带sessionKey_加x-grok-conv-id_且白名单不改写`() = runBlocking {
        val client = clientResponding(200 to completionJson("好"))
        client.completion(messages = chat, config = grok, sessionKey = "conv-2")
        assertEquals("apc-conv-2", headers.single()["x-grok-conv-id"])
        assertNull(headers.single()["x-session-id"])
        assertEquals(encoded(chat), messagesOf(bodies.single()))
    }

    @Test
    fun `E9_不传sessionKey_无会话头`() = runBlocking {
        val client = clientResponding(200 to sseBody("嗯"), 200 to completionJson("好"))
        client.streamChat(messages = chat, config = openRouterClaude).toList()
        client.completion(messages = chat, config = grok)
        headers.forEach {
            assertNull(it["x-session-id"])
            assertNull(it["x-grok-conv-id"])
        }
    }

    @Test
    fun `E11_流式首调400换名重试_第二发头与改写仍在`() = runBlocking {
        val client = clientResponding(400 to paramRejection, 200 to sseBody("她推开门"))
        val contents = client.streamChat(messages = chat, config = openRouterClaude, maxTokens = 1_000, sessionKey = "conv-3")
            .filterIsInstance<StreamToken.Content>().toList()
        assertEquals("她推开门", contents.joinToString("") { it.text })
        assertEquals(2, bodies.size)
        assertTrue(bodies[1], bodies[1].contains("\"max_completion_tokens\":1000"))
        for (i in 0..1) {
            assertEquals("第 ${i + 1} 发", "apc-conv-3", headers[i]["x-session-id"])
            assertEquals("第 ${i + 1} 发", encoded(adaptedChat), messagesOf(bodies[i]))
        }
    }

    @Test
    fun `E11_非流式换名后撞限升额_三发都带头且都改写`() = runBlocking {
        val client = clientResponding(
            400 to paramRejection,
            200 to completionJson("半截", "length"),
            200 to completionJson("完整"),
        )
        val result = client.completion(messages = chat, config = openRouterClaude, maxTokens = 1_000, sessionKey = "conv-4")
        assertEquals("完整", result)
        assertEquals(3, bodies.size)
        for (i in 0..2) {
            assertEquals("第 ${i + 1} 发", "apc-conv-4", headers[i]["x-session-id"])
            assertEquals("第 ${i + 1} 发", encoded(adaptedChat), messagesOf(bodies[i]))
        }
    }

    @Test
    fun `E42_MiniMax_先改写后无reasoning_content`() = runBlocking {
        val client = clientResponding(200 to completionJson("好"))
        client.completion(messages = chat, config = miniMax, sessionKey = "conv-5")
        val expected = adaptedChat.map { it.copy(reasoningContent = null) }
        assertEquals(encoded(expected), messagesOf(bodies.single()))
        assertFalse(bodies.single(), bodies.single().contains("reasoning_content"))
        assertNull("MiniMax 不在会话头名单", headers.single()["x-session-id"])
    }
}
