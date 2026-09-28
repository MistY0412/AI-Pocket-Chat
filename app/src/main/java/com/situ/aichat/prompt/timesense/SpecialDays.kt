package com.situ.aichat.prompt.timesense

import com.situ.aichat.gift.FestivalCalendar
import com.situ.aichat.prompt.schedule.ChineseHolidays
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * 日历上的日子（时间感知四期·图纸一 §3.5 ② 锁定·纯函数·**单源**）：双方生日 / 相识周年 / 相识整百天 / 节日 /
 * 法定假与调休补班。现在卡的「今天：」「近几天：」两行（[chatTodayItems] / [chatNearItems]）与主动消息的特别日子由头
 * （[proactiveNames]）都从这里取，不许别处再算一遍。
 *
 * 数据口径：生日 = DatePicker 的 **UTC 零点**毫秒 → 一律按 [ZoneOffset.UTC] 取月日（与 `formatBirthdayForPrompt` 同口径，
 * 防东八区零点前后差一天）；其余本地日期用 [Inputs.zone]；节日判日传当天**正午**毫秒（`FestivalCalendar` 按设备时区判日）。
 * 节日表 / 法定假表零改（2027 假期表外 → 只出节日，不出假期）。
 */
object SpecialDays {

    data class Inputs(
        val today: LocalDate,
        val zone: ZoneId,
        val userLabel: String,
        val userBirthdayUtcMillis: Long?,
        val characterBirthdayUtcMillis: Long?,
        val firstMessageDateMillis: Long?,
    )

    private const val TODAY_LIMIT = 4
    private const val NEAR_LIMIT = 3
    private const val PROACTIVE_LIMIT = 2
    private const val BIRTHDAY_AHEAD_DAYS = 7L
    private const val BIRTHDAY_PASSED_DAYS = 3L
    private const val NEAR_DAYS = 3L
    private const val HOLIDAY_SCAN_STEPS = 10

    fun chatTodayItems(i: Inputs): List<String> {
        val items = mutableListOf<String>()
        if (birthdayToday(i.userBirthdayUtcMillis, i.today)) items += "${i.userLabel}的生日"
        if (birthdayToday(i.characterBirthdayUtcMillis, i.today)) items += "你的生日"
        val acq = acquaintance(i)
        if (acq?.anniversaryToday == true) items += "你们相识 ${acq.years} 周年"
        if (acq != null && acq.hundredDaysToday) items += "你们相识整 ${acq.days} 天"
        items += festivalNames(i.today, i.zone, dedupeAgainstHoliday = true)
        when (val info = ChineseHolidays.dayInfoFor(i.today)) {
            is ChineseHolidays.DayInfo.Holiday -> {
                val (start, end) = holidayRange(i.today, info.name)
                items += "${info.name}假期第 ${ChronoUnit.DAYS.between(start, i.today) + 1} 天（${rangeText(start, end)}）"
            }
            ChineseHolidays.DayInfo.MakeupWorkday -> items += "调休补班日（按工作日上班上学）"
            null -> Unit
        }
        return items.take(TODAY_LIMIT)
    }

    fun chatNearItems(i: Inputs): List<String> {
        val items = mutableListOf<String>()
        birthdayNear(i.userBirthdayUtcMillis, i.today, "${i.userLabel}的生日")?.let { items += it }
        birthdayNear(i.characterBirthdayUtcMillis, i.today, "你的生日")?.let { items += it }
        acquaintance(i)?.let { acq ->
            val next = nextLanding(MonthDay.from(acq.first), i.today)
            val k = ChronoUnit.DAYS.between(i.today, next)
            // 近几天行按「那次周年」的届数（next.year − first.year）算：跨年时字面 today.year − first.year 会少一届（图纸一 §11 D-2·复核 R1 核准）。
            val years = next.year - acq.first.year
            if (k in 1..NEAR_DAYS && years >= 1) items += "相识 $years 周年还有 $k 天（${dateText(next)}）"
        }
        // 未来 1..3 天的节日：每个节日只取最早一次；那天属于名字包含它、且在 1..3 天内开始的法定假 → 由假期项说。
        val seen = mutableSetOf<String>()
        for (k in 1..NEAR_DAYS) {
            val date = i.today.plusDays(k)
            for (name in festivalNames(date, i.zone, dedupeAgainstHoliday = false)) {
                if (!seen.add(name)) continue
                val holiday = ChineseHolidays.dayInfoFor(date) as? ChineseHolidays.DayInfo.Holiday
                val coveredByHoliday = holiday != null && holiday.name.contains(name) &&
                    ChronoUnit.DAYS.between(i.today, holidayRange(date, holiday.name).first) in 1..NEAR_DAYS
                if (!coveredByHoliday) items += "${name}还有 $k 天（${dateText(date)}）"
            }
        }
        for (k in 1..NEAR_DAYS) {
            val date = i.today.plusDays(k)
            val holiday = ChineseHolidays.dayInfoFor(date) as? ChineseHolidays.DayInfo.Holiday ?: continue
            if (ChineseHolidays.dayInfoFor(date.minusDays(1)) == holiday) continue // 不是首日
            val (start, end) = holidayRange(date, holiday.name)
            items += "${holiday.name}假期还有 $k 天开始（${rangeText(start, end)}）"
        }
        if (ChineseHolidays.dayInfoFor(i.today.plusDays(1)) == ChineseHolidays.DayInfo.MakeupWorkday) items += "明天是调休补班日"
        return items.take(NEAR_LIMIT)
    }

