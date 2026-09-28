package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.prompt.ContextSegment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上下文日志读侧（四期·图纸三 §3.7·给图纸四日志页用）：解码形状 / 分段 / 改写计数、查上一条算缓存断点、行的失败类。
 * 老记录（v51 前）缺的项一律解成 null / 空表，由界面显示「旧记录没有这项」。
 */
@Singleton
class LogAnalysisReader @Inject constructor(
    private val logDao: LogDao,
    private val json: Json,
) {
    fun shapeOf(entry: LogEntryEntity): LogRequestShape? = LogRequestShape.decode(json, entry.shapeJson)

    /** '' / 坏 JSON → 空表。 */
    fun segmentsOf(contextSegmentsJson: String): List<ContextSegment> {
        if (contextSegmentsJson.isEmpty()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(ContextSegment.serializer()), contextSegmentsJson) }
            .getOrDefault(emptyList())
    }

    fun adaptationOf(entry: LogEntryEntity): LogSendAdaptation? = LogSendAdaptation.decode(json, entry.sendAdaptationJson)

    fun failureKindOf(row: LogListRow): LlmFailureKind? =
        LlmFailureClassifier.kindOfRow(row.isSuccess, row.failureKind, row.errorMessage)

    /**
     * 缓存从哪断开 + 上一条的时刻与分段（四期·图纸四 §3.4）：只与同一对话、同一来源、在它之前的最近一条成功记录比。
     * 判定分支与原 [cacheBreakOf] 逐支相同；查到上一条时一并带回它的时刻与分段（查不到 / 没去查 = null / 空表）。
     */
    suspend fun compareWithPrevious(entry: LogEntryEntity): CacheComparison {
        val conversationUuid = entry.conversationUuid
            ?: return CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())
        val shape = shapeOf(entry) ?: return CacheComparison(CacheBreak(CacheBreakKind.NO_DATA), null, emptyList())
        val previous = logDao.previousComparable(conversationUuid, entry.source, entry.timestampMillis, entry.id)
            ?: return CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS, totalTokensEstimate = shape.tokens.sum()), null, emptyList())
        val previousSegments = segmentsOf(previous.contextSegmentsJson)
        val previousShape = LogRequestShape.decode(json, previous.shapeJson)
            ?: return CacheComparison(CacheBreak(CacheBreakKind.NO_DATA), previous.timestampMillis, previousSegments)
        val cacheBreak = CacheBreakAnalyzer.analyze(shape, segmentsOf(entry.contextSegmentsJson), previousShape, previousSegments)
        return CacheComparison(cacheBreak, previous.timestampMillis, previousSegments)
    }

    /** 缓存从哪断开（[compareWithPrevious] 的简写·行为与图纸三原版逐支相同）。 */
    suspend fun cacheBreakOf(entry: LogEntryEntity): CacheBreak = compareWithPrevious(entry).cacheBreak
}

/** 与上一条可比记录对比的全部结果（四期·图纸四）：断点 + 上一条的时刻与分段（地图画「每轮会变」、红框写「和上一轮（HH:mm）相比」用）。 */
data class CacheComparison(val cacheBreak: CacheBreak, val previousTimestampMillis: Long?, val previousSegments: List<ContextSegment>)
