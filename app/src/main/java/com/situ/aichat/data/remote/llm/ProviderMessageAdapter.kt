package com.situ.aichat.data.remote.llm

/**
 * 非白名单服务商的发送前改写（时间感知四期·图纸一 §3.2 锁定·纯函数·幂等）。
 *
 * 为什么要改写：实测（`tools/prompt-ab-bench/RESULTS-2026-09-26.md`）Claude 兼容层把中途 system 提到开头、
 * Gemini 兼容端点并进 systemInstruction、Qwen3.5+ 非首位 system 报 400、MiniMax 静默丢弃——时间标记与末尾规则
 * 在这些服务商那里要么错位要么丢。改写后除开头一条 system 外再无 system：中途的并进相邻用户消息开头、末尾块
 * 整块贴进最后一条用户消息，都用 [NOTE_OPEN]/[NOTE_CLOSE] 框起来，并在开头 system 末尾附 [EXPLAIN_NOTE]。
 *
 * 唯一调用点 = `LlmClient.buildRequestJson`（每次 attempt 重算，重试幂等）；白名单（[ProviderFamily.keepsSystemInPlace]）
 * 返回**同一个 list 实例**（请求体逐字节不变）。不做「相邻同角色合并」（图纸 §0.2-4）。改写结果不写回存储。
 *
 * [NOTE_OPEN]/[NOTE_CLOSE] 与 `ReplyParser.systemNoteBlockRegex` / `systemNoteTagRegex` 字面互指（REDLINES §1·锁测试钉）。
 */
internal object ProviderMessageAdapter {

    const val NOTE_OPEN = "【系统说明】"
    const val NOTE_CLOSE = "【/系统说明】"

    /** 逐字锁定（实验台 ctxexp_q23.py NOTE 同文·已实测）。 */
    const val EXPLAIN_NOTE = "（说明：对方消息开头或结尾用【系统说明】框起来的内容，是 App 附上的时间与规则信息，不是对方打的字，不要回应它本身，也不要在回复里提到它。）"

    private const val SYS = "system"
    private const val USER = "user"
    private const val TOOL = "tool"

    data class AdaptResult(val messages: List<ChatMessageDto>, val leadingMerged: Int, val midMerged: Int, val tailMerged: Int)

    fun forSend(messages: List<ChatMessageDto>, config: ApiConfigValues): List<ChatMessageDto> =
        if (ProviderFamily.keepsSystemInPlace(config)) messages else adapt(messages).messages

    fun adapt(messages: List<ChatMessageDto>): AdaptResult {
        // 1. 开头连续 system 压成一条。
        val leadCount = messages.indexOfFirst { it.role != SYS }.let { if (it < 0) messages.size else it }
        var leadingMerged = 0
        var lead: ChatMessageDto? = when {
            leadCount >= 2 -> {
                leadingMerged = leadCount
                ChatMessageDto(role = SYS, content = messages.take(leadCount).joinToString("\n\n") { textOf(it) })
            }
            leadCount == 1 -> messages[0]
            else -> null
        }
        val body = messages.drop(leadCount)

        // 2. 没有任何非 system 消息：合成一条 system，不加说明。
        if (body.none { it.role != SYS }) {
            if (body.isEmpty() && leadCount <= 1) return AdaptResult(messages, 0, 0, 0)
            val merged = ChatMessageDto(role = SYS, content = (listOfNotNull(lead) + body).joinToString("\n\n") { textOf(it) })
            return AdaptResult(listOf(merged), messages.size, 0, 0)
        }

        // 3. 中途 system 并进相邻用户消息（框起来）。
        val last = body.indexOfLast { it.role != SYS }
        val out = mutableListOf<ChatMessageDto>()
        val pending = mutableListOf<String>()
        var midMerged = 0
        for (i in 0..last) {
            var m = body[i]
            if (m.role == SYS) {
                pending += textOf(m)
                continue
            }
            if (pending.isNotEmpty()) {
                val frame = NOTE_OPEN + pending.joinToString("\n") + NOTE_CLOSE
                // 复核 R1 修订：标记只能贴「紧挨着的」用户消息——往回跨过角色消息去贴，会把时间标到错的位置
                // （例：她早上主动发的消息前那条场边界注记被贴到昨晚的「晚安」上）。tool 消息前不能插用户消息（会拆散
                // 工具调用与结果），仍贴最后一条用户消息（实际不会出现，防御）。
                val lastUser = out.indexOfLast { it.role == USER }
                when {
                    m.role == USER -> m = prepend(m, frame + "\n")
                    lastUser >= 0 && (lastUser == out.lastIndex || m.role == TOOL) -> out[lastUser] = append(out[lastUser], "\n" + frame)
                    else -> out += ChatMessageDto(role = USER, content = frame)
                }
                midMerged += pending.size
                pending.clear()
            }
            out += m
        }

        // 4. 末尾块整块贴进最后一条用户消息。
        val tail = body.subList(last + 1, body.size)
        var tailMerged = 0
        if (tail.isNotEmpty()) {
            val block = "\n\n" + NOTE_OPEN + "\n" + tail.joinToString("\n\n") { textOf(it) } + "\n" + NOTE_CLOSE
            if (out.last().role == USER) {
                out[out.lastIndex] = append(out.last(), block)
            } else {
                out += ChatMessageDto(role = USER, content = block.removePrefix("\n\n"))
            }
            tailMerged = tail.size
        }

        // 5. 有框就在开头 system 末尾附说明。
        if (midMerged + tailMerged > 0) {
            lead = lead?.let { it.copy(content = textOf(it) + "\n\n" + EXPLAIN_NOTE, contentParts = null) }
                ?: ChatMessageDto(role = SYS, content = EXPLAIN_NOTE)
        }

        // 6. 一处没动 → 原实例。
        if (leadingMerged + midMerged + tailMerged == 0) return AdaptResult(messages, 0, 0, 0)
        return AdaptResult(listOfNotNull(lead) + out, leadingMerged, midMerged, tailMerged)
    }

    private fun textOf(m: ChatMessageDto): String =
        m.content ?: m.contentParts?.filterIsInstance<ChatContentPart.Text>()?.joinToString("\n") { it.text } ?: ""

    private fun prepend(m: ChatMessageDto, text: String): ChatMessageDto {
        val parts = m.contentParts ?: return m.copy(content = text + (m.content ?: ""))
        val first = parts.firstOrNull()
        val newParts = if (first is ChatContentPart.Text) {
            listOf(ChatContentPart.Text(text + first.text)) + parts.drop(1)
        } else {
            listOf(ChatContentPart.Text(text)) + parts
        }
        return m.copy(contentParts = newParts)
    }

    private fun append(m: ChatMessageDto, text: String): ChatMessageDto {
        val parts = m.contentParts ?: return m.copy(content = (m.content ?: "") + text)
        val lastPart = parts.lastOrNull()
        val newParts = if (lastPart is ChatContentPart.Text) {
            parts.dropLast(1) + ChatContentPart.Text(lastPart.text + text)
        } else {
            parts + ChatContentPart.Text(text)
        }
        return m.copy(contentParts = newParts)
    }
}
