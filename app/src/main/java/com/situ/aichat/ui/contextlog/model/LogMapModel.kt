package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.HistoryTimeDivider
import com.situ.aichat.prompt.saver.CacheSaverLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class LogMapZone { PREFIX, HISTORY, TAIL }
enum class LogMapLabel { SYSTEM_PROMPT, LEADING_NOTES, TAIL_BLOCKS } // 没有分段时的合成块名（界面取文案）
enum class LogMarkerKind { PAUSE, TICK, SCENE }

data class LogMarkerLine(val label: String, val detail: String?, val kind: LogMarkerKind)

sealed interface LogMapItem
data class LogMapZoneLabel(val zone: LogMapZone) : LogMapItem
data class LogMapBlock(
    val zone: LogMapZone, val names: List<String>, val moreCount: Int, val label: LogMapLabel?,
    val tokens: Int, val variable: Boolean, val savedBlock: Boolean, val heightDp: Int,
) : LogMapItem
data class LogMapHistory(
    val messageCount: Int, val tokens: Int, val heightDp: Int,
    val ticks: List<Float>, val markers: List<LogMarkerLine>, val moreMarkers: Int,
) : LogMapItem
data class LogMapCut(val kind: CacheBreakKind, val moduleName: String?, val previousTimeMillis: Long?, val uncachedTokens: Int) : LogMapItem

data class LogContextMap(val items: List<LogMapItem>, val note: CacheBreakKind?, val segmentsPurged: Boolean, val totalTokens: Int)

enum class LogMiniStyle { PREFIX, VARIABLE, HISTORY, TAIL }
data class LogMiniPart(val fraction: Float, val style: LogMiniStyle)
data class LogMiniMapModel(val parts: List<LogMiniPart>, val cutFraction: Float?)

private const val MARKERS_SHOWN = 6

