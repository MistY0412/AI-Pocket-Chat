package com.situ.aichat.diagnostics

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.LlmRequestCapture
import com.situ.aichat.data.remote.llm.RequestToolCallDto
import com.situ.aichat.data.remote.llm.RequestToolCallFunctionDto
import com.situ.aichat.data.remote.llm.SendAdaptationCounts
import com.situ.aichat.prompt.ContextSegment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * T1-3（四期·图纸三 §3.5 / E30）：请求形状 + 指纹 + 改写计数编码。指纹用 SHA-256 公开测试向量钉死；
 * token 期望按 [com.situ.aichat.prompt.TokenEstimator] 口径手算（CJK 1 字 1 token、其余 4 字符 1 token、至少 1）。
 */
class LogRequestShapeTest {

    /** 与 App 注入的 Json 同配置（di/NetworkModule）。 */
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }

    private fun msg(role: String, content: String) = ChatMessageDto(role = role, content = content)

    @Test
    fun fingerprintOf_matchesSha256PublicVectors_first16Hex() {
        assertEquals("ba7816bf8f01cfea", ContextSegment.fingerprintOf("abc"))
        assertEquals("e3b0c44298fc1c14", ContextSegment.fingerprintOf(""))
    }

    /** 复核 R1：消息指纹原料格式钉死（图纸三 §3.5「角色␀正文␀思考␀工具调用id␀工具调用」）——金标 = SHA-256("user\0abc\0\0\0") 前 16 位（python 独立复算）。 */
    @Test
    fun messageFingerprint_sourceFormatPinned() {
        assertEquals("773d7c06cc589e80", ContextSegment.messageFingerprintOf(msg("user", "abc")))
        assertEquals(ContextSegment.messageFingerprintOf(msg("user", "abc")), LogRequestShape.of(listOf(msg("user", "abc"))).fingerprints.single())
    }

    @Test
    fun roles_letters() {
        val s = LogRequestShape.of(listOf(msg("system", "a"), msg("user", "b"), msg("assistant", "c"), msg("tool", "d"), msg("developer", "e")))
        assertEquals("suat?", s.roles)
        assertEquals(5, s.fingerprints.size)
        assertEquals(5, s.tokens.size)
    }

    @Test
    fun tokens_estimatedFromReadableBody() {
        val s = LogRequestShape.of(listOf(msg("user", "你好"), msg("assistant", "hello world")))
        assertEquals(listOf(2, 2), s.tokens)
    }

    @Test
    fun timeMarkers_onlySystemStartingWithOpen() {
        val marker = "【时间 · 9月28日 周一 晚上 · 距离上条消息过去了约 3 小时】"
        val s = LogRequestShape.of(
            listOf(
                msg("system", "人设"),
                msg("user", "【时间 · 我自己打的】"),
                msg("system", marker),
                msg("system", "前面有字【时间 · x】"),
                msg("assistant", "嗯"),
            ),
        )
        assertEquals(listOf(LogTimeMarker(2, marker)), s.timeMarkers)
    }

    @Test
    fun fingerprint_sameKbDifferentImages_differ_butTokensEqual() {
        val a = ChatMessageDto(role = "user", contentParts = listOf(ChatContentPart.Text("看"), ChatContentPart.ImageUrl("data:image/jpeg;base64," + "A".repeat(4096))))
        val b = ChatMessageDto(role = "user", contentParts = listOf(ChatContentPart.Text("看"), ChatContentPart.ImageUrl("data:image/jpeg;base64," + "B".repeat(4096))))
        val s = LogRequestShape.of(listOf(a, b))
        assertEquals("可读替身一样（同 KB）", LogContextFormat.readableBody(a), LogContextFormat.readableBody(b))
        assertEquals(s.tokens[0], s.tokens[1])
        assertNotEquals(s.fingerprints[0], s.fingerprints[1])
    }

    @Test
    fun fingerprint_coversRoleReasoningToolCallsAndToolCallId() {
        val base = ChatMessageDto(role = "assistant", content = "好")
        val fp = { m: ChatMessageDto -> LogRequestShape.of(listOf(m)).fingerprints.single() }
        val call = RequestToolCallDto("c1", "function", RequestToolCallFunctionDto("record_promise", "{}"))
        assertNotEquals(fp(base), fp(base.copy(role = "user")))
        assertNotEquals(fp(base), fp(base.copy(reasoningContent = "想想")))
        assertNotEquals(fp(base), fp(base.copy(toolCalls = listOf(call))))
        assertNotEquals(fp(base.copy(toolCalls = listOf(call))), fp(base.copy(toolCalls = listOf(call.copy(function = RequestToolCallFunctionDto("record_promise", "{\"a\":1}"))))))
        assertNotEquals(fp(base), fp(base.copy(toolCallId = "c1")))
        assertEquals("同内容同指纹", fp(base), fp(ChatMessageDto(role = "assistant", content = "好")))
    }

    @Test
    fun encodeDecode_roundTrip_andBadInputsNull() {
        val s = LogRequestShape.of(listOf(msg("system", "【时间 · 今天】"), msg("user", "在吗")))
        assertEquals(s, LogRequestShape.decode(json, LogRequestShape.encode(json, s)))
        assertNull(LogRequestShape.decode(json, ""))
        assertNull(LogRequestShape.decode(json, "{bad"))
    }

    @Test
    fun sendAdaptation_encode_threeStates() {
        assertEquals("", LogSendAdaptation.encode(json, null))
        assertEquals("没捕获到请求", "", LogSendAdaptation.encode(json, LlmRequestCapture()))

        val req = ChatRequestDto(model = "m", messages = emptyList(), stream = true)
        val asIs = LlmRequestCapture().apply { offer(req, "deepseek", null) }
        assertEquals("{\"asIs\":true}", LogSendAdaptation.encode(json, asIs))

        val adapted = LlmRequestCapture().apply { offer(req, "anthropic", SendAdaptationCounts(1, 2, 0)) }
        assertEquals(LogSendAdaptation(asIs = false, leadingMerged = 1, midMerged = 2, tailMerged = 0), LogSendAdaptation.decode(json, LogSendAdaptation.encode(json, adapted)))
        assertNull(LogSendAdaptation.decode(json, ""))
        assertNull(LogSendAdaptation.decode(json, "nope"))
    }

    @Test
    fun capture_keepsOnlyFirstOffer() {
        val first = ChatRequestDto(model = "first", messages = emptyList(), stream = true)
        val second = ChatRequestDto(model = "second", messages = emptyList(), stream = true)
        val c = LlmRequestCapture()
        c.offer(first, "deepseek", null)
        c.offer(second, "anthropic", SendAdaptationCounts(1, 1, 1))
        assertSame(first, c.request)
        assertEquals("deepseek", c.providerTypeRaw)
        assertNull(c.adaptation)
    }

    @Test
    fun contextSegment_oldJsonWithoutFingerprint_decodes_newJsonRoundTrips() {
        val serializer = ListSerializer(ContextSegment.serializer())
        val old = "[{\"name\":\"核心规则\",\"charCount\":10,\"estimatedTokens\":5,\"position\":\"prefix\"}]"
        assertEquals(listOf(ContextSegment("核心规则", null, 10, 5, "prefix", null)), json.decodeFromString(serializer, old))
        val fresh = listOf(ContextSegment("人设", "persona", 4, 4, "prefix", ContextSegment.fingerprintOf("人设正文")))
        assertEquals(fresh, json.decodeFromString(serializer, json.encodeToString(serializer, fresh)))
    }
}
