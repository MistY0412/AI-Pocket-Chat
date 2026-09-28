package com.situ.aichat.diagnostics

import com.situ.aichat.data.remote.llm.ChatContentPart
import com.situ.aichat.data.remote.llm.ChatMessageDto

/**
 * 把发给大模型的消息渲染成可读全文 + 统一截断（批 D·纯函数·移植 iOS `LogService.formatContextForDisplay` /
 * `clippedTextForLog`）。
 *
 * 纯展示用，不被提示词侧检测器消费；唯一的解析方是同文件的 [parseRendered]（日志页「改写前」·改表头须同步）。
 * **全量记录（D-3 打磨·2026-07-16 用户拍板）**：旧版承袭 iOS 对后台重任务落库前剪 1200 字/条 +
 * 8000 字全文 + 6000 字回复——故事圣经、记忆档案被剪掉大半，无法判断生成质量，已整体取消。
 * 现统一只留 [STORED_TEXT_HARD_LIMIT] 极端安全帽，体积靠「detail 默认关 + 容量轮转 + 列表轻投影
 * （[LogListRow]）」控住。
 */
object LogContextFormat {

    /**
     * 落库文本极端安全帽（单字段字符数·防病态超长，非日常裁剪）。正常提示词/回复远小于此
     * （聊天全上下文 ~3 万字、故事 ~4 万字）；触发即按 [clip] 带原长提示截断。
     * 20 万字 ≈ 600KB UTF-8；上下文 + 回复双字段最坏 ~1.2MB，仍留在 SQLite CursorWindow（2MB/行）之下
     * ——改大此值前先想那道墙。
     */
    const val STORED_TEXT_HARD_LIMIT = 200_000

    /**
     * 估算用纯文本：各消息正文拼接（不含装饰），喂 [TokenEstimator]。
     * **多模态消息取 contentParts 的可读表示**——带图/带音频的消息 `content` 恒为 null，
     * 早先直接 `content.orEmpty()` 会让整条按 0 token 算（图片轮的估算严重偏低）。
     */
    fun plainText(messages: List<ChatMessageDto>): String =
        messages.joinToString(separator = "\n") { readableBody(it) }

    /**
     * 一条消息的可读正文：普通消息取 `content`；**多模态消息把 contentParts 摊开**——
     * text 段原样、媒体段渲染成**替身**（`[图片 · 约 N KB]`），绝不把 base64 落进诊断库
     * （REDLINES §3「内容类日志用替身」）。此前 contentParts 整个不看，带图回合的日志正文是空白的、
     * 「图片理解」那条更是只剩 system 段，排障时看不出图到底挂没挂。
     */
    internal fun readableBody(msg: ChatMessageDto): String {
        msg.content?.takeIf { it.isNotEmpty() }?.let { return it }
        val parts = msg.contentParts ?: return msg.content.orEmpty()
        return parts.joinToString(separator = "\n") { part ->
            if (part is ChatContentPart.Text) part.text else mediaPlaceholder(part).orEmpty()
        }
    }

    /** 媒体段的替身文字（单源·四期·图纸三 §3.8 导出请求共用）：图片 / 语音 → `[图片 · 约 N KB]` / `[语音 · 约 N KB]`；文字段 → null。 */
    internal fun mediaPlaceholder(part: ChatContentPart): String? = when (part) {
        is ChatContentPart.Text -> null
        is ChatContentPart.ImageUrl -> "[图片 · 约 ${approxKb(part.url)} KB]"
        is ChatContentPart.InputAudio -> "[语音 · 约 ${approxKb(part.base64)} KB]"
    }

    /** base64 / data URI 的近似原始体积（KB）——只给量级，不落原文。 */
    private fun approxKb(encoded: String): Int {
        val payload = encoded.substringAfterLast(',', encoded)
        return (payload.length.toLong() * 3 / 4 / 1024).toInt().coerceAtLeast(1)
    }

    /** 完整渲染（不截断；1:1 iOS formatContextForDisplay 的排版骨架）。 */
    fun render(messages: List<ChatMessageDto>): String {
        val lines = ArrayList<String>(messages.size * 3 + 5)
        lines += SEP
        lines += "       发送给大模型的完整上下文"
        lines += SEP
        lines += ""
        for ((index, msg) in messages.withIndex()) {
            val (icon, label) = roleIconLabel(msg.role)
            lines += "───── $icon $label [${index + 1}/${messages.size}] ─────"
            lines += readableBody(msg)
            lines += ""
        }
        lines += SEP
        lines += "共 ${messages.size} 条消息"
        return lines.joinToString(separator = "\n")
    }

    /** 落库上下文 = 完整渲染 + 极端安全帽。 */
    fun storedContext(messages: List<ChatMessageDto>): String =
        clip(render(messages), STORED_TEXT_HARD_LIMIT)

    /** 落库回复 = 原文 + 极端安全帽。 */
    fun storedResponse(text: String): String = clip(text, STORED_TEXT_HARD_LIMIT)

    /** 统一截断：超 [limit] 取前缀 + 截断提示（limit 为 null/≤0 不截，1:1 iOS clippedTextForLog）。 */
    fun clip(text: String, limit: Int?): String {
        if (limit == null || limit <= 0) return text
        if (text.length <= limit) return text
        // 截断点若劈开增补字符代理对（emoji），高位回退一格——库里不留孤代理（复核 R1-🔵·旧口径同病顺手治）。
        val cut = if (Character.isHighSurrogate(text[limit - 1])) limit - 1 else limit
        return text.take(cut) + "\n\n[日志内容已截断，共 ${text.length} 字]"
    }

    /** [parseRendered] 的一条：表头标签（系统提示 / 用户 / 角色 / 原 role）+ 正文。 */
    data class RenderedMessage(val label: String, val body: String)

    /**
     * [render] 的逆（四期·图纸四 §3.7）：表头之后到下一个表头或整行 == [SEP] 之前为正文（去掉末尾一个空行；被 [clip]
     * 截断的末条正文到全文结尾）；没有表头 → 空表。改表头格式须两处同改并跑往返测试（REDLINES §1）。
     */
    fun parseRendered(text: String): List<RenderedMessage> {
        val out = ArrayList<RenderedMessage>()
        var label: String? = null
        val body = ArrayList<String>()
        fun flush() {
            val l = label ?: return
            if (body.lastOrNull() == "") body.removeAt(body.lastIndex)
            out += RenderedMessage(l, body.joinToString(separator = "\n"))
            body.clear(); label = null
        }
        for (line in text.split('\n')) {
            val header = RENDERED_HEADER.matchEntire(line)
            when {
                header != null -> { flush(); label = header.groupValues[1] }
                line == SEP -> flush()
                label != null -> body += line
            }
        }
        flush()
        return out
    }

    /** [render] 的逐条表头（组 1 = 标签）。 */
    private val RENDERED_HEADER = Regex("""^───── \S+ (.+) \[\d+/\d+] ─────$""")

    private fun roleIconLabel(role: String): Pair<String, String> = when (role) {
        "system" -> "⚙️" to "系统提示"
        "user" -> "👤" to "用户"
        "assistant" -> "🤖" to "角色"
        else -> "📝" to role
    }

    private const val SEP = "═══════════════════════════════"
}
