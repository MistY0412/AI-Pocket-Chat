package com.situ.aichat.ui.moments

import androidx.annotation.StringRes
import com.situ.aichat.R
import com.situ.aichat.ui.offline.MoonRender
import com.situ.aichat.ui.offline.SkyBucket
import com.situ.aichat.ui.offline.skyBucketForHour
import com.situ.aichat.util.MoonPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// 发布页「此刻」模型（朋友圈发布页·乙 §3.4.2·纯函数）：时段七档、时间 / 月日 / 星期、月相、星种子。
// 天色底色桶与见面回忆共用 [skyBucketForHour]；时段词是发布页自己的七档（与天色对齐·不是提示词那套六档）。

/** 发布页时段词七档（与天色对齐·不是提示词那套六档）。 */
internal enum class ComposeDayPart(@StringRes val label: Int) {
    EARLY_MORNING(R.string.moment_compose_daypart_early),     // 5–7
    MORNING(R.string.moment_compose_daypart_morning),          // 8–11
    NOON(R.string.moment_compose_daypart_noon),                // 12–13
    AFTERNOON(R.string.moment_compose_daypart_afternoon),      // 14–15
    DUSK(R.string.moment_compose_daypart_dusk),                // 16–18
    EVENING(R.string.moment_compose_daypart_evening),          // 19–22
    LATE_NIGHT(R.string.moment_compose_daypart_late),          // 23–4
}

internal fun composeDayPartForHour(hour: Int): ComposeDayPart = when (hour) {
    in 5..7 -> ComposeDayPart.EARLY_MORNING
    in 8..11 -> ComposeDayPart.MORNING
    in 12..13 -> ComposeDayPart.NOON
    in 14..15 -> ComposeDayPart.AFTERNOON
    in 16..18 -> ComposeDayPart.DUSK
    in 19..22 -> ComposeDayPart.EVENING
    else -> ComposeDayPart.LATE_NIGHT
}

internal data class ComposeSkyMoment(
    val bucket: SkyBucket,          // = skyBucketForHour(hour)
    val dayPart: ComposeDayPart,
    val time: String,               // "HH:mm"
    val monthDay: String,           // zh「九月二十七」/ 其他语言 DateTimeFormatter.ofPattern("MMM d", locale)
    val weekday: String,            // DayOfWeek.getDisplayName(TextStyle.SHORT, locale)（zh = 周六）
    val moon: MoonRender?,          // 只在 NIGHT / LATE_NIGHT 且 MoonPhase.illumination ≥ 0.05 时非空
    val starSeed: Int,              // = 当地日期 toEpochDay().toInt()
) { val lightSky: Boolean get() = bucket == SkyBucket.DAY }

/** 照亮率低于此不画月（朔前后·= 见面回忆 `moonRenderFor` 口径）。 */
private const val MOON_MIN_ILLUMINATION = 0.05f

internal fun composeSkyMoment(nowMillis: Long, zone: ZoneId, locale: Locale): ComposeSkyMoment {
    val t = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val bucket = skyBucketForHour(t.hour)
    val nightly = bucket == SkyBucket.NIGHT || bucket == SkyBucket.LATE_NIGHT
    val illumination = MoonPhase.illumination(nowMillis).toFloat()
    return ComposeSkyMoment(
        bucket = bucket,
        dayPart = composeDayPartForHour(t.hour),
        time = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(t),
        monthDay = if (locale.language == "zh") {
            chineseMonthDay(t.monthValue, t.dayOfMonth)
        } else {
            DateTimeFormatter.ofPattern("MMM d", locale).format(t)
        },
        weekday = t.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
        moon = if (nightly && illumination >= MOON_MIN_ILLUMINATION) MoonRender(illumination, MoonPhase.isWaxing(nowMillis)) else null,
        starSeed = t.toLocalDate().toEpochDay().toInt(),
    )
}

/** 星星数（= 见面回忆本色桶口径）：清晨 2 · 晚上 10 · 深夜 14 · 其余 0。 */
internal fun composeStarCount(bucket: SkyBucket): Int = when (bucket) {
    SkyBucket.DAWN -> 2
    SkyBucket.NIGHT -> 10
    SkyBucket.LATE_NIGHT -> 14
    SkyBucket.DAY, SkyBucket.DUSK -> 0
}

private val CN_DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

/** 1..31 的中文读法：一…十、十一…十九、二十、二十一…、三十、三十一。 */
private fun chineseNumber(n: Int): String = when {
    n < 10 -> CN_DIGITS[n]
    n == 10 -> "十"
    n < 20 -> "十" + CN_DIGITS[n - 10]
    else -> CN_DIGITS[n / 10] + "十" + CN_DIGITS[n % 10]
}

/** 中文月日：9/27 →「九月二十七」（不带「日」）。 */
internal fun chineseMonthDay(month: Int, day: Int): String = chineseNumber(month) + "月" + chineseNumber(day)
