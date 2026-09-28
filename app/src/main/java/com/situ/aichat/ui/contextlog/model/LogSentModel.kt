package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.prompt.HistoryTimeDivider
import kotlinx.serialization.json.Json

enum class LogSentRole { SYSTEM, USER, ASSISTANT, TOOL, OTHER }
enum class LogAddedKind { TIME_MARKER, NOTE }

sealed interface LogSentPiece {
    data class Text(val text: String) : LogSentPiece
    data class Added(val kind: LogAddedKind, val text: String) : LogSentPiece
}

/**
 * 一条消息（[index] 从 1 起）。[rawRole] = 原始角色串（OTHER 时界面显示它）。
 * TODO(图纸未覆盖): §3.7 锁定结构没有原始角色串，而 §4.5 要求 OTHER 显示「原 role」——加了带默认值的尾参 [rawRole]（§11 登记）。
 * [raw] = 这条消息的原文（实际请求 = 正文含【系统说明】框；改写前 = 逐条表头下的正文）——阅读器「复制这条 / 复制全部」用（四期·图纸五 §3.7）。
 */
data class LogSentMessage(val index: Int, val role: LogSentRole, val chars: Int, val pieces: List<LogSentPiece>, val rawRole: String = "", val raw: String = "")

data class LogSentView(val messages: List<LogSentMessage>)

/**
 * App 在改写时加的框（**与 [ProviderMessageAdapter.NOTE_OPEN] / [ProviderMessageAdapter.NOTE_CLOSE] 逐字相同**·REDLINES §1）。
 * 用字面量而非拼常量：本仓库禁 `Regex.escape`（ICU 与 JVM 行为不一）；改框字面量须同步这里与 `LogSentModelTest`。
 */
private val NOTE_FRAME = Regex("""【系统说明】([\s\S]*?)【/系统说明】""")

/**
 * 「实际发送」（四期·图纸四 §3.7 锁定）：解码存库的请求体。原样发送时第一条之后的 system 整条算 App 附加；
 * 改写时用户消息里的【系统说明】框与开头 system 末尾的 [ProviderMessageAdapter.EXPLAIN_NOTE] 算 App 附加。解码失败 → null。
 */
fun sentFromRequest(json: Json, requestJson: String, asIs: Boolean): LogSentView? {
    val request = runCatching { json.decodeFromString(ChatRequestDto.serializer(), requestJson) }.getOrNull() ?: return null
    val firstSystem = request.messages.indexOfFirst { it.role == "system" }
    return LogSentView(
        request.messages.mapIndexed { i, m ->
            val body = m.content ?: m.contentParts?.filterIsInstance<ChatContentPart.Text>()?.joinToString("\n") { it.text }.orEmpty()
            val role = roleOf(m.role)
            val pieces = when {
                asIs && i >= 1 && role == LogSentRole.SYSTEM -> listOf(added(body))
                asIs -> listOf(LogSentPiece.Text(body))
                role == LogSentRole.USER -> splitFrames(body)
                // 原请求没有开头 system 时，改写器新造一条只有说明的 system（ProviderMessageAdapter 第 5 步·复核 R1）
                i == firstSystem && body == ProviderMessageAdapter.EXPLAIN_NOTE -> listOf(LogSentPiece.Added(LogAddedKind.NOTE, body))
                i == firstSystem && body.endsWith("\n\n" + ProviderMessageAdapter.EXPLAIN_NOTE) -> listOf(
                    LogSentPiece.Text(body.removeSuffix("\n\n" + ProviderMessageAdapter.EXPLAIN_NOTE)),
                    LogSentPiece.Added(LogAddedKind.NOTE, ProviderMessageAdapter.EXPLAIN_NOTE),
                )
                else -> listOf(LogSentPiece.Text(body))
            }
            LogSentMessage(i + 1, role, body.length, pieces, m.role, raw = body)
        },
    )
}

/** 「改写前」：解析 `fullContext`（[LogContextFormat.render] 的逐条表头格式）；第一条之后的系统提示整条算 App 附加。解析不出任何一条 → null。 */
fun sentFromRendered(fullContext: String): LogSentView? {
    val parsed = LogContextFormat.parseRendered(fullContext)
    if (parsed.isEmpty()) return null
    return LogSentView(
        parsed.mapIndexed { i, m ->
            val role = when (m.label) {
                "系统提示" -> LogSentRole.SYSTEM
                "用户" -> LogSentRole.USER
                "角色" -> LogSentRole.ASSISTANT
                "tool" -> LogSentRole.TOOL // 渲染时 tool 没有中文标签、表头写原 role（复核 R1：两种视图同叫「工具」）
                else -> LogSentRole.OTHER
            }
            val pieces = if (i >= 1 && role == LogSentRole.SYSTEM) listOf(added(m.body)) else listOf(LogSentPiece.Text(m.body))
            LogSentMessage(i + 1, role, m.body.length, pieces, m.label, raw = m.body)
        },
    )
}

private fun roleOf(role: String): LogSentRole = when (role) {
    "system" -> LogSentRole.SYSTEM
    "user" -> LogSentRole.USER
    "assistant" -> LogSentRole.ASSISTANT
    "tool" -> LogSentRole.TOOL
    else -> LogSentRole.OTHER
}

private fun added(text: String) =
    LogSentPiece.Added(if (text.startsWith(HistoryTimeDivider.OPEN)) LogAddedKind.TIME_MARKER else LogAddedKind.NOTE, text)

/** 用户消息按【系统说明】框切：框内（去首尾换行）= App 附加，框外非空白部分（去首尾换行）= 正文，按原顺序；没有框 = 整条正文。 */
private fun splitFrames(body: String): List<LogSentPiece> {
    val matches = NOTE_FRAME.findAll(body).toList()
    if (matches.isEmpty()) return listOf(LogSentPiece.Text(body))
    val out = ArrayList<LogSentPiece>()
    var from = 0
    fun outside(until: Int) {
        val seg = body.substring(from, until)
        if (seg.isNotBlank()) out += LogSentPiece.Text(seg.trim('\n'))
    }
    for (m in matches) {
        outside(m.range.first)
        out += added(m.groupValues[1].trim('\n'))
        from = m.range.last + 1
    }
    outside(body.length)
    return out
}

// ── 界面折叠（§3.7 锁定）：> 10 条 → 前 4 + 折叠条 + 后 2；正文 > 300 字、App 附加 > 120 字只显示前段 + 「展开」 ──

const val SENT_FOLD_OVER = 10
const val SENT_FOLD_HEAD = 4
const val SENT_FOLD_TAIL = 2
const val SENT_TEXT_FOLD = 300
const val SENT_ADDED_FOLD = 120

/** 该片段在未展开时是否会被截断。 */
fun LogSentPiece.foldLimit(): Int = when (this) {
    is LogSentPiece.Text -> SENT_TEXT_FOLD
    is LogSentPiece.Added -> SENT_ADDED_FOLD
}

/** 片段显示文字：未展开且超长 → 前 N 字 + 「…」。 */
fun visiblePieceText(piece: LogSentPiece, expanded: Boolean): String {
    val text = pieceText(piece)
    val limit = piece.foldLimit()
    if (expanded || text.length <= limit) return text
    // 不把 emoji 等代理对切成半个（同 LogContextFormat.clip·复核 R1）
    val end = if (Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit
    return text.take(end) + "…"
}

fun pieceText(piece: LogSentPiece): String = when (piece) {
    is LogSentPiece.Text -> piece.text
    is LogSentPiece.Added -> piece.text
}
