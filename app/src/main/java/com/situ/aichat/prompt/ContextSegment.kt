package com.situ.aichat.prompt

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * 上下文分段信息：发给大模型的某一模块的字符数 + 估算 token（批 D·上下文日志「结构化展示」）。
 *
 * 1:1 iOS `ContextSegment`，但**弃用 iOS 的 `iconName`（SF Symbol 串）**——本项目 UI 按 Fable-5 设计语言
 * 自绘图标，改存 [systemModuleType]（[com.situ.aichat.prompt.SystemModuleType] 的 rawValue，自定义模块=null），
 * 由展示层自行映射图标。序列化为 JSON 存进 `LogEntryEntity.contextSegmentsJson`，仅日志详情页消费。
 *
 * 仅聊天管线（[PromptScene] 四态走 [PromptBuilder.buildMessages] 同一模块系统）产生分段；后台生成类任务
 * （朋友圈/日记/故事/记忆/日程/通知/礼物）不经模块系统，传空分段（1:1 iOS）。
 */
@Serializable
data class ContextSegment(
    /** 模块显示名（= [com.situ.aichat.prompt.PromptModule.name]，如「核心规则」「对话历史」）。 */
    val name: String,
    /** 系统模块类型 rawValue（自定义模块=null）；展示层据此映射 Fable-5 图标。 */
    val systemModuleType: String? = null,
    /** 该段字符数。 */
    val charCount: Int,
    /** [TokenEstimator] 估算的 token 数。 */
    val estimatedTokens: Int,
    /** 位置：[POSITION_PREFIX] 前置区 / [POSITION_HISTORY] 对话历史 / [POSITION_SUFFIX] 后置区。 */
    val position: String,
    /**
     * 指纹（四期·图纸三）：前置 / 后置模块段 = 该段文字的 [fingerprintOf]；省钱挪位段 = 它那条消息的 [messageFingerprintOf]
     * （与请求形状逐条指纹同源，缓存断点靠它认出挪位块·复核 R1）；「对话历史」段与 v51 前的记录 = null。
     */
    val fingerprint: String? = null,
) {
    companion object {
        const val POSITION_PREFIX = "prefix"
        const val POSITION_HISTORY = "history"
        const val POSITION_SUFFIX = "suffix"

        /** 内容指纹：UTF-8 的 SHA-256 取前 16 位小写十六进制（四期·图纸三·不可逆，只用来比「变没变」）。 */
        fun fingerprintOf(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            val hex = "0123456789abcdef"
            return buildString(16) {
                for (i in 0 until 8) {
                    val b = digest[i].toInt() and 0xff
                    append(hex[b ushr 4]).append(hex[b and 0x0f])
                }
            }
        }

        /**
         * 一条消息的指纹（单源：请求形状逐条指纹与省钱挪位段共用——同源才比得上·复核 R1）。原料 = 角色 / 正文 / 思考 /
         * 工具调用 id / 工具调用全拼，以 `\u0000` 分隔；媒体段按**原始数据**算（同 KB 不同图不会误判「没变」），原料只进 SHA-256、不落库。
         */
        fun messageFingerprintOf(m: ChatMessageDto): String {
            val body = m.contentParts?.joinToString("\u0001") { part ->
                when (part) {
                    is ChatContentPart.Text -> "t:" + part.text
                    is ChatContentPart.ImageUrl -> "i:" + part.url
                    is ChatContentPart.InputAudio -> "a:" + part.base64
                }
            } ?: m.content.orEmpty()
            return fingerprintOf(
                m.role + "\u0000" + body + "\u0000" + m.reasoningContent.orEmpty() + "\u0000" + m.toolCallId.orEmpty() + "\u0000" +
                    m.toolCalls?.joinToString("\u0001") { it.id + "|" + it.function.name + "|" + it.function.arguments }.orEmpty(),
            )
        }
    }
}
