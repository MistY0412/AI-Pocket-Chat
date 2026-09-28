package com.situ.aichat.prompt.timesense

import com.situ.aichat.prompt.TimeAnchorFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * T1-5（时间感知四期·图纸一 §3.5 ③④ / §4 M3–M7b·E19 / E20 / E22）：TimeSenseLines 行文 + 现在卡装配。
 * 锁定文本在测试里重新打字为字面量（不引实现常量，除双保险 pin）。时区钉 Asia/Shanghai。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeSenseLinesTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private val pause = "几分钟到半小时的停顿是正常聊天节奏，一般不用特意提。"
    private val calendarOnce = "节日、生日这类日子自然提一次就够，别每条都提。"
    private val persistent = "前面聊到的长期、持续的事，本来就会延续——具体哪些还算数、此刻你是什么状态，你按现在的时间自己判断。"

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    private fun days(today: LocalDate, userBirthday: Long? = null) =
        SpecialDays.Inputs(today, zone, "小明", userBirthday, null, null)

    private fun lines(gap: Long?, rhythm: String? = null, today: LocalDate = LocalDate.of(2026, 7, 10), userBirthday: Long? = null) =
        TimeSenseLines.build(ChatTimeSense(gap, rhythm), days(today, userBirthday), at(2026, 7, 10, 21, 40), zone)

    @Test
    fun `锁定常量逐字`() {
        assertEquals(pause, TimeSenseLines.PAUSE_DAMPENER)
        assertEquals(calendarOnce, TimeSenseLines.CALENDAR_ONCE_NOTE)
    }

    @Test
    fun `M7b_持续事附言新全文_不含括号例子`() {
        assertEquals(persistent, TimeAnchorFormatter.PERSISTENT_NOTE)
        assertFalse(TimeAnchorFormatter.PERSISTENT_NOTE.contains("感冒"))
        assertFalse(TimeAnchorFormatter.PERSISTENT_NOTE.contains("外地"))
    }

    @Test
    fun `E19_间隔行_恰5分钟出_4分59秒不出_约只出现一次`() {
        assertEquals("这条消息距离上条过去了约 5 分钟", lines(300).gapLine)
        assertNull(lines(299).gapLine)
        assertNull(lines(null).gapLine)
        assertEquals("这条消息距离上条过去了约 25 分钟", lines(25 * 60).gapLine)
        assertEquals("这条消息距离上条过去了约 6 小时", lines(6 * 3600).gapLine)
        assertFalse(lines(25 * 60).gapLine!!.contains("约约"))
    }

    @Test
    fun `作息行_用块级称呼`() {
        assertEquals("小明过去一个月的消息大多在 20:00–01:00 之间，这个钟点前后还没来找过你", lines(null, "20:00–01:00").rhythmLine)
        assertNull(lines(null).rhythmLine)
    }

    @Test
    fun `日子两行拼接格式`() {
        // 2026-09-26：今天 = 中秋节假期第 2 天；用户生日 9/29 → 近几天一项。
        val b = LocalDate.of(1998, 9, 29).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(
            listOf("今天：中秋节假期第 2 天（9月25日–9月27日）", "近几天：小明的生日还有 3 天（9月29日）"),
            lines(null, today = LocalDate.of(2026, 9, 26), userBirthday = b).calendarLines,
        )
        // 多项用「；」连：2026-09-23 用户生日 9/24 + 中秋假期 9/25 开始。
        val b2 = LocalDate.of(1998, 9, 24).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(
            listOf("近几天：小明的生日还有 1 天（9月24日）；中秋节假期还有 2 天开始（9月25日–9月27日）"),
            lines(null, today = LocalDate.of(2026, 9, 23), userBirthday = b2).calendarLines,
        )
        assertEquals("普通日无日子行", emptyList<String>(), lines(null).calendarLines)
    }

    // MARK: - 现在卡装配（§3.5 ④·M3–M6）

    private fun block(anchor: String) = anchor.substringAfter("<time_context>\n").substringBefore("\n</time_context>").split("\n")

    @Test
    fun `现在卡_在线聊天整张_日子行在现在行后_间隔行取代旧行_附言按序`() {
        val now = at(2026, 9, 26, 21, 40)
        val sense = TimeSenseLines.build(ChatTimeSense(5 * 3600 - 60, null), days(LocalDate.of(2026, 9, 26)), now, zone)
        val out = TimeAnchorFormatter.buildTimeAnchor(now, now.minusSeconds(5 * 3600), sense = sense)
        assertEquals(
            "<time_context>\n现在：2026-09-26 周六 21:40（晚上）\n今天：中秋节假期第 2 天（9月25日–9月27日）\n" +
                "这条消息距离上条过去了约 5 小时\n</time_context>\n" +
                "↑ 以上是此刻的真实时间，以它为准。$pause$calendarOnce" +
                "这几个小时你在过自己的生活，现在才重新拿起手机。前面聊的还算近，接得上就自然接。$persistent",
            out,
        )
    }

    @Test
    fun `现在卡_相识行在日子行之后_作息行在间隔行之后`() {
        val now = at(2026, 9, 26, 4, 0)
        val sense = TimeSenseLines.build(ChatTimeSense(600, "20:00–01:00"), days(LocalDate.of(2026, 9, 26)), now, zone)
        val acq = TimeAnchorFormatter.AcquaintanceFacts(firstMessageDate = at(2026, 6, 1, 10, 0).toEpochMilli(), streakCount = 3)
        val out = TimeAnchorFormatter.buildTimeAnchor(now, now.minusSeconds(900), acquaintance = acq, userLabel = "小明", sense = sense)
        assertEquals(
            listOf(
                "现在：2026-09-26 周六 04:00（深夜）",
                "今天：中秋节假期第 2 天（9月25日–9月27日）",
                "你和小明是 2026-06-01 第一次聊天认识的，到今天相识 117 天。",
                "这条消息距离上条过去了约 10 分钟",
                "小明过去一个月的消息大多在 20:00–01:00 之间，这个钟点前后还没来找过你",
            ),
            block(out),
        )
    }

    @Test
    fun `E22_第一次对话_日子与作息照出_不加降温句`() {
        val now = at(2026, 9, 26, 4, 0)
        val sense = TimeSenseLines.build(ChatTimeSense(null, "20:00–01:00"), days(LocalDate.of(2026, 9, 26)), now, zone)
        val out = TimeAnchorFormatter.buildTimeAnchor(now, null, userLabel = "小明", sense = sense)
        assertEquals(
            "<time_context>\n现在：2026-09-26 周六 04:00（深夜）\n今天：中秋节假期第 2 天（9月25日–9月27日）\n这是你们的第一次对话\n" +
                "小明过去一个月的消息大多在 20:00–01:00 之间，这个钟点前后还没来找过你\n</time_context>\n" +
                "↑ 以上是此刻的真实时间，以它为准。$calendarOnce",
            out,
        )
        assertFalse(out.contains(pause))
    }

    @Test
    fun `无日子时不加日子附言_短间隔无五档`() {
        val now = at(2026, 7, 10, 21, 40)
        val out = TimeAnchorFormatter.buildTimeAnchor(now, now.minusSeconds(420), sense = lines(420))
        assertEquals(
            "<time_context>\n现在：2026-07-10 周五 21:40（晚上）\n这条消息距离上条过去了约 7 分钟\n</time_context>\n" +
                "↑ 以上是此刻的真实时间，以它为准。$pause",
            out,
        )
    }

    @Test
    fun `E20_任何情况都不出这次从_聊到现在`() {
        val now = at(2026, 9, 26, 21, 40)
        val outs = listOf(
            TimeAnchorFormatter.buildTimeAnchor(now, now.minusSeconds(60), sense = lines(60)),
            TimeAnchorFormatter.buildTimeAnchor(now, now.minusSeconds(3 * 86400), sense = lines(3 * 86400, "20:00–01:00")),
            TimeAnchorFormatter.buildTimeAnchor(now, null, sense = lines(null)),
        )
        for (o in outs) {
            assertFalse(o, o.contains("这次从"))
            assertFalse(o, o.contains("这一段从"))
            assertFalse(o, o.contains("聊到现在"))
            assertFalse("在线新路不再出旧方向化间隔行：$o", o.contains("隔了约"))
        }
        assertTrue(outs[1].contains("这条消息距离上条过去了约 3 天"))
    }
}
