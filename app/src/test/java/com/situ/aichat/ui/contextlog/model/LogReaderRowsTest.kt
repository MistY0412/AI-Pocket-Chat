package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.prompt.ContextSegment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-5（四期·图纸五 §3.9）：普通态折叠 / 目录 / 断点三种位置 / 空条 / 展开钮，搜索态「只看有命中」开关，
 * 以及行的附加标记（pos / tocPos / divider / gapTop / gapBottom）逐行对手写期望表。
 */
class LogReaderRowsTest {

    private val a = "核心规则：别说教。" // 9 → [0, 9)
    private val b = "你是林晚，喜欢紫色。" // 10 → [11, 21)
    private val c = "今天的日程：上班。" // 9 → [23, 32)
    private val prompt = listOf(a, b, c).joinToString("\n\n")
    private fun seg(name: String, text: String, fp: String = ContextSegment.fingerprintOf(text)) =
        ContextSegment(name, null, text.length, 0, ContextSegment.POSITION_PREFIX, fp)
    private val segments = listOf(seg("核心规则", a), seg("身份", b), seg("日程", c))

    private fun msg(i: Int, role: LogSentRole, vararg pieces: LogSentPiece) = LogSentMessage(i, role, pieces.sumOf { pieceText(it).length }, pieces.toList())
    private fun user(i: Int, t: String = "消息$i") = msg(i, LogSentRole.USER, LogSentPiece.Text(t))
    private fun ai(i: Int, t: String = "回复$i") = msg(i, LogSentRole.ASSISTANT, LogSentPiece.Text(t))

