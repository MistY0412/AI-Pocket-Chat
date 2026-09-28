package com.situ.aichat.diagnostics

import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.saver.CacheSaverLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-2（四期·图纸三 §3.7 / E26）：缓存断点七种「有上一条」的判定（NO_PREVIOUS / NO_DATA 由 [LogAnalysisReader]
 * 产出，见 `LogAnalysisReaderTest`）+ SYSTEM_PROMPT 定位到 / 定位不到模块 + 判定顺序。形状全部手工构造，
 * `cachedTokensEstimate` 按 §3.7 手算（默认 = 上一条前 i 条 token 之和；SYSTEM_PROMPT = 前 k 个前置模块 token 之和）。
 */
class CacheBreakAnalyzerTest {

    private fun shape(roles: String, fps: List<String>, tokens: List<Int>, markers: List<Int> = emptyList()) =
        LogRequestShape(roles, fps, tokens, markers.map { LogTimeMarker(it, "【时间 · 9月28日 周一 晚上】") })

    private fun seg(name: String, tokens: Int, fp: String?, position: String = ContextSegment.POSITION_PREFIX) =
        ContextSegment(name, null, tokens * 2, tokens, position, fp)

    private fun analyze(cur: LogRequestShape, prev: LogRequestShape, curSegs: List<ContextSegment> = emptyList(), prevSegs: List<ContextSegment> = emptyList()) =
        CacheBreakAnalyzer.analyze(cur, curSegs, prev, prevSegs)

    @Test
    fun tail_previousIsWholePrefix() {
        val prev = shape("su", listOf("S", "U1"), listOf(10, 5))
        val cur = shape("suau", listOf("S", "U1", "A1", "U2"), listOf(10, 5, 7, 3))
        assertEquals(CacheBreak(CacheBreakKind.TAIL, messageIndex = 2, cachedTokensEstimate = 15, totalTokensEstimate = 25), analyze(cur, prev))
    }

    @Test
    fun systemPrompt_locatedToModule() {
        val prev = shape("su", listOf("S1", "U1"), listOf(100, 5))
        val cur = shape("su", listOf("S2", "U1"), listOf(110, 5))
        val prevSegs = listOf(seg("核心规则", 40, "x"), seg("人设", 60, "y"), seg("对话历史", 5, null, ContextSegment.POSITION_HISTORY))
        val curSegs = listOf(seg("核心规则", 40, "x"), seg("人设", 70, "y2"), seg("对话历史", 5, null, ContextSegment.POSITION_HISTORY))
        assertEquals(
            CacheBreak(CacheBreakKind.SYSTEM_PROMPT, messageIndex = 0, moduleName = "人设", cachedTokensEstimate = 40, totalTokensEstimate = 115),
            analyze(cur, prev, curSegs, prevSegs),
        )
    }

    @Test
    fun systemPrompt_moduleAddedAtEnd_namesTheNewOne() {
        val prev = shape("su", listOf("S1", "U1"), listOf(40, 5))
        val cur = shape("su", listOf("S2", "U1"), listOf(55, 5))
        val r = analyze(cur, prev, listOf(seg("核心规则", 40, "x"), seg("新模块", 15, "n")), listOf(seg("核心规则", 40, "x")))
        assertEquals(CacheBreakKind.SYSTEM_PROMPT, r.kind)
        assertEquals("新模块", r.moduleName)
        assertEquals(40, r.cachedTokensEstimate)
    }

    @Test
    fun systemPrompt_notLocated_whenAllModulesSame() {
        val prev = shape("su", listOf("S1", "U1"), listOf(100, 5))
        val cur = shape("su", listOf("S2", "U1"), listOf(100, 5))
        val segs = listOf(seg("核心规则", 40, "x"), seg("人设", 60, "y"))
        assertEquals(
            CacheBreak(CacheBreakKind.SYSTEM_PROMPT, messageIndex = 0, moduleName = null, cachedTokensEstimate = 0, totalTokensEstimate = 105),
            analyze(cur, prev, segs, segs),
        )
        // 旧分段（无指纹）只能比名字：名字全同 = 定位不到
        val old = listOf(seg("核心规则", 40, null), seg("人设", 60, null))
        assertEquals(null, analyze(cur, prev, segs, old).moduleName)
    }

    @Test
    fun timeMarker() {
        val prev = shape("suasu", listOf("S", "U1", "A1", "T1", "U2"), listOf(10, 3, 4, 2, 5), markers = listOf(3))
        val cur = shape("suasua", listOf("S", "U1", "A1", "T1x", "U2", "A2"), listOf(10, 3, 4, 2, 5, 6))
        assertEquals(CacheBreak(CacheBreakKind.TIME_MARKER, messageIndex = 3, cachedTokensEstimate = 17, totalTokensEstimate = 30), analyze(cur, prev))
    }