    fun proactiveNames(i: Inputs): List<String> {
        val names = mutableListOf<String>()
        val acq = acquaintance(i)
        if (birthdayToday(i.userBirthdayUtcMillis, i.today)) names += "${i.userLabel}的生日"
        if (acq?.anniversaryToday == true) names += "你们相识 ${acq.years} 周年"
        if (birthdayToday(i.characterBirthdayUtcMillis, i.today)) names += "你的生日"
        if (acq != null && acq.hundredDaysToday) names += "你们相识整 ${acq.days} 天"
        names += festivalNames(i.today, i.zone, dedupeAgainstHoliday = false)
        return names.take(PROACTIVE_LIMIT)
    }

    // MARK: - 生日（UTC 取月日）

    private fun birthdayMonthDay(utcMillis: Long): MonthDay =
        MonthDay.from(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate())

    private fun birthdayToday(utcMillis: Long?, today: LocalDate): Boolean =
        utcMillis != null && birthdayMonthDay(utcMillis).atYear(today.year) == today

    private fun birthdayNear(utcMillis: Long?, today: LocalDate, subject: String): String? {
        if (utcMillis == null) return null
        val md = birthdayMonthDay(utcMillis)
        val next = nextLanding(md, today)
        val ahead = ChronoUnit.DAYS.between(today, next)
        if (ahead in 1..BIRTHDAY_AHEAD_DAYS) return "${subject}还有 $ahead 天（${dateText(next)}）"
        val prev = md.atYear(today.year).let { if (it.isAfter(today)) md.atYear(today.year - 1) else it }
        val passed = ChronoUnit.DAYS.between(prev, today)
        if (passed in 1..BIRTHDAY_PASSED_DAYS) return "${subject}刚过去 $passed 天（${dateText(prev)}）"
        return null
    }

    /** 某月日的下一次落点：今年落点，早于今天则明年（2/29 在平年由 [MonthDay.atYear] 落到 2/28）。 */
    private fun nextLanding(md: MonthDay, today: LocalDate): LocalDate =
        md.atYear(today.year).let { if (it.isBefore(today)) md.atYear(today.year + 1) else it }

    // MARK: - 相识

    private class Acquaintance(val first: LocalDate, val days: Long, val years: Int, val anniversaryToday: Boolean) {
        val hundredDaysToday: Boolean get() = days >= 100 && days % 100 == 0L && !anniversaryToday
    }

    private fun acquaintance(i: Inputs): Acquaintance? {
        val millis = i.firstMessageDateMillis ?: return null
        val first = Instant.ofEpochMilli(millis).atZone(i.zone).toLocalDate()
        val years = i.today.year - first.year
        val anniversary = MonthDay.from(first).atYear(i.today.year) == i.today && years >= 1
        return Acquaintance(first, ChronoUnit.DAYS.between(first, i.today), years, anniversary)
    }

    // MARK: - 节日 / 法定假

    /** 该日命中的节日名；[dedupeAgainstHoliday] = 名字被当天法定假名包含（「中秋」⊂「中秋节」）的不单列。 */
    private fun festivalNames(date: LocalDate, zone: ZoneId, dedupeAgainstHoliday: Boolean): List<String> {
        val names = FestivalCalendar.festivalsMatching(date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()).map { it.name }
        if (!dedupeAgainstHoliday) return names
        val holiday = ChineseHolidays.dayInfoFor(date) as? ChineseHolidays.DayInfo.Holiday ?: return names
        return names.filterNot { holiday.name.contains(it) }
    }

    /** 同名法定假区间：从 [date] 向前 / 向后逐日扫（各最多 [HOLIDAY_SCAN_STEPS] 步）。 */
    private fun holidayRange(date: LocalDate, name: String): Pair<LocalDate, LocalDate> {
        val same = ChineseHolidays.DayInfo.Holiday(name)
        var start = date
        var end = date
        repeat(HOLIDAY_SCAN_STEPS) { if (ChineseHolidays.dayInfoFor(start.minusDays(1)) == same) start = start.minusDays(1) }
        repeat(HOLIDAY_SCAN_STEPS) { if (ChineseHolidays.dayInfoFor(end.plusDays(1)) == same) end = end.plusDays(1) }
        return start to end
    }

    private fun dateText(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"

    private fun rangeText(start: LocalDate, end: LocalDate): String = "${dateText(start)}–${dateText(end)}"
}
