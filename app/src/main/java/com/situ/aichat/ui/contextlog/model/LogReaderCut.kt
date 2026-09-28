package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.HistoryTimeDivider

/** 断点在阅读器里的落点（四期·图纸五 §3.5）。 */
sealed interface LogReaderCutAt {
    /** 目录第 [before] 节之前（== 节数 = 目录末尾）。 */
    data class InOutline(val before: Int) : LogReaderCutAt
    data class BeforePiece(val message: Int, val piece: Int) : LogReaderCutAt
    /** [message] = 阅读器第几条（从 1 起）。 */
    data class BeforeMessage(val message: Int) : LogReaderCutAt
}

/**
 * 改写时合并进开头 system 的几条（形状下标 0 until [leadCount]）拆回（§3.4 锁定）：第 0 条留作正文（系统提示词），其余成 App 附加框。
 * 边界用形状的逐条指纹找回——与形状**同一函数** [ContextSegment.messageFingerprintOf]（PITFALLS §2 #33）。拆不回 → null。
 */
fun splitMergedLead(messages: List<LogSentMessage>, shape: LogRequestShape?, leadCount: Int): List<LogSentMessage>? {
    if (shape == null || shape.fingerprints.size < leadCount) return null
    val m1 = messages.firstOrNull() ?: return null
    if (m1.role != LogSentRole.SYSTEM) return null
    val t = (m1.pieces.firstOrNull() as? LogSentPiece.Text)?.text ?: return null
    fun fp(x: String) = ContextSegment.messageFingerprintOf(ChatMessageDto(role = "system", content = x))
    val parts = ArrayList<String>(leadCount)
    var a = 0
    for (k in 0 until leadCount) {
        val b: Int
        if (k < leadCount - 1) {
            var c = t.indexOf(PROMPT_PART_SEPARATOR, a)
            while (c >= 0 && fp(t.substring(a, c)) != shape.fingerprints[k]) c = t.indexOf(PROMPT_PART_SEPARATOR, c + 1)
            if (c < 0) return null
            b = c
        } else {
            b = t.length
            if (a > b || fp(t.substring(a, b)) != shape.fingerprints[k]) return null
        }
        parts += t.substring(a, b)
        a = b + PROMPT_PART_SEPARATOR.length
    }
    val added = (1 until leadCount).map { k ->
        LogSentPiece.Added(if (parts[k].startsWith(HistoryTimeDivider.OPEN)) LogAddedKind.TIME_MARKER else LogAddedKind.NOTE, parts[k])
    }
    val first = m1.copy(pieces = listOf(LogSentPiece.Text(parts[0])) + added + m1.pieces.drop(1))
    return listOf(first) + messages.drop(1)
}

/** 形状下标 k → 阅读器列表下标（从 0 起）；对照不成立 → null（§3.4 锁定）。 */
fun readerIndexMap(source: LogReaderSource, adaptation: LogSendAdaptation?, shape: LogRequestShape?, messageCount: Int): IntArray? {
    if (shape == null) return null
    if (source == LogReaderSource.REQUEST && adaptation != null && !adaptation.asIs) return adaptedIndexMap(shape.roles, messageCount)
    return if (shape.roles.length == messageCount) IntArray(messageCount) { it } else null
}

/**
 * 改写时的对照：用形状的角色串造一组只含记号的假消息，跑一遍**真**改写器 [ProviderMessageAdapter.adapt]，看每个记号落进了第几条
 * （不另写一份改写规则·PITFALLS §2 #30 / #33）。条数对不上 / 任一记号找不到 → null。
 */
internal fun adaptedIndexMap(roles: String, messageCount: Int): IntArray? {
    val fake = roles.mapIndexed { k, r ->
        val role = when (r) {
            's' -> "system"
            'u' -> "user"
            'a' -> "assistant"
            't' -> "tool"
            else -> "other"
        }
        ChatMessageDto(role = role, content = "\u0001$k\u0001")
    }
    val out = ProviderMessageAdapter.adapt(fake).messages
    if (out.size != messageCount) return null
    val texts = out.map { m -> m.content ?: m.contentParts?.filterIsInstance<ChatContentPart.Text>()?.joinToString("\n") { it.text }.orEmpty() }
    val map = IntArray(roles.length)
    for (k in roles.indices) {
        val at = texts.indexOfFirst { it.contains("\u0001$k\u0001") }
        if (at < 0) return null
        map[k] = at
    }
    return map
}

/**
 * 断点落点（§3.5 锁定·与图纸四地图同口径）：系统提示词 → 目录里那一节之前（模块本轮找不到 → 最后一个模块节之后；
 * 不带模块名 → 第一个「未分段内容」节之前；没有目录 → 第 1 条之前）；其余按 [CacheBreak.messageIndex] 经对照落到「第 N 条之前」，
 * 落在被合并的消息内部时只有「合并进开头 system 的说明」能精确到片段，其余画不出位置（null）。
 */
fun readerCutAt(frame: LogMapCut, brk: CacheBreak, outline: LogPromptOutline?, indexMap: IntArray?, lead: Int): LogReaderCutAt? {
    if (frame.kind == CacheBreakKind.SYSTEM_PROMPT) {
        if (outline == null) return LogReaderCutAt.BeforeMessage(1)
        val sections = outline.sections
        val module = brk.moduleName ?: return LogReaderCutAt.InOutline(sections.indexOfFirst { it.title == null }.takeIf { it >= 0 } ?: 0)
        val idx = sections.indexOfFirst { it.title == module }
        return LogReaderCutAt.InOutline(if (idx >= 0) idx else sections.indexOfLast { it.title != null } + 1)
    }
    val i = brk.messageIndex ?: return null
    if (indexMap == null || i !in indexMap.indices) return null
    val j = indexMap[i]
    if (i > 0 && indexMap[i - 1] == j) {
        return if (lead >= 2 && j == 0 && i < lead) LogReaderCutAt.BeforePiece(1, i) else null
    }
    return LogReaderCutAt.BeforeMessage(j + 1)
}
