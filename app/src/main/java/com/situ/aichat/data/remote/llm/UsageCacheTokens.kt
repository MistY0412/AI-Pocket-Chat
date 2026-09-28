package com.situ.aichat.data.remote.llm

/** 各家「缓存命中」报法归一（四期·图纸二 §3.3·优先级锁定）：DeepSeek 专有字段 → OpenAI 标准 prompt_tokens_details.cached_tokens（OpenAI / GLM / Grok / OpenRouter）→ Kimi 顶层 cached_tokens。都没有 = null（服务商没报）。 */
internal object UsageCacheTokens {
    fun hit(u: UsageDto): Int? = u.promptCacheHitTokens ?: u.promptTokensDetails?.cachedTokens ?: u.cachedTokens

    /** 未命中：DeepSeek 专有字段；否则 = prompt_tokens − 命中（不小于 0）；命中或 prompt_tokens 未知 = null。 */
    fun miss(u: UsageDto): Int? = u.promptCacheMissTokens ?: hit(u)?.let { h -> u.promptTokens?.let { (it - h).coerceAtLeast(0) } }
}
