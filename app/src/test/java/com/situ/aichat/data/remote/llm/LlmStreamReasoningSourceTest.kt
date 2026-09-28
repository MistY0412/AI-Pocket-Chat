package com.situ.aichat.data.remote.llm

import android.util.Log
import com.situ.aichat.data.model.ApiProviderType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * T2（图纸 2026-09-26-工具回喂回传思考内容）：真 [LlmClient.streamChat] 吃固定 SSE 报文（OkHttp 拦截器·零真网络，
 * 手法同 [LlmStreamLivenessTest]），验思考片段的**来源旗标**：`reasoning_content` 字段 → true（要回传）；
 * OpenRouter `reasoning` 字段 / 正文内联 `<think>` → false（不回传）。思考文本、正文、工具增量与改前一致。
 */
class LlmStreamReasoningSourceTest {

    private val config = ApiConfigValues(
        providerType = ApiProviderType.DEEPSEEK,
        apiKey = "k",
        baseUrl = "https://example.test",
        modelName = "deepseek-v4-flash",
        isThinkingModel = true,
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    private fun stream(vararg dataLines: String): List<StreamToken> {
        val body = buildString {
            dataLines.forEach { appendLine("data: $it"); appendLine() }
            append("data: [DONE]")
        }
        val ok = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }.build()
        return runBlocking {
            LlmClient(ok, Json { ignoreUnknownKeys = true })
                .streamChat(messages = listOf(ChatMessageDto(role = "user", content = "喂")), config = config)
                .toList()
        }
    }

    @Test
    fun `reasoning_content字段_旗标为真_文本原样`() {
        val tokens = stream(
            """{"choices":[{"delta":{"reasoning_content":"先想"}}]}""",
            """{"choices":[{"delta":{"reasoning_content":"一下"}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"record_promise","arguments":"{}"}}]}}]}""",
        )
        assertEquals(
            listOf(
                StreamToken.Reasoning("先想", fromReasoningContent = true),
                StreamToken.Reasoning("一下", fromReasoningContent = true),
                StreamToken.ToolCallDelta(ToolCallChunk(index = 0, id = "call_1", functionName = "record_promise", argumentChunk = "{}")),
            ),
            tokens,
        )
    }

    @Test
    fun `OpenRouter的reasoning字段_旗标为假`() {
        val tokens = stream("""{"choices":[{"delta":{"reasoning":"想想"}}]}""", """{"choices":[{"delta":{"content":"好"}}]}""")
        assertEquals(listOf(StreamToken.Reasoning("想想", fromReasoningContent = false), StreamToken.Content("好")), tokens)
    }

    @Test
    fun `内联think标签_旗标为假`() {
        val tokens = stream("""{"choices":[{"delta":{"content":"<think>想想</think>好"}}]}""")
        assertEquals(listOf(StreamToken.Reasoning("想想", fromReasoningContent = false), StreamToken.Content("好")), tokens)
    }

    @Test
    fun `两字段同帧_取reasoning_content且旗标为真`() {
        // 既有优先级 reasoning_content ?: reasoning 不变；旗标跟着「实际取到的那一路」走。
        val tokens = stream("""{"choices":[{"delta":{"reasoning_content":"甲","reasoning":"乙"}}]}""")
        assertEquals(listOf(StreamToken.Reasoning("甲", fromReasoningContent = true)), tokens)
    }
}
