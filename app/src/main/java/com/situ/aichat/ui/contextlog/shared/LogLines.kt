package com.situ.aichat.ui.contextlog.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.FailureRateAlert
import com.situ.aichat.diagnostics.LlmFailureClassifier
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.formatLogTime
import com.situ.aichat.ui.contextlog.model.LogFailureView
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogTurnCard
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import java.time.ZoneId

/*
 * 日志页各屏拼好的整行文字（四期·图纸四 §4.2–§4.6·两张脸共用，只写一处）。枚举 → 单条文案的映射在 [LogCopy.kt]；
 * 本文件是「把几条文案按图纸规则拼成一行」（§11 登记：LogCopy 已到 §2.1 行数预算，拼行另起此文件）。
 */

/** 「HH:mm · N tk」（子项 / 后台行·估算带 ≈）。 */
fun timeTokensText(millis: Long, tokens: Int, estimated: Boolean): String =
    formatLogTime(millis) + " · " + LogFormat.callTokens(tokens, estimated)

/** 失败子项尾巴「 · 失败」。 */
@Composable
@ReadOnlyComposable
fun kidTailText(): String = " · " + stringResource(R.string.clog_kid_failed)

/** 轮卡第 2 行（成功）：回复 N 条 · N tk · 缓存 X% · X 秒 · 重试 N 次（缺项跳过·§4.2）。 */
@Composable
@ReadOnlyComposable
fun turnMetaText(card: LogTurnCard): String = buildList {
    card.replies?.takeIf { it > 0 }?.let { add(stringResource(R.string.clog_turn_replies, it)) }
    add(LogFormat.callTokens(card.tokens, card.tokensEstimated))
    card.cacheRatePercent?.let { add(stringResource(R.string.clog_turn_cache, it)) }
    card.durationMillis?.let { add(stringResource(R.string.clog_turn_duration, LogFormat.seconds(it))) }
    if (card.retries > 0) add(stringResource(R.string.clog_turn_retries, card.retries))
}.joinToString(" · ")

/** 轮卡失败行「失败 · 类名（短句）」。 */
@Composable
@ReadOnlyComposable
fun turnFailedText(kind: LlmFailureKind): String {
    val copy = failureCopy(kind)
    return stringResource(R.string.clog_turn_failed, stringResource(copy.name), stringResource(copy.short))
}

/** 条目页说明行：角色名（空 → 来源）· 对话「标题」· 引用串。 */
@Composable
@ReadOnlyComposable
fun entryCaptionText(e: LogEntryEntity, conversationTitle: String?, quote: LogTurnQuoteText?): String = buildList {
    add(e.characterName.ifBlank { e.source })
    conversationTitle?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.clog_caption_chat, it)) }
    quote?.let { add(quoteText(it)) }
}.joinToString(" · ")

/** 三个改写计数里非 0 的个数（「改写了 N 处」）。 */
fun adaptedCount(a: LogSendAdaptation): Int = listOf(a.leadingMerged, a.midMerged, a.tailMerged).count { it > 0 }

/** 改写说明的有序列表项（资源 id 与计数；§4.5 第 3 条）。 */
internal fun adaptedItems(a: LogSendAdaptation): List<Pair<Int, Int>> = buildList {
    if (a.leadingMerged > 0) add(R.string.clog_sent_item_leading to a.leadingMerged)
    if (a.midMerged > 0) add(R.string.clog_sent_item_mid to a.midMerged)
    if (a.tailMerged > 0) add(R.string.clog_sent_item_tail to a.tailMerged)
}

/** 「实际发出去的样子」导航行副行。 */
@Composable
@ReadOnlyComposable
fun sentSubtitleText(a: LogSendAdaptation?): String = when {
    a == null -> stringResource(R.string.clog_sent_sub_none)
    a.asIs || adaptedCount(a) == 0 -> stringResource(R.string.clog_sent_sub_asis) // 计数全 0 = 这一条没要改的（复核 R1）
    else -> stringResource(R.string.clog_sent_sub_adapted, adaptedCount(a))
}

