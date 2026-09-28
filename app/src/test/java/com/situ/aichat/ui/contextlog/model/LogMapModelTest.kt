package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.diagnostics.LogTimeMarker
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.saver.CacheSaverLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-6（四期·图纸四 §3.6）：行高 / 合并 / 每轮会变 / 挪位块 / 时间标记 / 红框九种位置 / 迷你地图。数值手算。 */
class LogMapModelTest {

    private fun shape(roles: String, tokens: List<Int>, fps: List<String> = roles.indices.map { "f$it" }, markers: List<LogTimeMarker> = emptyList()) =
        LogRequestShape(roles, fps, tokens, markers)

    private fun cmp(kind: CacheBreakKind, module: String? = null, cached: Int = 0, total: Int = 0, prev: Long? = 1_000L, prevSegs: List<ContextSegment> = emptyList()) =
        CacheComparison(CacheBreak(kind, moduleName = module, cachedTokensEstimate = cached, totalTokensEstimate = total), prev, prevSegs)

    private fun seg(name: String, tokens: Int, fp: String?, position: String = ContextSegment.POSITION_PREFIX) =
        ContextSegment(name, null, tokens * 2, tokens, position, fp)

    // roles: 0 s 系统提示 300 / 1 s 前置说明 20 / 2 u 100 / 3 a 100 / 4 s 时间标记 10 / 5 u 100 / 6 s 末尾 50 → total 680
    private val pause = "【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】"
    private val plain = shape("ssuasus", listOf(300, 20, 100, 100, 10, 100, 50), markers = listOf(LogTimeMarker(4, pause)))

    @Test
    fun heightFormula_threeBands() {
        assertEquals("340×10/1000 = 3.4 → 下限 20", 20, heightOf(10, 1_000))
        assertEquals("340×500/1000 = 170", 170, heightOf(500, 1_000))
        assertEquals("340×900/1000 = 306 → 上限 240", 240, heightOf(900, 1_000))
        assertEquals("340×3/40 = 25.5 → 26", 26, heightOf(3, 40))
        assertEquals("total 0 → 20", 20, heightOf(5, 0))
    }

    @Test
    fun noSegments_syntheticBlocks_history_ticks_tail_cutBeforeTail() {
        val map = buildContextMap(plain, emptyList(), cmp(CacheBreakKind.TAIL, cached = 630, total = 680), segmentsPurged = true)
        assertEquals(
            listOf(
                LogMapZoneLabel(LogMapZone.PREFIX),
                LogMapBlock(LogMapZone.PREFIX, emptyList(), 0, LogMapLabel.SYSTEM_PROMPT, 300, false, false, 150),
                LogMapBlock(LogMapZone.PREFIX, emptyList(), 0, LogMapLabel.LEADING_NOTES, 20, false, false, 20),
                LogMapZoneLabel(LogMapZone.HISTORY),
                LogMapHistory(3, 310, 155, listOf(200f / 310f), listOf(LogMarkerLine("今天 21:52", "距离上条消息过去了约 12 分钟", LogMarkerKind.PAUSE)), 0),
                LogMapCut(CacheBreakKind.TAIL, null, 1_000L, 50),
                LogMapZoneLabel(LogMapZone.TAIL),
                LogMapBlock(LogMapZone.TAIL, emptyList(), 0, LogMapLabel.TAIL_BLOCKS, 50, false, false, 25),
            ),
            map.items,
        )
        assertNull(map.note)
        assertTrue(map.segmentsPurged)
        assertEquals(680, map.totalTokens)

        val mini = buildMiniMap(map)
        assertEquals(listOf(LogMiniStyle.PREFIX, LogMiniStyle.HISTORY, LogMiniStyle.TAIL), mini.parts.map { it.style })
        assertEquals(320f / 680f, mini.parts[0].fraction, 1e-6f)
        assertEquals(310f / 680f, mini.parts[1].fraction, 1e-6f)
        assertEquals(630f / 680f, mini.cutFraction!!, 1e-6f)
    }

