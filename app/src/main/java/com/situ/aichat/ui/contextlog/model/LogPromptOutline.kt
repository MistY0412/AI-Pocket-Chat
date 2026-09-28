package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.prompt.ContextSegment

/** 系统提示词目录一节：title = 模块名（null = 未分段内容，如世界书）；[start, end) = 在系统提示词文字里的区间（不含节间 "\n\n"）。 */
data class LogOutlineSection(val title: String?, val start: Int, val end: Int, val changed: Boolean) {
    val chars: Int get() = end - start
}

data class LogPromptOutline(val sections: List<LogOutlineSection>)

/** 系统提示词里模块之间的连接符（= PromptBuilderModules.kt:217 prefixParts.joinToString 的分隔·REDLINES §1 登记）。 */
const val PROMPT_PART_SEPARATOR = "\n\n"

/**
 * 系统提示词目录切分（四期·图纸五 §3.3 锁定）：按前置分段的顺序，在「游标处或其后某个 [PROMPT_PART_SEPARATOR] 之后」找
 * 长度 = `charCount`、[ContextSegment.fingerprintOf] 相等的那一截（分段指纹与系统提示词里那段文字同源）；两段之间对不上的空隙
 * （世界书前 / 后锚等）成一节「未分段内容」。v51 前的记录（指纹 null）只按字数从游标处顺切，切不齐整个不拆（返回 null）。
 * 没有前置分段 / 一段也没定位到 → null。
 */
fun promptOutline(text: String, segments: List<ContextSegment>, previousSegments: List<ContextSegment>): LogPromptOutline? {
    val prefix = segments.filter { it.position == ContextSegment.POSITION_PREFIX }
    if (prefix.isEmpty()) return null
    var cursor = 0
    val out = ArrayList<LogOutlineSection>()
    var located = 0

    fun gap(a: Int, b: Int) {
        var g0 = a
        var g1 = b
        while (g0 < g1 && text[g0] == '\n') g0++
        while (g1 > g0 && text[g1 - 1] == '\n') g1--
        if (g0 < g1 && text.substring(g0, g1).isNotBlank()) out += LogOutlineSection(null, g0, g1, changed = false)
    }

    for (seg in prefix) {
        val s: Int = if (seg.fingerprint != null) {
            locate(text, cursor, seg) ?: continue // 找不到 → 跳过这一段（它的文字落进下一处空隙）
        } else {
            val e = cursor + seg.charCount
            if (e <= text.length && (e == text.length || text.startsWith(PROMPT_PART_SEPARATOR, e))) cursor else return null
        }
        val e = s + seg.charCount
        gap(cursor, s)
        out += LogOutlineSection(seg.name, s, e, changed = changedOf(seg, previousSegments))
        located++
        cursor = e
        if (text.startsWith(PROMPT_PART_SEPARATOR, cursor)) cursor += PROMPT_PART_SEPARATOR.length
    }
    gap(cursor, text.length)
    return if (located == 0) null else LogPromptOutline(out)
}

/** 候选起点依次为 [cursor]、以及其后每个分隔符之后；越过全文长度即停。 */
private fun locate(text: String, cursor: Int, seg: ContextSegment): Int? {
    var s = cursor
    var j = text.indexOf(PROMPT_PART_SEPARATOR, cursor)
    while (true) {
        val e = s + seg.charCount
        if (e > text.length) return null
        if (ContextSegment.fingerprintOf(text.substring(s, e)) == seg.fingerprint) return s
        if (j < 0) return null
        s = j + PROMPT_PART_SEPARATOR.length
        j = text.indexOf(PROMPT_PART_SEPARATOR, j + 1)
    }
}

/** 上一轮同名段存在、两边指纹都有且不相等（与地图「每轮会变」同口径·图纸四 §3.6 第 3 步）。 */
private fun changedOf(seg: ContextSegment, previousSegments: List<ContextSegment>): Boolean {
    val prev = previousSegments.firstOrNull { it.name == seg.name } ?: return false
    return prev.fingerprint != null && seg.fingerprint != null && prev.fingerprint != seg.fingerprint
}
