package com.situ.aichat.prompt.timesense

import com.situ.aichat.gift.FestivalCalendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * T1-7（时间感知四期·图纸一 §3.5 ②·E26–E29 + 七项必测）：SpecialDays。农历走 `android.icu.util.ChineseCalendar` → Robolectric；
 * 设备时区钉 Asia/Shanghai（节日判日按设备时区）。期望按 §3.5 规则 + 2026 官方假期表手算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialDaysTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(shanghai))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    /** DatePicker 口径：生日 = 当天 UTC 零点。 */
    private fun utcBirthday(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localAt10(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(10, 0).atZone(shanghai).toInstant().toEpochMilli()

    private fun inputs(
        today: LocalDate,
        userBirthday: Long? = null,
        charBirthday: Long? = null,
        first: Long? = null,
        zone: ZoneId = shanghai,
    ) = SpecialDays.Inputs(today, zone, "小明", userBirthday, charBirthday, first)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    /** 2026 年七夕：不手写日期，用节日表自己找（图纸 T1-7 要求）。 */
    private fun qixi2026(): LocalDate {
        val qixi = FestivalCalendar.festivalById("qixi")!!
        return generateSequence(d(2026, 7, 1)) { it.plusDays(1) }.takeWhile { !it.isAfter(d(2026, 9, 30)) }
            .first { qixi.matches(it.atStartOfDay(shanghai).plusHours(12).toInstant().toEpochMilli()) }
    }

    @Test
    fun `中秋假期第2天_今天项带区间_近几天空_主动无名`() {
        val i = inputs(d(2026, 9, 26))
        assertEquals(listOf("中秋节假期第 2 天（9月25日–9月27日）"), SpecialDays.chatTodayItems(i))
        assertEquals(emptyList<String>(), SpecialDays.chatNearItems(i))
        assertEquals("法定假本身不进主动由头", emptyList<String>(), SpecialDays.proactiveNames(i))
    }

    @Test
    fun `E28_中秋节当天_只出假期一项_主动由头不去重`() {
        val i = inputs(d(2026, 9, 25))
        assertEquals(listOf("中秋节假期第 1 天（9月25日–9月27日）"), SpecialDays.chatTodayItems(i))
        assertEquals(listOf("中秋"), SpecialDays.proactiveNames(i))
    }

    @Test
    fun `假期前两天_近几天说假期开始_不单列中秋还有`() {
        val near = SpecialDays.chatNearItems(inputs(d(2026, 9, 23)))
        assertEquals(listOf("中秋节假期还有 2 天开始（9月25日–9月27日）"), near)
        assertFalse(near.any { it.startsWith("中秋还有") })
        // 国庆同理：10/1 节日「国庆」⊂ 假期「国庆节」且假期 3 天后开始 → 只说假期。
        assertEquals(listOf("国庆节假期还有 3 天开始（10月1日–10月7日）"), SpecialDays.chatNearItems(inputs(d(2026, 9, 28))))
    }

    @Test
    fun `调休补班_今天与明天两种说法`() {
        assertEquals(listOf("调休补班日（按工作日上班上学）"), SpecialDays.chatTodayItems(inputs(d(2026, 9, 20))))
        assertEquals(listOf("明天是调休补班日"), SpecialDays.chatNearItems(inputs(d(2026, 9, 19))))
    }

    @Test
    fun `七夕当天_今天项与主动由头`() {
        val day = qixi2026()
        assertTrue(SpecialDays.chatTodayItems(inputs(day)).contains("七夕"))
        assertEquals(listOf("七夕"), SpecialDays.proactiveNames(inputs(day)))
        val twoBefore = SpecialDays.chatNearItems(inputs(day.minusDays(2)))
        assertTrue(twoBefore.toString(), twoBefore.contains("七夕还有 2 天（${day.monthValue}月${day.dayOfMonth}日）"))
    }

    @Test
    fun `用户生日_还有3天_刚过去2天_7天边界`() {
        val b = utcBirthday(1998, 9, 29)
        assertEquals(listOf("小明的生日还有 3 天（9月29日）"), SpecialDays.chatNearItems(inputs(d(2026, 9, 26), userBirthday = b)))
        assertTrue(SpecialDays.chatNearItems(inputs(d(2026, 10, 1), userBirthday = b)).contains("小明的生日刚过去 2 天（9月29日）"))
        assertTrue(SpecialDays.chatNearItems(inputs(d(2026, 9, 22), userBirthday = b)).contains("小明的生日还有 7 天（9月29日）"))
        assertFalse(SpecialDays.chatNearItems(inputs(d(2026, 9, 21), userBirthday = b)).any { it.startsWith("小明的生日") })
        assertFalse("刚过 4 天不出", SpecialDays.chatNearItems(inputs(d(2026, 10, 3), userBirthday = b)).any { it.startsWith("小明的生日") })
        assertTrue(SpecialDays.chatTodayItems(inputs(d(2026, 9, 29), userBirthday = b)).contains("小明的生日"))
        assertTrue("角色生日主语=你", SpecialDays.chatNearItems(inputs(d(2026, 9, 26), charBirthday = b)).contains("你的生日还有 3 天（9月29日）"))
    }

    @Test
    fun `E26_2月29日生日_平年按2月28日`() {
        val b = utcBirthday(2000, 2, 29)
        assertEquals(listOf("小明的生日", "调休补班日（按工作日上班上学）"), SpecialDays.chatTodayItems(inputs(d(2026, 2, 28), userBirthday = b)))
        assertEquals(listOf("小明的生日还有 3 天（2月28日）"), SpecialDays.chatNearItems(inputs(d(2026, 2, 25), userBirthday = b)))
    }

    @Test
    fun `E27_生日按UTC取月日_不随所在时区差一天`() {
        // UTC 零点在纽约是前一天晚上 8 点——若按本地时区读会差成 9/28。
        val b = utcBirthday(1998, 9, 29)
        val ny = ZoneId.of("America/New_York")
        assertTrue(SpecialDays.chatTodayItems(inputs(d(2026, 9, 29), userBirthday = b, zone = ny)).contains("小明的生日"))
        assertFalse(SpecialDays.chatTodayItems(inputs(d(2026, 9, 28), userBirthday = b, zone = ny)).contains("小明的生日"))
    }

    @Test
    fun `E29_2027年假期表外_只出节日不出假期`() {
        assertEquals(listOf("情人节"), SpecialDays.chatTodayItems(inputs(d(2027, 2, 14))))
        assertEquals("无法定假 → 不去重", listOf("国庆"), SpecialDays.chatTodayItems(inputs(d(2027, 10, 1))))
    }

    @Test
    fun `相识1周年与整200天`() {
        assertEquals(
            listOf("你们相识 1 周年", "中秋节假期第 2 天（9月25日–9月27日）"),
            SpecialDays.chatTodayItems(inputs(d(2026, 9, 26), first = localAt10(2025, 9, 26))),
        )
        assertEquals(listOf("你们相识整 200 天"), SpecialDays.chatTodayItems(inputs(d(2026, 7, 10), first = localAt10(2025, 12, 22))))
        assertEquals("199 天不出", emptyList<String>(), SpecialDays.chatTodayItems(inputs(d(2026, 7, 9), first = localAt10(2025, 12, 22))))
    }

    @Test
    fun `主动由头_顺序与上限2`() {
        val day = qixi2026()
        val b = utcBirthday(1998, day.monthValue, day.dayOfMonth)
        val first = localAt10(2025, day.monthValue, day.dayOfMonth)
        assertEquals(listOf("小明的生日", "你们相识 1 周年"), SpecialDays.proactiveNames(inputs(day, b, b, first)))
        assertEquals(listOf("你们相识 1 周年", "你的生日"), SpecialDays.proactiveNames(inputs(day, charBirthday = b, first = first)))
        assertEquals(listOf("你的生日", "七夕"), SpecialDays.proactiveNames(inputs(day, charBirthday = b)))
        val twoHundred = inputs(d(2026, 7, 10), first = localAt10(2025, 12, 22))
        assertEquals(listOf("你们相识整 200 天"), SpecialDays.proactiveNames(twoHundred))
    }

    @Test
    fun `今天项上限4_近几天项上限3`() {
        val b = utcBirthday(1998, 2, 14)
        // 2026-02-14：情人节 + 调休补班；再叠双方生日与相识周年 → 5 项取前 4（补班被挤掉）。
        assertEquals(
            listOf("小明的生日", "你的生日", "你们相识 1 周年", "情人节"),
            SpecialDays.chatTodayItems(inputs(d(2026, 2, 14), b, b, localAt10(2025, 2, 14))),
        )
        // 2026-09-23：用户生日 9/24、角色生日 9/25、相识周年 9/26 + 中秋假期开始 → 取前 3。
        assertEquals(
            listOf("小明的生日还有 1 天（9月24日）", "你的生日还有 2 天（9月25日）", "相识 1 周年还有 3 天（9月26日）"),
            SpecialDays.chatNearItems(inputs(d(2026, 9, 23), utcBirthday(1998, 9, 24), utcBirthday(1998, 9, 25), localAt10(2025, 9, 26))),
        )
    }

    @Test
    fun `D2_跨年的相识周年按那次周年的届数`() {
        // 图纸 §11 D-2（留复核裁决）：12/30 时 1/1 的周年是第 2 届（2025-01-01 相识）。
        val near = SpecialDays.chatNearItems(inputs(d(2026, 12, 30), first = localAt10(2025, 1, 1)))
        assertEquals("相识 2 周年还有 2 天（1月1日）", near.first())
    }

    @Test
    fun `E39_什么都没有的普通日_全空不抛`() {
        val i = inputs(d(2026, 7, 10))
        assertEquals(emptyList<String>(), SpecialDays.chatTodayItems(i))
        assertEquals(emptyList<String>(), SpecialDays.chatNearItems(i))
        assertEquals(emptyList<String>(), SpecialDays.proactiveNames(i))
    }
}