    @Test
    fun cutPositions_allKinds() {
        fun cutIndex(kind: CacheBreakKind, module: String? = null, s: LogRequestShape = plain) =
            buildContextMap(s, emptyList(), cmp(kind, module), false).items.indexOfFirst { it is LogMapCut }
        // 未插红框时：0 前置标签 / 1 系统提示 / 2 前置说明 / 3 聊天标签 / 4 聊天行 / 5 末尾标签 / 6 末尾块
        assertEquals("SYSTEM_PROMPT 无模块 → 前置区第一块前", 1, cutIndex(CacheBreakKind.SYSTEM_PROMPT))
        assertEquals("模块名找不到块 → 同上（E22 兜底）", 1, cutIndex(CacheBreakKind.SYSTEM_PROMPT, "不存在"))
        assertEquals(2, cutIndex(CacheBreakKind.LEADING_NOTE))
        assertEquals(4, cutIndex(CacheBreakKind.TIME_MARKER))
        assertEquals(4, cutIndex(CacheBreakKind.HISTORY_WINDOW_SLID))
        assertEquals(4, cutIndex(CacheBreakKind.HISTORY_CHANGED))
        assertEquals("没有挪位块 → 按 HISTORY_CHANGED", 4, cutIndex(CacheBreakKind.SAVED_BLOCK))
        assertEquals(5, cutIndex(CacheBreakKind.TAIL))
        val noLeading = shape("suasus", listOf(300, 100, 100, 10, 100, 50))
        assertEquals("没有前置说明块 → 聊天记录区标签前", 2, cutIndex(CacheBreakKind.LEADING_NOTE, s = noLeading))

        for (kind in listOf(CacheBreakKind.NO_PREVIOUS, CacheBreakKind.NO_DATA)) {
            val map = buildContextMap(plain, emptyList(), cmp(kind), false)
            assertTrue(map.items.none { it is LogMapCut })
            assertEquals(kind, map.note)
            assertNull("没有红框 → 迷你地图无红线（E21）", buildMiniMap(map).cutFraction)
        }
    }

    @Test
    fun cutCarriesPreviousTimeAndNonNegativeUncached() {
        val cut = buildContextMap(plain, emptyList(), cmp(CacheBreakKind.HISTORY_CHANGED, cached = 900, total = 680, prev = 42L), false)
            .items.filterIsInstance<LogMapCut>().single()
        assertEquals(42L, cut.previousTimeMillis)
        assertEquals("cached > total → 0", 0, cut.uncachedTokens)
    }

    @Test
    fun merge5Percent_variableAndTargetStayAlone() {
        val s = shape("su", listOf(990, 10))
        val segs = listOf(seg("核心规则", 500, "k"), seg("人设", 400, "p-new"), seg("A", 20, "a"), seg("B", 20, "b"), seg("C", 10, "c"), seg("D", 40, "d"))
        fun prefixBlocks(c: CacheComparison) = buildContextMap(s, segs, c, false).items.filterIsInstance<LogMapBlock>().filter { it.zone == LogMapZone.PREFIX }

        val withVariableC = prefixBlocks(cmp(CacheBreakKind.HISTORY_CHANGED, prevSegs = listOf(seg("人设", 400, "p-old"), seg("C", 10, "c-old"))))
        assertEquals(listOf(listOf("核心规则"), listOf("人设"), listOf("A", "B"), listOf("C"), listOf("D")), withVariableC.map { it.names })
        assertEquals(listOf(false, true, false, true, false), withVariableC.map { it.variable })
        assertEquals(40, withVariableC[2].tokens)
        assertEquals(0, withVariableC[2].moreCount)

        val allSmallMerge = prefixBlocks(cmp(CacheBreakKind.HISTORY_CHANGED, prevSegs = listOf(seg("人设", 400, "p-new"))))
        assertEquals(listOf(listOf("核心规则"), listOf("人设"), listOf("A", "B")), allSmallMerge.map { it.names })
        assertEquals("A+B+C+D = 90，其余 2 个", 90, allSmallMerge[2].tokens)
        assertEquals(2, allSmallMerge[2].moreCount)
        assertEquals("合并后按合并 token 算行高：340×90/1000 = 30.6 → 31", 31, allSmallMerge[2].heightDp)
        assertEquals("上一条没有同名段 / 指纹 null → 不算会变", false, allSmallMerge[1].variable)

        val map = buildContextMap(s, segs, cmp(CacheBreakKind.SYSTEM_PROMPT, module = "B"), false)
        val blocks = map.items.filterIsInstance<LogMapBlock>().filter { it.zone == LogMapZone.PREFIX }
        assertEquals("红框目标 B 保持独立", listOf(listOf("核心规则"), listOf("人设"), listOf("A"), listOf("B"), listOf("C", "D")), blocks.map { it.names })
        val cutAt = map.items.indexOfFirst { it is LogMapCut }
        assertEquals(listOf("B"), (map.items[cutAt + 1] as LogMapBlock).names)
    }

