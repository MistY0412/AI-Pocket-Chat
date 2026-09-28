package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-6（四期·图纸五 §3.8）：不重叠、大小写不敏感、2000 上限、按筛选的可见命中与分类计数、高亮裁剪与当前处。 */
class LogReaderSearchTest {

    private val noPrev = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())

    private fun view(vararg messages: LogSentMessage) =
        buildReaderView(LogSentView(messages.toList()), LogReaderSource.RENDERED, null, emptyList(), noPrev, null, null, truncated = false)

    private fun msg(i: Int, role: LogSentRole, vararg pieces: LogSentPiece) = LogSentMessage(i, role, 0, pieces.toList())

    @Test
    fun nonOverlapping_andAcrossPieces() {
        val v = view(msg(1, LogSentRole.USER, LogSentPiece.Text("aaa")), msg(2, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.NOTE, "xaax"), LogSentPiece.Text("aa")))
        val s = searchReader(v, "aa")
        assertEquals("「aaa」搜「aa」只 1 处", listOf(LogReaderHit(1, 0, 0, 2), LogReaderHit(2, 0, 1, 3), LogReaderHit(2, 1, 0, 2)), s.hits)
        assertFalse(s.capped)
        assertEquals("aa", s.query)
    }

    @Test
    fun caseInsensitive() {
        val v = view(msg(1, LogSentRole.USER, LogSentPiece.Text("Hello hello HELLO")))
        assertEquals(listOf(0, 6, 12), searchReader(v, "hello").hits.map { it.start }) // E23
    }

    @Test
    fun cappedAt2000() {
        val v = view(msg(1, LogSentRole.USER, LogSentPiece.Text("猫".repeat(2_500))))
        val s = searchReader(v, "猫")
        assertEquals(2_000, s.hits.size) // E22
        assertTrue(s.capped)
        assertEquals(LogReaderHit(1, 0, 1_999, 2_000), s.hits.last())
        assertFalse("不到上限不记 capped", searchReader(view(msg(1, LogSentRole.USER, LogSentPiece.Text("猫".repeat(1_999)))), "猫").capped)
    }

    private val mixed = view(
        msg(1, LogSentRole.SYSTEM, LogSentPiece.Text("紫色的天")),
        msg(2, LogSentRole.USER, LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 紫】"), LogSentPiece.Text("紫")),
        msg(3, LogSentRole.ASSISTANT, LogSentPiece.Text("紫紫")),
    )

    @Test
    fun visibleHits_byFilter_andCountsByClass() {
        val s = searchReader(mixed, "紫")
        assertEquals(5, s.hits.size)
        assertEquals("被筛掉的消息不参与（E25）", listOf(LogReaderHit(3, 0, 0, 1), LogReaderHit(3, 0, 1, 2)), s.visibleHits(mixed, LogReaderFilter.ASSISTANT))
        assertEquals(s.hits, s.visibleHits(mixed, LogReaderFilter.ALL))
        assertEquals(
            mapOf(
                LogReaderFilter.ALL to 5, LogReaderFilter.SYSTEM to 1, LogReaderFilter.USER to 2,
                LogReaderFilter.ASSISTANT to 2, LogReaderFilter.ADDED to 2, LogReaderFilter.TOOL to 0,
            ),
            s.countsBy(mixed),
        )
        assertEquals(s.visibleHits(mixed, LogReaderFilter.USER), readerNav(s, mixed, LogReaderFilter.USER))
        assertEquals(emptyList<LogReaderHit>(), readerNav(null, mixed, LogReaderFilter.ALL))
        assertEquals(emptyList<LogReaderHit>(), readerNav(s, null, LogReaderFilter.ALL))
    }

    @Test
    fun highlights_clippedToRow_andCurrent() {
        val hits = listOf(LogReaderHit(1, 0, 2, 6), LogReaderHit(1, 0, 8, 10), LogReaderHit(1, 1, 0, 3), LogReaderHit(2, 0, 0, 1))
        assertEquals(
            "行 [4, 9)：第一处裁成 [4, 6) → [0, 2)；第二处 [8, 9) → [4, 5)",
            listOf(LogHighlight(0, 2, current = false), LogHighlight(4, 5, current = true)),
            highlightsIn(hits, 1, 0, 4, 9, current = LogReaderHit(1, 0, 8, 10)),
        )
        assertEquals("不相交 / 别的片 / 别的条不算", emptyList<LogHighlight>(), highlightsIn(hits, 1, 0, 6, 8, current = null))
    }
}
