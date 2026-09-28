package com.situ.aichat.data.remote.llm

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1-2（时间感知四期·图纸二 §3.3 · E17–E21）：各家缓存命中报法归一。
 * 输入 = 各家官方响应里的 usage 片段（JSON 字符串），用与 NetworkModule 同配置的 Json 解码；期望从规格独立写出。
 */
class UsageCacheTokensTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun usage(raw: String): UsageDto = json.decodeFromString(UsageDto.serializer(), raw)

    @Test
    fun `E17_DeepSeek专有字段_原样取用`() {
        val u = usage("""{"prompt_tokens":1000,"completion_tokens":50,"prompt_cache_hit_tokens":720,"prompt_cache_miss_tokens":280}""")
        assertEquals(720, UsageCacheTokens.hit(u))
        assertEquals(280, UsageCacheTokens.miss(u))
    }

    @Test
    fun `E17_DeepSeek专有字段优先于标准字段`() {
        // 同时带两种报法时以 DeepSeek 专有为准（K4：DeepSeek 命中 / 未命中与改动前相同）。
        val u = usage(
            """{"prompt_tokens":1000,"prompt_cache_hit_tokens":720,"prompt_cache_miss_tokens":280,""" +
                """"prompt_tokens_details":{"cached_tokens":640},"cached_tokens":600}""",
        )
        assertEquals(720, UsageCacheTokens.hit(u))
        assertEquals(280, UsageCacheTokens.miss(u))
    }

    @Test
    fun `E18_OpenAI标准prompt_tokens_details_未命中等于prompt减命中`() {
        val u = usage("""{"prompt_tokens":2006,"completion_tokens":300,"prompt_tokens_details":{"cached_tokens":1920,"audio_tokens":0}}""")
        assertEquals(1920, UsageCacheTokens.hit(u))
        assertEquals(86, UsageCacheTokens.miss(u))
    }

    @Test
    fun `E18_标准字段优先于Kimi顶层字段`() {
        val u = usage("""{"prompt_tokens":500,"prompt_tokens_details":{"cached_tokens":300},"cached_tokens":100}""")
        assertEquals(300, UsageCacheTokens.hit(u))
        assertEquals(200, UsageCacheTokens.miss(u))
    }

    @Test
    fun `E19_Kimi顶层cached_tokens`() {
        val u = usage("""{"prompt_tokens":800,"completion_tokens":20,"total_tokens":820,"cached_tokens":512}""")
        assertEquals(512, UsageCacheTokens.hit(u))
        assertEquals(288, UsageCacheTokens.miss(u))
    }

    @Test
    fun `E20_服务商不报缓存_两者皆null`() {
        val u = usage("""{"prompt_tokens":800,"completion_tokens":20}""")
        assertNull(UsageCacheTokens.hit(u))
        assertNull(UsageCacheTokens.miss(u))
        // prompt_tokens_details 在但没有 cached_tokens 也算没报。
        val u2 = usage("""{"prompt_tokens":800,"prompt_tokens_details":{"audio_tokens":0}}""")
        assertNull(UsageCacheTokens.hit(u2))
        assertNull(UsageCacheTokens.miss(u2))
    }

    @Test
    fun `E20_报了命中但prompt_tokens未知_未命中为null`() {
        val u = usage("""{"prompt_tokens_details":{"cached_tokens":300}}""")
        assertEquals(300, UsageCacheTokens.hit(u))
        assertNull(UsageCacheTokens.miss(u))
    }

    @Test
    fun `E21_命中大于prompt的坏数据_未命中取0`() {
        val u = usage("""{"prompt_tokens":100,"prompt_tokens_details":{"cached_tokens":150}}""")
        assertEquals(150, UsageCacheTokens.hit(u))
        assertEquals(0, UsageCacheTokens.miss(u))
    }
}