/** 上下文地图装配（四期·图纸四 §3.6 锁定·按序 1–9）。 */
fun buildContextMap(shape: LogRequestShape, segments: List<ContextSegment>, comparison: CacheComparison, segmentsPurged: Boolean): LogContextMap {
    // 1.
    val tokens = shape.tokens
    fun tk(i: Int) = tokens.getOrElse(i) { 0 } // 形状各列表恒等长；防坏 JSON 越界
    val total = tokens.sum()
    val p = shape.roles
    val firstNon = p.indexOfFirst { it != 's' }
    val lastNon = p.indexOfLast { it != 's' }
    // 2. 挪位块下标
    val savedFp = segments.firstOrNull { it.name == CacheSaverLayout.SEGMENT_NAME }?.fingerprint
    val savedIndex = if (savedFp == null || firstNon < 0) -1 else (firstNon..lastNon).firstOrNull { shape.fingerprints.getOrNull(it) == savedFp } ?: -1
    val cut = comparison.cacheBreak

    // 3. 前置区
    val prefix = ArrayList<LogMapBlock>()
    if (segments.isNotEmpty()) {
        segments.filter { it.position == ContextSegment.POSITION_PREFIX }.forEach { seg ->
            val prev = comparison.previousSegments.firstOrNull { it.name == seg.name }
            val variable = prev != null && prev.fingerprint != null && seg.fingerprint != null && prev.fingerprint != seg.fingerprint
            prefix += block(LogMapZone.PREFIX, listOf(seg.name), null, seg.estimatedTokens, variable)
        }
    } else if (p.firstOrNull() == 's') {
        prefix += block(LogMapZone.PREFIX, emptyList(), LogMapLabel.SYSTEM_PROMPT, tk(0), variable = false)
    }
    val leading = (1 until firstNon).sumOf { tk(it) }
    if (leading > 0) prefix += block(LogMapZone.PREFIX, emptyList(), LogMapLabel.LEADING_NOTES, leading, variable = false)

    // 8（先定前置区红框目标，合并时让它保持独立）
    // 复核 R1：模块名在这一轮的分段里找不到 = 上一轮末尾的模块这一轮没了（或分段被清掉）——缓存断在这一轮最后一个
    // 模块之后，红框放那里、不写模块名（原 T-3 放第一块前，位置与「约 N tk」对不上）。
    val moduleMissing = cut.kind == CacheBreakKind.SYSTEM_PROMPT && cut.moduleName != null && prefix.none { cut.moduleName in it.names }
    // 起始锚（历史之前那条时间标记）变了：它在「聊天记录前的说明」块里，红框跟 LEADING_NOTE 放一处（复核 R1）。
    val cutAtLeading = cut.kind == CacheBreakKind.LEADING_NOTE ||
        (cut.kind == CacheBreakKind.TIME_MARKER && firstNon >= 0 && (cut.messageIndex ?: firstNon) < firstNon)
    val prefixTarget: LogMapBlock? = when {
        cut.kind == CacheBreakKind.SYSTEM_PROMPT && !moduleMissing ->
            cut.moduleName?.let { m -> prefix.firstOrNull { m in it.names } } ?: prefix.firstOrNull()
        cutAtLeading -> prefix.firstOrNull { it.label == LogMapLabel.LEADING_NOTES }
        else -> null
    }

    // 5. 聊天记录区（去掉挪位块）
    val history: LogMapHistory? = if (firstNon < 0) null else {
        val range = (firstNon..lastNon).filter { it != savedIndex }
        val sum = range.sumOf { tk(it) }
        val inRange = shape.timeMarkers.filter { it.index in firstNon..lastNon && it.index != savedIndex }.sortedBy { it.index }
        val ticks = inRange.map { m -> if (sum == 0) 0f else range.filter { it < m.index }.sumOf { tk(it) }.toFloat() / sum }
        LogMapHistory(
            messageCount = range.count { p[it] != 's' },
            tokens = sum,
            heightDp = heightOf(sum, total),
            ticks = ticks,
            markers = inRange.takeLast(MARKERS_SHOWN).map { parseMarker(it.text) },
            moreMarkers = max(0, inRange.size - MARKERS_SHOWN),
        )
    }
    val saved = if (savedIndex >= 0) {
        LogMapBlock(LogMapZone.HISTORY, listOf(CacheSaverLayout.SEGMENT_NAME), 0, null, tk(savedIndex), variable = true, savedBlock = true, heightDp = heightOf(tk(savedIndex), total))
    } else null

    // 6. 末尾块区
    val tail = ArrayList<LogMapBlock>()
    if (segments.isNotEmpty()) {
        segments.filter { it.position == ContextSegment.POSITION_SUFFIX }.forEach { tail += block(LogMapZone.TAIL, listOf(it.name), null, it.estimatedTokens, variable = false) }
    } else if (lastNon >= 0) {
        // TODO(图纸未覆盖): 请求里没有任何非 system 消息（lastNon < 0）时不画末尾块，免得与前置区重复计同一批消息（§11 登记）。
        val rest = (lastNon + 1 until tokens.size).sumOf { tk(it) }
        if (rest > 0) tail += block(LogMapZone.TAIL, emptyList(), LogMapLabel.TAIL_BLOCKS, rest, variable = false)
    }

    // 4 + 7. 合并（两区各自）并按合并后 token 算行高
    val prefixMerged = merge(prefix, prefixTarget).map { it.copy(heightDp = heightOf(it.tokens, total)) }
    val tailMerged = merge(tail, null).map { it.copy(heightDp = heightOf(it.tokens, total)) }

    // 9. 顺序
    val items = ArrayList<LogMapItem>()
    if (prefixMerged.isNotEmpty()) { items += LogMapZoneLabel(LogMapZone.PREFIX); items += prefixMerged }
    if (history != null) { items += LogMapZoneLabel(LogMapZone.HISTORY); items += history; saved?.let { items += it } }
    if (tailMerged.isNotEmpty()) { items += LogMapZoneLabel(LogMapZone.TAIL); items += tailMerged }

    // 8. 红框
    val note = cut.kind.takeIf { it == CacheBreakKind.NO_PREVIOUS || it == CacheBreakKind.NO_DATA }
    if (note == null) {
        val frame = LogMapCut(cut.kind, cut.moduleName.takeUnless { moduleMissing }, comparison.previousTimestampMillis, max(0, cut.totalTokensEstimate - cut.cachedTokensEstimate))
        val targetName = prefixTarget?.names?.firstOrNull()
        val historyIndex = items.indexOfFirst { it is LogMapHistory }
        val firstPrefix = items.indexOfFirst { it is LogMapBlock && it.zone == LogMapZone.PREFIX }
        val lastSegmentBlock = items.indexOfLast { it is LogMapBlock && it.zone == LogMapZone.PREFIX && it.label == null }
        val at = when {
            moduleMissing -> if (lastSegmentBlock >= 0) lastSegmentBlock + 1 else firstPrefix
            cut.kind == CacheBreakKind.SYSTEM_PROMPT -> items.indexOfFirst { it is LogMapBlock && it.zone == LogMapZone.PREFIX && (targetName == null || targetName in it.names) }
            cutAtLeading -> items.indexOfFirst { it is LogMapBlock && it.label == LogMapLabel.LEADING_NOTES }
                .takeIf { it >= 0 } ?: items.indexOf(LogMapZoneLabel(LogMapZone.HISTORY))
            cut.kind == CacheBreakKind.SAVED_BLOCK -> items.indexOfFirst { it is LogMapBlock && it.savedBlock }.takeIf { it >= 0 } ?: historyIndex
            cut.kind == CacheBreakKind.TAIL -> items.indexOf(LogMapZoneLabel(LogMapZone.TAIL))
            else -> historyIndex // TIME_MARKER / HISTORY_WINDOW_SLID / HISTORY_CHANGED
        }
        // 目标位置不存在（如没有前置区 / 没有聊天记录区 / 没有末尾块区）：系统提示词的断点放最前（它本来就在请求开头·复核 R1），其余放最后（原 T-5）。
        items.add(if (at >= 0) at else if (cut.kind == CacheBreakKind.SYSTEM_PROMPT) 0 else items.size, frame)
    }
    return LogContextMap(items, note, segmentsPurged, total)
}

