package com.situ.aichat.data.remote.llm

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** 发送前改写计数（= ProviderMessageAdapter.AdaptResult 三计数）。 */
data class SendAdaptationCounts(val leadingMerged: Int, val midMerged: Int, val tailMerged: Int)

/**
 * 上下文日志「实际发出去的样子」捕获（四期·图纸三 §3.2）：记录层放进协程上下文，[LlmClient] 在首发构建点把
 * 请求对象交进来；**只留第一次**（= 本次调用的主请求；400 自愈重发等不覆盖）。只读不改请求。
 */
class LlmRequestCapture : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LlmRequestCapture>

    @Volatile var request: ChatRequestDto? = null
        private set
    @Volatile var providerTypeRaw: String? = null
        private set
    /** null = 原样发送（白名单）或没捕获；非 null = 非白名单改写计数。 */
    @Volatile var adaptation: SendAdaptationCounts? = null
        private set

    @Synchronized
    fun offer(request: ChatRequestDto, providerTypeRaw: String, adaptation: SendAdaptationCounts?) {
        if (this.request != null) return
        this.request = request
        this.providerTypeRaw = providerTypeRaw
        this.adaptation = adaptation
    }
}
