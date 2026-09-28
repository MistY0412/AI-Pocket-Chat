package com.situ.aichat.ui.diary

import com.situ.aichat.data.local.entity.DiaryEntryEntity
import com.situ.aichat.data.local.entity.DiaryEntryWithComments
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * T1（琉璃 2.0 卷六·一 §3.1）：日记本两张脸共用的筛选与月边界纯函数。期望值从规格独立反推——
 * 「我的」= 无作者角色、「TA 的信」= 有作者角色；东八区某月一号零点 = 前一天 16:00 UTC（手算毫秒）。
 */
class DiaryListSupportTest {

    private fun ewc(uuid: String, author: String? = null) =
        DiaryEntryWithComments(entry = DiaryEntryEntity(uuid = uuid, authorCharacterUuid = author), comments = emptyList())

    private val entries = listOf(ewc("mine-1"), ewc("letter", author = "char-1"), ewc("mine-2"))

    @Test fun `ALL keeps all three`() {
        assertEquals(listOf("mine-1", "letter", "mine-2"), filterDiaryEntries(entries, DiaryEntryFilter.ALL).map { it.entry.uuid })
    }

    @Test fun `MINE keeps the two without an author character, order preserved`() {
        assertEquals(listOf("mine-1", "mine-2"), filterDiaryEntries(entries, DiaryEntryFilter.MINE).map { it.entry.uuid })
    }

    @Test fun `THEIRS keeps only the letter`() {
        assertEquals(listOf("letter"), filterDiaryEntries(entries, DiaryEntryFilter.THEIRS).map { it.entry.uuid })
    }

    @Test fun `month start is local midnight of day one`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 2026-09-01 00:00 +08:00 = 2026-08-31 16:00Z
        assertEquals(1788192000000L, diaryMonthStartMillis(YearMonth.of(2026, 9), shanghai))
        // 2026-02-01 00:00 +08:00 = 2026-01-31 16:00Z
        assertEquals(1769875200000L, diaryMonthStartMillis(YearMonth.of(2026, 2), shanghai))
    }
}