/** Token 卡附行：思考 · 命中 · 未命中（服务商没报缓存时只写思考）。 */
@Composable
@ReadOnlyComposable
fun tokensExtraText(e: LogEntryEntity): String {
    val reasoning = LogFormat.grouped(e.reasoningTokens.toLong())
    return if (e.cacheHitTokens + e.cacheMissTokens == 0) {
        stringResource(R.string.clog_tokens_extra_reasoning, reasoning)
    } else {
        stringResource(R.string.clog_tokens_extra, reasoning, LogFormat.grouped(e.cacheHitTokens.toLong()), LogFormat.grouped(e.cacheMissTokens.toLong()))
    }
}

/** 地图页说明行「名字 · HH:mm 这一轮 · 输入 N tk」。 */
@Composable
@ReadOnlyComposable
fun mapCaptionText(e: LogEntryEntity, totalTokens: Int): String =
    stringResource(R.string.clog_map_caption, e.characterName.ifBlank { e.source }, formatLogTime(e.timestampMillis), LogTokenFormat.compact(totalTokens))

/** 实际发送页说明行（没有服务商 → 去掉括号段）。 */
@Composable
@ReadOnlyComposable
fun sentCaptionText(e: LogEntryEntity, providerLabel: String?): String {
    val name = e.characterName.ifBlank { e.source }
    val time = formatLogTime(e.timestampMillis)
    return if (providerLabel != null) stringResource(R.string.clog_sent_caption, name, time, e.modelName, providerLabel)
    else stringResource(R.string.clog_sent_caption_no_provider, name, time, e.modelName)
}

/** 失败详情「时间」值：日标签 + HH:mm:ss。 */
@Composable
@ReadOnlyComposable
fun failureTimeText(e: LogEntryEntity, f: LogFailureView): String =
    stringResource(R.string.clog_time_value, dayInlineText(f.dayKind, e.timestampMillis), LogFormat.timeWithSeconds(e.timestampMillis, ZoneId.systemDefault()))

/** 失败详情「同类失败」值：日标签 N 次（HH:mm、HH:mm、HH:mm）。 */
@Composable
@ReadOnlyComposable
fun sameKindText(e: LogEntryEntity, f: LogFailureView): String =
    stringResource(R.string.clog_same_kind_value, dayInlineText(f.dayKind, e.timestampMillis), f.sameKindCount, f.sameKindTimes.joinToString("、") { formatLogTime(it) })

/** 失败详情「角色」值：角色名（有引用时接「 · 引用串」）。 */
@Composable
@ReadOnlyComposable
fun failureCharacterText(e: LogEntryEntity, quote: LogTurnQuoteText?): String =
    e.characterName + (quote?.let { " · " + quoteText(it) } ?: "")

/** 地图页服务商卡：命中值「N tk（X%）」/ 未命中值「N tk」；没报 → null。 */
@Composable
@ReadOnlyComposable
fun providerHitMissText(e: LogEntryEntity): Pair<String, String>? {
    val hit = e.cacheHitTokens.toLong()
    val miss = e.cacheMissTokens.toLong()
    val rate = LogFormat.cacheRate(hit, miss) ?: return null
    return stringResource(R.string.clog_provider_hit_value, LogFormat.grouped(hit), rate) to
        stringResource(R.string.clog_provider_miss_value, LogFormat.grouped(miss))
}

/** 告警条每行：原「「来源」失败 a/b（p%）」+「 · 类名」后缀（该来源没有主类时不接·§4.1a 第 3 条）。 */
@Composable
@ReadOnlyComposable
fun alertLineTexts(alerts: List<FailureRateAlert>, kinds: Map<String, LlmFailureKind>): List<String> = alerts.map {
    stringResource(R.string.contextlog_alert_line, it.source, it.failures, it.total, it.percent) +
        (kinds[it.source]?.let { k -> stringResource(R.string.clog_alert_kind, stringResource(failureCopy(k).name)) } ?: "")
}

/** 全部流水失败卡第三行（§4.1b）：粗体类名 + 「 · 」+ 一句话。 */
@Composable
@ReadOnlyComposable
fun flowFailureLine(entry: LogListRow): AnnotatedString {
    val kind = LlmFailureClassifier.kindOfRow(false, entry.failureKind, entry.errorMessage) ?: LlmFailureKind.OTHER
    val copy = failureCopy(kind)
    val name = stringResource(copy.name)
    val full = stringResource(R.string.clog_flow_failure_line, name, stringResource(copy.short))
    val at = full.indexOf(name)
    return buildAnnotatedString {
        append(full)
        if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), at, at + name.length)
    }
}
