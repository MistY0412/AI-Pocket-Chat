package com.situ.aichat.diagnostics

/** 日志页引用「你那句话」用的消息轻投影（四期·图纸四）：只读，不含 embedding 等大列。 */
data class LogMessageBrief(
    val messageUUID: String,
    val conversationUuid: String,
    val roleRaw: String,
    val content: String,
    val timestamp: Long,
    val imageRelativePath: String?,
    val isVoiceMessage: Boolean,
    val messageKindRaw: String,
)
