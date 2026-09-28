package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.LlmRequestCapture
import com.situ.aichat.data.remote.llm.UsageCacheTokens
import com.situ.aichat.data.remote.llm.UsageDto
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.TokenEstimator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 日志实体拼装（四期·图纸三 §3.4·从 [ContextLogService] 搬出）：成功 / 失败两种。原有列的取值与搬出前逐字相同；
 * 新列 = 对话 / 轮次关联（[LogTrace]）+ 服务商 / 改写计数 / 实际发送（[LlmRequestCapture]）+ 请求形状 + 失败分类。
 * 隐私：[detail] 关时 fullContext / responseContent / requestJson 一律不存正文（形状与改写计数不含正文，恒存）。
 */
internal object LogEntryFactory {

    fun success(
        json: Json, detail: Boolean, nowMillis: Long, source: String, characterName: String, modelName: String,
        messages: List<ChatMessageDto>, responseText: String, durationMillis: Long?, usage: UsageDto?,
        segments: List<ContextSegment>, toolInfoJson: String, trace: LogTrace?, capture: LlmRequestCapture?,
    ): LogEntryEntity {
        val estimated = usage == null
        val promptTokens = usage?.promptTokens ?: TokenEstimator.estimate(LogContextFormat.plainText(messages))
        val completionTokens = usage?.completionTokens ?: TokenEstimator.estimate(responseText)
        return LogEntryEntity(
            timestampMillis = nowMillis,
            characterName = characterName,
            modelName = modelName,
            isSuccess = true,
            source = source,
            messageCount = messages.size,
            durationMillis = durationMillis,
            errorMessage = null,
            fullContext = if (detail) LogContextFormat.storedContext(messages) else "",
            responseContent = if (detail) LogContextFormat.storedResponse(responseText) else null,
            contextSegmentsJson = encodeSegments(json, segments),
            toolInfoJson = toolInfoJson,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
            cacheHitTokens = usage?.let(UsageCacheTokens::hit) ?: 0,
            cacheMissTokens = usage?.let(UsageCacheTokens::miss) ?: 0,
            isTokenEstimated = estimated,
            conversationUuid = trace?.conversationUuid,
            characterUuid = trace?.characterUuid,
            turnId = trace?.turnId,
            anchorMessageUuid = trace?.anchorMessageUuid,
            providerType = capture?.providerTypeRaw,
            sendAdaptationJson = LogSendAdaptation.encode(json, capture),
            requestJson = requestJsonOf(json, detail, capture),
            shapeJson = LogRequestShape.encode(json, LogRequestShape.of(messages)),
        )
    }

    fun failure(
        json: Json, detail: Boolean, nowMillis: Long, source: String, characterName: String, modelName: String,
        messages: List<ChatMessageDto>, errorMessage: String, failure: LlmFailure, segments: List<ContextSegment>,
        trace: LogTrace?, capture: LlmRequestCapture?,
    ): LogEntryEntity = LogEntryEntity(
        timestampMillis = nowMillis,
        characterName = characterName,
        modelName = modelName,
        isSuccess = false,
        source = source,
        messageCount = messages.size,
        durationMillis = null,
        errorMessage = errorMessage,
        fullContext = if (detail) LogContextFormat.storedContext(messages) else "",
        responseContent = null,
        contextSegmentsJson = encodeSegments(json, segments),
        isTokenEstimated = true,
        conversationUuid = trace?.conversationUuid,
        characterUuid = trace?.characterUuid,
        turnId = trace?.turnId,
        anchorMessageUuid = trace?.anchorMessageUuid,
        providerType = capture?.providerTypeRaw,
        sendAdaptationJson = LogSendAdaptation.encode(json, capture),
        requestJson = requestJsonOf(json, detail, capture),
        shapeJson = LogRequestShape.encode(json, LogRequestShape.of(messages)),
        failureKind = failure.kind.raw,
        httpStatus = failure.httpStatus,
    )

    /** 实际发出的请求体：只在 detail 开着时存（媒体换替身、超安全帽存 ''）。 */
    private fun requestJsonOf(json: Json, detail: Boolean, capture: LlmRequestCapture?): String =
        if (detail) capture?.request?.let { LogReplayRequest.encodeForStore(json, it) } ?: "" else ""

    private fun encodeSegments(json: Json, segments: List<ContextSegment>): String {
        if (segments.isEmpty()) return ""
        return runCatching {
            json.encodeToString(ListSerializer(ContextSegment.serializer()), segments)
        }.getOrDefault("")
    }
}
