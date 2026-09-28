package com.situ.aichat.ui.contextlog.shared

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.ui.contextlog.formatLogTime
import com.situ.aichat.ui.contextlog.model.LogCharacterRowModel
import com.situ.aichat.ui.contextlog.model.LogDayKind
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogMapLabel
import com.situ.aichat.ui.contextlog.model.LogMediaTag
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 原列表页的中文字面量（四期·图纸四 §4.9：**逐字**收进这里供两张脸的「全部流水」共用，不进资源——
 * 诊断域旧文案，设置全面重构卷三统一资源化）。
 */
internal object LogFlowText {
    const val EMPTY_ALL = "还没有任何调用日志"
    const val DETAIL_OFF_HINT = "详细记录已关闭——卡片只显元数据与分段统计，上下文 / 回复正文不留存。可在「保留设置」中开启。"

    fun emptyCategory(displayName: String) = "「$displayName」分类下暂无日志"

    /** 「发送 N 条 · 回复 X」（X = 原 `formatDuration`，null 不接）。 */
    fun sentLine(messageCount: Int, duration: String?) = buildString {
        append("发送 $messageCount 条")
        if (duration != null) append(" · 回复 $duration")
    }
}

/** 一类失败的五条文案（键中缀 = [LlmFailureKind.raw]）。 */
data class LogFailureCopy(@StringRes val name: Int, @StringRes val short: Int, @StringRes val reason: Int, @StringRes val step1: Int, @StringRes val step2: Int)

fun failureCopy(kind: LlmFailureKind): LogFailureCopy = when (kind) {
    LlmFailureKind.TIMEOUT -> LogFailureCopy(R.string.clog_fail_timeout_name, R.string.clog_fail_timeout_short, R.string.clog_fail_timeout_reason, R.string.clog_fail_timeout_step1, R.string.clog_fail_timeout_step2)
    LlmFailureKind.BAD_FORMAT -> LogFailureCopy(R.string.clog_fail_bad_format_name, R.string.clog_fail_bad_format_short, R.string.clog_fail_bad_format_reason, R.string.clog_fail_bad_format_step1, R.string.clog_fail_bad_format_step2)
    LlmFailureKind.RATE_LIMITED -> LogFailureCopy(R.string.clog_fail_rate_limited_name, R.string.clog_fail_rate_limited_short, R.string.clog_fail_rate_limited_reason, R.string.clog_fail_rate_limited_step1, R.string.clog_fail_rate_limited_step2)
    LlmFailureKind.CONTENT_BLOCKED -> LogFailureCopy(R.string.clog_fail_content_blocked_name, R.string.clog_fail_content_blocked_short, R.string.clog_fail_content_blocked_reason, R.string.clog_fail_content_blocked_step1, R.string.clog_fail_content_blocked_step2)
    LlmFailureKind.INVALID_KEY -> LogFailureCopy(R.string.clog_fail_invalid_key_name, R.string.clog_fail_invalid_key_short, R.string.clog_fail_invalid_key_reason, R.string.clog_fail_invalid_key_step1, R.string.clog_fail_invalid_key_step2)
    LlmFailureKind.INSUFFICIENT_BALANCE -> LogFailureCopy(R.string.clog_fail_insufficient_balance_name, R.string.clog_fail_insufficient_balance_short, R.string.clog_fail_insufficient_balance_reason, R.string.clog_fail_insufficient_balance_step1, R.string.clog_fail_insufficient_balance_step2)
    LlmFailureKind.NETWORK -> LogFailureCopy(R.string.clog_fail_network_name, R.string.clog_fail_network_short, R.string.clog_fail_network_reason, R.string.clog_fail_network_step1, R.string.clog_fail_network_step2)
    LlmFailureKind.OTHER -> LogFailureCopy(R.string.clog_fail_other_name, R.string.clog_fail_other_short, R.string.clog_fail_other_reason, R.string.clog_fail_other_step1, R.string.clog_fail_other_step2)
}

