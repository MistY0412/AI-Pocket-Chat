package com.situ.aichat.data.remote.llm

import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.data.model.ThinkingBudgetLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1（图纸 2026-09-26-末尾助手消息补空思考 §4 锁定项）：门控 + 补位范围。
 * 期望值按 2026-09-26 真 API 实测反推：只补最后一条仍 400 → 必须全补；不传思考参数也 400 → 门控看「没明确关」；
 * 门控只认官方地址（用户拍板收紧·选了 DeepSeek 却填中转站的不算）。
 */
class DeepSeekReasoningPlaceholderTest {

    private fun config(
        provider: ApiProviderType = ApiProviderType.DEEPSEEK,
        baseUrl: String = "https://api.deepseek.com",
        thinkingModel: Boolean = true,
        level: ThinkingBudgetLevel = ThinkingBudgetLevel.AUTO,
    ) = ApiConfigValues(
        providerType = provider, apiKey = "", baseUrl = baseUrl, modelName = "deepseek-v4-flash",
        thinkingBudgetLevel = level, isThinkingModel = thinkingModel, toolCallingEnabled = true,
    )

    private val deepSeek = config()

    private fun sys(text: String) = ChatMessageDto(role = "system", content = text)
    private fun user(text: String) = ChatMessageDto(role = "user", content = text)
    private fun assistant(text: String, reasoning: String? = null) =
        ChatMessageDto(role = "assistant", content = text, reasoningContent = reasoning)

    // ── 门控 ──

    @Test
    fun 门控_DeepSeek服务商_思考自动或开_生效() {
        assertTrue(DeepSeekReasoningPlaceholder.applies(deepSeek))
        assertTrue(DeepSeekReasoningPlaceholder.applies(config(level = ThinkingBudgetLevel.HIGH)))
    }

    @Test
    fun 门控_OpenAI兼容但指向DeepSeek官方host_生效() {
        assertTrue(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.OPENAI_COMPATIBLE, baseUrl = " https://API.DeepSeek.com/v1 ")))
    }

    @Test
    fun 门控_其他服务商与其他host_不生效() {
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.OPENAI_COMPATIBLE, baseUrl = "https://api.openai.com/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.OPENAI_COMPATIBLE, baseUrl = "https://api.moonshot.cn/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.OPENROUTER, baseUrl = "https://openrouter.ai/api/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.ANTHROPIC, baseUrl = "https://api.anthropic.com/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(provider = ApiProviderType.OPENAI_COMPATIBLE, baseUrl = "不是网址")))
    }

    @Test
    fun 门控_选了DeepSeek但地址是中转站或仿冒域名_不生效() {
        // 2026-09-26 用户拍板收紧：只认请求真的发往官方地址，不看服务商类型。
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(baseUrl = "https://relay.example.com/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(baseUrl = "https://api.deepseek.com.example.com/v1")))
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(baseUrl = "https://example.com/api.deepseek.com")))
    }

    @Test
    fun 门控_思考被明确关掉_不生效() {
        assertFalse(DeepSeekReasoningPlaceholder.applies(config(level = ThinkingBudgetLevel.OFF)))
    }

    @Test
    fun 门控_没标思考模型_不传思考参数_DeepSeek默认在思考_仍生效() {
        assertTrue(DeepSeekReasoningPlaceholder.applies(config(thinkingModel = false)))
        assertTrue(DeepSeekReasoningPlaceholder.applies(config(thinkingModel = false, level = ThinkingBudgetLevel.OFF)))
    }

    // ── 补位范围 ──

    @Test
    fun 最后一条user之后的每条assistant都补空串_system与之前的不动() {
        val messages = listOf(
            sys("人设"), assistant("更早的话"), user("我给你发了个红包～"),
            assistant("好呀"), sys("【时间 · 5分钟后】"), assistant("[系统记录：收下了红包]"), sys("【此刻】"),
        )
        val out = DeepSeekReasoningPlaceholder.fill(messages, deepSeek)
        assertEquals(messages.size, out.size)
        assertNull("user 之前的 assistant 不动", out[1].reasoningContent)
        assertEquals("", out[3].reasoningContent)
        assertEquals("", out[5].reasoningContent)
        assertEquals("除了补的字段，其余逐字不变", messages.map { it.copy(reasoningContent = null) }, out.map { it.copy(reasoningContent = null) })
        assertNull("输入表不被改动", messages[3].reasoningContent)
    }

    @Test
    fun 已带reasoning_content的不动() {
        val messages = listOf(user("在吗"), assistant("嗯", reasoning = "想了想"), assistant("在的"))
        val out = DeepSeekReasoningPlaceholder.fill(messages, deepSeek)
        assertEquals("想了想", out[1].reasoningContent)
        assertEquals("", out[2].reasoningContent)
    }

    @Test
    fun 工具回喂形状_没思考的工具调用那条补空串_tool结果与带思考的不动() {
        val call = RequestToolCallDto(id = "call_1", type = "function", function = RequestToolCallFunctionDto("record_promise", "{}"))
        val noThought = listOf(
            user("明天下午三点我去店里找你"),
            ChatMessageDto(role = "assistant", content = null, toolCalls = listOf(call)),
            ChatMessageDto(role = "tool", content = "已记下", toolCallId = "call_1"),
        )
        val out = DeepSeekReasoningPlaceholder.fill(noThought, deepSeek)
        assertEquals("", out[1].reasoningContent)
        assertEquals(listOf(call), out[1].toolCalls)
        assertNull(out[2].reasoningContent)

        val withThought = noThought.toMutableList().also { it[1] = it[1].copy(reasoningContent = "先记下。") }
        assertSame(withThought, DeepSeekReasoningPlaceholder.fill(withThought, deepSeek))
    }

    @Test
    fun 没有user消息_从头算() {
        val out = DeepSeekReasoningPlaceholder.fill(listOf(sys("人设"), assistant("世界书注入")), deepSeek)
        assertEquals("", out[1].reasoningContent)
    }

    @Test
    fun 末条是user的正常回合_原样返回同一实例() {
        val messages = listOf(sys("人设"), user("早"), assistant("早呀"), user("吃了吗"), sys("【此刻】"))
        assertSame(messages, DeepSeekReasoningPlaceholder.fill(messages, deepSeek))
    }

    @Test
    fun 门控不过_原样返回同一实例() {
        val messages = listOf(user("在吗"), assistant("[系统记录：收下了红包]"))
        assertSame(messages, DeepSeekReasoningPlaceholder.fill(messages, config(level = ThinkingBudgetLevel.OFF)))
        assertSame(messages, DeepSeekReasoningPlaceholder.fill(messages, config(provider = ApiProviderType.OPENAI_COMPATIBLE, baseUrl = "https://api.openai.com/v1")))
    }
}
