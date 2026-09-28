package com.situ.aichat.diagnostics

import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.LlmRequestCapture
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.HistoryTimeDivider
import com.situ.aichat.prompt.TokenEstimator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一次请求的形状（四期·图纸三 §3.5）：不含正文，恒存。 */
@Serializable
data class LogRequestShape(
    /** 每条消息的角色首字母：system → s、user → u、assistant → a、tool → t、其他 → ?。 */
    val roles: String,
    val fingerprints: List<String>,
    val tokens: List<Int>,
    val timeMarkers: List<LogTimeMarker> = emptyList(),
) {
    companion object {
        fun of(messages: List<ChatMessageDto>): LogRequestShape = LogRequestShape(
            roles = messages.joinToString("") { roleLetter(it.role) },
            fingerprints = messages.map(ContextSegment::messageFingerprintOf),
            tokens = messages.map { TokenEstimator.estimate(LogContextFormat.readableBody(it)) },
            timeMarkers = messages.withIndex()
                .filter { (_, m) -> m.role == "system" && m.content?.startsWith(HistoryTimeDivider.OPEN) == true }
                .map { (i, m) -> LogTimeMarker(i, m.content.orEmpty()) },
        )

        fun encode(json: Json, shape: LogRequestShape): String = json.encodeToString(serializer(), shape)

        /** '' / 坏 JSON → null。 */
        fun decode(json: Json, text: String): LogRequestShape? {
            if (text.isEmpty()) return null
            return runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        }

        private fun roleLetter(role: String): String = when (role) {
            "system" -> "s"
            "user" -> "u"
            "assistant" -> "a"
            "tool" -> "t"
            else -> "?"
        }
    }
}

/** 时间标记：消息下标 + 原文（App 生成的「【时间 · …】」，不含用户内容）。 */
@Serializable
data class LogTimeMarker(val index: Int, val text: String)

/** 发送前改写计数（存 [com.situ.aichat.data.local.entity.LogEntryEntity.sendAdaptationJson]）：asIs = 原样发送。 */
@Serializable
data class LogSendAdaptation(val asIs: Boolean, val leadingMerged: Int = 0, val midMerged: Int = 0, val tailMerged: Int = 0) {
    companion object {
        /** capture 为 null 或没捕获到请求 → ''；adaptation null → {"asIs":true}；否则三计数。 */
        fun encode(json: Json, capture: LlmRequestCapture?): String {
            if (capture?.request == null) return ""
            val counts = capture.adaptation
            val value = if (counts == null) LogSendAdaptation(asIs = true)
            else LogSendAdaptation(asIs = false, leadingMerged = counts.leadingMerged, midMerged = counts.midMerged, tailMerged = counts.tailMerged)
            return json.encodeToString(serializer(), value)
        }

        fun decode(json: Json, text: String): LogSendAdaptation? {
            if (text.isEmpty()) return null
            return runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        }
    }
}
