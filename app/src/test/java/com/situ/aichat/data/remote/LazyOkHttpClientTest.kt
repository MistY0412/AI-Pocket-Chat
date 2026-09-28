package com.situ.aichat.data.remote

import android.content.Context
import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.data.remote.llm.ApiBalanceService
import com.situ.aichat.data.remote.llm.ApiConfigValues
import com.situ.aichat.data.remote.llm.CapabilityDetector
import com.situ.aichat.data.remote.llm.LlmClient
import com.situ.aichat.data.remote.llm.modelcatalog.ModelCatalogService
import com.situ.aichat.di.NetworkModule
import com.situ.aichat.tts.SystemTtsEngine
import com.situ.aichat.tts.TtsService
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 启动主线程读盘清零 ①（图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）：全 App 那一个 OkHttpClient 懒建。
 * 锁四件事：构造（含五个使用方）不建客户端；首次取在 IO 线程建、不在调用方线程；并发首取也只建一次、永远同一实例；
 * NetworkModule 建出来的客户端配置逐字不变。「调用方线程」用单线程调度器模拟主线程。
 */
class LazyOkHttpClientTest {

    /** 按线程对象比，不按名字比：协程调试模式会给线程名追加「@coroutine#N」，按名字判「不是 fake-main」恒真 = 假绿。 */
    private lateinit var fakeMainThread: Thread
    private val fakeMainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-main").also { fakeMainThread = it } }
    private val fakeMain = fakeMainExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        fakeMainExecutor.shutdownNow()
    }

    private val builds = AtomicInteger(0)
    private val buildThreads = mutableListOf<Thread>()

    /** 计次 + 记线程的工厂；[client] 可带拦截器（端到端用例吐编排好的响应）。 */
    private fun countingLazy(client: () -> OkHttpClient = { OkHttpClient() }) = LazyOkHttpClient {
        builds.incrementAndGet()
        synchronized(buildThreads) { buildThreads += Thread.currentThread() }
        client()
    }

    @Test
    fun 构造不建客户端_五个使用方也不建() {
        val lazy = countingLazy()
        val json = Json { ignoreUnknownKeys = true }

        LlmClient(lazy, json)
        CapabilityDetector(lazy, json)
        ModelCatalogService(lazy, json)
        ApiBalanceService(lazy, json)
        TtsService(lazy, json, mockk<Context>(relaxed = true), mockk<SystemTtsEngine>(relaxed = true))

        assertEquals("构造期（启动时在主线程）不许建 OkHttpClient", 0, builds.get())
    }

    @Test
    fun 首次取在IO线程建_不在调用方线程() = runBlocking(fakeMain) {
        val lazy = countingLazy()

        lazy.get()

        assertEquals(1, builds.get())
        assertTrue("建客户端不许落在调用方线程：${buildThreads.single().name}", buildThreads.single() !== fakeMainThread)
    }

    @Test
    fun 并发首取只建一次_永远同一实例() = runBlocking {
        val lazy = countingLazy()

        val clients = (1..16).map { async(Dispatchers.Default) { lazy.get() } }.awaitAll() + lazy.get()

        assertEquals(1, builds.get())
        clients.forEach { assertSame(clients.first(), it) }
    }

    /**
     * 端到端：真 [ModelCatalogService] 从「主线程」拉一次模型列表，客户端在别的线程建，请求照常走完。
     * 选它是因为它**自己不切线程**——`http.get()` 在调用方线程上求值后才进 IO（复核 R1 🔵-3：LlmClient.completion
     * 外层已包 IO，拿它测证明不了懒持有自己会切线程）。
     */
    @Test
    fun 使用方首用_客户端不在调用方线程建_请求照常() = runBlocking(fakeMain) {
        val lazy = countingLazy {
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"data":[{"id":"m1","owned_by":"org"}]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        }
        val catalog = ModelCatalogService(lazy, Json { ignoreUnknownKeys = true })
        val config = ApiConfigValues(
            providerType = ApiProviderType.OPENAI_COMPATIBLE, apiKey = "k", baseUrl = "https://example.test", modelName = "m",
        )

        val models = catalog.fetchModels(config)

        assertEquals(listOf("m1"), models.map { it.id })
        assertEquals(1, builds.get())
        assertTrue("建客户端不许落在调用方线程：${buildThreads.single().name}", buildThreads.single() !== fakeMainThread)
    }

    /** NetworkModule 的懒客户端：注入时不建；建出来的配置 = 原 provideOkHttpClient 逐字（图纸 §4 锁定项）。 */
    @Test
    fun NetworkModule懒客户端_配置逐字不变() = runBlocking {
        val client = withContext(Dispatchers.IO) { NetworkModule.provideLazyOkHttpClient().get() }

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertEquals("无 cookie 存储（对齐 iOS ephemeral）", okhttp3.CookieJar.NO_COOKIES, client.cookieJar)
    }
}