/** 一轮详情迷你地图卡的断点导航行（§4.9 映射）。 */
@Composable
@ReadOnlyComposable
fun breakNavText(kind: CacheBreakKind, moduleName: String?): String = when (kind) {
    CacheBreakKind.SYSTEM_PROMPT ->
        if (moduleName != null) stringResource(R.string.clog_break_nav_module, moduleName) else stringResource(R.string.clog_break_nav_system)
    CacheBreakKind.LEADING_NOTE -> stringResource(R.string.clog_break_nav_leading)
    CacheBreakKind.TIME_MARKER -> stringResource(R.string.clog_break_nav_time)
    CacheBreakKind.HISTORY_WINDOW_SLID -> stringResource(R.string.clog_break_nav_slid)
    CacheBreakKind.HISTORY_CHANGED -> stringResource(R.string.clog_break_nav_changed)
    CacheBreakKind.SAVED_BLOCK -> stringResource(R.string.clog_break_nav_saved)
    CacheBreakKind.TAIL -> stringResource(R.string.clog_break_nav_tail)
    CacheBreakKind.NO_PREVIOUS -> stringResource(R.string.clog_break_nav_first)
    CacheBreakKind.NO_DATA -> stringResource(R.string.clog_break_nav_old)
}

/** 红框正文 / 地图顶部说明（§4.9 映射；NO_* 两种只用于地图顶部说明行）。 */
@Composable
@ReadOnlyComposable
fun cutBodyText(kind: CacheBreakKind, moduleName: String?): String = when (kind) {
    CacheBreakKind.TAIL -> stringResource(R.string.clog_cut_body_tail)
    CacheBreakKind.SAVED_BLOCK -> stringResource(R.string.clog_cut_body_saved)
    CacheBreakKind.SYSTEM_PROMPT ->
        if (moduleName != null) stringResource(R.string.clog_cut_body_module, moduleName) else stringResource(R.string.clog_cut_body_system)
    CacheBreakKind.LEADING_NOTE -> stringResource(R.string.clog_cut_body_leading)
    CacheBreakKind.TIME_MARKER -> stringResource(R.string.clog_cut_body_time)
    CacheBreakKind.HISTORY_WINDOW_SLID -> stringResource(R.string.clog_cut_body_slid)
    CacheBreakKind.HISTORY_CHANGED -> stringResource(R.string.clog_cut_body_changed)
    CacheBreakKind.NO_PREVIOUS -> stringResource(R.string.clog_cut_body_no_previous)
    CacheBreakKind.NO_DATA -> stringResource(R.string.clog_cut_body_no_data)
}

@StringRes
fun mapLabelRes(label: LogMapLabel): Int = when (label) {
    LogMapLabel.SYSTEM_PROMPT -> R.string.clog_map_label_system
    LogMapLabel.LEADING_NOTES -> R.string.clog_map_label_leading
    LogMapLabel.TAIL_BLOCKS -> R.string.clog_map_label_tail
}

/** 数字用中文单位（万）还是英文（k / M）：跟界面语言走。 */
@Composable
@ReadOnlyComposable
fun chineseUnits(): Boolean = LocalConfiguration.current.locales[0].language == "zh"

@Composable
@ReadOnlyComposable
fun formatDate(date: LocalDate, @StringRes pattern: Int): String =
    date.format(DateTimeFormatter.ofPattern(stringResource(pattern), LocalConfiguration.current.locales[0]))

@Composable
@ReadOnlyComposable
fun formatDate(millis: Long, @StringRes pattern: Int): String = formatDate(LogFormat.localDate(millis, ZoneId.systemDefault()), pattern)

/** 行内日标签：今天 / 昨天 / 短日期（失败详情时刻、同类失败）。 */
@Composable
@ReadOnlyComposable
fun dayInlineText(dayKind: LogDayKind, millis: Long): String = when (dayKind) {
    LogDayKind.TODAY -> stringResource(R.string.clog_day_today_inline)
    LogDayKind.YESTERDAY -> stringResource(R.string.clog_day_yesterday_inline)
    LogDayKind.EARLIER -> formatDate(millis, R.string.clog_date_short_pattern)
}

/** 角色页节头：今天 · 日期 / 昨天 · 日期 / 日期。 */
@Composable
@ReadOnlyComposable
fun dayHeaderText(dayKind: LogDayKind, date: LocalDate): String {
    val d = formatDate(date, R.string.clog_date_pattern)
    return when (dayKind) {
        LogDayKind.TODAY -> stringResource(R.string.clog_day_header_today, d)
        LogDayKind.YESTERDAY -> stringResource(R.string.clog_day_header_yesterday, d)
        LogDayKind.EARLIER -> d
    }
}

