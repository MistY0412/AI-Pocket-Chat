package com.situ.aichat.ui.contextlog.model

import kotlin.math.max
import kotlin.math.min

/** 一处命中：第 [message] 条（从 1 起）第 [piece] 片文字里的 [start, end)。 */
data class LogReaderHit(val message: Int, val piece: Int, val start: Int, val end: Int)

data class LogReaderSearch(val query: String, val hits: List<LogReaderHit>, val capped: Boolean)

/** 一段高亮（相对行文字起点）；[current] = 当前处（加深）。 */
data class LogHighlight(val start: Int, val end: Int, val current: Boolean)

/** 全篇最多记这么多处（超出显示「+」）。 */
const val READER_MAX_HITS = 2000

/**
 * 搜索（四期·图纸五 §3.8 锁定）：按消息、片段顺序在片段正文里找（含 App 附加与时间标记，不搜表头），不区分大小写、不重叠；
 * 收满 [READER_MAX_HITS] 处立即停并记 `capped`。[query] 由调用方保证非空（VM 已去首尾空白）。
 */
fun searchReader(view: LogReaderView, query: String): LogReaderSearch {
    val hits = ArrayList<LogReaderHit>()
    for (m in view.messages) {
        m.pieces.forEachIndexed { p, piece ->
            val text = pieceText(piece)
            var i = text.indexOf(query, 0, ignoreCase = true)
            while (i >= 0) {
                hits += LogReaderHit(m.index, p, i, i + query.length)
                if (hits.size >= READER_MAX_HITS) return LogReaderSearch(query, hits, capped = true)
                i = text.indexOf(query, i + query.length, ignoreCase = true)
            }
        }
    }
    return LogReaderSearch(query, hits, capped = false)
}

/** 当前筛选下可见的命中（保持顺序）：被芯片滤掉的消息不参与「第几处 / 共几处」和上下跳。 */
fun LogReaderSearch.visibleHits(view: LogReaderView, filter: LogReaderFilter): List<LogReaderHit> =
    hits.filter { filter.matches(view.messages[it.message - 1]) }

/** 各筛选类里的命中数（芯片计数）。 */
fun LogReaderSearch.countsBy(view: LogReaderView): Map<LogReaderFilter, Int> =
    LogReaderFilter.entries.associateWith { f -> hits.count { f.matches(view.messages[it.message - 1]) } }

/** 一行文字 [from, to) 里的高亮：同一条同一片、与区间相交的命中，裁到交集后减 [from]。 */
fun highlightsIn(hits: List<LogReaderHit>, message: Int, piece: Int, from: Int, to: Int, current: LogReaderHit?): List<LogHighlight> =
    hits.filter { it.message == message && it.piece == piece && it.start < to && it.end > from }
        .map { LogHighlight(max(it.start, from) - from, min(it.end, to) - from, it == current) }

/** 当前可上下跳的命中（搜索为 null 或没有视图 → 空表）。 */
fun readerNav(search: LogReaderSearch?, view: LogReaderView?, filter: LogReaderFilter): List<LogReaderHit> =
    if (search == null || view == null) emptyList() else search.visibleHits(view, filter)
