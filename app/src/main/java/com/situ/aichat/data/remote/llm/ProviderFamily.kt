package com.situ.aichat.data.remote.llm

import com.situ.aichat.data.model.ApiProviderType

/**
 * 服务商 / 模型族判定（时间感知四期·图纸一 §3.1 / §3.2b 锁定）。
 *
 * 只回答两件事，不知道提示词语义：
 * 1. [keepsSystemInPlace]：这份配置背后的模型模板是否「中途 system 原位生效」（白名单 = DeepSeek / GLM / Grok /
 *    Kimi / OpenAI，实测见 `tools/prompt-ab-bench/RESULTS-2026-09-26.md`）。白名单一字不动；其余由
 *    [ProviderMessageAdapter] 发送前改写。按「模型族」判而不只看服务商——同一中转站 / OpenRouter 后面可能是任何模型，
 *    决定消息怎么拼的是模型模板；认不出一律改写（用户拍板）。
 * 2. [sessionHeaders]：OpenRouter / xAI 的会话标识请求头（模型读到的内容一字不变）。只由
 *    `LlmClient.buildPostRequest` 凭显式 sessionKey 追加——不进 `LlmHttp.authHeaders`（那里被能力探测与模型目录复用）。
 */
internal object ProviderFamily {

    const val OPENROUTER_SESSION_HEADER = "x-session-id"
    const val XAI_CONVERSATION_HEADER = "x-grok-conv-id"
    const val SESSION_KEY_PREFIX = "apc-"

    fun hostOf(baseUrl: String): String =
        runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()

    fun keepsSystemInPlace(config: ApiConfigValues): Boolean {
        when (config.providerType) {
            ApiProviderType.ANTHROPIC, ApiProviderType.GEMINI, ApiProviderType.MINIMAX -> return false
            ApiProviderType.DEEPSEEK -> return true
            else -> Unit
        }
        val base = config.modelName.trim().lowercase().substringAfterLast('/')
        return base.startsWith("deepseek") || base.startsWith("glm") || base.startsWith("grok") ||
            base.startsWith("kimi") || base.startsWith("moonshot") ||
            (base.startsWith("gpt-") && !base.startsWith("gpt-oss")) || base.startsWith("chatgpt") ||
            O_SERIES.matches(base)
    }

    private val O_SERIES = Regex("^o[1-9](-.*)?$")

    /** 这份配置会不会自动缓存提示词前缀（四期·图纸二 §3.4）：Claude 兼容层与经 OpenRouter 的 Claude 都要显式缓存标记（App 不发）→ false；其余 true（认不出按会缓存处理，不误报「不省钱」）。 */
    fun cachesPromptAutomatically(config: ApiConfigValues): Boolean =
        !(config.providerType == ApiProviderType.ANTHROPIC || config.modelName.trim().lowercase().substringAfterLast('/').startsWith("claude"))

    /** sessionKey 为 null / 空白 → 空表；OpenRouter（服务商或 host）→ `x-session-id`；`api.x.ai` → `x-grok-conv-id`；值 `apc-{key}` 截 256。 */
    fun sessionHeaders(config: ApiConfigValues, sessionKey: String?): Map<String, String> {
        val key = sessionKey?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        val value = (SESSION_KEY_PREFIX + key).take(256)
        val host = hostOf(config.baseUrl)
        return when {
            config.providerType == ApiProviderType.OPENROUTER || host == "openrouter.ai" -> mapOf(OPENROUTER_SESSION_HEADER to value)
            host == "api.x.ai" -> mapOf(XAI_CONVERSATION_HEADER to value)
            else -> emptyMap()
        }
    }
}