/**
 * 迷你地图（§3.6）：块与聊天记录行按 token 占比横排，相邻同样式合并；红框之前的占比之和 = 红线位置。
 * 占比的分母 = **各部分 token 之和**（复核 R1）：分段估算的前置 / 末尾块加起来不一定等于整条请求的 [LogContextMap.totalTokens]
 * （世界书等不成段的内容），按总数算会让横条（按权重自动归一）与红线（按总数）各算各的，红线画进稳定的那一截里。
 */
fun buildMiniMap(map: LogContextMap): LogMiniMapModel {
    val parts = ArrayList<LogMiniPart>()
    var cutFraction: Float? = null
    var acc = 0f
    val partsTotal = map.items.sumOf { item ->
        when (item) {
            is LogMapBlock -> item.tokens
            is LogMapHistory -> item.tokens
            else -> 0
        }
    }
    fun add(tokens: Int, style: LogMiniStyle) {
        val f = if (partsTotal == 0) 0f else tokens.toFloat() / partsTotal
        acc += f
        val last = parts.lastOrNull()
        if (last != null && last.style == style) parts[parts.lastIndex] = last.copy(fraction = last.fraction + f) else parts += LogMiniPart(f, style)
    }
    for (item in map.items) {
        when (item) {
            is LogMapBlock -> add(
                item.tokens,
                when {
                    item.savedBlock || item.variable -> LogMiniStyle.VARIABLE
                    item.zone == LogMapZone.TAIL -> LogMiniStyle.TAIL
                    else -> LogMiniStyle.PREFIX
                },
            )
            is LogMapHistory -> add(item.tokens, LogMiniStyle.HISTORY)
            is LogMapCut -> cutFraction = acc
            is LogMapZoneLabel -> Unit
        }
    }
    return LogMiniMapModel(parts, cutFraction)
}

/** 时间标记原文 → 一行（§3.6）：含「——」= 场边界；否则按「 · 」切，有后段 = 停顿、没有 = 刻度。 */
fun parseMarker(text: String): LogMarkerLine {
    val inner = text.removePrefix(HistoryTimeDivider.OPEN).removeSuffix(HistoryTimeDivider.CLOSE)
    if (inner.contains("——")) return LogMarkerLine(inner.substringBefore("——"), null, LogMarkerKind.SCENE)
    val parts = inner.split(" · ")
    val detail = parts.drop(1).joinToString(" · ").ifEmpty { null }
    return LogMarkerLine(parts.first(), detail, if (detail == null) LogMarkerKind.TICK else LogMarkerKind.PAUSE)
}

private fun block(zone: LogMapZone, names: List<String>, label: LogMapLabel?, tokens: Int, variable: Boolean) =
    LogMapBlock(zone, names, 0, label, tokens, variable, savedBlock = false, heightDp = 0)

/** 行高 = min(240, max(20, round(340 × tokens / total)))；total == 0 → 20。 */
internal fun heightOf(tokens: Int, total: Int): Int =
    if (total == 0) 20 else min(240, max(20, (340.0 * tokens / total).roundToInt()))

/**
 * 合并（§3.6 第 4 步）：连续的「不会变、占区内 token < 5%、不是红框目标」的块合成一块（名字留前两个 + 其余个数），只有一个的不合。
 * TODO(图纸未覆盖): 合成块（label 非空·如「聊天记录前的说明」）没有名字可列，不参与合并、会打断连续段（§11 登记）。
 */
private fun merge(blocks: List<LogMapBlock>, target: LogMapBlock?): List<LogMapBlock> {
    val zoneTotal = blocks.sumOf { it.tokens }
    fun small(b: LogMapBlock) = !b.variable && b.label == null && b !== target && b.tokens * 100L < zoneTotal * 5L
    val out = ArrayList<LogMapBlock>()
    var i = 0
    while (i < blocks.size) {
        var j = i
        while (j < blocks.size && small(blocks[j])) j++
        if (j - i >= 2) {
            val run = blocks.subList(i, j)
            val names = run.flatMap { it.names }
            out += run.first().copy(names = names.take(2), moreCount = names.size - 2, tokens = run.sumOf { it.tokens })
            i = j
        } else {
            out += blocks[i]
            i++
        }
    }
    return out
}

/** 分段被清掉（对话 / 语音通话正常必有分段）·条目页与阅读器共用（四期·图纸五 §2.2·原条目页 VM 装配里的表达式只搬不改）。 */
fun segmentsPurgedOf(entry: LogEntryEntity): Boolean =
    entry.contextSegmentsJson.isEmpty() && (entry.source == LogSource.CHAT || entry.source == LogSource.VOICE_CALL)
