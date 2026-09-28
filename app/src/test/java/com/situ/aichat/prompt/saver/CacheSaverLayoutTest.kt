package com.situ.aichat.prompt.saver

import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * T1-1（时间感知四期·图纸二 §3.2 ① · E8–E13）：叙述句改写 + 插入点 + 挪位分段。期望从规格独立写出（锁定文本重新打字）。
 */
class CacheSaverLayoutTest {

    private val today = LocalDate.of(2026, 9, 28)

    // MARK: - narrate

    @Test
    fun `E13_说话人是角色_写成你说过`() {
        assertEquals("6月14日，你说过：「好想看看」", CacheSaverLayout.narrate("[2026-06-14 22:33] 夏晴子：好想看看", "夏晴子", today))
    }

    @Test
    fun `E13_用户说话人_原名保留`() {
        assertEquals("6月14日，阿远说过：「小时候…」", CacheSaverLayout.narrate("[2026-06-14 22:31] 阿远：小时候…", "夏晴子", today))
    }

    @Test
    fun `E10_去年的片段_带年份`() {
        assertEquals("2025年6月14日，你说过：「好想看看」", CacheSaverLayout.narrate("[2025-06-14 22:33] 夏晴子：好想看看", "夏晴子", today))
        assertEquals("明年也带年份", "2027年1月2日，阿远说过：「x」", CacheSaverLayout.narrate("[2027-01-02 08:00] 阿远：x", "夏晴子", today))
    }

    @Test
    fun `月日去前导零`() {
        assertEquals("1月5日，你说过：「早」", CacheSaverLayout.narrate("[2026-01-05 07:02] 夏晴子：早", "夏晴子", today))
    }

    @Test
    fun `E11_内容有换行_整段进引号`() {
        assertEquals(
            "6月14日，阿远说过：「第一行\n第二行」",
            CacheSaverLayout.narrate("[2026-06-14 22:31] 阿远：第一行\n第二行", "夏晴子", today),
        )
    }

    @Test
    fun `内容里再有全角冒号_只在第一个冒号切说话人`() {
        assertEquals("6月14日，阿远说过：「提醒：别忘了」", CacheSaverLayout.narrate("[2026-06-14 22:31] 阿远：提醒：别忘了", "夏晴子", today))
    }

    @Test
    fun `E12_见面_档案_日子片段_原样`() {
        val meeting = "[2026-06-20 20:00 · 线下见面] 夏晴子：到了吗"
        val archive = "[2026-06-20 23:10 · 见面档案] 一起吃了火锅"
        val day = "[2026-06-21 周日 · 日子] 两人聊到很晚"
        assertEquals(meeting, CacheSaverLayout.narrate(meeting, "夏晴子", today))
        assertEquals(archive, CacheSaverLayout.narrate(archive, "夏晴子", today))
        assertEquals(day, CacheSaverLayout.narrate(day, "夏晴子", today))
    }

    @Test
    fun `不成形的文本_原样`() {
        assertEquals("随便一句话", CacheSaverLayout.narrate("随便一句话", "夏晴子", today))
        assertEquals("前面有字 [2026-06-14 22:31] 阿远：x", CacheSaverLayout.narrate("前面有字 [2026-06-14 22:31] 阿远：x", "夏晴子", today))
    }

    // MARK: - insertIndex

    private fun m(role: String, content: String = role) = ChatMessageDto(role = role, content = content)

    @Test
    fun `插入点_最后一条非system是用户_插它前面`() {
        val msgs = listOf(m("system", "sys"), m("assistant"), m("user", "u2"), m("system", "tail"))
        assertEquals(2, CacheSaverLayout.insertIndex(msgs, historyStart = 1))
    }

    @Test
    fun `R1_一轮多条用户消息_插在这段用户消息第一条前面_不拆开`() {
        // 图片 + 文字各自成条（多模态独立成条）：[sys, 角色, 用户图, 用户字, 后置]
        val imageThenText = listOf(m("system", "sys"), m("assistant"), m("user", "img"), m("user", "你看这个"), m("system", "tail"))
        assertEquals(2, CacheSaverLayout.insertIndex(imageThenText, historyStart = 1))
        // 跨 5 分钟停顿连发：中间的时间标记一并越过，块在停顿前那条之前、第一条之前的时间标记之后。
        val acrossPause = listOf(
            m("system", "sys"), m("assistant"), m("system", "【时间 · 今天 21:30】"), m("user", "在吗"),
            m("system", "【时间 · 今天 21:39】"), m("user", "今天好累"), m("system", "tail"),
        )
        assertEquals(3, CacheSaverLayout.insertIndex(acrossPause, historyStart = 1))
        // 历史全是用户消息：插在第一条前面。
        val onlyUsers = listOf(m("system", "sys"), m("user", "u1"), m("user", "u2"))
        assertEquals(1, CacheSaverLayout.insertIndex(onlyUsers, historyStart = 1))
    }

    @Test
    fun `E8_最后一条非system是角色_插它后面`() {
        val msgs = listOf(m("system", "sys"), m("user"), m("assistant", "a1"), m("assistant", "a2"), m("system", "tail"))
        assertEquals(4, CacheSaverLayout.insertIndex(msgs, historyStart = 1))
    }

    @Test
    fun `E9_历史为空_插在列表末尾`() {
        val msgs = listOf(m("system", "sys"), m("system", "截断提示"))
        assertEquals(2, CacheSaverLayout.insertIndex(msgs, historyStart = 2))
    }

    @Test
    fun `历史开始前的非system不算`() {
        // historyStart 之前的消息（理论上全是 system）即使不是 system 也不参与查找。
        val msgs = listOf(m("user", "早于历史"), m("system", "a"), m("system", "b"))
        assertEquals(3, CacheSaverLayout.insertIndex(msgs, historyStart = 1))
    }

    // MARK: - insertBlock

    @Test
    fun `插入一条system并记挪位分段`() {
        val msgs = mutableListOf(m("system", "sys"), m("assistant"), m("user", "最新"))
        val sink = mutableListOf<ContextSegment>()
        val block = "## 与当前话题相关的历史对话片段\n6月14日，你说过：「好想看看」"
        CacheSaverLayout.insertBlock(msgs, historyStart = 1, block = block, segmentSink = sink)
        assertEquals(listOf("sys", "assistant", block, "最新"), msgs.map { it.content })
        assertEquals("system", msgs[2].role)
        assertEquals(
            // 复核 R1：挪位段指纹 = 这条 system 消息的指纹（图纸三 §3.5 原料「角色␀正文␀思考␀工具调用id␀工具调用」），与请求形状同源
            listOf(ContextSegment("每轮会变的内容（省钱模式）", null, block.length, TokenEstimator.estimate(block), "history", fingerprint = ContextSegment.fingerprintOf("system\u0000" + block + "\u0000\u0000\u0000"))),
            sink,
        )
        assertEquals(LogRequestShape.of(msgs).fingerprints[2], sink.single().fingerprint)
        assertTrue(CacheSaverLayout.SEGMENT_NAME == "每轮会变的内容（省钱模式）")
    }

    @Test
    fun `无日志收集器时只插消息`() {
        val msgs = mutableListOf(m("user", "u"))
        CacheSaverLayout.insertBlock(msgs, historyStart = 0, block = "B", segmentSink = null)
        assertEquals(listOf("B", "u"), msgs.map { it.content })
    }
}
