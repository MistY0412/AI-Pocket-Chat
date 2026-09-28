package com.situ.aichat.ui.contextlog.model

/** 分段卡里一行的位置（整张消息卡由逐行拼成）。 */
enum class LogCardPos { SINGLE, TOP, MIDDLE, BOTTOM }

/** 阅读器列表的一行（四期·图纸五 §3.9 锁定）。 */
sealed interface LogReaderRow {
    data class Header(val message: Int) : LogReaderRow
    data class TimePill(val message: Int) : LogReaderRow
    data class TextBlock(val message: Int, val piece: Int, val start: Int, val end: Int, val ellipsis: Boolean) : LogReaderRow
    data class Added(val message: Int, val piece: Int, val expanded: Boolean) : LogReaderRow
    data class Section(val index: Int, val open: Boolean) : LogReaderRow
    data class SectionBlock(val index: Int, val start: Int, val end: Int) : LogReaderRow
    data class SectionsToggle(val allOpen: Boolean) : LogReaderRow
    data class Cut(val frame: LogMapCut) : LogReaderRow
    data class Toggle(val message: Int, val expanded: Boolean) : LogReaderRow
    data class Empty(val message: Int) : LogReaderRow
    data class Collapsed(val hidden: Int, val after: Int) : LogReaderRow
}

data class LogReaderItem(
    val row: LogReaderRow,
    val key: String,
    val pos: LogCardPos,
    /** 目录区（Section / SectionBlock / SectionsToggle / 目录里的 Cut）内的位置；非目录行 = null。 */
    val tocPos: LogCardPos?,
    /** 行顶画分隔线。 */
    val divider: Boolean,
    /** 行顶留 AppSpacing.xs。 */
    val gapTop: Boolean,
    /** 行底留 AppSpacing.s（每条消息的最后一行·画在目录灰底之外）。 */
    val gapBottom: Boolean,
    /**
     * 目录灰底**里面**行底留 AppSpacing.s：展开节的最后一块（复核 R1 🔵-1 作者订正——原并在 [gapBottom] 里画在灰底外，
     * 展开的节与下一节之间灰底断开一截、搜索态末块正文贴着灰底下沿）。
     */
    val tocGapBottom: Boolean = false,
)

/** 页面开关的快照（界面状态 → 纯逻辑）；[search] 只在搜索态且关键词非空时带进来。 */
data class LogReaderControlsState(
    val filter: LogReaderFilter, val showAll: Boolean, val expanded: Set<Int>, val sectionsToggled: Set<Int>,
    val search: LogReaderSearch?, val onlyHits: Boolean,
)

/** 拼装中的一行：[unit] = 同条消息内的「单元」（算 gapTop 用）；[first] = 该条消息产出的第一行。 */
private class Draft(val row: LogReaderRow, val message: Int?, val unit: String, val toc: Boolean, val first: Boolean) {
    var gapBottom = false
    var tocGapBottom = false
}

/** 一条消息在当前态下的拼法（普通态 / 搜索态的差别都收在这里）。 */
private class MsgMode(
    val cuts: Boolean,
    val showSection: (Int) -> Boolean,
    val open: (Int) -> Boolean,
    val sectionsToggle: Boolean,
    val toggle: Boolean,
)

