package com.situ.aichat.ui.character

import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * T1-1（琉璃 2.0 卷五 §7）：编辑页生日规则共用件。断言按规格独立反推：只许选今天及以前 / 今年及以前；
 * 默认生日 = UTC 2000-01-01 零点（M3 DatePicker 按 UTC 读·卷五复核 R1）；年龄 = 周岁（今年生日没过就少一岁）。
 */
@OptIn(ExperimentalMaterial3Api::class)
class CharacterEditSupportTest {

    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `过去或今天可选 - 明天不可选`() {
        val now = System.currentTimeMillis()
        assertTrue(PastOrPresentDates.isSelectableDate(now - 60_000))
        assertTrue(PastOrPresentDates.isSelectableDate(now - 365 * dayMs))
        assertFalse(PastOrPresentDates.isSelectableDate(now + dayMs))
    }

    @Test
    fun `今年可选 - 明年不可选`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        assertTrue(PastOrPresentDates.isSelectableYear(year))
        assertTrue(PastOrPresentDates.isSelectableYear(1900))
        assertFalse(PastOrPresentDates.isSelectableYear(year + 1))
    }

    @Test
    fun `默认生日是 UTC 2000-01-01 零点（DatePicker 按 UTC 读）`() {
        // 卷五复核 R1：原为本地零点，东八区对话框落在 1999-12-31。946684800000 = 2000-01-01T00:00:00Z。
        assertEquals(946_684_800_000L, defaultBirthdayMillis())
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = defaultBirthdayMillis() }
        assertEquals(2000, c.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH))
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun `周岁 - 生日已过满 30 未过 29`() {
        val passed = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1); add(Calendar.YEAR, -30) }
        val upcoming = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); add(Calendar.YEAR, -30) }
        assertEquals(30, yearsSince(passed.timeInMillis))
        assertEquals(29, yearsSince(upcoming.timeInMillis))
    }
}
