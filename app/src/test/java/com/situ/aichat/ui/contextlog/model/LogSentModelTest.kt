package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.diagnostics.LogReplayRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1-7（四期·图纸四 §3.7）：输入一律用**真** [ProviderMessageAdapter.adapt] 产出 + 真存库编码（[LogReplayRequest.encodeForStore]）
 * 与真 [LogContextFormat.render]——框字面量一旦与改写端漂移，这里先红。
 */
class LogSentModelTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true } // = NetworkModule.provideJson
    private val marker = "【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"
    private val original = listOf(
        ChatMessageDto(role = "system", content = "你是林晚。"),
        ChatMessageDto(role = "system", content = "【前情提要】昨天聊了猫。"),
        ChatMessageDto(role = "user", content = "早呀"),
        ChatMessageDto(role = "assistant", content = "早～"),
        ChatMessageDto(role = "system", content = marker),
        ChatMessageDto(role = "user", content = "在干嘛"),
        ChatMessageDto(role = "system", content = "回复规则：短一点。"),
    )

    private fun stored(messages: List<ChatMessageDto>) = LogReplayRequest.encodeForStore(json, ChatRequestDto(model = "m", messages = messages, stream = false))

    @Test
    fun adapted_framesExplainNoteAndOutsideText() {
        val adapted = ProviderMessageAdapter.adapt(original)
        assertEquals("前提：改写端真把三类都改了", Triple(2, 1, 1), Triple(adapted.leadingMerged, adapted.midMerged, adapted.tailMerged))
        val view = sentFromRequest(json, stored(adapted.messages), asIs = false)!!
        assertEquals(listOf(LogSentRole.SYSTEM, LogSentRole.USER, LogSentRole.ASSISTANT, LogSentRole.USER), view.messages.map { it.role })
        assertEquals(listOf(1, 2, 3, 4), view.messages.map { it.index })
        assertEquals(
            listOf(LogSentPiece.Text("你是林晚。\n\n【前情提要】昨天聊了猫。"), LogSentPiece.Added(LogAddedKind.NOTE, ProviderMessageAdapter.EXPLAIN_NOTE)),
            view.messages[0].pieces,
        )
        assertEquals(listOf(LogSentPiece.Text("早呀")), view.messages[1].pieces)
        assertEquals(
            "中途框在开头、末尾框在结尾、框外文字去首尾换行",
            listOf(
                LogSentPiece.Added(LogAddedKind.TIME_MARKER, marker),
                LogSentPiece.Text("在干嘛"),
                LogSentPiece.Added(LogAddedKind.NOTE, "回复规则：短一点。"),
            ),
            view.messages[3].pieces,
        )
        assertEquals(adapted.messages[3].content!!.length, view.messages[3].chars)
        assertEquals("user", view.messages[3].rawRole)
        // 图纸五 T1-7：raw = 实际正文（含【系统说明】框）
        assertEquals(adapted.messages[3].content, view.messages[3].raw)
        assertEquals(adapted.messages[0].content, view.messages[0].raw)
    }

    @Test
    fun asIs_laterSystemMessagesWholeAdded() {
        val view = sentFromRequest(json, stored(original), asIs = true)!!
        assertEquals(7, view.messages.size)
        assertEquals(listOf(LogSentPiece.Text("你是林晚。")), view.messages[0].pieces)
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.NOTE, "【前情提要】昨天聊了猫。")), view.messages[1].pieces)
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.TIME_MARKER, marker)), view.messages[4].pieces)
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.NOTE, "回复规则：短一点。")), view.messages[6].pieces)
        assertEquals(listOf(LogSentPiece.Text("在干嘛")), view.messages[5].pieces)
    }

    @Test
    fun multimodal_textPartsJoined_mediaIsPlaceholder() {
        val m = ChatMessageDto(role = "user", contentParts = listOf(ChatContentPart.Text("看这个"), ChatContentPart.ImageUrl("data:image/jpeg;base64,${"A".repeat(4_096)}")))
        val view = sentFromRequest(json, stored(listOf(m)), asIs = true)!!
        assertEquals(listOf(LogSentPiece.Text("看这个\n[图片 · 约 3 KB]")), view.messages.single().pieces)
    }

    @Test
    fun badJson_null() {
        assertNull(sentFromRequest(json, "{坏的", asIs = true))
        assertNull(sentFromRequest(json, "", asIs = false))
    }

    @Test
    fun rendered_beforeRewrite() {
        val view = sentFromRendered(LogContextFormat.render(original + ChatMessageDto(role = "tool", content = "结果")))!!
        assertEquals(
            listOf(LogSentRole.SYSTEM, LogSentRole.SYSTEM, LogSentRole.USER, LogSentRole.ASSISTANT, LogSentRole.SYSTEM, LogSentRole.USER, LogSentRole.SYSTEM, LogSentRole.TOOL),
            view.messages.map { it.role }, // tool 两种视图同叫「工具」（复核 R1）
        )
        assertEquals(listOf(LogSentPiece.Text("你是林晚。")), view.messages[0].pieces)
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.TIME_MARKER, marker)), view.messages[4].pieces)
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.NOTE, "回复规则：短一点。")), view.messages[6].pieces)
        assertEquals("tool", view.messages[7].rawRole)
        // 图纸五 T1-7：raw = parseRendered 的正文
        assertEquals(LogContextFormat.parseRendered(LogContextFormat.render(original)).map { it.body }, view.messages.take(7).map { it.raw })
        assertEquals("结果", view.messages[7].raw)
        assertNull(sentFromRendered("没有表头"))
    }

    @Test
    fun folding_rowsAndPieceText() {
        val longText = LogSentPiece.Text("字".repeat(301))
        val longAdded = LogSentPiece.Added(LogAddedKind.NOTE, "注".repeat(121))
        assertEquals("字".repeat(300) + "…", visiblePieceText(longText, expanded = false))
        assertEquals("注".repeat(120) + "…", visiblePieceText(longAdded, expanded = false))
        assertEquals("字".repeat(301), visiblePieceText(longText, expanded = true))
        assertEquals("恰 300 字不截", "字".repeat(300), visiblePieceText(LogSentPiece.Text("字".repeat(300)), expanded = false))
        assertEquals(SENT_TEXT_FOLD, longText.foldLimit())
        assertEquals(SENT_ADDED_FOLD, longAdded.foldLimit())
        // 复核 R1：第 300 个字符是 emoji 的前半 → 不切半个，少截一位
        assertEquals("字".repeat(299) + "…", visiblePieceText(LogSentPiece.Text("字".repeat(299) + "😀x"), expanded = false))
    }

    /** 复核 R1：原请求没有开头 system 时改写器新造一条只有说明的 system → 整条算 App 附加，不当正文。 */
    @Test
    fun adapted_noLeadingSystem_explainNoteOnlyMessageIsAdded() {
        val adapted = ProviderMessageAdapter.adapt(listOf(original[2], original[3], original[4], original[5]))
        assertEquals("前提：只挪了中途的时间标记", Triple(0, 1, 0), Triple(adapted.leadingMerged, adapted.midMerged, adapted.tailMerged))
        val view = sentFromRequest(json, stored(adapted.messages), asIs = false)!!
        assertEquals(listOf(LogSentPiece.Added(LogAddedKind.NOTE, ProviderMessageAdapter.EXPLAIN_NOTE)), view.messages[0].pieces)
    }
}
