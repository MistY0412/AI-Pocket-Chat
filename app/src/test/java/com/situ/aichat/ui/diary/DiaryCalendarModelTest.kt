package com.situ.aichat.ui.diary

import com.situ.aichat.data.local.entity.DiaryEntryEntity
import com.situ.aichat.data.local.entity.DiaryEntryWithComments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * T1（琉璃 2.0 卷六·一 §3.2）：心情日历的分组 / 代表心情 / 周格 / 本月心情计数。期望值从规格独立反推：
 * 按东八区本地日分组；代表心情 = 该日最新（列表在前）一条带心情的**用户**日记（信不抢、空串跳过）；
 * 周日起的月历格前后补空；本月计数只数本月、按次数降序。
 */
class DiaryCalendarModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun ewc(uuid: String, ts: Long, mood: String? = null, author: String? = null) = DiaryEntryWithComments(
        entry = DiaryEntryEntity(uuid = uuid, timestamp = ts, moodEmoji = mood, authorCharacterUuid = author),
        comments = emptyList(),
    )

    @Test fun `23_30 and next day 00_30 land on two local days`() {
        val data = diaryCalendarData(
            listOf(ewc("late", at(2026, 9, 10, 23, 30)), ewc("early", at(2026, 9, 11, 0, 30))),
            zone,
        )
        assertEquals(listOf("late"), data.entriesByDay[LocalDate.of(2026, 9, 10)]!!.map { it.entry.uuid })
        assertEquals(listOf("early"), data.entriesByDay[LocalDate.of(2026, 9, 11)]!!.map { it.entry.uuid })
    }

    @Test fun `representative mood is the newest user diary, letters and empty moods skipped`() {
        // 列表按时间降序（与 VM 同口径）：最新的是一封带心情的信 → 不抢；其次一条空串心情 → 跳过；再次才是用户 😌。
        val day = listOf(
            ewc("letter", at(2026, 9, 12, 22, 0), mood = "🥰", author = "char-1"),
            ewc("blank", at(2026, 9, 12, 21, 0), mood = ""),
            ewc("mine", at(2026, 9, 12, 20, 0), mood = "😌"),
            ewc("older", at(2026, 9, 12, 8, 0), mood = "😢"),
        )
        val data = diaryCalendarData(day, zone)
        assertEquals("😌", data.moodByDay[LocalDate.of(2026, 9, 12)])
    }

    @Test fun `a day with only a letter has no representative mood`() {
        val data = diaryCalendarData(listOf(ewc("letter", at(2026, 9, 13, 9, 0), mood = "🥰", author = "char-1")), zone)
        assertNull(data.moodByDay[LocalDate.of(2026, 9, 13)])
    }

    @Test fun `September 2026 weeks start on Sunday with two leading and three trailing blanks`() {
        val weeks = diaryCalendarWeeks(YearMonth.of(2026, 9))
        assertEquals(5, weeks.size)
        val first = listOf(null, null) + (1..5).map { LocalDate.of(2026, 9, it) }
        assertEquals(first, weeks.first())
        val last = (27..30).map { LocalDate.of(2026, 9, it) } + listOf(null, null, null)
        assertEquals(last, weeks.last())
        weeks.forEach { assertEquals(7, it.size) }
    }

    @Test fun `February 2026 fills exactly four weeks with no blanks`() {
        val weeks = diaryCalendarWeeks(YearMonth.of(2026, 2))
        assertEquals(4, weeks.size)
        assertEquals(28, weeks.flatten().count { it != null })
        assertEquals(LocalDate.of(2026, 2, 1), weeks.first().first())
        assertEquals(LocalDate.of(2026, 2, 28), weeks.last().last())
    }

    @Test fun `month mood counts only count this month, descending`() {
        val moodByDay = mapOf(
            LocalDate.of(2026, 9, 1) to "😌",
            LocalDate.of(2026, 9, 2) to "🎉",
            LocalDate.of(2026, 9, 3) to "😌",
            LocalDate.of(2026, 9, 4) to null,
            LocalDate.of(2026, 8, 31) to "🎉",
            LocalDate.of(2026, 10, 1) to "🎉",
        )
        val counts = diaryMonthMoodCounts(moodByDay, YearMonth.of(2026, 9)).map { it.key to it.value }
        assertEquals(listOf("😌" to 2, "🎉" to 1), counts)
    }
}
