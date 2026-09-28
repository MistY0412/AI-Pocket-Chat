package com.situ.aichat.diagnostics

import com.situ.aichat.data.remote.llm.LlmError
import com.situ.aichat.voice.VoiceCallTurnBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * T1-1（四期·图纸三 §3.6 / E25）：失败分类规则逐条。期望值从 §3.6 规则表独立反推；[LlmError] 的中文消息在测试里
 * **重新打字**为字面量，另以 `== LlmError.X.message` 双保险钉住（文案改了这里会红，提醒同步分类规则）。
 */
class LlmFailureClassifierTest {

    private fun http(code: Int, body: String? = null) = LlmFailureClassifier.classify(LlmError.Http(code, body))
    private fun text(s: String) = LlmFailureClassifier.classifyText(s)

    // —— raw 值（REDLINES §1：改 raw = 老记录分类失效） ——

    @Test
    fun rawValues_areLocked_andRoundTrip() {
        assertEquals(
            listOf("timeout", "bad_format", "rate_limited", "content_blocked", "invalid_key", "insufficient_balance", "network", "other"),
            LlmFailureKind.entries.map { it.raw },
        )
        for (k in LlmFailureKind.entries) assertEquals(k, LlmFailureKind.fromRaw(k.raw))
        assertNull(LlmFailureKind.fromRaw(null))
        assertNull(LlmFailureKind.fromRaw("nope"))
    }

    // —— Http：状态码 ——

