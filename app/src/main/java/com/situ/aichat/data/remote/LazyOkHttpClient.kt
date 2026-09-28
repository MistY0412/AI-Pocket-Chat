package com.situ.aichat.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 全 App 共用的那一个 [OkHttpClient] 的懒持有（启动主线程读盘清零 ①·图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）。
 *
 * 为什么要懒：OkHttp 建客户端时要初始化 TLS 上下文，它自己用 `StrictMode.noteSlowCall("newSSLContext")` 标成慢调用
 * （体检模拟器冷启 50–130ms）；而用它的五个单例（LlmClient / CapabilityDetector / ModelCatalogService / ApiBalanceService /
 * TtsService）都在启动时被主线程构造（Application 注入的 ContextLogService、AppViewModel 的依赖图一路拉到）。
 * 本类构造时不建客户端；[get] 首次取时切到 IO 线程建，之后一律给同一个实例——全 App 仍只有一个 OkHttpClient
 * （连接池 / 调度线程共用），配置由 NetworkModule 传进来的 [factory] 原样决定。
 */
class LazyOkHttpClient(factory: () -> OkHttpClient) {

    /** 已有现成客户端（测试 / 自带拦截器）时直接包一层。 */
    constructor(client: OkHttpClient) : this({ client })

    private val holder = lazy(factory)

    /** 取客户端：已建好直接给；还没建就切到 IO 线程建（并发首取也只建一次，`lazy` 默认同步锁）。 */
    suspend fun get(): OkHttpClient =
        if (holder.isInitialized()) holder.value else withContext(Dispatchers.IO) { holder.value }
}