/** 把视图 + 页面开关拼成列表行（§3.9：普通态折叠 / 目录 / 断点；搜索态只列、自动展开）。 */
fun readerRows(view: LogReaderView, c: LogReaderControlsState): List<LogReaderItem> {
    val out = ArrayList<Draft>()
    val normalOpen = { i: Int -> (i in view.defaultOpen) xor (i in c.sectionsToggled) }
    val search = c.search
    if (search == null) {
        val shown = view.messages.filter { c.filter.matches(it) }
        val pinned = when (val cut = view.cutAt) {
            is LogReaderCutAt.InOutline -> 1
            is LogReaderCutAt.BeforePiece -> cut.message
            is LogReaderCutAt.BeforeMessage -> cut.message
            null -> null
        }
        val fold = !c.showAll && shown.size > SENT_FOLD_OVER
        val mode = MsgMode(cuts = true, showSection = { true }, open = normalOpen, sectionsToggle = true, toggle = true)
        var hidden = 0
        var lastKept = 0
        shown.forEachIndexed { i, m ->
            val keep = !fold || i < SENT_FOLD_HEAD || i >= shown.size - SENT_FOLD_TAIL || m.index == pinned
            if (!keep) {
                hidden++
                return@forEachIndexed
            }
            if (hidden > 0) {
                out += Draft(LogReaderRow.Collapsed(hidden, lastKept), null, "collapsed", toc = false, first = false)
                hidden = 0
            }
            messageRows(view, m, m.index in c.expanded, mode, out)
            lastKept = m.index
        }
        if (hidden > 0) out += Draft(LogReaderRow.Collapsed(hidden, lastKept), null, "collapsed", toc = false, first = false)
    } else {
        val nav = search.visibleHits(view, c.filter)
        val hitMsgs = nav.map { it.message }.toSet()
        val sections = view.outline?.sections.orEmpty()
        val hitSections = sections.indices.filter { i ->
            nav.any { it.message == 1 && it.piece == 0 && it.start >= sections[i].start && it.start < sections[i].end }
        }.toSet()
        for (m in view.messages) {
            if (!c.filter.matches(m) || (c.onlyHits && m.index !in hitMsgs)) continue
            val mode = MsgMode(
                cuts = false,
                showSection = { !c.onlyHits || it in hitSections },
                open = { it in hitSections || (!c.onlyHits && normalOpen(it)) },
                sectionsToggle = false,
                toggle = m.index !in hitMsgs,
            )
            messageRows(view, m, m.index in hitMsgs || m.index in c.expanded, mode, out)
        }
    }
    return finish(out)
}

private fun messageRows(view: LogReaderView, m: LogSentMessage, expanded: Boolean, mode: MsgMode, out: MutableList<Draft>) {
    val cutAt = if (mode.cuts) view.cutAt else null
    val start = out.size
    fun add(row: LogReaderRow, unit: String, toc: Boolean = false) {
        out += Draft(row, m.index, unit, toc, first = out.size == start)
    }
    fun cut(toc: Boolean) = view.frame?.let { add(LogReaderRow.Cut(it), if (toc) "toc" else "cut", toc) }

    if (cutAt is LogReaderCutAt.BeforeMessage && cutAt.message == m.index) cut(toc = false)
    if (isTimePill(m)) {
        // TimePill 独立单元 "t"（只可能排在本条的 Cut 之后·复核 R1 裁决 T-1 核准、作者已补进 §3.9 单元表）。
        add(LogReaderRow.TimePill(m.index), "t")
        return
    }
    add(LogReaderRow.Header(m.index), "h")
    val bodyStart = out.size
    val outline = view.outline
    m.pieces.forEachIndexed { p, piece ->
        if (outline != null && m.index == 1 && p == 0) {
            val sections = outline.sections
            for (i in sections.indices) {
                if (!mode.showSection(i)) continue
                if (cutAt == LogReaderCutAt.InOutline(i)) cut(toc = true)
                val open = mode.open(i)
                add(LogReaderRow.Section(i, open), "toc", toc = true)
                if (open) {
                    view.sectionBlocks[i].forEach { b -> add(LogReaderRow.SectionBlock(i, b.start, b.end), "toc", toc = true) }
                    if (view.sectionBlocks[i].isNotEmpty()) out.last().tocGapBottom = true
                }
            }
            if (cutAt == LogReaderCutAt.InOutline(sections.size)) cut(toc = true)
            if (mode.sectionsToggle) add(LogReaderRow.SectionsToggle(allOpen = sections.indices.all(mode.open)), "toc", toc = true)
            return@forEachIndexed
        }
        if (cutAt == LogReaderCutAt.BeforePiece(m.index, p)) cut(toc = false)
        when (piece) {
            is LogSentPiece.Text -> {
                val text = piece.text
                when {
                    text.isEmpty() -> Unit
                    expanded || text.length <= SENT_TEXT_FOLD ->
                        view.textBlocks[m.index to p].orEmpty().forEach { b -> add(LogReaderRow.TextBlock(m.index, p, b.start, b.end, ellipsis = false), "p$p") }
                    else -> {
                        val end = if (Character.isHighSurrogate(text[SENT_TEXT_FOLD - 1])) SENT_TEXT_FOLD - 1 else SENT_TEXT_FOLD
                        add(LogReaderRow.TextBlock(m.index, p, 0, end, ellipsis = true), "p$p")
                    }
                }
            }
            is LogSentPiece.Added -> add(LogReaderRow.Added(m.index, p, expanded), "p$p")
        }
    }
    if (out.size == bodyStart) add(LogReaderRow.Empty(m.index), "empty")
    val foldable = m.pieces.withIndex().any { (p, piece) -> !(outline != null && m.index == 1 && p == 0) && pieceText(piece).length > piece.foldLimit() }
    if (mode.toggle && (expanded || foldable)) add(LogReaderRow.Toggle(m.index, expanded), "toggle")
    out.last().gapBottom = true
}

