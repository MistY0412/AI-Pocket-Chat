package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.MessageEntity
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 上下文日志的对话 / 轮次关联（四期·图纸三 §3.3）：放进协程上下文，[ContextLogService.completion] /
 * [ContextLogService.streamedCompletion] 自动读取；[ContextLogService.recordSuccess] / [ContextLogService.recordError]
 * 由调用方显式传。触发器 launch 时带上它，下游后台调用就归到这一轮。
 */
data class LogTrace(
    val conversationUuid: String?,
    val characterUuid: String?,
    val turnId: String?,
    val anchorMessageUuid: String?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LogTrace> {
        /** 新的一轮（turnId = 随机 UUID）。 */
        fun newTurn(conversationUuid: String, characterUuid: String?, anchorMessageUuid: String?): LogTrace =
            LogTrace(conversationUuid, characterUuid, UUID.randomUUID().toString(), anchorMessageUuid)

        /**
         * 挂在某条消息上、不属于任何一轮的调用（图片理解）。带上角色 uuid（四期·图纸四复核 R1）：不带时日志页只能按角色名
         * 归属，角色改名后这些记录会挂到一个不存在的「旧名字」下、同名角色还会互相串。
         */
        fun forMessage(conversationUuid: String, characterUuid: String?, messageUuid: String): LogTrace =
            LogTrace(conversationUuid, characterUuid, null, messageUuid)

        /** 本轮锚点 = [history]（时间正序）里第一条 uuid 属于 [turnUserMessageUuids] 的消息；没有 = null。 */
        fun anchorOf(history: List<MessageEntity>, turnUserMessageUuids: Set<String>): String? =
            history.firstOrNull { it.messageUUID in turnUserMessageUuids }?.messageUUID
    }
}
