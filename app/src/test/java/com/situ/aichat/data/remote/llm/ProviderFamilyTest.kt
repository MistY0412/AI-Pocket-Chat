package com.situ.aichat.data.remote.llm

import com.situ.aichat.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-1（时间感知四期·图纸一 §3.1 / §3.2b）：白名单判例表 + 会话头选择。
 * 判例逐条取自图纸 §3.1「判例（T1 必测）」，期望从规格独立写出。
 */
class ProviderFamilyTest {

    private fun cfg(type: ApiProviderType, model: String, baseUrl: String = "https://example.test/v1") =
        ApiConfigValues(providerType = type, apiKey = "k", baseUrl = baseUrl, modelName = model)

    private val compat = ApiProviderType.OPENAI_COMPATIBLE
    private val openRouter = ApiProviderType.OPENROUTER

    @Test
    fun `白名单判例_原位生效的模型族为true`() {
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(compat, "deepseek-v4-flash")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "deepseek/deepseek-v4-flash:free")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "z-ai/glm-4.6")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "x-ai/grok-4")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "moonshotai/kimi-k2")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(compat, "gpt-5")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "openai/gpt-4o")))
        assertTrue(ProviderFamily.keepsSystemInPlace(cfg(compat, "o3-mini")))
        assertTrue("DEEPSEEK 服务商恒不改写", ProviderFamily.keepsSystemInPlace(cfg(ApiProviderType.DEEPSEEK, "whatever")))
    }

    @Test
    fun `非白名单判例_为false`() {
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(compat, "gpt-oss-120b")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(openRouter, "anthropic/claude-sonnet-4-5")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(ApiProviderType.ANTHROPIC, "claude-sonnet-4-5")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(ApiProviderType.GEMINI, "gemini-2.5-pro")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(compat, "qwen3-max")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(compat, "qwen3.5-plus")))
        assertFalse(ProviderFamily.keepsSystemInPlace(cfg(ApiProviderType.MINIMAX, "MiniMax-M2")))
        assertFalse("GEMINI 服务商配 deepseek 模型名的怪配置仍改写", ProviderFamily.keepsSystemInPlace(cfg(ApiProviderType.GEMINI, "deepseek-v4-flash")))
        assertFalse("空模型名认不出 → 改写", ProviderFamily.keepsSystemInPlace(cfg(compat, "")))
    }

    @Test
    fun `会话头_sessionKey为null或空白_不加任何头`() {
        val c = cfg(openRouter, "x", "https://openrouter.ai/api/v1")
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(c, null))
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(c, ""))
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(c, "   "))
    }

    @Test
    fun `会话头_OpenRouter服务商或host_加x-session-id`() {
        assertEquals(
            mapOf("x-session-id" to "apc-abc"),
            ProviderFamily.sessionHeaders(cfg(openRouter, "x", "https://proxy.example/v1"), "abc"),
        )
        assertEquals(
            "自定义 OPENAI_COMPATIBLE 指向 openrouter.ai 也认",
            mapOf("x-session-id" to "apc-abc"),
            ProviderFamily.sessionHeaders(cfg(compat, "x", "https://OpenRouter.ai/api/v1"), " abc "),
        )
    }

    @Test
    fun `会话头_api点x点ai_加x-grok-conv-id`() {
        assertEquals(
            mapOf("x-grok-conv-id" to "apc-uuid-1"),
            ProviderFamily.sessionHeaders(cfg(compat, "grok-4", "https://api.x.ai/v1"), "uuid-1"),
        )
    }

    @Test
    fun `会话头_其他host_空表`() {
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(cfg(compat, "gpt-5", "https://api.openai.com/v1"), "abc"))
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(cfg(ApiProviderType.DEEPSEEK, "d", "https://api.deepseek.com/v1"), "abc"))
        assertEquals(emptyMap<String, String>(), ProviderFamily.sessionHeaders(cfg(compat, "x", "not a url ::"), "abc"))
    }

    @Test
    fun `会话头_超长key截到256字`() {
        val longKey = "k".repeat(400)
        val v = ProviderFamily.sessionHeaders(cfg(openRouter, "x"), longKey).getValue("x-session-id")
        assertEquals(256, v.length)
        assertEquals("apc-" + "k".repeat(252), v)
    }

    @Test
    fun `自动缓存_Claude兼容层与经OpenRouter的Claude为false_其余true`() {
        // 四期·图纸二 §3.4 / T1-4：Claude 要显式缓存标记（App 不发）→ 不会因省钱模式省钱。
        assertFalse(ProviderFamily.cachesPromptAutomatically(cfg(ApiProviderType.ANTHROPIC, "claude-sonnet-4-5")))
        assertFalse("ANTHROPIC 服务商无论模型名", ProviderFamily.cachesPromptAutomatically(cfg(ApiProviderType.ANTHROPIC, "whatever")))
        assertFalse(ProviderFamily.cachesPromptAutomatically(cfg(openRouter, "anthropic/claude-sonnet-4-5")))
        assertFalse("大小写与空白容错", ProviderFamily.cachesPromptAutomatically(cfg(compat, "  Claude-Opus-4 ")))
        assertTrue(ProviderFamily.cachesPromptAutomatically(cfg(ApiProviderType.DEEPSEEK, "deepseek-v4-flash")))
        assertTrue(ProviderFamily.cachesPromptAutomatically(cfg(openRouter, "z-ai/glm-4.6")))
        assertTrue(ProviderFamily.cachesPromptAutomatically(cfg(ApiProviderType.GEMINI, "gemini-2.5-pro")))
        assertTrue(ProviderFamily.cachesPromptAutomatically(cfg(compat, "qwen3-max")))
        assertTrue(ProviderFamily.cachesPromptAutomatically(cfg(ApiProviderType.MINIMAX, "MiniMax-M2")))
        assertTrue("认不出按会缓存处理", ProviderFamily.cachesPromptAutomatically(cfg(compat, "")))
    }

    @Test
    fun `hostOf_小写并容错`() {
        assertEquals("api.x.ai", ProviderFamily.hostOf("  https://API.X.AI/v1 "))
        assertEquals("", ProviderFamily.hostOf("::::"))
    }
}
