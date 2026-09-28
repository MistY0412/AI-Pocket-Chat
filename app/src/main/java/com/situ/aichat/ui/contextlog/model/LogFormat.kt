package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.diagnostics.LogTokenFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 日志页数字 / 时刻格式（四期·图纸四 §3.9·纯函数·两张脸共用）。
 * 数字一律 [Locale.ROOT]（小数点恒「.」、千分位恒「,」），与 [LogTokenFormat] 同口径。`HH:mm` 时刻沿用
 * `ui/contextlog/ContextLogComponents.kt` 的 `formatLogTime`（不另写一份）。
 */
object LogFormat {

    /**
     * 大数 token：中文——< 1 万原数、< 100 万「x.x万」（去掉「.0」）、其余四舍五入到整万；
     * 英文——< 1000 原数、< 100 万「x.xk」、其余「x.xM」（都去掉「.0」）。382000 → 38.2万、410000 → 41万、2380000 → 238万。
     */
    fun bigTokens(n: Long, chineseUnits: Boolean): String = if (chineseUnits) {
        when {
            n < 10_000 -> n.toString()
            n < 1_000_000 -> oneDecimal(n / 10_000.0) + "万"
            else -> (n / 10_000.0).roundToLong().toString() + "万"
        }
    } else {
        when {
            n < 1_000 -> n.toString()
            n < 1_000_000 -> oneDecimal(n / 1_000.0) + "k"
            else -> oneDecimal(n / 1_000_000.0) + "M"
        }
    }

    /** 图表刻度：中文 = 以万为单位的纯数字（一位小数、去「.0」·单位写在卡标题里）；英文 = [bigTokens]。 */
    fun axisTokens(n: Long, chineseUnits: Boolean): String =
        if (chineseUnits) oneDecimal(n / 10_000.0) else bigTokens(n, chineseUnits = false)

    /** 单次调用 token（与原列表同口径）：估算值带「≈」+ 「 tk」。 */
    fun callTokens(n: Int, isEstimated: Boolean): String = LogTokenFormat.withEstimatePrefix(n, isEstimated) + " tk"

    /** 千分位：5760 → 5,760。 */
    fun grouped(n: Long): String = String.format(Locale.ROOT, "%,d", n)

    /** 秒（一位小数）：6800 → 6.8。 */
    fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f", ms / 1000.0)

    /** hit + miss > 0 时的命中率整数百分比（四舍五入）；服务商没报 → null（绝不当 0%）。 */
    fun cacheRate(hit: Long, miss: Long): Int? =
        if (hit + miss <= 0L) null else (hit * 100.0 / (hit + miss)).roundToInt()

    /** 本地日期同一天 → TODAY；前一天 → YESTERDAY；其余 EARLIER。 */
    fun dayKind(millis: Long, nowMillis: Long, zone: ZoneId): LogDayKind {
        val day = localDate(millis, zone)
        val today = localDate(nowMillis, zone)
        return when (day) {
            today -> LogDayKind.TODAY
            today.minusDays(1) -> LogDayKind.YESTERDAY
            else -> LogDayKind.EARLIER
        }
    }

    fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** 时刻 `HH:mm:ss`（失败详情）。 */
    fun timeWithSeconds(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(TIME_SECONDS)

    /** 条目页完整时刻 `yyyy-MM-dd HH:mm:ss`。 */
    fun fullTime(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(FULL)

    /** 服务商显示名。 */
    fun providerName(raw: String): String = ApiProviderType.fromRaw(raw).displayName

    private fun oneDecimal(v: Double): String = String.format(Locale.ROOT, "%.1f", v).removeSuffix(".0")

    private val TIME_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
}
