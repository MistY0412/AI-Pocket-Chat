package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.prompt.ContextSegment

/** 阅读器内容来源：实际请求 / 兜底（App 发送前拼好的 `fullContext`）。 */
enum class LogReaderSource { REQUEST, RENDERED }

/** 筛选芯片（按「谁说的」）。 */
enum class LogReaderFilter { ALL, SYSTEM, USER, ASSISTANT, ADDED, TOOL }

/** 概览细条一截的颜色类。 */
enum class LogStripKind { SYSTEM, USER, ASSISTANT, ADDED, OTHER }

/** [start, end) 半开区间。 */
data class LogSpan(val start: Int, val end: Int)
data class LogStripRun(val kind: LogStripKind, val chars: Int)
data class LogReaderStrip(val runs: List<LogStripRun>, val total: Int, val cutFraction: Float?)

/** 上下文阅读器视图（四期·图纸五 §3.2 锁定）：全部在 Dispatchers.Default 上预算好，界面只做遍历拼装。 */
data class LogReaderView(
    val source: LogReaderSource,
    /** index 从 1 起、与列表下标 + 1 相等。 */
    val messages: List<LogSentMessage>,
    /** 第 1 条（系统提示词）的目录；null = 不拆。 */
    val outline: LogPromptOutline?,
    /** 断点文案（来自地图）；null = 没有可比的上一轮 / 旧记录。 */
    val frame: LogMapCut?,
    /** 断点落点；null = 画不出位置。 */
    val cutAt: LogReaderCutAt?,
    val strip: LogReaderStrip,
    val counts: Map<LogReaderFilter, Int>,
    val totalChars: Int,
    val truncated: Boolean,
    /** (消息 index, 片段下标) → 展开后的分块；只含 Text 片段，目录所在片段除外。 */
    val textBlocks: Map<Pair<Int, Int>, List<LogSpan>>,
    /** 目录各节的分块（偏移 = 系统提示词文字里的绝对位置）。 */
    val sectionBlocks: List<List<LogSpan>>,
    /** 目录默认展开的节。 */
    val defaultOpen: Set<Int>,
)

/** 每块最多字数（约 17 行·不到一屏：跳到某块 = 那处命中必在屏内）。 */
const val READER_BLOCK_CHARS = 400

/** 阅读器装配（§3.2 按序 1–10）。 */
fun buildReaderView(
    base: LogSentView, source: LogReaderSource, shape: LogRequestShape?, segments: List<ContextSegment>,
    comparison: CacheComparison, frame: LogMapCut?, adaptation: LogSendAdaptation?, truncated: Boolean,
): LogReaderView {
    // 1. 会改写的服务商把开头几条 system 合成了一条 → 拆回
    val lead = if (source == LogReaderSource.REQUEST && adaptation != null && !adaptation.asIs && adaptation.leadingMerged >= 2) adaptation.leadingMerged else 0
    val split = if (lead >= 2) splitMergedLead(base.messages, shape, lead) else null
    val messages = split ?: base.messages
    val leadSplit = split != null
    // 2. 目录
    val m1 = messages.firstOrNull()
    val promptText = (m1?.pieces?.firstOrNull() as? LogSentPiece.Text)?.text
    val outline = if (m1 != null && m1.role == LogSentRole.SYSTEM && promptText != null) promptOutline(promptText, segments, comparison.previousSegments) else null
    // 3–5.
    val indexMap = readerIndexMap(source, adaptation, shape, messages.size)
    val cutAt = frame?.let { readerCutAt(it, comparison.cacheBreak, outline, indexMap, if (leadSplit) lead else 0) }
    val strip = readerStrip(messages, outline, cutAt)
    // 6.
    val counts = LogReaderFilter.entries.associateWith { f -> messages.count { f.matches(it) } }
    // 7–8.
    val textBlocks = HashMap<Pair<Int, Int>, List<LogSpan>>()
    for (m in messages) {
        m.pieces.forEachIndexed { p, piece ->
            if (piece is LogSentPiece.Text && !(outline != null && m.index == 1 && p == 0)) textBlocks[m.index to p] = readerBlocks(piece.text)
        }
    }
    val sectionBlocks = if (outline != null && promptText != null) outline.sections.map { readerBlocks(promptText, it.start, it.end) } else emptyList()
    // 9.
    val defaultOpen = if (outline == null) emptySet() else buildSet {
        outline.sections.forEachIndexed { i, s -> if (s.changed) add(i) }
        if (cutAt is LogReaderCutAt.InOutline && cutAt.before < outline.sections.size) add(cutAt.before)
    }
    return LogReaderView(
        source = source, messages = messages, outline = outline, frame = frame, cutAt = cutAt, strip = strip, counts = counts,
        totalChars = messages.sumOf { it.chars }, truncated = truncated, textBlocks = textBlocks, sectionBlocks = sectionBlocks,
        defaultOpen = defaultOpen,
    )
}

