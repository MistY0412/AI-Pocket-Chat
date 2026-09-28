package com.situ.aichat.data.remote.llm

import java.net.URI

/**
 * DeepSeek 思考模式「本轮 assistant 消息必须带 `reasoning_content`」的补位（图纸 2026-09-26-末尾助手消息补空思考）。
 *
 * DeepSeek 官方端点（2026-09-26 真 API 实测 v4-flash / v4-pro / deepseek-chat）在思考模式下，请求带 tools 时要求
 * **最后一条 user 之后的每一条** assistant 都带 `reasoning_content`，缺一条就 400「The `reasoning_content` in the
 * thinking mode must be passed back to the API.」；空串被接受。聊天历史末尾可能是「角色这边」的记录——重新生成后
 * 留下的邀约卡 / 红包收退事件、自动恢复、世界书第 0 层角色条目——它们本来就没有思考原文，这里如实补 `""`。
 *
 * 工具回喂同理：模型只回工具调用、流里却一段 `reasoning_content` 都没有时，那条 assistant(tool_calls) 也补 `""`。
 *
 * 门控：只认**请求真的发往 DeepSeek 官方地址**（baseUrl host = `api.deepseek.com`·不看服务商类型——选了 DeepSeek
 * 却填中转站地址的不算，2026-09-26 用户拍板收紧），且思考没被明确关掉（不传思考参数时 DeepSeek 默认也在思考，实测照样 400）。
 * 其余情况原样返回同一实例，请求字节不变。已带 `reasoning_content` 的消息不动。
 */
internal object DeepSeekReasoningPlaceholder {

    private const val DEEPSEEK_HOST = "api.deepseek.com"

    /** 需要时返回补好的新表；不需要时返回 [messages] 本身。 */
    fun fill(messages: List<ChatMessageDto>, config: ApiConfigValues): List<ChatMessageDto> {
        if (!applies(config)) return messages
        val lastUser = messages.indexOfLast { it.role == "user" }
        var patched: MutableList<ChatMessageDto>? = null
        for (i in lastUser + 1 until messages.size) {
            val message = messages[i]
            if (message.role == "assistant" && message.reasoningContent == null) {
                val out = patched ?: messages.toMutableList().also { patched = it }
                out[i] = message.copy(reasoningContent = "")
            }
        }
        return patched ?: messages
    }

    /** 请求发往 DeepSeek 官方地址，且这次请求没有显式关思考。 */
    internal fun applies(config: ApiConfigValues): Boolean {
        val isOfficialDeepSeek = runCatching { URI(config.baseUrl.trim()).host?.lowercase() }.getOrNull() == DEEPSEEK_HOST
        return isOfficialDeepSeek && ReasoningPayloadMapper.payload(config).thinking?.type != "disabled"
    }
}
