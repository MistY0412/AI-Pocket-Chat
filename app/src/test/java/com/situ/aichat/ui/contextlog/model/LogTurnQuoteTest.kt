package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.model.MessageKind
import com.situ.aichat.diagnostics.LogMessageBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T1-4（四期·图纸四 §3.3 LogTurnQuote）：本轮用户消息 / 引用串 / 回复条数。 */
class LogTurnQuoteTest {

    private var t = 0L
    private fun m(uuid: String, role: String, content: String = "", image: String? = null, voice: Boolean = false, kind: String = "plain_text") =
        LogMessageBrief(uuid, "c1", role, content, t++, image, voice, kind)

    @Test
    fun turnUserMessages_skipsOthers_stopsAtAssistant_missingAnchorEmpty() {
        val ordered = listOf(
            m("u0", "user", "前一轮"), m("a0", "assistant"),
            m("u1", "user", "你好"), m("s1", "system", "耳语"), m("u2", "user", "在吗"), m("a1", "assistant"), m("u3", "user"),
        )
        assertEquals(listOf("u1", "u2"), LogTurnQuote.turnUserMessages(ordered, "u1").map { it.messageUUID })
        assertEquals(listOf("u2"), LogTurnQuote.turnUserMessages(ordered, "u2").map { it.messageUUID })
        assertEquals("锚点本身是 assistant → 立即停", emptyList<LogMessageBrief>(), LogTurnQuote.turnUserMessages(ordered, "a1"))
        assertEquals(emptyList<LogMessageBrief>(), LogTurnQuote.turnUserMessages(ordered, "不存在"))
        // 复核 R1：上界 = 这一轮第一条主调用的时刻（含）——u2 发于 4，上界 3 时不算进来、上界 4 时算
        assertEquals(listOf("u1"), LogTurnQuote.turnUserMessages(ordered, "u1", untilMillis = 3).map { it.messageUUID })
        assertEquals(listOf("u1", "u2"), LogTurnQuote.turnUserMessages(ordered, "u1", untilMillis = 4).map { it.messageUUID })
    }

    @Test
    fun quoteOf_mediaAndText() {
        assertEquals(
            "纯图片 = 只 IMAGE",
            LogTurnQuoteText(listOf(LogMediaTag.IMAGE), null),
            LogTurnQuote.quoteOf(listOf(m("i", "user", "[图片]", image = "a.jpg"))),
        )
        assertEquals(
            "带配文 = IMAGE + 文字",
            LogTurnQuoteText(listOf(LogMediaTag.IMAGE), "我家猫"),
            LogTurnQuote.quoteOf(listOf(m("i", "user", "我家猫", image = "a.jpg"))),
        )
        assertEquals(
            "空白配文不进文字",
            LogTurnQuoteText(listOf(LogMediaTag.IMAGE), null),
            LogTurnQuote.quoteOf(listOf(m("i", "user", "  ", image = "a.jpg"))),
        )
        assertEquals(
            "语音 = VOICE，转写不进文字",
            LogTurnQuoteText(listOf(LogMediaTag.VOICE), null),
            LogTurnQuote.quoteOf(listOf(m("v", "user", "语音转写的字", voice = true))),
        )
        assertEquals(
            "多条依序：媒体依序、文字半角空格相连",
            LogTurnQuoteText(listOf(LogMediaTag.VOICE, LogMediaTag.IMAGE), "在吗 看看"),
            LogTurnQuote.quoteOf(listOf(m("t", "user", "在吗"), m("v", "user", "x", voice = true), m("i", "user", "看看", image = "a.jpg"))),
        )
        assertEquals(
            "结构化卡片走人话预览（绝不露 JSON）",
            LogTurnQuoteText(emptyList(), "🧧 红包"),
            LogTurnQuote.quoteOf(listOf(m("r", "user", "{\"amount\":88}", kind = MessageKind.RED_PACKET.raw))),
        )
        assertNull("全空 → null", LogTurnQuote.quoteOf(listOf(m("e", "user", ""))))
        assertNull(LogTurnQuote.quoteOf(emptyList()))
    }

    @Test
    fun replyCount_untilNextUser_missingNull() {
        val ordered = listOf(m("u1", "user"), m("a1", "assistant"), m("s", "system"), m("a2", "assistant"), m("u2", "user"), m("a3", "assistant"))
        assertEquals(2, LogTurnQuote.replyCount(ordered, "u1"))
        assertEquals(1, LogTurnQuote.replyCount(ordered, "u2"))
        assertEquals(0, LogTurnQuote.replyCount(listOf(m("x", "user")), "x"))
        assertNull(LogTurnQuote.replyCount(ordered, "不存在"))
    }
}