    @Test
    fun savedBlock_afterHistory_variable_andCutBeforeIt() {
        val s = shape("suasu", listOf(100, 50, 50, 30, 50), fps = listOf("S", "U1", "A1", "SAVED", "U2"))
        val segs = listOf(seg("人设", 100, "p"), seg(CacheSaverLayout.SEGMENT_NAME, 30, "SAVED", ContextSegment.POSITION_HISTORY))
        val map = buildContextMap(s, segs, cmp(CacheBreakKind.SAVED_BLOCK), false)
        val i = map.items
        val history = i.filterIsInstance<LogMapHistory>().single()
        assertEquals("挪位块不算进聊天记录", 150, history.tokens)
        assertEquals(3, history.messageCount)
        val saved = i.filterIsInstance<LogMapBlock>().single { it.savedBlock }
        assertEquals(LogMapBlock(LogMapZone.HISTORY, listOf(CacheSaverLayout.SEGMENT_NAME), 0, null, 30, true, true, 36), saved)
        assertTrue("红框在挪位块之前", i[i.indexOf(saved) - 1] is LogMapCut)
        assertTrue("挪位块在聊天记录行之后", i.indexOf(saved) > i.indexOf(history))

        val mini = buildMiniMap(map)
        assertEquals(listOf(LogMiniStyle.PREFIX, LogMiniStyle.HISTORY, LogMiniStyle.VARIABLE), mini.parts.map { it.style })
        assertEquals(250f / 280f, mini.cutFraction!!, 1e-6f)
    }

    @Test
    fun markers_lastSixAndMore_ticksByTokens() {
        val roles = "su" + "s".repeat(8) + "u"
        val markers = (2..9).map { LogTimeMarker(it, "【时间 · M$it】") }
        val history = buildContextMap(shape(roles, List(11) { 10 }, markers = markers), emptyList(), cmp(CacheBreakKind.TAIL), false)
            .items.filterIsInstance<LogMapHistory>().single()
        assertEquals((4..9).map { LogMarkerLine("M$it", null, LogMarkerKind.TICK) }, history.markers)
        assertEquals(2, history.moreMarkers)
        assertEquals("区间 1..10 共 100；下标 k 之前 = (k − 1) × 10", (1..8).map { it / 10f }, history.ticks)
        assertEquals(2, history.messageCount)
    }

    @Test
    fun parseMarker_threeKinds() {
        assertEquals(LogMarkerLine("今天 21:52", "距离上条消息过去了约 12 分钟", LogMarkerKind.PAUSE), parseMarker(pause))
        assertEquals(LogMarkerLine("今天 22:06", null, LogMarkerKind.TICK), parseMarker("【时间 · 今天 22:06】"))
        assertEquals(LogMarkerLine("9月25日 周四", null, LogMarkerKind.SCENE), parseMarker("【时间 · 9月25日 周四——以上对话发生在两天前】"))
    }

    @Test
    fun noSystemMessages_historyOnly() {
        val map = buildContextMap(shape("uau", listOf(10, 20, 30)), emptyList(), cmp(CacheBreakKind.TAIL), false)
        assertEquals(LogMapZoneLabel(LogMapZone.HISTORY), map.items.first())
        assertTrue(map.items.none { it is LogMapBlock })
        assertTrue("没有末尾块区 → 红框放最后", map.items.last() is LogMapCut)
    }

    @Test
    fun suffixSegments_eachABlock() {
        val s = shape("sus", listOf(100, 50, 50))
        val segs = listOf(seg("人设", 100, "p"), seg("回复规则", 30, "r", ContextSegment.POSITION_SUFFIX), seg("格式", 20, "g", ContextSegment.POSITION_SUFFIX))
        val tail = buildContextMap(s, segs, cmp(CacheBreakKind.NO_PREVIOUS), false).items.filterIsInstance<LogMapBlock>().filter { it.zone == LogMapZone.TAIL }
        assertEquals(listOf(listOf("回复规则"), listOf("格式")), tail.map { it.names })
    }

