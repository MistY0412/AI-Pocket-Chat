package com.situ.aichat.ui.moments

import com.situ.aichat.ui.offline.SkyBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * T1-1（朋友圈发布页·乙 §7·E17）：发布页「此刻」模型——时段七档边界、中文月日、星期与英文月日、月相、星种子。
 * 期望从图纸 §3.4.2 独立反推；2026-09-27 实为周日（图纸 E17 例句写「周六」是笔误·§11 D-2）。
 */
class ComposeSkyModelTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val zh = Locale.SIMPLIFIED_CHINESE

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, shanghai).toInstant().toEpochMilli()

    @Test fun 时段七档边界() {
        val expected = mapOf(
            4 to ComposeDayPart.LATE_NIGHT,
            5 to ComposeDayPart.EARLY_MORNING, 7 to ComposeDayPart.EARLY_MORNING,
            8 to ComposeDayPart.MORNING, 11 to ComposeDayPart.MORNING,
            12 to ComposeDayPart.NOON, 13 to ComposeDayPart.NOON,
            14 to ComposeDayPart.AFTERNOON, 15 to ComposeDayPart.AFTERNOON,
            16 to ComposeDayPart.DUSK, 18 to ComposeDayPart.DUSK,
            19 to ComposeDayPart.EVENING, 22 to ComposeDayPart.EVENING,
            23 to ComposeDayPart.LATE_NIGHT, 0 to ComposeDayPart.LATE_NIGHT,
        )
        for ((hour, part) in expected) assertEquals("hour=$hour", part, composeDayPartForHour(hour))
    }

    @Test fun 中文月日() {
        assertEquals("九月二十七", chineseMonthDay(9, 27))
        assertEquals("一月一", chineseMonthDay(1, 1))
        assertEquals("十月十", chineseMonthDay(10, 10))
        assertEquals("十一月二十", chineseMonthDay(11, 20))
        assertEquals("十二月三十一", chineseMonthDay(12, 31))
        assertEquals("二月二十九", chineseMonthDay(2, 29))
        assertEquals("三月十九", chineseMonthDay(3, 19))
        assertEquals("四月三十", chineseMonthDay(4, 30))
    }

    @Test fun 傍晚一刻_中文() {
        val m = composeSkyMoment(at(2026, 9, 27, 17, 42), shanghai, zh)
        assertEquals("17:42", m.time)
        assertEquals("九月二十七", m.monthDay)
        assertEquals("周日", m.weekday)
        assertEquals(ComposeDayPart.DUSK, m.dayPart)
        assertEquals(SkyBucket.DUSK, m.bucket)
        assertTrue(!m.lightSky)
        assertNull("傍晚不画月", m.moon)
    }

    @Test fun 英文星期与月日() {
        val m = composeSkyMoment(at(2026, 9, 27, 17, 42), shanghai, Locale.ENGLISH)
        assertEquals("Sun", m.weekday)
        assertEquals("Sep 27", m.monthDay)
        val sat = composeSkyMoment(at(2026, 9, 26, 10, 5), shanghai, Locale.ENGLISH)
        assertEquals("Sat", sat.weekday)
        assertEquals("10:05", sat.time)
        assertEquals("周六", composeSkyMoment(at(2026, 9, 26, 10, 5), shanghai, zh).weekday)
    }

    @Test fun 月相_满月夜有月_白天无月_新月无月() {
        val night = composeSkyMoment(at(2026, 9, 27, 21, 15), shanghai, zh)
        val moon = night.moon
        assertNotNull(moon)
        assertTrue("照亮率 ${moon!!.illumination}", moon.illumination > 0.95f)
        assertNull("同日白天没有月亮", composeSkyMoment(at(2026, 9, 27, 12, 0), shanghai, zh).moon)
        assertNull("新月夜不画月", composeSkyMoment(at(2024, 1, 11, 21, 0), shanghai, zh).moon)
    }

    @Test fun 白天是浅天() {
        val m = composeSkyMoment(at(2026, 9, 27, 9, 0), shanghai, zh)
        assertEquals(SkyBucket.DAY, m.bucket)
        assertTrue(m.lightSky)
    }

    @Test fun 星种子_同一天相等_换天不等() {
        val a = composeSkyMoment(at(2026, 9, 27, 0, 30), shanghai, zh).starSeed
        val b = composeSkyMoment(at(2026, 9, 27, 23, 50), shanghai, zh).starSeed
        val c = composeSkyMoment(at(2026, 9, 28, 0, 30), shanghai, zh).starSeed
        assertEquals(a, b)
        assertEquals(a + 1, c)
    }

    @Test fun 星星数() {
        assertEquals(2, composeStarCount(SkyBucket.DAWN))
        assertEquals(0, composeStarCount(SkyBucket.DAY))
        assertEquals(0, composeStarCount(SkyBucket.DUSK))
        assertEquals(10, composeStarCount(SkyBucket.NIGHT))
        assertEquals(14, composeStarCount(SkyBucket.LATE_NIGHT))
    }
}