/** 首页角色行 / 系统任务行的日格式：今天 P / 昨天 HH:mm · P / 短日期 · P。 */
@Composable
@ReadOnlyComposable
private fun dayMeta(row: LogCharacterRowModel, p: String): String = when (row.dayKind) {
    LogDayKind.TODAY -> stringResource(R.string.clog_meta_today, p)
    LogDayKind.YESTERDAY -> stringResource(R.string.clog_meta_yesterday, formatLogTime(row.lastActivityMillis), p)
    LogDayKind.EARLIER -> stringResource(R.string.clog_meta_earlier, formatDate(row.lastActivityMillis, R.string.clog_date_short_pattern), p)
}

/** 首页角色行元信息（§4.9 末段组装规则）。 */
@Composable
@ReadOnlyComposable
fun characterMetaText(row: LogCharacterRowModel): String {
    val parts = buildList {
        if (row.turns > 0) add(stringResource(R.string.clog_meta_turns, row.turns))
        if (row.voiceTurns > 0) add(stringResource(R.string.clog_meta_voice, row.voiceTurns))
        if (row.background > 0) add(stringResource(R.string.clog_meta_background, row.background))
    }
    return dayMeta(row, parts.joinToString(" · "))
}

/** 首页系统任务行元信息（§4.9 末段）。 */
@Composable
@ReadOnlyComposable
fun systemMetaText(row: LogCharacterRowModel): String {
    val d = dayMeta(row, stringResource(R.string.clog_meta_calls, row.calls))
    return if (row.topSources.size >= 2) {
        stringResource(R.string.clog_system_meta_many, row.topSources[0] + stringResource(R.string.clog_source_sep) + row.topSources[1], d)
    } else {
        stringResource(R.string.clog_system_meta_one, row.topSources.firstOrNull().orEmpty(), d)
    }
}

/** 「你：[图片]「看我做的饭」」。 */
@Composable
@ReadOnlyComposable
fun quoteText(quote: LogTurnQuoteText): String {
    val image = stringResource(R.string.clog_quote_image)
    val voice = stringResource(R.string.clog_quote_voice)
    val media = quote.media.joinToString("") { if (it == LogMediaTag.IMAGE) image else voice }
    val text = quote.text?.let { stringResource(R.string.clog_quote_wrap, it) }.orEmpty()
    return stringResource(R.string.clog_turn_quote, media + text)
}

/** 后台行 / 子项图标（§4.10）：按来源所属第一个分类取。 */
fun sourceIcon(source: String): ImageVector = when (LogCategory.sourceFilterCategoriesFor(source).firstOrNull()) {
    LogCategory.CHAT -> Icons.Outlined.ChatBubbleOutline
    LogCategory.VOICE_CALL -> Icons.Outlined.Call
    LogCategory.MEMORY -> Icons.Outlined.Bookmark
    LogCategory.ANALYSIS -> Icons.Outlined.Insights
    LogCategory.MOMENTS -> Icons.Outlined.Schedule
    LogCategory.DIARY -> Icons.AutoMirrored.Outlined.MenuBook
    LogCategory.STORY -> Icons.Outlined.AutoStories
    LogCategory.SCHEDULE -> Icons.Outlined.Event
    LogCategory.GIFT -> Icons.Outlined.CardGiftcard
    LogCategory.WORLD -> Icons.Outlined.Public
    else -> Icons.Outlined.Settings
}

/** 失败类图标（§4.10·失败详情头卡）。 */
fun failureIcon(kind: LlmFailureKind): ImageVector = when (kind) {
    LlmFailureKind.TIMEOUT -> Icons.Outlined.HourglassEmpty
    LlmFailureKind.BAD_FORMAT -> Icons.Outlined.ErrorOutline
    LlmFailureKind.RATE_LIMITED -> Icons.Outlined.Speed
    LlmFailureKind.CONTENT_BLOCKED -> Icons.Outlined.Block
    LlmFailureKind.INVALID_KEY -> Icons.Outlined.Key
    LlmFailureKind.INSUFFICIENT_BALANCE -> Icons.Outlined.AccountBalanceWallet
    LlmFailureKind.NETWORK -> Icons.Outlined.WifiOff
    LlmFailureKind.OTHER -> Icons.AutoMirrored.Outlined.HelpOutline
}
