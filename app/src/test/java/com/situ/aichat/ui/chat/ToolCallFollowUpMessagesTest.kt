package com.situ.aichat.ui.chat

import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.CompletedToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1（图纸 2026-09-26-工具回喂回传思考内容）：回喂消息装配。规格（DeepSeek 思考模式工具调用文档 + iOS 原设计
 * ANDROID_PORT_SPEC §2.10）：同一轮的 assistant tool_calls 消息必须带回 `reasoning_content`；没有思考时该键缺席，
 * 线格式与旧版一致。断言按线格式（解析后的 JSON 键）写，不依赖装配实现细节。
 */
class ToolCallFollowUpMessagesTest {

    private val wireJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    private val system = ChatMessageDto(role = "system", content = "你是夏晴子。")
    private val user = ChatMessageDto(role = "user", content = "那说好了,明天下午三点我去店里找你")
    private val call = CompletedToolCall(id = "call_1", name = "record_promise", arguments = """{"content":"明天下午三点阿远来店里"}""")

    private fun wire(msg: ChatMessageDto): JsonObject =
        wireJson.parseToJsonElement(wireJson.encodeToString(ChatMessageDto.serializer(), msg)).jsonObject

    @Test
    fun 带思考_assistant工具消息回传reasoning_content原文() {
        val reasoning = "用户约了明天下午三点，\n先记下这条约定。"
        val out = buildToolCallFollowUpMessages(listOf(system, user), listOf(call), false, reasoning)

        // 原消息原样在前 → 一条 assistant → 每个 call 一条 tool。
        assertEquals(listOf("system", "user", "assistant", "tool"), out.map { it.role })
        assertEquals(listOf(system, user), out.take(2))
        val assistant = wire(out[2])
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        assertEquals("原文逐字回传（不 trim·含换行）", reasoning, assistant["reasoning_content"]!!.jsonPrimitive.content)
        assertFalse("正文为空时不发 content 键", assistant.containsKey("content"))
        val tc = assistant["tool_calls"]!!.jsonArray.single().jsonObject
        assertEquals("call_1", tc["id"]!!.jsonPrimitive.content)
        assertEquals("function", tc["type"]!!.jsonPrimitive.content)
        assertEquals("record_promise", tc["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(call.arguments, tc["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        // tool 结果挂回同一个 id，文案不因思考回传而变。
        assertEquals("call_1", out[3].toolCallId)
        assertEquals(PROMISE_FOLLOW_UP_TEXT, out[3].content)
    }

    @Test
    fun 无思考_不发reasoning_content键_线格式与旧版一致() {
        val out = buildToolCallFollowUpMessages(listOf(user), listOf(call), false, null)

        assertNull(out[1].reasoningContent)
        val assistant = wire(out[1])
        assertFalse("没有思考 → 键缺席（非思考模型 / 其它服务商请求字节不变）", assistant.containsKey("reasoning_content"))
        assertEquals(setOf("role", "tool_calls"), assistant.keys)
    }

    @Test
    fun 多个工具调用_共用一条assistant消息与同一份思考() {
        val second = CompletedToolCall(id = "call_2", name = "propose_future_meeting", arguments = """{"when_text":"周末","activity":"爬山"}""")
        val out = buildToolCallFollowUpMessages(listOf(user), listOf(call, second), false, "想")

        assertEquals(1, out.count { it.role == "assistant" })
        assertEquals("想", out[1].reasoningContent)
        assertEquals(listOf("call_1", "call_2"), out[1].toolCalls!!.map { it.id })
        assertEquals(listOf("call_1", "call_2"), out.filter { it.role == "tool" }.map { it.toolCallId })
        assertTrue(out.drop(2).all { it.reasoningContent == null })
    }
}
