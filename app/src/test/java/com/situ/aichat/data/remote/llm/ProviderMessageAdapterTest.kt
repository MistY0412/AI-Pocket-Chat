package com.situ.aichat.data.remote.llm

import com.situ.aichat.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * T1-2（时间感知四期·图纸一 §3.2·E1–E8）：发送前改写。
 * 每例断言**完整输出列表**（角色序列 + 每条内容全文），期望按 §3.2 算法手算、锁定文本重新打字为字面量。
 */
class ProviderMessageAdapterTest {

    /** 重新打字的 EXPLAIN_NOTE（不引实现常量），并与实现双保险 pin。 */
    private val note = "（说明：对方消息开头或结尾用【系统说明】框起来的内容，是 App 附上的时间与规则信息，不是对方打的字，不要回应它本身，也不要在回复里提到它。）"

    private fun sys(t: String) = ChatMessageDto(role = "system", content = t)
    private fun user(t: String) = ChatMessageDto(role = "user", content = t)
    private fun asst(t: String) = ChatMessageDto(role = "assistant", content = t)

    private val claude = ApiConfigValues(ApiProviderType.ANTHROPIC, "k", "https://api.anthropic.com", "claude-sonnet-4-5")
    private val deepseek = ApiConfigValues(ApiProviderType.DEEPSEEK, "k", "https://api.deepseek.com/v1", "deepseek-v4-flash")

    @Test
    fun `锁定文本与实现常量一致`() {
        assertEquals(note, ProviderMessageAdapter.EXPLAIN_NOTE)
        assertEquals("【系统说明】", ProviderMessageAdapter.NOTE_OPEN)
        assertEquals("【/系统说明】", ProviderMessageAdapter.NOTE_CLOSE)
    }

    @Test
    fun `E1_白名单返回同一实例`() {
        val input = listOf(sys("P"), user("u1"), sys("T"), asst("a1"), user("u2"), sys("C"))
        assertSame(input, ProviderMessageAdapter.forSend(input, deepseek))
    }

    @Test
    fun `E1b_非白名单但无可改之处_也返回同一实例`() {
        val input = listOf(sys("P"), user("u1"), asst("a1"), user("u2"))
        assertSame(input, ProviderMessageAdapter.forSend(input, claude))
        val empty = emptyList<ChatMessageDto>()
        assertSame(empty, ProviderMessageAdapter.adapt(empty).messages)
    }