    /** 复核 R1：上一轮末尾的模块这一轮没了 → 红框在这一轮最后一个模块之后、不写模块名（原 T-3 放第一块前、位置与「约 N tk」对不上）。 */
    @Test
    fun systemPrompt_moduleVanished_cutAfterLastSegment_noModuleName() {
        val s = shape("su", listOf(300, 100))
        val segs = listOf(seg("人设", 200, "p"), seg("记忆", 100, "m"))
        val map = buildContextMap(s, segs, cmp(CacheBreakKind.SYSTEM_PROMPT, module = "朋友圈上下文", cached = 300, total = 400), false)
        val i = map.items
        val cutAt = i.indexOfFirst { it is LogMapCut }
        assertEquals("记忆", (i[cutAt - 1] as LogMapBlock).names.single())
        assertNull("找不到的模块不写进红框（走「系统提示词」文案·E22）", (i[cutAt] as LogMapCut).moduleName)
        assertEquals("红线在前置区之后：300 / 400", 300f / 400f, buildMiniMap(map).cutFraction!!, 1e-6f)
    }

    /** 复核 R1：没有任何前置块时系统提示词的断点放最前（原 T-5 放最后，迷你地图红线跑到最右）。 */
    @Test
    fun systemPrompt_noPrefixBlocks_cutFirst() {
        val map = buildContextMap(shape("ua", listOf(50, 50)), emptyList(), cmp(CacheBreakKind.SYSTEM_PROMPT), false)
        assertTrue(map.items.first() is LogMapCut)
        assertEquals(0f, buildMiniMap(map).cutFraction!!, 1e-6f)
    }

    /** 复核 R1：变的是历史之前那条起始时间锚 → 红框在「聊天记录前的说明」块之前（它就在那块里）。 */
    @Test
    fun timeMarker_atStartAnchor_cutBeforeLeadingBlock() {
        val cutIndex = buildContextMap(plain, emptyList(), CacheComparison(CacheBreak(CacheBreakKind.TIME_MARKER, messageIndex = 1), 1_000L, emptyList()), false)
            .items.indexOfFirst { it is LogMapCut }
        assertEquals("0 前置标签 / 1 系统提示 / 2 红框 / 3 前置说明", 2, cutIndex)
        val inHistory = buildContextMap(plain, emptyList(), CacheComparison(CacheBreak(CacheBreakKind.TIME_MARKER, messageIndex = 4), 1_000L, emptyList()), false)
            .items.indexOfFirst { it is LogMapCut }
        assertEquals("历史里的时间标记照旧在聊天记录行之前", 4, inHistory)
    }

    /** 复核 R1：分段估算的块加起来 < 整条请求（世界书等不成段）→ 迷你地图按各部分之和归一，红线与横条同一把尺。 */
    @Test
    fun miniMap_normalizedByPartsTotal() {
        val s = shape("su", listOf(1_000, 100)) // 整条 1100，但分段只认出 400
        val segs = listOf(seg("人设", 300, "p"), seg("朋友圈上下文", 100, "f-new"))
        val map = buildContextMap(s, segs, cmp(CacheBreakKind.SYSTEM_PROMPT, module = "朋友圈上下文", prevSegs = listOf(seg("朋友圈上下文", 100, "f-old"))), false)
        assertEquals(1_100, map.totalTokens)
        val mini = buildMiniMap(map)
        assertEquals(1f, mini.parts.sumOf { it.fraction.toDouble() }.toFloat(), 1e-6f)
        assertEquals("人设 300 / 各部分 500", 300f / 500f, mini.cutFraction!!, 1e-6f)
        assertEquals(listOf(LogMiniStyle.PREFIX, LogMiniStyle.VARIABLE, LogMiniStyle.HISTORY), mini.parts.map { it.style })
    }

    /** T1-4（四期·图纸五 §2.2）：分段被清掉 = 对话 / 语音通话且分段空；有分段 / 后台来源 → false。 */
    @Test
    fun segmentsPurgedOf_chatAndVoiceOnly() {
        fun e(source: String, segs: String) = LogEntryEntity(id = 1, source = source, contextSegmentsJson = segs)
        assertTrue(segmentsPurgedOf(e(LogSource.CHAT, "")))
        assertTrue(segmentsPurgedOf(e(LogSource.VOICE_CALL, "")))
        assertFalse(segmentsPurgedOf(e(LogSource.CHAT, "[]x")))
        assertFalse(segmentsPurgedOf(e(LogSource.DIARY_GENERATION, "")))
    }
}