    /**
     * 复核 R1 🔴-2：历史起点前的起始锚（写的是第一条历史的时刻）变了——第一条历史也换了 = 窗口前滑；
     * 第一条历史没换（过了午夜「昨天」改叫「9月26日」）= 时间标记。
     */
    @Test
    fun startAnchorChanged_windowSlid_vs_midnightRelabel() {
        val prev = shape("ssuau", listOf("S", "A0", "U0", "A1", "U1"), listOf(10, 2, 3, 4, 5), markers = listOf(1))
        val slid = shape("ssaua", listOf("S", "A1x", "A1", "U1", "A2"), listOf(10, 2, 4, 5, 6), markers = listOf(1))
        assertEquals(CacheBreak(CacheBreakKind.HISTORY_WINDOW_SLID, messageIndex = 1, cachedTokensEstimate = 10, totalTokensEstimate = 27), analyze(slid, prev))
        val relabeled = shape("ssuaua", listOf("S", "A0x", "U0", "A1", "U1", "A2"), listOf(10, 2, 3, 4, 5, 6), markers = listOf(1))
        assertEquals(CacheBreak(CacheBreakKind.TIME_MARKER, messageIndex = 1, cachedTokensEstimate = 10, totalTokensEstimate = 30), analyze(relabeled, prev))
    }

    @Test
    fun tail_afterLastNonSystem_suffixChanged() {
        val prev = shape("suas", listOf("S", "U1", "A1", "SUF"), listOf(10, 3, 4, 8))
        val cur = shape("suas", listOf("S", "U1", "A1", "SUF2"), listOf(10, 3, 4, 9))
        assertEquals(CacheBreak(CacheBreakKind.TAIL, messageIndex = 3, cachedTokensEstimate = 17, totalTokensEstimate = 26), analyze(cur, prev))
    }

    @Test
    fun leadingNote_beforeFirstNonSystem() {
        val prev = shape("ssu", listOf("S", "N1", "U1"), listOf(10, 6, 3))
        val cur = shape("ssu", listOf("S", "N2", "U1"), listOf(10, 7, 3))
        assertEquals(CacheBreak(CacheBreakKind.LEADING_NOTE, messageIndex = 1, cachedTokensEstimate = 10, totalTokensEstimate = 20), analyze(cur, prev))
        // 全是 system（firstNon < 0）也归前置说明
        val allSys = shape("ss", listOf("S", "N1"), listOf(10, 6))
        assertEquals(CacheBreakKind.LEADING_NOTE, analyze(shape("ss", listOf("S", "N2"), listOf(10, 6)), allSys).kind)
    }

    @Test
    fun savedBlock_matchedBySegmentFingerprint() {
        val prev = shape("susua", listOf("S", "U1", "BLK", "U2", "A2"), listOf(10, 3, 20, 4, 5))
        val cur = shape("susua", listOf("S", "U1", "BLK2", "U2", "A2"), listOf(10, 3, 22, 4, 5))
        val prevSegs = listOf(seg(CacheSaverLayout.SEGMENT_NAME, 20, "BLK", ContextSegment.POSITION_HISTORY))
        assertEquals(
            CacheBreak(CacheBreakKind.SAVED_BLOCK, messageIndex = 2, cachedTokensEstimate = 13, totalTokensEstimate = 44),
            analyze(cur, prev, emptyList(), prevSegs),
        )
    }

    @Test
    fun historyWindowSlid_firstHistoryDiffers() {
        val prev = shape("sua", listOf("S", "U1", "A1"), listOf(10, 3, 4))
        val cur = shape("sua", listOf("S", "U2", "A2"), listOf(10, 5, 6))
        assertEquals(CacheBreak(CacheBreakKind.HISTORY_WINDOW_SLID, messageIndex = 1, cachedTokensEstimate = 10, totalTokensEstimate = 21), analyze(cur, prev))
    }

    @Test
    fun historyChanged_midHistory_saverSegmentNotMatching() {
        val prev = shape("suaua", listOf("S", "U1", "A1", "U2", "A2"), listOf(10, 3, 4, 5, 6))
        val cur = shape("suaua", listOf("S", "U1", "A1", "U2x", "A2"), listOf(10, 3, 4, 5, 6))
        val prevSegs = listOf(seg(CacheSaverLayout.SEGMENT_NAME, 20, "OTHER", ContextSegment.POSITION_HISTORY))
        assertEquals(
            CacheBreak(CacheBreakKind.HISTORY_CHANGED, messageIndex = 3, cachedTokensEstimate = 17, totalTokensEstimate = 28),
            analyze(cur, prev, emptyList(), prevSegs),
        )
    }

    @Test
    fun order_systemPromptBeatsTimeMarker_atIndexZero() {
        val prev = shape("su", listOf("T", "U1"), listOf(10, 3), markers = listOf(0))
        val cur = shape("su", listOf("T2", "U1"), listOf(10, 3))
        assertEquals(CacheBreakKind.SYSTEM_PROMPT, analyze(cur, prev).kind)
    }

    @Test
    fun order_noLeadingSystem_indexZeroIsHistoryWindowSlid() {
        val prev = shape("ua", listOf("U1", "A1"), listOf(3, 4))
        val cur = shape("ua", listOf("U0", "A1"), listOf(3, 4))
        assertEquals(CacheBreak(CacheBreakKind.HISTORY_WINDOW_SLID, messageIndex = 0, cachedTokensEstimate = 0, totalTokensEstimate = 7), analyze(cur, prev))
    }
}
