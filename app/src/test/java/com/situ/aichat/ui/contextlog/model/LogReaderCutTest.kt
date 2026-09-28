package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogReplayRequest
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1-3（四期·图纸五 §3.4 / §3.5）：输入一律用**真** [ProviderMessageAdapter.adapt] 与真 [LogRequestShape.of]——
 * 改写器以后改规则，这里的对照先红（不复刻规则·PITFALLS §2 #30 / #33）。期望下标按改写器注释手推。
 */
class LogReaderCutTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }

    private fun m(role: String, content: String) = ChatMessageDto(role = role, content = content)

    /** 真形状 + 真改写 → 对照表（messageCount = 改写后的条数）。 */
    private fun mapOf(original: List<ChatMessageDto>): IntArray? {
        val adapted = ProviderMessageAdapter.adapt(original).messages
        return adaptedIndexMap(LogRequestShape.of(original).roles, adapted.size)
    }

    @Test
    fun indexMap_leadingThreeSystemsMerged() {
        val o = listOf(m("system", "你是林晚。"), m("system", "【对话较长】"), m("system", "【前情提要】猫"), m("user", "早"), m("assistant", "早～"), m("user", "在吗"))
        assertArrayEquals(intArrayOf(0, 0, 0, 1, 2, 3), mapOf(o))
    }

    @Test
    fun indexMap_midMarkerIntoNextUser() {
        val o = listOf(m("system", "你是林晚。"), m("user", "早"), m("assistant", "早～"), m("system", "【时间 · 今天 21:52】"), m("user", "在吗"))
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 3), mapOf(o))
    }

    @Test
    fun indexMap_markerBeforeAssistant_appendedToPreviousUser() {
        val o = listOf(m("system", "你是林晚。"), m("user", "早"), m("system", "【时间 · 今天 09:00】"), m("assistant", "早～"))
        assertArrayEquals(intArrayOf(0, 1, 1, 2), mapOf(o))
    }

    @Test
    fun indexMap_tailBlockIntoLastUser() {
        val o = listOf(m("system", "你是林晚。"), m("user", "早"), m("system", "回复规则：短一点。"), m("system", "此刻：困"))
        assertArrayEquals(intArrayOf(0, 1, 1, 1), mapOf(o))
    }

    @Test
    fun indexMap_noLeadingSystem_explainNoteShiftsAllByOne() {
        val o = listOf(m("user", "早"), m("assistant", "早～"), m("system", "【时间 · 今天 21:52】"), m("user", "在吗"))
        assertArrayEquals(intArrayOf(1, 2, 3, 3), mapOf(o))
    }

    @Test
    fun indexMap_countMismatch_null() {
        assertNull(adaptedIndexMap("suasu", 5))
        assertArrayEquals("原样 / 兜底 = 一一对应", intArrayOf(0, 1, 2), readerIndexMap(LogReaderSource.RENDERED, null, LogRequestShape("sua", listOf("a", "b", "c"), listOf(1, 1, 1)), 3))
        assertNull("条数对不上", readerIndexMap(LogReaderSource.REQUEST, LogSendAdaptation(asIs = true), LogRequestShape("sua", listOf("a", "b", "c"), listOf(1, 1, 1)), 4))
        assertNull("没有形状", readerIndexMap(LogReaderSource.REQUEST, null, null, 3))
    }

    private fun stored(messages: List<ChatMessageDto>) = LogReplayRequest.encodeForStore(json, ChatRequestDto(model = "m", messages = messages, stream = false))

    /** 开头三条合并（系统提示词自己带 "\n\n"）+ 一条中途标记（→ 开头 system 末尾附说明）。 */
    private val leadOriginal = listOf(
        m("system", "你是林晚。\n\n喜欢猫。"),
        m("system", "【对话较长，前面的部分已省略】"),
        m("system", "【时间 · 昨天 21:00】"),
        m("user", "早"),
        m("assistant", "早～"),
        m("system", "【时间 · 今天 09:00】"),
        m("user", "在吗"),
    )

    @Test
    fun splitMergedLead_recoversBoundariesByFingerprint() {
        val adapted = ProviderMessageAdapter.adapt(leadOriginal)
        assertEquals("前提", 3, adapted.leadingMerged)
        val base = sentFromRequest(json, stored(adapted.messages), asIs = false)!!
        val split = splitMergedLead(base.messages, LogRequestShape.of(leadOriginal), 3)!!
        assertEquals(
            listOf(
                LogSentPiece.Text("你是林晚。\n\n喜欢猫。"),
                LogSentPiece.Added(LogAddedKind.NOTE, "【对话较长，前面的部分已省略】"),
                LogSentPiece.Added(LogAddedKind.TIME_MARKER, "【时间 · 昨天 21:00】"),
                LogSentPiece.Added(LogAddedKind.NOTE, ProviderMessageAdapter.EXPLAIN_NOTE),
            ),
            split[0].pieces,
        )
        assertEquals("字数 / 原文不变", base.messages[0].copy(pieces = split[0].pieces), split[0])
        assertEquals(base.messages.drop(1), split.drop(1))
    }

    @Test
    fun splitMergedLead_fingerprintMismatch_null() {
        val adapted = ProviderMessageAdapter.adapt(leadOriginal)
        val base = sentFromRequest(json, stored(adapted.messages), asIs = false)!!
        val wrong = LogRequestShape.of(leadOriginal).let { it.copy(fingerprints = listOf("x", "y", "z") + it.fingerprints.drop(3)) }
        assertNull("E11：拆不回 → 不拆", splitMergedLead(base.messages, wrong, 3))
        assertNull("没有形状", splitMergedLead(base.messages, null, 3))
    }

    private val outline = LogPromptOutline(
        listOf(
            LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection(null, 11, 23, false),
            LogOutlineSection("身份", 25, 35, false), LogOutlineSection("日程", 37, 46, false),
        ),
    )

    private fun frame(kind: CacheBreakKind) = LogMapCut(kind, null, null, 0)
    private fun brk(kind: CacheBreakKind, index: Int? = null, module: String? = null) = CacheBreak(kind, messageIndex = index, moduleName = module)

    @Test
    fun cutAt_systemPromptBranches() {
        val sp = frame(CacheBreakKind.SYSTEM_PROMPT)
        assertEquals(LogReaderCutAt.InOutline(2), readerCutAt(sp, brk(CacheBreakKind.SYSTEM_PROMPT, 0, "身份"), outline, null, 0))
        assertEquals("模块本轮缺失 → 最后一个模块节之后", LogReaderCutAt.InOutline(4), readerCutAt(sp, brk(CacheBreakKind.SYSTEM_PROMPT, 0, "旧模块"), outline, null, 0))
        assertEquals("无模块名 → 第一个未分段节", LogReaderCutAt.InOutline(1), readerCutAt(sp, brk(CacheBreakKind.SYSTEM_PROMPT, 0), outline, null, 0))
        val noGap = LogPromptOutline(listOf(LogOutlineSection("核心规则", 0, 9, false)))
        assertEquals("无模块名且没有未分段节 → 0", LogReaderCutAt.InOutline(0), readerCutAt(sp, brk(CacheBreakKind.SYSTEM_PROMPT, 0), noGap, null, 0))
        assertEquals("没有目录 → 第 1 条之前", LogReaderCutAt.BeforeMessage(1), readerCutAt(sp, brk(CacheBreakKind.SYSTEM_PROMPT, 0, "身份"), null, null, 0))
    }

    @Test
    fun cutAt_asIsLeadingNote_beforeSecondMessage() {
        val map = readerIndexMap(LogReaderSource.REQUEST, LogSendAdaptation(asIs = true), LogRequestShape.of(leadOriginal), leadOriginal.size)
        assertEquals(LogReaderCutAt.BeforeMessage(2), readerCutAt(frame(CacheBreakKind.LEADING_NOTE), brk(CacheBreakKind.LEADING_NOTE, 1), outline, map, 0))
    }

    @Test
    fun cutAt_adaptedSplitLead_beforePiece_elseMergedInsideIsNull() {
        val adapted = ProviderMessageAdapter.adapt(leadOriginal)
        val a = LogSendAdaptation(asIs = false, leadingMerged = adapted.leadingMerged, midMerged = adapted.midMerged, tailMerged = adapted.tailMerged)
        val map = readerIndexMap(LogReaderSource.REQUEST, a, LogRequestShape.of(leadOriginal), adapted.messages.size)!!
        assertArrayEquals(intArrayOf(0, 0, 0, 1, 2, 3, 3), map)
        assertEquals("起始锚（形状第 2 条）→ 拆回后的第 2 片之前", LogReaderCutAt.BeforePiece(1, 2), readerCutAt(frame(CacheBreakKind.TIME_MARKER), brk(CacheBreakKind.TIME_MARKER, 2), outline, map, 3))
        assertNull("没拆回（lead = 0）→ 画不出", readerCutAt(frame(CacheBreakKind.LEADING_NOTE), brk(CacheBreakKind.LEADING_NOTE, 1), outline, map, 0))
        assertEquals("中途标记变了 → 它并进的那条用户消息之前", LogReaderCutAt.BeforeMessage(4), readerCutAt(frame(CacheBreakKind.TIME_MARKER), brk(CacheBreakKind.TIME_MARKER, 5), outline, map, 3))
        assertNull("落在合并进用户消息的内部（非开头）→ null", readerCutAt(frame(CacheBreakKind.HISTORY_CHANGED), brk(CacheBreakKind.HISTORY_CHANGED, 6), outline, map, 3))
    }

    @Test
    fun cutAt_noMapOrIndex_null() {
        val f = frame(CacheBreakKind.HISTORY_CHANGED)
        assertNull("对照不成立（E14）", readerCutAt(f, brk(CacheBreakKind.HISTORY_CHANGED, 3), outline, null, 0))
        assertNull("下标越界", readerCutAt(f, brk(CacheBreakKind.HISTORY_CHANGED, 9), outline, intArrayOf(0, 1, 2), 0))
        assertNull("没有下标", readerCutAt(f, brk(CacheBreakKind.HISTORY_CHANGED), outline, intArrayOf(0, 1, 2), 0))
    }
}
