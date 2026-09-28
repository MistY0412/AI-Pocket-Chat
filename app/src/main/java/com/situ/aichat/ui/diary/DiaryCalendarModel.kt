package com.situ.aichat.ui.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.situ.aichat.data.local.entity.DiaryEntryWithComments
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 心情日历的可变量（琉璃 2.0 卷六·一：自 [DiaryCalendarSection] 只搬·两张脸共用）：今天 / 显示哪个月 / 选中哪天。 */
@Stable
internal class DiaryCalendarState(val today: LocalDate, initialMonth: YearMonth) {
    var displayedMonth by mutableStateOf(initialMonth)
    var selectedDate by mutableStateOf(today)
}

/** 同暖陶三个 `remember`（进页那一刻定今天与本月·不跨重建保存）。 */
@Composable
internal fun rememberDiaryCalendarState(zone: ZoneId): DiaryCalendarState =
    remember { DiaryCalendarState(LocalDate.now(zone), YearMonth.now(zone)) }

/** 按本地日分组 + 每日代表心情（纯函数·T1）。 */
internal class DiaryCalendarData(
    val entriesByDay: Map<LocalDate, List<DiaryEntryWithComments>>,
    val moodByDay: Map<LocalDate, String?>,
)

internal fun diaryCalendarData(entries: List<DiaryEntryWithComments>, zone: ZoneId): DiaryCalendarData {
    val entriesByDay = entries.groupBy { Instant.ofEpochMilli(it.entry.timestamp).atZone(zone).toLocalDate() }
    // 每日代表心情 = 该日最新一条带心情的**用户**日记（entries 按时间降序；R4：TA 的信不抢格子染色）。
    val moodByDay = entriesByDay.mapValues { (_, list) ->
        list.firstNotNullOfOrNull { ewc ->
            ewc.entry.takeIf { it.authorCharacterUuid == null }?.moodEmoji?.takeIf(String::isNotEmpty)
        }
    }
    return DiaryCalendarData(entriesByDay, moodByDay)
}

/** 周日起的月历格，前导 / 尾随空白补 null（纯函数·T1·原 :94–103 逐字）。 */
internal fun diaryCalendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7 // 周日=0、周一=1…
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
        while (size % 7 != 0) add(null)
    }
    return cells.chunked(7)
}

/** 本月心情分布（emoji 计数·降序·纯函数·T1·原 :108–114 逐字）。 */
internal fun diaryMonthMoodCounts(moodByDay: Map<LocalDate, String?>, month: YearMonth): List<Map.Entry<String, Int>> =
    moodByDay.entries
        .filter { YearMonth.from(it.key) == month && it.value != null }
        .groupingBy { it.value!! }
        .eachCount()
        .entries.sortedByDescending { it.value }