/** 筛选命中（§3.2 锁定）。 */
fun LogReaderFilter.matches(m: LogSentMessage): Boolean = when (this) {
    LogReaderFilter.ALL -> true
    LogReaderFilter.SYSTEM -> m.role == LogSentRole.SYSTEM && m.pieces.any { it is LogSentPiece.Text }
    LogReaderFilter.USER -> m.role == LogSentRole.USER
    LogReaderFilter.ASSISTANT -> m.role == LogSentRole.ASSISTANT
    LogReaderFilter.ADDED -> m.pieces.any { it is LogSentPiece.Added }
    LogReaderFilter.TOOL -> m.role == LogSentRole.TOOL || m.role == LogSentRole.OTHER
}

/** 整条只有一个「时间标记」附加的 system → 画成居中胶囊。 */
fun isTimePill(m: LogSentMessage): Boolean =
    m.role == LogSentRole.SYSTEM && m.pieces.size == 1 && (m.pieces[0] as? LogSentPiece.Added)?.kind == LogAddedKind.TIME_MARKER

/**
 * 分块（§3.2 锁定）：≤ [maxChars] 一块，超出时优先在最后一个换行处切（该换行被块界吃掉·可产生空块 = 原文空行），
 * 没有换行则硬切（代理对不劈开）。[from] == [to] → 空表。
 */
fun readerBlocks(text: String, from: Int = 0, to: Int = text.length, maxChars: Int = READER_BLOCK_CHARS): List<LogSpan> {
    val out = ArrayList<LogSpan>()
    var s = from
    while (s < to) {
        if (to - s <= maxChars) {
            out += LogSpan(s, to)
            break
        }
        val limit = s + maxChars
        val nl = text.lastIndexOf('\n', limit - 1)
        if (nl >= s) {
            out += LogSpan(s, nl)
            s = nl + 1
        } else {
            var e = limit
            if (e - 1 > s && Character.isHighSurrogate(text[e - 1])) e -= 1
            out += LogSpan(s, e)
            s = e
        }
    }
    return out
}

/**
 * 概览细条（§3.6 锁定）：逐片段按字数一截（正文按消息角色、App 附加单独一色），相邻同色合并；
 * 分母 = 各截之和，断点位置与色条同一套算法（PITFALLS §2 #35）。
 */
fun readerStrip(messages: List<LogSentMessage>, outline: LogPromptOutline?, cutAt: LogReaderCutAt?): LogReaderStrip {
    val runs = ArrayList<LogStripRun>()
    for (m in messages) {
        for (piece in m.pieces) {
            val len = pieceText(piece).length
            if (len == 0) continue
            val kind = if (piece is LogSentPiece.Added) LogStripKind.ADDED else when (m.role) {
                LogSentRole.SYSTEM -> LogStripKind.SYSTEM
                LogSentRole.USER -> LogStripKind.USER
                LogSentRole.ASSISTANT -> LogStripKind.ASSISTANT
                LogSentRole.TOOL, LogSentRole.OTHER -> LogStripKind.OTHER
            }
            val last = runs.lastOrNull()
            if (last != null && last.kind == kind) runs[runs.lastIndex] = last.copy(chars = last.chars + len) else runs += LogStripRun(kind, len)
        }
    }
    val total = runs.sumOf { it.chars }
    fun before(m: Int): Int = messages.filter { it.index < m }.sumOf { msg -> msg.pieces.sumOf { pieceText(it).length } }
    val off: Int? = when (cutAt) {
        is LogReaderCutAt.InOutline -> {
            val sections = outline?.sections.orEmpty()
            if (cutAt.before < sections.size) sections[cutAt.before].start
            else messages.firstOrNull()?.pieces?.firstOrNull()?.let { pieceText(it).length } ?: 0
        }
        is LogReaderCutAt.BeforePiece -> before(cutAt.message) +
            (messages.firstOrNull { it.index == cutAt.message }?.pieces?.take(cutAt.piece)?.sumOf { pieceText(it).length } ?: 0)
        is LogReaderCutAt.BeforeMessage -> before(cutAt.message)
        null -> null
    }
    return LogReaderStrip(runs, total, if (total == 0 || off == null) null else off.toFloat() / total)
}

/** 芯片可选项（§3.2 锁定）：0 条的类不出，「全部」与当前选中的恒在；保持枚举顺序。 */
fun readerChipOptions(counts: Map<LogReaderFilter, Int>, selected: LogReaderFilter): List<LogReaderFilter> =
    LogReaderFilter.entries.filter { it == LogReaderFilter.ALL || it == selected || (counts[it] ?: 0) > 0 }

/** 「复制全部」（§3.2 锁定）：兜底 → 原样 `fullContext`；实际请求 → 实际发出的这些消息按全文格式排（[LogContextFormat.render]）。 */
fun readerCopyAllText(view: LogReaderView, entry: LogEntryEntity): String =
    if (view.source == LogReaderSource.RENDERED) entry.fullContext
    else LogContextFormat.render(view.messages.map { ChatMessageDto(role = it.rawRole, content = it.raw) })
