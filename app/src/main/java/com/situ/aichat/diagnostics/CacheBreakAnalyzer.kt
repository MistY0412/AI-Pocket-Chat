package com.situ.aichat.diagnostics

import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.saver.CacheSaverLayout
import kotlin.math.max
import kotlin.math.min

/**
 * 缓存从哪断开（四期·图纸三 §3.7）：
 * - NO_PREVIOUS 没有可比的上一条 / NO_DATA 形状缺失（旧记录）；
 * - TAIL 只在末尾追加（前缀全命中）/ SAVED_BLOCK 省钱模式挪位块变了 / SYSTEM_PROMPT 系统提示词变了；
 * - LEADING_NOTE 历史之前的前置说明变了 / TIME_MARKER 时间标记变了；
 * - HISTORY_WINDOW_SLID 历史窗口往前滑（第一条历史就不同；历史开头的起始时间锚跟着它变也算这一种·复核 R1）/ HISTORY_CHANGED 历史中段变了。
 */
enum class CacheBreakKind { NO_PREVIOUS, NO_DATA, TAIL, SAVED_BLOCK, SYSTEM_PROMPT, LEADING_NOTE, TIME_MARKER, HISTORY_WINDOW_SLID, HISTORY_CHANGED }

/** [messageIndex] = 第一处不同的消息下标（NO_* 为 null）；[moduleName] 只在 SYSTEM_PROMPT 且定位到模块时有；token 均为估算。 */
data class CacheBreak(val kind: CacheBreakKind, val messageIndex: Int? = null, val moduleName: String? = null, val cachedTokensEstimate: Int = 0, val totalTokensEstimate: Int = 0)

/**
 * 缓存断点纯函数：与同一对话、同一来源的上一条成功请求逐条比指纹，第一处不同按**上一条请求**的区域归因。
 * 只做「消息级 + 系统提示词里的模块级」，不做 token 级对齐（图纸三 §0.2 #7）。
 */
object CacheBreakAnalyzer {

    fun analyze(current: LogRequestShape, currentSegments: List<ContextSegment>, previous: LogRequestShape, previousSegments: List<ContextSegment>): CacheBreak {
        val p = previous.roles
        val firstNon = p.indexOfFirst { it != 's' }
        val lastNon = p.indexOfLast { it != 's' }
        val total = current.tokens.sum()

        // 1. 第一处不同
        val n = min(current.fingerprints.size, previous.fingerprints.size)
        val i = (0 until n).firstOrNull { current.fingerprints[it] != previous.fingerprints[it] } ?: n
        var cached = previous.tokens.take(i).sum()
        var moduleName: String? = null

        val kind = when {
            // 2. 上一条整条都是前缀
            i >= previous.fingerprints.size -> CacheBreakKind.TAIL
            // 3. 第一条就是 system 且变了 → 定位到前置模块
            i == 0 && p.firstOrNull() == 's' -> {
                val a = previousSegments.filter { it.position == ContextSegment.POSITION_PREFIX }
                val b = currentSegments.filter { it.position == ContextSegment.POSITION_PREFIX }
                val k = (0 until max(a.size, b.size)).firstOrNull {
                    it >= a.size || it >= b.size || a[it].name != b[it].name ||
                        (a[it].fingerprint != null && b[it].fingerprint != null && a[it].fingerprint != b[it].fingerprint)
                }
                if (k != null) {
                    moduleName = (b.getOrNull(k) ?: a.getOrNull(k))?.name
                    cached = a.take(k).sumOf { it.estimatedTokens }
                } else {
                    moduleName = null
                    cached = 0
                }
                CacheBreakKind.SYSTEM_PROMPT
            }
            // 4. 时间标记；但历史起点前的起始锚（写的是第一条历史的时刻）变了、且第一条历史也换了 = 窗口前滑（复核 R1）
            previous.timeMarkers.any { it.index == i } ->
                if (i < firstNon && firstHistoryChanged(current, previous, firstNon)) CacheBreakKind.HISTORY_WINDOW_SLID
                else CacheBreakKind.TIME_MARKER
            // 5. 最后一条非 system 之后（后置区）
            lastNon >= 0 && i > lastNon -> CacheBreakKind.TAIL
            // 6. 第一条非 system 之前（前置说明）
            firstNon < 0 || i < firstNon -> CacheBreakKind.LEADING_NOTE
            // 7. 省钱模式挪位块
            previousSegments.firstOrNull { it.name == CacheSaverLayout.SEGMENT_NAME }?.fingerprint
                ?.let { it == previous.fingerprints[i] } == true -> CacheBreakKind.SAVED_BLOCK
            // 8. 第一条历史就不同
            i == firstNon -> CacheBreakKind.HISTORY_WINDOW_SLID
            // 9. 其余
            else -> CacheBreakKind.HISTORY_CHANGED
        }
        return CacheBreak(kind, messageIndex = i, moduleName = moduleName, cachedTokensEstimate = cached, totalTokensEstimate = total)
    }

    /** 两次请求的第一条历史（第一条非 system）是否不同（[firstNon] = 上一条请求里它的下标）。 */
    private fun firstHistoryChanged(current: LogRequestShape, previous: LogRequestShape, firstNon: Int): Boolean {
        val c = current.roles.indexOfFirst { it != 's' }
        return c < 0 || current.fingerprints[c] != previous.fingerprints[firstNon]
    }
}