    @Test
    fun `E2_典型聊天形状_中途标记进下一条用户开头_末尾块贴最后一条用户_前置附说明`() {
        val input = listOf(
            sys("P"), user("u1"), asst("a1"),
            sys("【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"), user("u2"), asst("a2"),
            sys("【时间 · 今天 22:06】"), user("u3"),
            sys("C1"), sys("C2"), sys("C3"),
        )
        val r = ProviderMessageAdapter.adapt(input)
        assertEquals(
            listOf(
                sys("P\n\n$note"), user("u1"), asst("a1"),
                user("【系统说明】【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】【/系统说明】\nu2"), asst("a2"),
                user("【系统说明】【时间 · 今天 22:06】【/系统说明】\nu3\n\n【系统说明】\nC1\n\nC2\n\nC3\n【/系统说明】"),
            ),
            r.messages,
        )
        assertEquals(Triple(0, 2, 3), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
        assertEquals("forSend 对 Claude 走同一改写", r.messages, ProviderMessageAdapter.forSend(input, claude))
    }

    @Test
    fun `E3_工具回喂形状_卡片追加到最后一条用户末尾_tool不动_无尾块`() {
        val call = RequestToolCallDto(id = "t1", type = "function", function = RequestToolCallFunctionDto(name = "f", arguments = "{}"))
        val toolCallMsg = ChatMessageDto(role = "assistant", content = null, toolCalls = listOf(call), reasoningContent = "想")
        val toolResult = ChatMessageDto(role = "tool", content = "result", toolCallId = "t1")
        val input = listOf(sys("P"), user("u1"), asst("a1"), user("u2"), sys("C1"), sys("C2"), toolCallMsg, toolResult)
        val r = ProviderMessageAdapter.adapt(input)
        assertEquals(
            listOf(sys("P\n\n$note"), user("u1"), asst("a1"), user("u2\n【系统说明】C1\nC2【/系统说明】"), toolCallMsg, toolResult),
            r.messages,
        )
        assertEquals(Triple(0, 2, 0), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
    }

    @Test
    fun `E4_多模态_前插进首个Text_尾追加新Text_content保持null`() {
        val img = ChatContentPart.ImageUrl("data:image/jpeg;base64,AAA")
        val input = listOf(
            sys("P"), user("u1"), asst("a1"), sys("T1"),
            ChatMessageDto(role = "user", contentParts = listOf(ChatContentPart.Text("看这张"), img)),
            sys("C1"),
        )
        val r = ProviderMessageAdapter.adapt(input)
        assertEquals(
            listOf(
                sys("P\n\n$note"), user("u1"), asst("a1"),
                ChatMessageDto(
                    role = "user",
                    contentParts = listOf(
                        ChatContentPart.Text("【系统说明】T1【/系统说明】\n看这张"),
                        img,
                        ChatContentPart.Text("\n\n【系统说明】\nC1\n【/系统说明】"),
                    ),
                ),
            ),
            r.messages,
        )
    }

    @Test
    fun `E4b_多模态_首个不是Text则最前插Text_末个是Text则拼进它`() {
        val audio = ChatContentPart.InputAudio("b64")
        val input = listOf(
            sys("P"), asst("a0"), sys("T1"),
            ChatMessageDto(role = "user", contentParts = listOf(audio, ChatContentPart.Text("语音"))),
            sys("C1"),
        )
        val r = ProviderMessageAdapter.adapt(input)
        assertEquals(
            listOf(
                sys("P\n\n$note"), asst("a0"),
                ChatMessageDto(
                    role = "user",
                    contentParts = listOf(
                        ChatContentPart.Text("【系统说明】T1【/系统说明】\n"),
                        audio,
                        ChatContentPart.Text("语音\n\n【系统说明】\nC1\n【/系统说明】"),
                    ),
                ),
            ),
            r.messages,
        )
    }

    @Test
    fun `E5_只有system_合成一条且不加说明`() {
        val r = ProviderMessageAdapter.adapt(listOf(sys("A"), sys("B")))
        assertEquals(listOf(sys("A\n\nB")), r.messages)
        assertEquals(Triple(2, 0, 0), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
        val single = listOf(sys("A"))
        assertSame("单条 system 原样返回", single, ProviderMessageAdapter.adapt(single).messages)
    }

    @Test
    fun `E6_开头多条system_压成一条_无框则不附说明`() {
        val r = ProviderMessageAdapter.adapt(listOf(sys("A"), sys("B"), sys("C"), user("u")))
        assertEquals(listOf(sys("A\n\nB\n\nC"), user("u")), r.messages)
        assertEquals(Triple(3, 0, 0), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
    }

    @Test
    fun `E6b_开头多条system加末尾块_压成一条后附说明`() {
        val r = ProviderMessageAdapter.adapt(listOf(sys("A"), sys("B"), user("u"), sys("C")))
        assertEquals(listOf(sys("A\n\nB\n\n$note"), user("u\n\n【系统说明】\nC\n【/系统说明】")), r.messages)
        assertEquals(Triple(2, 0, 1), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
    }

    @Test
    fun `E7_最后一条非system是assistant_尾块成为新的最后一条user且去开头空行`() {
        val r = ProviderMessageAdapter.adapt(listOf(sys("P"), user("u"), asst("a"), sys("C1"), sys("C2")))
        assertEquals(
            listOf(sys("P\n\n$note"), user("u"), asst("a"), user("【系统说明】\nC1\n\nC2\n【/系统说明】")),
            r.messages,
        )
    }

    @Test
    fun `E8_中途标记前是assistant且之前没有user_在该assistant前插框user`() {
        val r = ProviderMessageAdapter.adapt(listOf(sys("P"), asst("你好"), sys("T1"), asst("在吗"), user("嗯")))
        assertEquals(
            listOf(sys("P\n\n$note"), asst("你好"), user("【系统说明】T1【/系统说明】"), asst("在吗"), user("嗯")),
            r.messages,
        )
        assertEquals(Triple(0, 1, 0), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
    }

    @Test
    fun `E8b_复核R1_两条角色消息之间的标记_不往回贴到更早的用户消息_就地插框user`() {
        // 真实形状：昨晚道晚安 → 她早上主动发消息（场边界注记在它前面）→ 你半小时后回。
        val r = ProviderMessageAdapter.adapt(
            listOf(sys("P"), user("晚安"), asst("晚安呀"), sys("B"), asst("早呀"), sys("T"), user("刚醒")),
        )
        assertEquals(
            listOf(
                sys("P\n\n$note"), user("晚安"), asst("晚安呀"), user("【系统说明】B【/系统说明】"), asst("早呀"),
                user("【系统说明】T【/系统说明】\n刚醒"),
            ),
            r.messages,
        )
        assertEquals(Triple(0, 2, 0), Triple(r.leadingMerged, r.midMerged, r.tailMerged))
    }

    @Test
    fun `E8c_复核R1_tool消息前的标记_仍贴最后一条用户_不拆散工具调用与结果`() {
        val call = ChatMessageDto(role = "assistant", content = null, toolCalls = emptyList())
        val result = ChatMessageDto(role = "tool", content = "ok", toolCallId = "c1")
        val r = ProviderMessageAdapter.adapt(listOf(sys("P"), user("帮我记一下"), call, sys("T"), result))
        assertEquals(
            listOf(sys("P\n\n$note"), user("帮我记一下\n【系统说明】T【/系统说明】"), call, result),
            r.messages,
        )
    }

    @Test
    fun `无前置system_有框时最前插说明`() {
        val r = ProviderMessageAdapter.adapt(listOf(user("u"), sys("C")))
        assertEquals(listOf(sys(note), user("u\n\n【系统说明】\nC\n【/系统说明】")), r.messages)
    }

    @Test
    fun `前置system带contentParts_附说明时改成纯文本`() {
        val lead = ChatMessageDto(role = "system", contentParts = listOf(ChatContentPart.Text("P1"), ChatContentPart.Text("P2")))
        val r = ProviderMessageAdapter.adapt(listOf(lead, user("u"), sys("C")))
        assertEquals(sys("P1\nP2\n\n$note"), r.messages.first())
    }
}