/** 行的附加标记（§3.9「两态同」）：key / 卡内位置 / 目录区位置 / 分隔线 / 上下留白。 */
private fun finish(drafts: List<Draft>): List<LogReaderItem> = drafts.mapIndexed { i, d ->
    val prev = drafts.getOrNull(i - 1)
    val tocPos = if (!d.toc) null else posOf(first = prev?.toc != true, last = drafts.getOrNull(i + 1)?.toc != true)
    LogReaderItem(
        row = d.row,
        key = keyOf(d.row),
        pos = posOf(first = i == 0, last = i == drafts.lastIndex),
        tocPos = tocPos,
        divider = i > 0 && (d.first || d.row is LogReaderRow.Collapsed),
        gapTop = prev != null && d.message != null && prev.message == d.message && prev.unit != "h" && prev.unit != d.unit,
        gapBottom = d.gapBottom,
        tocGapBottom = d.tocGapBottom,
    )
}

private fun posOf(first: Boolean, last: Boolean): LogCardPos = when {
    first && last -> LogCardPos.SINGLE
    first -> LogCardPos.TOP
    last -> LogCardPos.BOTTOM
    else -> LogCardPos.MIDDLE
}

private fun keyOf(row: LogReaderRow): String = when (row) {
    is LogReaderRow.Header -> "h${row.message}"
    is LogReaderRow.TimePill -> "t${row.message}"
    is LogReaderRow.TextBlock -> "b${row.message}.${row.piece}.${row.start}"
    is LogReaderRow.Added -> "a${row.message}.${row.piece}"
    is LogReaderRow.Section -> "s${row.index}"
    is LogReaderRow.SectionBlock -> "sb${row.index}.${row.start}"
    is LogReaderRow.SectionsToggle -> "st"
    is LogReaderRow.Cut -> "cut"
    is LogReaderRow.Toggle -> "g${row.message}"
    is LogReaderRow.Empty -> "e${row.message}"
    is LogReaderRow.Collapsed -> "c${row.after}"
}

/** 命中所在的第一行下标（跳转用）；没有 → -1。 */
fun readerRowOf(items: List<LogReaderItem>, hit: LogReaderHit): Int = items.indexOfFirst { item ->
    when (val r = item.row) {
        is LogReaderRow.TextBlock -> r.message == hit.message && r.piece == hit.piece && hit.start >= r.start && hit.start < r.end
        is LogReaderRow.Added -> r.message == hit.message && r.piece == hit.piece
        is LogReaderRow.TimePill -> r.message == hit.message
        is LogReaderRow.SectionBlock -> hit.message == 1 && hit.piece == 0 && hit.start >= r.start && hit.start < r.end
        else -> false
    }
}
