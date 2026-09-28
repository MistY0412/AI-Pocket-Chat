package com.situ.aichat.ui.chat

import com.situ.aichat.data.calendar.CalendarAction
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.CompletedToolCall
import com.situ.aichat.data.remote.llm.RequestToolCallDto
import com.situ.aichat.data.remote.llm.RequestToolCallFunctionDto
import com.situ.aichat.meeting.FutureMeetingTool
import com.situ.aichat.offline.OfflineMeetingAction

/**
 * 工具回喂请求的消息装配（纯函数·从 `AssistantTurnEngine.fetchToolCallFollowUp` 只搬不改抽出）：
 * 原消息 + 一条 assistant(tool_calls) + 每个 call 一条 role=tool 结果（1:1 iOS fetchToolCallFollowUp）。
 * 日历工具按 call 自身参数就地解析动作给具体执行描述；线下 / 约见面 / 约定工具给明确结果文案（[toolFollowUpResultText]）。
 *
 * [reasoningContent] = 同一轮工具流里收到的 `reasoning_content` 原文（DeepSeek 思考模式硬性要求回传，缺了回 400
 * 「reasoning_content … must be passed back」·图纸 2026-09-26）；null = 本轮没有 → 省略该键，请求字节与旧版一致。
 */
internal fun buildToolCallFollowUpMessages(
    originalMessages: List<ChatMessageDto>,
    completedCalls: List<CompletedToolCall>,
    calendarNeedsConfirmation: Boolean,
    reasoningContent: String?,
): List<ChatMessageDto> {
    val assistantMsg = ChatMessageDto(
        role = "assistant",
        content = null,
        reasoningContent = reasoningContent,
        toolCalls = completedCalls.map {
            RequestToolCallDto(id = it.id, type = "function", function = RequestToolCallFunctionDto(it.name, it.arguments))
        },
    )

    val followUp = ArrayList<ChatMessageDto>(originalMessages.size + completedCalls.size + 1)
    followUp.addAll(originalMessages)
    followUp.add(assistantMsg)
    for (call in completedCalls) {
        // 每个 call 按自身参数**就地**解析对应日历动作（替代旧的「按位下标映射 calendarActions」——
        // 解析失败 / 线下 / 约见面混调时下标会错配，把别的调用的结果文案安到这条上，见 H3）。
        val calendarAction = if (
            !OfflineMeetingAction.isOfflineMeetingTool(call.name) && !FutureMeetingTool.isFutureMeetingTool(call.name)
        ) {
            runCatching { CalendarAction.fromToolCallArguments(call.arguments) }.getOrNull()
        } else {
            null
        }
        // 据实陈述、绝不预报「已完成」（确认卡待确认 / 自动执行将写入），口吻交角色提示词把关（决定 C）。
        // ③ 大输出安全阀（单点接线）：结果文案过阀截断。当前状态串短、永不触发（0-3 golden 看门）；
        // 将来「内容返回型工具」的大输出经此自动截断、防撑爆对话。
        val resultText = truncateToolResultText(
            toolFollowUpResultText(calendarAction, call.name, calendarNeedsConfirmation),
        )
        followUp.add(ChatMessageDto(role = "tool", content = resultText, toolCallId = call.id))
    }
    return followUp
}
