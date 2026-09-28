package com.situ.aichat.diagnostics

import com.situ.aichat.data.remote.llm.LlmError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 上下文日志失败分类（四期·图纸三 §3.6）：8 类。[raw] 落库进 `log_entries.failureKind`——
 * **改 raw 值 = 老记录分类失效**（REDLINES §1）。
 */
enum class LlmFailureKind(val raw: String) {
    TIMEOUT("timeout"), BAD_FORMAT("bad_format"), RATE_LIMITED("rate_limited"), CONTENT_BLOCKED("content_blocked"),
    INVALID_KEY("invalid_key"), INSUFFICIENT_BALANCE("insufficient_balance"), NETWORK("network"), OTHER("other");
    companion object { fun fromRaw(raw: String?): LlmFailureKind? = entries.firstOrNull { it.raw == raw } }
}

/** 一次失败的分类结果：类别 + 能取到时的 HTTP 状态码。 */
data class LlmFailure(val kind: LlmFailureKind, val httpStatus: Int?)

/**
 * 失败分类规则（**自写**：先看错误类型，再看状态码，再看报错关键词；不照抄任何第三方错误解析代码）。
 * 写入时从 [Throwable] 分（异常类型只在那一刻还在）；只有字符串的失败与 v50 老记录走 [classifyText] 兜底。
 */
object LlmFailureClassifier {

    /** 关键词表（比对前整段 `lowercase()`，`contains` 判定）。 */
    private val BALANCE = listOf(
        "insufficient balance", "insufficient_balance", "insufficient_quota", "exceeded your current quota",
        "exceeded_current_quota", "payment required", "billing", "credit balance", "余额不足", "欠费", "账户余额",
    )
    private val CONTENT = listOf(
        "content_filter", "content filter", "content_policy", "content policy", "moderation", "sensitive",
        "inappropriate", "data_inspection_failed", "high risk", "敏感", "违规", "不安全", "审核",
    )
    private val KEY = listOf(
        "invalid api key", "invalid_api_key", "incorrect api key", "api_key_invalid", "authentication",
        "unauthorized", "invalid token", "鉴权", "认证失败",
    )
    private val RATE = listOf("rate limit", "rate_limit", "ratelimit", "too many requests", "请求过于频繁", "限流")
    private val TIMEOUT_WORDS = listOf("timeout", "timed out", "no stream event within", "超时")
    private val NETWORK = listOf(
        "unable to resolve host", "failed to connect", "connection reset", "connection refused",
        "network is unreachable", "software caused connection abort", "网络",
    )
    /** 原文 `startsWith`（不转小写）：[LlmError] 三种格式类错误的中文消息开头。 */
    private val FORMAT_PREFIX = listOf("解析响应失败", "服务器返回了无效响应", "API URL 无效")

    private val PAREN_STATUS = Regex("""\((\d{3})\)""")
    private val HTTP_STATUS = Regex("""HTTP 错误：(\d{3})""")

    fun classify(error: Throwable): LlmFailure = when (error) {
        is LlmError.Http -> LlmFailure(byStatus(error.statusCode, error.bodySummary.orEmpty().lowercase()), error.statusCode)
        is LlmError.Timeout -> LlmFailure(LlmFailureKind.TIMEOUT, null)
        is LlmError.DecodingError, is LlmError.InvalidResponse, is LlmError.InvalidUrl -> LlmFailure(LlmFailureKind.BAD_FORMAT, null)
        is LlmError.Stream -> LlmFailure(byWords(error.detail.lowercase()) ?: LlmFailureKind.OTHER, null)
        is SocketTimeoutException -> LlmFailure(LlmFailureKind.TIMEOUT, null)
        is InterruptedIOException ->
            if (error.message.orEmpty().lowercase().contains("timeout")) LlmFailure(LlmFailureKind.TIMEOUT, null)
            else LlmFailure(LlmFailureKind.NETWORK, null)
        is IOException -> LlmFailure(LlmFailureKind.NETWORK, null)
        else -> classifyText(error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName)
    }

    fun classifyText(errorMessage: String): LlmFailure {
        val head = errorMessage.substringBefore(" - ")
        val code = (PAREN_STATUS.find(head) ?: HTTP_STATUS.find(head))?.groupValues?.get(1)?.toIntOrNull()
        if (code != null) return LlmFailure(byStatus(code, errorMessage.lowercase()), code)
        if (FORMAT_PREFIX.any { errorMessage.startsWith(it) }) return LlmFailure(LlmFailureKind.BAD_FORMAT, null)
        return LlmFailure(byWords(errorMessage.lowercase()) ?: LlmFailureKind.OTHER, null)
    }

    /** 行的失败类：成功 → null；有 [failureKindRaw] → 它；否则按 [errorMessage] 现算（v50 老记录）。 */
    fun kindOfRow(isSuccess: Boolean, failureKindRaw: String?, errorMessage: String?): LlmFailureKind? {
        if (isSuccess) return null
        return LlmFailureKind.fromRaw(failureKindRaw) ?: classifyText(errorMessage.orEmpty()).kind
    }

    /** 有状态码时：余额 / 内容 / key 关键词优先（服务商常把它们挂在 400 / 429 上），再按码分。 */
    private fun byStatus(code: Int, lower: String): LlmFailureKind = when {
        BALANCE.any { lower.contains(it) } -> LlmFailureKind.INSUFFICIENT_BALANCE
        CONTENT.any { lower.contains(it) } -> LlmFailureKind.CONTENT_BLOCKED
        KEY.any { lower.contains(it) } -> LlmFailureKind.INVALID_KEY
        else -> when (code) {
            401, 403 -> LlmFailureKind.INVALID_KEY
            402 -> LlmFailureKind.INSUFFICIENT_BALANCE
            408, 504 -> LlmFailureKind.TIMEOUT
            429 -> LlmFailureKind.RATE_LIMITED
            400, 404, 405, 413, 415, 422 -> LlmFailureKind.BAD_FORMAT
            else -> LlmFailureKind.OTHER
        }
    }

    /** 无状态码时按关键词：余额 → 内容 → key → 限流 → 超时 → 网络，第一个命中的类；都不中 = null。 */
    private fun byWords(lower: String): LlmFailureKind? = when {
        BALANCE.any { lower.contains(it) } -> LlmFailureKind.INSUFFICIENT_BALANCE
        CONTENT.any { lower.contains(it) } -> LlmFailureKind.CONTENT_BLOCKED
        KEY.any { lower.contains(it) } -> LlmFailureKind.INVALID_KEY
        RATE.any { lower.contains(it) } -> LlmFailureKind.RATE_LIMITED
        TIMEOUT_WORDS.any { lower.contains(it) } -> LlmFailureKind.TIMEOUT
        NETWORK.any { lower.contains(it) } -> LlmFailureKind.NETWORK
        else -> null
    }
}
