package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.model.MessageContentSentinels
import com.situ.aichat.data.model.MessageKind
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.ui.chat.MessagePreviewText

enum class LogMediaTag { IMAGE, VOICE }

/** 一轮「你那句话」：媒体标签（依序）+ 文字（以一个半角空格相连；没有文字 = null）。 */
data class LogTurnQuoteText(val media: List<LogMediaTag>, val text: String?)

/**
 * 从消息轻投影算一轮的用户消息、引用串、回复条数（四期·图纸四 §3.3 锁定·纯函数）。
 * 输入 `ordered` = 同一会话、时刻升序的轻投影（`MessageRepository.logBriefsInRange`）。
 */
object LogTurnQuote {

    private const val ROLE_USER = "user"
    private const val ROLE_ASSISTANT = "assistant"

    /**
     * 从锚点起往后收 user 条目，跳过其他非 assistant 角色，遇到第一条 assistant 停；锚点不在列表 → 空表。
     * [untilMillis] = 这一轮第一条主调用的记录时刻：之后才发的消息不属于这一轮（复核 R1：连着失败的几轮共用同一锚点，
     * 不设上界时前一轮的引用会把后面才发的话也算进去）。
     */
    fun turnUserMessages(ordered: List<LogMessageBrief>, anchorUuid: String, untilMillis: Long = Long.MAX_VALUE): List<LogMessageBrief> {
        val start = ordered.indexOfFirst { it.messageUUID == anchorUuid }
        if (start < 0) return emptyList()
        val out = ArrayList<LogMessageBrief>()
        for (i in start until ordered.size) {
            val m = ordered[i]
            if (m.timestamp > untilMillis) break
            when (m.roleRaw) {
                ROLE_ASSISTANT -> break
                ROLE_USER -> out += m
            }
        }
        return out
    }

    /**
     * 引用：图片 → IMAGE（配文 ≠ `[图片]` 哨兵且非空白时进文字）；语音 → VOICE（转写不进文字）；
     * 其余 → [MessagePreviewText.forKind] 的人话（非空白才进）。媒体与文字都空 → null。
     */
    fun quoteOf(userMessages: List<LogMessageBrief>): LogTurnQuoteText? {
        val media = ArrayList<LogMediaTag>()
        val texts = ArrayList<String>()
        for (m in userMessages) {
            when {
                m.imageRelativePath != null -> {
                    media += LogMediaTag.IMAGE
                    if (m.content != MessageContentSentinels.IMAGE_PLACEHOLDER && m.content.isNotBlank()) texts += m.content
                }
                m.isVoiceMessage -> media += LogMediaTag.VOICE
                else -> {
                    val preview = MessagePreviewText.forKind(MessageKind.fromRaw(m.messageKindRaw), m.content)
                    if (preview.isNotBlank()) texts += preview
                }
            }
        }
        if (media.isEmpty() && texts.isEmpty()) return null
        return LogTurnQuoteText(media, texts.takeIf { it.isNotEmpty() }?.joinToString(" "))
    }

    /** 该条之后、下一条 user 之前的 assistant 条数；该条不在列表 → null。 */
    fun replyCount(ordered: List<LogMessageBrief>, lastUserUuid: String): Int? {
        val at = ordered.indexOfFirst { it.messageUUID == lastUserUuid }
        if (at < 0) return null
        var n = 0
        for (i in at + 1 until ordered.size) {
            when (ordered[i].roleRaw) {
                ROLE_USER -> break
                ROLE_ASSISTANT -> n++
            }
        }
        return n
    }
}