    @Test
    fun http_byStatusCode() {
        assertEquals(LlmFailure(LlmFailureKind.INVALID_KEY, 401), http(401))
        assertEquals(LlmFailure(LlmFailureKind.INSUFFICIENT_BALANCE, 402), http(402))
        assertEquals(LlmFailure(LlmFailureKind.INVALID_KEY, 403), http(403))
        assertEquals(LlmFailure(LlmFailureKind.TIMEOUT, 408), http(408))
        assertEquals(LlmFailure(LlmFailureKind.RATE_LIMITED, 429), http(429))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 400), http(400))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 404), http(404))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 405), http(405))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 413), http(413))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 415), http(415))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, 422), http(422))
        assertEquals(LlmFailure(LlmFailureKind.OTHER, 500), http(500))
        assertEquals(LlmFailure(LlmFailureKind.TIMEOUT, 504), http(504))
        assertEquals(LlmFailure(LlmFailureKind.OTHER, 418), http(418))
    }

    @Test
    fun http_bodyKeywords_beatStatusCode_inBalanceContentKeyOrder() {
        assertEquals(LlmFailure(LlmFailureKind.INSUFFICIENT_BALANCE, 429), http(429, "{\"error\":{\"code\":\"insufficient_quota\"}}"))
        assertEquals(LlmFailure(LlmFailureKind.CONTENT_BLOCKED, 400), http(400, "finish: content_filter"))
        assertEquals(LlmFailure(LlmFailureKind.INVALID_KEY, 400), http(400, "Invalid API Key provided"))
        assertEquals("余额先于内容", LlmFailureKind.INSUFFICIENT_BALANCE, http(400, "余额不足；内容涉及敏感").kind)
        assertEquals("内容先于 key", LlmFailureKind.CONTENT_BLOCKED, http(401, "moderation / unauthorized").kind)
        assertEquals("限流词不参与有码判定", LlmFailureKind.BAD_FORMAT, http(400, "rate limit").kind)
    }

    // —— 类型 ——

    @Test
    fun llmErrorTypes() {
        assertEquals(LlmFailure(LlmFailureKind.TIMEOUT, null), LlmFailureClassifier.classify(LlmError.Timeout))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), LlmFailureClassifier.classify(LlmError.DecodingError))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), LlmFailureClassifier.classify(LlmError.InvalidResponse))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), LlmFailureClassifier.classify(LlmError.InvalidUrl))
        assertEquals(LlmFailure(LlmFailureKind.RATE_LIMITED, null), LlmFailureClassifier.classify(LlmError.Stream("upstream: Rate Limit reached")))
        assertEquals(LlmFailure(LlmFailureKind.OTHER, null), LlmFailureClassifier.classify(LlmError.Stream("something odd")))
    }

    @Test
    fun javaIoTypes() {
        assertEquals(LlmFailure(LlmFailureKind.TIMEOUT, null), LlmFailureClassifier.classify(SocketTimeoutException("read")))
        assertEquals(LlmFailure(LlmFailureKind.TIMEOUT, null), LlmFailureClassifier.classify(InterruptedIOException("timeout")))
        assertEquals("不含 timeout 的中断落回 IOException = 网络", LlmFailure(LlmFailureKind.NETWORK, null), LlmFailureClassifier.classify(InterruptedIOException("interrupted")))
        assertEquals(LlmFailure(LlmFailureKind.NETWORK, null), LlmFailureClassifier.classify(UnknownHostException("api.example.com")))
    }

    @Test
    fun otherThrowable_fallsBackToText_orSimpleName() {
        assertEquals("老 FirstStreamEventTimeout", LlmFailureKind.TIMEOUT, LlmFailureClassifier.classify(VoiceCallTurnBudget.FirstStreamEventTimeout(20_000)).kind)
        assertEquals(LlmFailureKind.OTHER, LlmFailureClassifier.classify(IllegalStateException()).kind)
        assertEquals(LlmFailureKind.RATE_LIMITED, LlmFailureClassifier.classify(IllegalStateException("限流中")).kind)
    }

    // —— classifyText：LlmError 的中文消息原文 ——

    @Test
    fun classifyText_llmErrorMessages() {
        val decoding = "解析响应失败，模型名可能不正确。"
        val invalidResponse = "服务器返回了无效响应。"
        val invalidUrl = "API URL 无效，请检查配置。"
        val timeout = "请求超时，请检查网络连接。"
        val stream = "流式错误：connection reset by peer"
        assertEquals(decoding, LlmError.DecodingError.message)
        assertEquals(invalidResponse, LlmError.InvalidResponse.message)
        assertEquals(invalidUrl, LlmError.InvalidUrl.message)
        assertEquals(timeout, LlmError.Timeout.message)
        assertEquals(stream, LlmError.Stream("connection reset by peer").message)

        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), text(decoding))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), text(invalidResponse))
        assertEquals(LlmFailure(LlmFailureKind.BAD_FORMAT, null), text(invalidUrl))
        assertEquals("「超时」先于「网络」", LlmFailure(LlmFailureKind.TIMEOUT, null), text(timeout))
        assertEquals(LlmFailure(LlmFailureKind.NETWORK, null), text(stream))
    }

    @Test
    fun classifyText_statusCodeInHead() {
        val auth = "鉴权失败 (401)，请检查 API Key 是否正确。"
        assertEquals(auth, LlmError.Http(401, null).message)
        assertEquals(LlmFailure(LlmFailureKind.INVALID_KEY, 401), text(auth))
        assertEquals(LlmFailure(LlmFailureKind.INSUFFICIENT_BALANCE, 402), text("HTTP 错误：402 - payment needed"))
        assertEquals(LlmFailure(LlmFailureKind.OTHER, 503), text("服务器错误 (503)，服务暂时不可用。"))
        assertEquals("尾部关键词仍参与有码判定", LlmFailure(LlmFailureKind.INSUFFICIENT_BALANCE, 429), text("请求过于频繁 (429)，请稍后再试。 - insufficient_quota"))
        assertEquals("「 - 」之后的括号数字不当状态码", LlmFailure(LlmFailureKind.OTHER, null), text("请求失败 - upstream said (500)"))
    }

    @Test
    fun classifyText_wordsOrder_andNoMatch() {
        assertEquals("限流先于超时", LlmFailureKind.RATE_LIMITED, text("rate limit then timed out").kind)
        assertEquals("超时先于网络", LlmFailureKind.TIMEOUT, text("Failed to connect: timeout").kind)
        assertEquals(LlmFailureKind.NETWORK, text("Unable to resolve host \"x\"").kind)
        assertEquals("格式前缀大小写敏感（原文 startsWith）", LlmFailureKind.OTHER, text("api url 无效").kind)
        assertEquals(LlmFailure(LlmFailureKind.OTHER, null), text(""))
    }

    // —— kindOfRow ——

    @Test
    fun kindOfRow_threeBranches() {
        assertNull(LlmFailureClassifier.kindOfRow(isSuccess = true, failureKindRaw = "timeout", errorMessage = "x"))
        assertEquals(LlmFailureKind.RATE_LIMITED, LlmFailureClassifier.kindOfRow(false, "rate_limited", "请求超时，请检查网络连接。"))
        assertEquals("v50 老记录读时现算", LlmFailureKind.TIMEOUT, LlmFailureClassifier.kindOfRow(false, null, "请求超时，请检查网络连接。"))
        assertEquals(LlmFailureKind.OTHER, LlmFailureClassifier.kindOfRow(false, null, null))
    }
}
