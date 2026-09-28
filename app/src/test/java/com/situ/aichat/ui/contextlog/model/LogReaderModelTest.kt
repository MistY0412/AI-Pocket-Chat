package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.diagnostics.LogReplayRequest
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-2（四期·图纸五 §3.2 / §3.6）：筛选 / 胶囊判定 / 芯片 / 分块 / 细条 / 计数 / 复制全部。期望一律按规格手算。 */
class LogReaderModelTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true } // = NetworkModule.provideJson
    private val noPrev = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())

    private fun msg(i: Int, role: LogSentRole, vararg pieces: LogSentPiece) =
        LogSentMessage(i, role, pieces.sumOf { pieceText(it).length }, pieces.toList())

    private fun text(t: String) = LogSentPiece.Text(t)
    private fun time(t: String) = LogSentPiece.Added(LogAddedKind.TIME_MARKER, t)
    private fun note(t: String) = LogSentPiece.Added(LogAddedKind.NOTE, t)

    @Test
    fun matches_sixFilters() {
        val sys = msg(1, LogSentRole.SYSTEM, text("规则"))
        val sysAdded = msg(2, LogSentRole.SYSTEM, time("【时间 · 今天 09:00】"))
        val userFramed = msg(3, LogSentRole.USER, time("【时间 · 今天 09:01】"), text("早"))
        val ai = msg(4, LogSentRole.ASSISTANT, text("早～"))
        val tool = msg(5, LogSentRole.TOOL, text("结果"))
        val other = msg(6, LogSentRole.OTHER, text("?"))
        val all = listOf(sys, sysAdded, userFramed, ai, tool, other)
        fun of(f: LogReaderFilter) = all.filter { f.matches(it) }.map { it.index }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), of(LogReaderFilter.ALL))
        assertEquals("只有附加的 system 不算系统提示", listOf(1), of(LogReaderFilter.SYSTEM))
        assertEquals(listOf(3), of(LogReaderFilter.USER))
        assertEquals(listOf(4), of(LogReaderFilter.ASSISTANT))
        assertEquals(listOf(2, 3), of(LogReaderFilter.ADDED))
        assertEquals(listOf(5, 6), of(LogReaderFilter.TOOL))
    }

    @Test
    fun isTimePill_onlyWholeTimeMarkerSystem() {
        assertTrue(isTimePill(msg(1, LogSentRole.SYSTEM, time("【时间 · 今天 09:00】"))))
        assertFalse("附加但不是时间标记（E39）", isTimePill(msg(1, LogSentRole.SYSTEM, note("【前情提要】"))))
        assertFalse(isTimePill(msg(1, LogSentRole.SYSTEM, time("【时间 · 今天 09:00】"), text("x"))))
        assertFalse(isTimePill(msg(1, LogSentRole.USER, time("【时间 · 今天 09:00】"))))
    }

    @Test
    fun chipOptions_zeroHidden_allAndSelectedAlwaysShown() {
        val counts = mapOf(
            LogReaderFilter.ALL to 5, LogReaderFilter.SYSTEM to 1, LogReaderFilter.USER to 0,
            LogReaderFilter.ASSISTANT to 2, LogReaderFilter.ADDED to 0, LogReaderFilter.TOOL to 0,
        )
        assertEquals(listOf(LogReaderFilter.ALL, LogReaderFilter.SYSTEM, LogReaderFilter.ASSISTANT), readerChipOptions(counts, LogReaderFilter.ALL))
        assertEquals(
            listOf(LogReaderFilter.ALL, LogReaderFilter.SYSTEM, LogReaderFilter.USER, LogReaderFilter.ASSISTANT),
            readerChipOptions(counts, LogReaderFilter.USER),
        )
    }

    @Test
    fun blocks_shortWholeAndNewlineCut() {
        assertEquals(listOf(LogSpan(0, 3)), readerBlocks("abc"))
        assertEquals(emptyList<LogSpan>(), readerBlocks("abc", 2, 2))
        val t = "a".repeat(300) + "\n" + "b".repeat(300)
        val blocks = readerBlocks(t)
        assertEquals("在换行处切、换行被块界吃掉", listOf(LogSpan(0, 300), LogSpan(301, 601)), blocks)
        assertEquals("无硬切时块以换行连回 = 原文", t, blocks.joinToString("\n") { t.substring(it.start, it.end) })
    }

    @Test
    fun blocks_emptyBlockIsBlankLine() {
        val t = "x".repeat(399) + "\n" + "\n" + "y".repeat(399) + "\n" + "z".repeat(10)
        val blocks = readerBlocks(t)
        assertEquals(listOf(LogSpan(0, 399), LogSpan(400, 400), LogSpan(401, 800), LogSpan(801, 811)), blocks)
        assertEquals(t, blocks.joinToString("\n") { t.substring(it.start, it.end) })
    }

    @Test
    fun blocks_hardCutAndSurrogate_andRange() {
        assertEquals("无换行硬切 400（E38）", listOf(LogSpan(0, 400), LogSpan(400, 800), LogSpan(800, 1000)), readerBlocks("字".repeat(1000)))
        val emoji = "字".repeat(399) + "😀" + "字".repeat(100)
        assertEquals("代理对不劈开（E36）", listOf(LogSpan(0, 399), LogSpan(399, 501)), readerBlocks(emoji))
        assertEquals("区间内切", listOf(LogSpan(10, 20)), readerBlocks("0123456789abcdefghijklmn", 10, 20))
    }

    private val stripMessages = listOf(
        msg(1, LogSentRole.SYSTEM, text("S".repeat(10))),
        msg(2, LogSentRole.USER, time("T".repeat(5)), text("u".repeat(3)), text("")),
        msg(3, LogSentRole.USER, text("v".repeat(4))),
        msg(4, LogSentRole.ASSISTANT, text("a".repeat(6))),
        msg(5, LogSentRole.TOOL, text("t".repeat(2))),
    )

    @Test
    fun strip_runsMergeSameKind_totalIsSumOfRuns() {
        val s = readerStrip(stripMessages, null, null)
        assertEquals(
            listOf(
                LogStripRun(LogStripKind.SYSTEM, 10), LogStripRun(LogStripKind.ADDED, 5), LogStripRun(LogStripKind.USER, 7),
                LogStripRun(LogStripKind.ASSISTANT, 6), LogStripRun(LogStripKind.OTHER, 2),
            ),
            s.runs,
        )
        assertEquals(30, s.total)
        assertNull(s.cutFraction)
    }

    @Test
    fun strip_cutOffsets_threeKinds() {
        assertEquals("第 3 条之前 = 10 + 5 + 3", 18f / 30, readerStrip(stripMessages, null, LogReaderCutAt.BeforeMessage(3)).cutFraction!!, 1e-6f)
        assertEquals("第 2 条第 1 片之前 = 10 + 5", 15f / 30, readerStrip(stripMessages, null, LogReaderCutAt.BeforePiece(2, 1)).cutFraction!!, 1e-6f)
        val outline = LogPromptOutline(listOf(LogOutlineSection("甲", 0, 4, false), LogOutlineSection("乙", 6, 10, false)))
        assertEquals("目录第 1 节之前 = 该节起点", 6f / 30, readerStrip(stripMessages, outline, LogReaderCutAt.InOutline(1)).cutFraction!!, 1e-6f)
        assertEquals("目录末尾 = 第 1 条第 0 片长度", 10f / 30, readerStrip(stripMessages, outline, LogReaderCutAt.InOutline(2)).cutFraction!!, 1e-6f)
        assertNull("空内容没有比例", readerStrip(emptyList(), null, LogReaderCutAt.BeforeMessage(1)).cutFraction)
    }

    private fun stored(messages: List<ChatMessageDto>) = LogReplayRequest.encodeForStore(json, ChatRequestDto(model = "m", messages = messages, stream = false))

    /** 没有开头 system 的改写：改写器新造只有说明的 system（E12）；中途标记并进下一条用户；带一条 tool（E18）。 */
    private val original = listOf(
        ChatMessageDto(role = "user", content = "早呀"),
        ChatMessageDto(role = "assistant", content = "早～"),
        ChatMessageDto(role = "system", content = "【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"),
        ChatMessageDto(role = "user", content = "在干嘛"),
        ChatMessageDto(role = "assistant", content = "嗯"),
        ChatMessageDto(role = "tool", content = "结果"),
    )

    private fun adaptedView(): Pair<LogReaderView, List<ChatMessageDto>> {
        val adapted = ProviderMessageAdapter.adapt(original)
        val base = sentFromRequest(json, stored(adapted.messages), asIs = false)!!
        val view = buildReaderView(
            base, LogReaderSource.REQUEST, LogRequestShape.of(original), emptyList(), noPrev, null,
            LogSendAdaptation(asIs = false, leadingMerged = adapted.leadingMerged, midMerged = adapted.midMerged, tailMerged = adapted.tailMerged), truncated = false,
        )
        return view to adapted.messages
    }

    @Test
    fun counts_framedUserIsUserAndAdded_explainSystemNotSystem_tool() {
        val (view, adapted) = adaptedView()
        assertEquals("前提：改写器新造了说明 system", ProviderMessageAdapter.EXPLAIN_NOTE, adapted[0].content)
        assertEquals(
            mapOf(
                LogReaderFilter.ALL to 6, LogReaderFilter.SYSTEM to 0, LogReaderFilter.USER to 2,
                LogReaderFilter.ASSISTANT to 2, LogReaderFilter.ADDED to 2, LogReaderFilter.TOOL to 1,
            ),
            view.counts,
        )
        assertEquals(adapted.sumOf { it.content!!.length }, view.totalChars)
        assertNull("第 1 条不是带正文的系统提示 → 不拆目录", view.outline)
    }

    @Test
    fun truncatedPassesThrough_textBlocksCoverTextPieces() {
        val base = LogSentView(listOf(msg(1, LogSentRole.USER, time("【时间 · 今天 09:00】"), text("早"))))
        val view = buildReaderView(base, LogReaderSource.RENDERED, null, emptyList(), noPrev, null, null, truncated = true)
        assertTrue(view.truncated)
        assertEquals("只收 Text 片段", mapOf((1 to 1) to listOf(LogSpan(0, 1))), view.textBlocks)
        assertEquals(emptySet<Int>(), view.defaultOpen)
    }

    @Test
    fun copyAll_requestRendersActualMessages_renderedIsFullContextVerbatim() {
        val (view, adapted) = adaptedView()
        val entry = LogEntryEntity(id = 1, timestampMillis = 0, fullContext = "不该用它")
        assertEquals("实际请求 → 实际发出的消息按全文格式排（E30）", LogContextFormat.render(adapted), readerCopyAllText(view, entry))
        val rendered = view.copy(source = LogReaderSource.RENDERED)
        assertEquals("兜底 → fullContext 原样", "不该用它", readerCopyAllText(rendered, entry))
    }
}