    private val noPrev = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())
    private val frame = LogMapCut(CacheBreakKind.HISTORY_CHANGED, null, null, 10)

    private fun view(
        messages: List<LogSentMessage>, comparison: CacheComparison = noPrev, frame: LogMapCut? = null, shape: LogRequestShape? = null,
    ) = buildReaderView(LogSentView(messages), LogReaderSource.RENDERED, shape, segments, comparison, frame, null, truncated = false)

    private fun ctl(
        filter: LogReaderFilter = LogReaderFilter.ALL, showAll: Boolean = false, expanded: Set<Int> = emptySet(),
        toggled: Set<Int> = emptySet(), search: LogReaderSearch? = null, onlyHits: Boolean = true,
    ) = LogReaderControlsState(filter, showAll, expanded, toggled, search, onlyHits)

    private fun keys(v: LogReaderView, c: LogReaderControlsState = ctl()) = readerRows(v, c).map { it.key }

    // ── 普通态：折叠 ──

    @Test
    fun twelve_noCut_headFourCollapsedSixTailTwo() {
        val rows = readerRows(view((1..12).map { user(it) }), ctl())
        assertEquals(
            listOf("h1", "b1.0.0", "h2", "b2.0.0", "h3", "b3.0.0", "h4", "b4.0.0", "c4", "h11", "b11.0.0", "h12", "b12.0.0"),
            rows.map { it.key },
        )
        assertEquals(LogReaderRow.Collapsed(hidden = 6, after = 4), rows[8].row)
        assertEquals("恰 10 条不折", 20, keys(view((1..10).map { user(it) })).size)
        assertEquals("全部显示", 24, keys(view((1..12).map { user(it) }), ctl(showAll = true)).size)
    }

    @Test
    fun twelve_cutAtSeventh_keptBetweenTwoCollapsedBars() {
        val shape = LogRequestShape("u".repeat(12), (1..12).map { "f$it" }, List(12) { 1 })
        val cmp = CacheComparison(CacheBreak(CacheBreakKind.HISTORY_CHANGED, messageIndex = 6), null, emptyList())
        val v = view((1..12).map { user(it) }, cmp, frame, shape)
        assertEquals("前提", LogReaderCutAt.BeforeMessage(7), v.cutAt)
        val rows = readerRows(v, ctl())
        assertEquals(
            listOf("h1", "b1.0.0", "h2", "b2.0.0", "h3", "b3.0.0", "h4", "b4.0.0", "c4", "cut", "h7", "b7.0.0", "c7", "h11", "b11.0.0", "h12", "b12.0.0"),
            rows.map { it.key },
        )
        assertEquals(LogReaderRow.Collapsed(2, 4), rows[8].row)
        assertEquals(LogReaderRow.Collapsed(3, 7), rows[12].row) // E15
    }

    @Test
    fun filterThenFold() {
        val v = view((1..24).map { if (it % 2 == 1) user(it) else ai(it) })
        val rows = readerRows(v, ctl(filter = LogReaderFilter.USER))
        assertEquals(listOf("h1", "b1.0.0", "h3", "b3.0.0", "h5", "b5.0.0", "h7", "b7.0.0", "c7", "h21", "b21.0.0", "h23", "b23.0.0"), rows.map { it.key })
        assertEquals(LogReaderRow.Collapsed(6, 7), rows[8].row)
    }

    @Test
    fun timePill_singleRow_emptyMessage_emptyRow() {
        val v = view(listOf(user(1), msg(2, LogSentRole.SYSTEM, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 今天 09:00】")), ai(3, "")))
        assertEquals(listOf("h1", "b1.0.0", "t2", "h3", "e3"), keys(v)) // E17
    }

    // ── 普通态：目录 ──

    private val tocMessages = listOf(msg(1, LogSentRole.SYSTEM, LogSentPiece.Text(prompt)), user(2))

    @Test
    fun outline_defaultOpenChanged_xorToggles_allToggleLabel() {
        val prev = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, listOf(seg("身份", b, fp = "0000000000000000")))
        val v = view(tocMessages, prev)
        assertEquals("「这段变了」默认展开", setOf(1), v.defaultOpen)
        val rows = readerRows(v, ctl())
        assertEquals(listOf("h1", "s0", "s1", "sb1.11", "s2", "st", "h2", "b2.0.0"), rows.map { it.key })
        assertEquals(LogReaderRow.SectionsToggle(allOpen = false), rows[5].row)
        assertEquals(LogReaderRow.SectionBlock(1, 11, 21), rows[3].row)
        val flipped = readerRows(v, ctl(toggled = setOf(0, 1, 2)))
        assertEquals("异或：默认开的被点一下收起", listOf("h1", "s0", "sb0.0", "s1", "s2", "sb2.23", "st", "h2", "b2.0.0"), flipped.map { it.key })
        val allOpen = readerRows(v, ctl(toggled = setOf(0, 2)))
        assertEquals(LogReaderRow.SectionsToggle(allOpen = true), allOpen.single { it.key == "st" }.row)
    }

    @Test
    fun cut_inOutline_beforePiece_beforeMessage() {
        val cmp = CacheComparison(CacheBreak(CacheBreakKind.SYSTEM_PROMPT, messageIndex = 0, moduleName = "日程"), null, emptyList())
        val v = view(tocMessages, cmp, LogMapCut(CacheBreakKind.SYSTEM_PROMPT, "日程", null, 10))
        assertEquals(LogReaderCutAt.InOutline(2), v.cutAt)
        assertEquals("断点那一节默认展开、红框在它之前", listOf("h1", "s0", "s1", "cut", "s2", "sb2.23", "st", "h2", "b2.0.0"), keys(v))

        val pieces = view(listOf(user(1), msg(2, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.NOTE, "说明"), LogSentPiece.Text("正文"))))
            .copy(cutAt = LogReaderCutAt.BeforePiece(2, 1), frame = frame)
        assertEquals(listOf("h1", "b1.0.0", "h2", "a2.0", "cut", "b2.1.0"), keys(pieces))

        val end = view(tocMessages, cmp.copy(cacheBreak = CacheBreak(CacheBreakKind.SYSTEM_PROMPT, 0, "旧模块")), LogMapCut(CacheBreakKind.SYSTEM_PROMPT, null, null, 10))
        assertEquals("模块本轮缺失 → 目录末尾", listOf("h1", "s0", "s1", "s2", "cut", "st", "h2", "b2.0.0"), keys(end))
    }

    // ── 普通态：展开钮 ──

    @Test
    fun toggle_rules_tocPieceDoesNotCount() {
        val long = "字".repeat(350)
        val v = view(listOf(msg(1, LogSentRole.SYSTEM, LogSentPiece.Text(prompt + "\n\n" + "长".repeat(400))), user(2, long)))
        assertEquals(listOf("h1", "s0", "s1", "s2", "s3", "st", "h2", "b2.0.0", "g2"), keys(v))
        val rows = readerRows(v, ctl())
        assertEquals(LogReaderRow.TextBlock(2, 0, 0, 300, ellipsis = true), rows[7].row)
        assertEquals(LogReaderRow.Toggle(2, expanded = false), rows[8].row)
        val opened = readerRows(v, ctl(expanded = setOf(2)))
        assertEquals(LogReaderRow.TextBlock(2, 0, 0, 350, ellipsis = false), opened[7].row)
        assertEquals(LogReaderRow.Toggle(2, expanded = true), opened[8].row)

        val added = view(listOf(msg(1, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.NOTE, "注".repeat(121)))))
        assertEquals("附加 > 120 也出展开钮", listOf("h1", "a1.0", "g1"), keys(added))
        val emoji = view(listOf(user(1, "字".repeat(299) + "😀" + "尾".repeat(10))))
        assertEquals("折叠点不劈开代理对", LogReaderRow.TextBlock(1, 0, 0, 299, ellipsis = true), readerRows(emoji, ctl())[1].row)
    }

    // ── 行的附加标记（手写期望表）──

    private data class Flags(
        val key: String, val pos: LogCardPos, val toc: LogCardPos?, val divider: Boolean, val gapTop: Boolean, val gapBottom: Boolean,
        val tocGap: Boolean = false,
    )

    @Test
    fun flags_table() {
        val v = view(
            listOf(
                msg(1, LogSentRole.SYSTEM, LogSentPiece.Text(prompt)),
                msg(2, LogSentRole.SYSTEM, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 今天 09:00】")),
                user(3, "早呀"),
                msg(4, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 今天 09:05】"), LogSentPiece.Text("在干嘛"), LogSentPiece.Added(LogAddedKind.NOTE, "规则")),
                ai(5, ""),
            ),
        )
        val M = LogCardPos.MIDDLE
        assertEquals(
            listOf(
                Flags("h1", LogCardPos.TOP, null, divider = false, gapTop = false, gapBottom = false),
                Flags("s0", M, LogCardPos.TOP, false, false, false),
                Flags("s1", M, M, false, false, false),
                Flags("s2", M, M, false, false, false),
                Flags("st", M, LogCardPos.BOTTOM, false, false, true),
                Flags("t2", M, null, true, false, false),
                Flags("h3", M, null, true, false, false),
                Flags("b3.0.0", M, null, false, false, true),
                Flags("h4", M, null, true, false, false),
                Flags("a4.0", M, null, false, false, false),
                Flags("b4.1.0", M, null, false, true, false),
                Flags("a4.2", M, null, false, true, true),
                Flags("h5", M, null, true, false, false),
                Flags("e5", LogCardPos.BOTTOM, null, false, false, true),
            ),
            readerRows(v, ctl()).map { Flags(it.key, it.pos, it.tocPos, it.divider, it.gapTop, it.gapBottom, it.tocGapBottom) },
        )
    }

    @Test
    fun flags_openSectionAndCutInToc_single() {
        val cmp = CacheComparison(CacheBreak(CacheBreakKind.SYSTEM_PROMPT, 0, "身份"), null, emptyList())
        val v = view(listOf(msg(1, LogSentRole.SYSTEM, LogSentPiece.Text(prompt))), cmp, LogMapCut(CacheBreakKind.SYSTEM_PROMPT, "身份", null, 10))
        val M = LogCardPos.MIDDLE
        assertEquals(
            listOf(
                Flags("h1", LogCardPos.TOP, null, false, false, false),
                Flags("s0", M, LogCardPos.TOP, false, false, false),
                Flags("cut", M, M, false, false, false),
                Flags("s1", M, M, false, false, false),
                Flags("sb1.11", M, M, false, false, gapBottom = false, tocGap = true), // 节末留白在灰底里（复核 R1 🔵-1）
                Flags("s2", M, M, false, false, false),
                Flags("st", LogCardPos.BOTTOM, LogCardPos.BOTTOM, false, false, true),
            ),
            readerRows(v, ctl()).map { Flags(it.key, it.pos, it.tocPos, it.divider, it.gapTop, it.gapBottom, it.tocGapBottom) },
        )
        assertEquals("整张卡只有一行 → SINGLE", LogCardPos.SINGLE, readerRows(view(listOf(msg(1, LogSentRole.SYSTEM, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 今天】")))), ctl()).single().pos)
    }

    // ── 搜索态 ──

    private val searchMessages = listOf(
        msg(1, LogSentRole.SYSTEM, LogSentPiece.Text(prompt)), // 「紫」在身份节（b 的第 7 字 → 11 + 7 = 18）
        user(2, "字".repeat(350) + "紫色"),
        user(3, "字".repeat(350)),
        msg(4, LogSentRole.SYSTEM, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 紫】")),
        msg(5, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.NOTE, "紫")),
        ai(6, "紫"),
    )

    @Test
    fun search_onlyHits_onlyHitMessagesAndSections_noCutNoToggleNoFold() {
        val v = view(searchMessages + (7..14).map { user(it) })
        val s = searchReader(v, "紫")
        assertEquals("前提", listOf(LogReaderHit(1, 0, 18, 19), LogReaderHit(2, 0, 350, 351), LogReaderHit(4, 0, 6, 7), LogReaderHit(5, 0, 0, 1), LogReaderHit(6, 0, 0, 1)), s.hits)
        val rows = readerRows(v.copy(cutAt = LogReaderCutAt.BeforeMessage(2), frame = frame), ctl(search = s, onlyHits = true))
        assertEquals(listOf("h1", "s1", "sb1.11", "h2", "b2.0.0", "t4", "h5", "a5.0", "h6", "b6.0.0"), rows.map { it.key }) // E24
        assertEquals("命中那条展开整段", LogReaderRow.TextBlock(2, 0, 0, 352, ellipsis = false), rows[4].row)
        val lastToc = rows.single { it.key == "sb1.11" }
        assertEquals("第 1 条到此为止：节末留白在灰底里、消息末留白在灰底外，两样都要（复核 R1 🔵-1）", true to true, lastToc.tocGapBottom to lastToc.gapBottom)
    }

    @Test
    fun search_onlyHitsOff_allMessages_hitExpanded_othersStillFold() {
        val v = view(searchMessages)
        val s = searchReader(v, "紫")
        val rows = readerRows(v, ctl(search = s, onlyHits = false))
        assertEquals(
            listOf("h1", "s0", "s1", "sb1.11", "s2", "h2", "b2.0.0", "h3", "b3.0.0", "g3", "t4", "h5", "a5.0", "h6", "b6.0.0"),
            rows.map { it.key },
        )
        assertEquals(LogReaderRow.TextBlock(3, 0, 0, 300, ellipsis = true), rows[8].row)
        assertEquals("收起态的节照普通态开合", listOf("h1", "s0", "sb0.0", "s1", "sb1.11", "s2"), readerRows(v, ctl(search = s, onlyHits = false, toggled = setOf(0))).take(6).map { it.key })
    }

    @Test
    fun rowOf_fourRowKinds() {
        val v = view(searchMessages)
        val s = searchReader(v, "紫")
        val items = readerRows(v, ctl(search = s, onlyHits = true))
        fun at(i: Int) = items[i].key
        assertEquals("sb1.11", at(readerRowOf(items, s.hits[0])))
        assertEquals("b2.0.0", at(readerRowOf(items, s.hits[1])))
        assertEquals("t4", at(readerRowOf(items, s.hits[2])))
        assertEquals("a5.0", at(readerRowOf(items, s.hits[3])))
        assertEquals(-1, readerRowOf(items, LogReaderHit(9, 0, 0, 1)))
    }
}
