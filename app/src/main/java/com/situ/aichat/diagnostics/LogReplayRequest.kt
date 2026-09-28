package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「导出可重放请求」（四期·图纸三 §3.8）：实际发出的请求体存库前消毒（图片 / 语音换替身文字），导出时包一层 meta。
 * 请求体本来就不含 API key 与请求地址（key 只在请求头）；导出 meta 也不放 baseUrl。
 */
object LogReplayRequest {

    private val prettyJson = Json { prettyPrint = true }
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    /** 请求里的图片 / 语音段换成 [LogContextFormat] 同款替身文字段（其余零改）。 */
    fun sanitized(request: ChatRequestDto): ChatRequestDto = request.copy(
        messages = request.messages.map { m ->
            val parts = m.contentParts ?: return@map m
            m.copy(contentParts = parts.map { part -> LogContextFormat.mediaPlaceholder(part)?.let { ChatContentPart.Text(it) } ?: part })
        },
    )

    /** 存库用：sanitized 后编码；超过 [LogContextFormat.STORED_TEXT_HARD_LIMIT] 字符 → ''（不存截断的坏 JSON）。 */
    fun encodeForStore(json: Json, request: ChatRequestDto): String {
        val encoded = json.encodeToString(ChatRequestDto.serializer(), sanitized(request))
        return if (encoded.length > LogContextFormat.STORED_TEXT_HARD_LIMIT) "" else encoded
    }

    /** 导出文本；[LogEntryEntity.requestJson] 为空或解析不了（库里坏 JSON）→ null（同「导不出」·复核 R1 裁决 T-1）。 */
    fun exportText(entry: LogEntryEntity, exportedAtMillis: Long): String? {
        if (entry.requestJson.isEmpty()) return null
        val request: JsonElement = runCatching { prettyJson.parseToJsonElement(entry.requestJson) }.getOrNull() ?: return null
        val root = buildJsonObject {
            put(
                "meta",
                buildJsonObject {
                    put("app", "AI Pocket Chat")
                    put("kind", "replayable_request")
                    put("loggedAtMillis", entry.timestampMillis)
                    put("exportedAtMillis", exportedAtMillis)
                    put("source", entry.source)
                    put("characterName", entry.characterName)
                    put("modelName", entry.modelName)
                    put("providerType", entry.providerType?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("note", "不含 API key 与请求地址；图片 / 语音已换成占位文字。")
                },
            )
            put("request", request)
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    /** `replay-yyyyMMdd-HHmmss-{id}.json`（本机时区，按 [LogEntryEntity.timestampMillis]）。 */
    fun fileName(entry: LogEntryEntity, zone: ZoneId = ZoneId.systemDefault()): String =
        "replay-" + fileStamp.format(Instant.ofEpochMilli(entry.timestampMillis).atZone(zone)) + "-" + entry.id + ".json"
}
