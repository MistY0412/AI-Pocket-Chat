package com.situ.aichat.prompt.saver

import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.prompt.PromptBuilder
import com.situ.aichat.prompt.TokenEstimator
import java.time.LocalDate

/**
 * 省钱模式的纯函数件（时间感知四期·图纸二 §3.2 ①）：叙述句改写 + 挪位块插入点 + 挪位分段。
 *
 * 只做字符串 / 消息列表变换，不读设置、不查库；挪什么由 [com.situ.aichat.prompt.buildSystemPromptWithSuffixes]
 * 决定，插在哪只在 [PromptBuilder.buildMessages] 一处调用。形状照实验台实测（`tools/prompt-ab-bench/ctxexp_routes.py`
 * 的 `narrativize` / `narr_layout`）：挪走的块成为一条 system，插在最新一条用户消息之前。
 */
internal object CacheSaverLayout {
    /** 挪位块在上下文日志里的分段名（锁定·图纸三 / 四按此名识别）。 */
    const val SEGMENT_NAME = "每轮会变的内容（省钱模式）"

    /** 聊天片段：`[yyyy-MM-dd HH:mm] 说话人：内容`（整条匹配，内容可含换行）。见面 / 档案 / 日子片段的方括号里有「 · 」，天然不匹配。 */
    private val CHAT_SNIPPET = Regex("""\[(\d{4})-(\d{2})-(\d{2}) \d{2}:\d{2}\] ([^：\n]+)：([\s\S]*)""")

    /** 聊天片段 → 叙述句：`6月14日，你说过：「…」`；片段年份 ≠ [today] 年份时写 `2025年6月14日，…`；说话人 = [characterName] → 「你」，否则原名。不匹配原样返回。 */
    fun narrate(snippet: String, characterName: String, today: LocalDate): String {
        val m = CHAT_SNIPPET.matchEntire(snippet) ?: return snippet
        val (y, mo, d, speaker, body) = m.destructured
        val date = (if (y.toInt() == today.year) "" else "${y}年") + "${mo.toInt()}月${d.toInt()}日"
        val who = if (speaker == characterName) "你" else speaker
        return "$date，${who}说过：「$body」"
    }

    /**
     * 插入点：从末尾往回找 [historyStart] 起最后一条非 system；它不是 user → 它的下标 + 1；它是 user → 继续往回越过
     * 同一段连续的 user（夹在中间的 system 如时间标记一并越过），取这段 user 第一条的下标——一轮里的多条用户消息
     * （图片 + 文字、语音、跨停顿连发各自成条）不被挪位块拆开（复核 R1 🟡-2）；找不到 → [messages].size。
     */
    fun insertIndex(messages: List<ChatMessageDto>, historyStart: Int): Int {
        var firstUser = -1
        for (i in messages.indices.reversed()) {
            if (i < historyStart) break
            when (messages[i].role) {
                PromptBuilder.ROLE_SYSTEM -> continue
                PromptBuilder.ROLE_USER -> firstUser = i
                else -> return if (firstUser >= 0) firstUser else i + 1
            }
        }
        return if (firstUser >= 0) firstUser else messages.size
    }

    /** 把 [block] 作为一条 system 插到 [insertIndex]，并往 [segmentSink] 记一段（name = [SEGMENT_NAME]、systemModuleType = null、position = POSITION_HISTORY、字符数 / TokenEstimator.estimate(block)、指纹 = 这条消息的 messageFingerprintOf·复核 R1）。 */
    fun insertBlock(messages: MutableList<ChatMessageDto>, historyStart: Int, block: String, segmentSink: MutableList<ContextSegment>?) {
        val moved = ChatMessageDto(role = PromptBuilder.ROLE_SYSTEM, content = block)
        messages.add(insertIndex(messages, historyStart), moved)
        segmentSink?.add(
            ContextSegment(
                name = SEGMENT_NAME,
                systemModuleType = null,
                charCount = block.length,
                estimatedTokens = TokenEstimator.estimate(block),
                position = ContextSegment.POSITION_HISTORY,
                fingerprint = ContextSegment.messageFingerprintOf(moved),
            ),
        )
    }
}
