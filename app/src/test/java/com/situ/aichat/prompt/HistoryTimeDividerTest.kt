package com.situ.aichat.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * HistoryTimeDivider 纯函数单测（Fable-5 时间感知优化 · chunk1）。
 * 规格独立反推：变化才标（四期·图纸一 §3.3：间隔 ≥5 分钟插停顿标记「· 距离上条消息过去了约 X」；不到 5 分钟但
 * 跨自然日或跨半点格插刻度）、历史第一条总给起始锚、相对 now 的今天/昨天/更早措辞、手写星期映射。zone 注入保证断言确定性。
 */
class HistoryTimeDividerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, sec: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, sec).atZone(zone).toInstant().toEpochMilli()

    private fun inst(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    // MARK: - 历史第一条起始锚

    @Test
    fun firstMessage_olderThanToday_returnsAnchor() {
        // prev=null 且首条在往日 → 给起始锚（消除跨日歧义）；措辞相对 now（昨天下午）。
        val now = inst(2026, 6, 26, 0, 17)
        assertEquals(
            "【时间 · 昨天 14:50】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 50), null, now, zone),
        )
    }

    @Test
    fun firstMessage_today_returnsNull() {
        // prev=null 但首条就在今天 → 省略起始锚（与 <time_context> 当前时间重复）。
        val now = inst(2026, 6, 26, 14, 0)
        assertNull(HistoryTimeDivider.lineFor(ms(2026, 6, 26, 9, 30), null, now, zone))
    }

    // MARK: - 间隔阈值（变化才标）

    @Test
    fun exactlyFiveMinutes_insertsPauseMarker() {
        // E14：恰 5 分钟 → 停顿标记（四期阈值；旧 30 分钟下不插）。
        val now = inst(2026, 6, 25, 15, 0)
        assertEquals(
            "【时间 · 今天 14:55 · 距离上条消息过去了约 5 分钟】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 55), ms(2026, 6, 25, 14, 50), now, zone),
        )
    }

    @Test
    fun twentyNineMinutes_insertsPauseMarker() {
        // 旧口径「29 < 30 不插」→ 四期 ≥5 分钟即停顿标记。
        val now = inst(2026, 6, 25, 15, 0)
        assertEquals(
            "【时间 · 今天 14:59 · 距离上条消息过去了约 29 分钟】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 59), ms(2026, 6, 25, 14, 30), now, zone),
        )
    }

    @Test
    fun thirtyMinutes_pauseMarkerCarriesGap() {
        val now = inst(2026, 6, 25, 15, 0)
        assertEquals(
            "【时间 · 今天 15:00 · 距离上条消息过去了约 30 分钟】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 15, 0), ms(2026, 6, 25, 14, 30), now, zone),
        )
    }

    @Test
    fun crossDay_tenMinutes_insertsPauseMarker() {
        // 23:55 → 次日 00:05 = 10 分钟 ≥5 → 按实际间隔判：停顿标记。
        val now = inst(2026, 6, 26, 1, 0)
        assertEquals(
            "【时间 · 今天 00:05 · 距离上条消息过去了约 10 分钟】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 26, 0, 5), ms(2026, 6, 25, 23, 55), now, zone),
        )
    }

    @Test
    fun crossDay_underFiveMinutes_shortTickUnchanged() {
        // E15：跨天且 <5 分钟 → 短版刻度（K6 不变）。
        val now = inst(2026, 6, 26, 1, 0)
        assertEquals(
            "【时间 · 今天 00:03】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 26, 0, 3), ms(2026, 6, 25, 23, 59), now, zone),
        )
    }

    @Test
    fun underFiveMinutes_sameHalfHourSlot_returnsNull() {
        // E14：4 分 59 秒、同一半小时格 → 不插。
        val now = inst(2026, 6, 25, 15, 0)
        assertNull(HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 4, 59), ms(2026, 6, 25, 14, 0), now, zone))
    }

    @Test
    fun underFiveMinutes_crossingHalfHour_insertsTickWithRealTime() {
        // E14：4 分钟跨过 :30 → 刻度，写那条消息的真实时间（不是 14:30）。
        val now = inst(2026, 6, 25, 15, 0)
        assertEquals(
            "【时间 · 今天 14:32】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 32), ms(2026, 6, 25, 14, 28), now, zone),
        )
        // 跨整点同理。
        assertEquals(
            "【时间 · 今天 15:01】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 15, 1), ms(2026, 6, 25, 14, 58), now, zone),
        )
    }

    @Test
    fun clockRollback_noPauseMarker_onlyTickOrDayRules() {
        // E18：本条早于上条（时钟回拨）→ 不出停顿标记；只按跨天 / 刻度判。
        val now = inst(2026, 6, 26, 1, 0)
        assertEquals(
            "【时间 · 昨天 14:20】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 20), ms(2026, 6, 25, 14, 40), now, zone),
        )
        assertNull(HistoryTimeDivider.lineFor(ms(2026, 6, 25, 14, 5), ms(2026, 6, 25, 14, 25), now, zone))
        assertEquals(
            "【时间 · 昨天 23:50】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 25, 23, 50), ms(2026, 6, 26, 0, 2), now, zone),
        )
    }

    // MARK: - 精确间隔 formatGapPrecise（四期·图纸一 §3.3 表）

    @Test
    fun formatGapPrecise_table() {
        assertEquals("约 5 分钟", TimeAnchorFormatter.formatGapPrecise(299))
        assertEquals("约 12 分钟", TimeAnchorFormatter.formatGapPrecise(12 * 60))
        assertEquals("约 1 小时", TimeAnchorFormatter.formatGapPrecise(59 * 60 + 30))
        assertEquals("约 3 小时", TimeAnchorFormatter.formatGapPrecise(3 * 3600))
        assertEquals("一年多", TimeAnchorFormatter.formatGapPrecise(400L * 86400))
        assertEquals("不足半分钟也至少 1 分钟", "约 1 分钟", TimeAnchorFormatter.formatGapPrecise(10))
    }

    // MARK: - 相对 now 措辞

    @Test
    fun label_today_yesterday_older() {
        val now = inst(2026, 6, 26, 0, 17)
        assertEquals("今天 00:15", HistoryTimeDivider.formatLabel(ms(2026, 6, 26, 0, 15), now, zone))
        assertEquals("昨天 14:56", HistoryTimeDivider.formatLabel(ms(2026, 6, 25, 14, 56), now, zone))
        // 更早：6/23 = 周二（手写映射，不随 locale）。
        assertEquals("6月23日 周二 09:30", HistoryTimeDivider.formatLabel(ms(2026, 6, 23, 9, 30), now, zone))
    }

    // MARK: - 场边界长版注记（时间感知三期 §4.2·断言从模板独立反推，不照抄实现输出）

    /** ReplyParser 同款 echo 正则（**手抄一份**：本测试要反向证明注记去适配了它，不是它去适配注记；四期放宽去掉行尾锚）。 */
    private val echoRegex = Regex("""(?m)^[ \t]*【时间 ·[^】\n]*】[ \t]*""")

    @Test
    fun regrounding_firstMessage_neverAnnotated() {
        // E1：prev=null 时没有「以上」可指 → withRegrounding 任意值都只给短版起始锚。
        val now = inst(2026, 9, 3, 2, 36)
        val anchorOn = HistoryTimeDivider.lineFor(ms(2026, 9, 1, 14, 50), null, now, zone, withRegrounding = true)
        val anchorOff = HistoryTimeDivider.lineFor(ms(2026, 9, 1, 14, 50), null, now, zone, withRegrounding = false)
        assertEquals("9月1日 周二 14:50", anchorOff?.removePrefix("【时间 · ")?.removeSuffix("】"))
        assertEquals(anchorOff, anchorOn)
        // 首条落今日仍返 null（注记开关不改这条规则）。
        assertNull(HistoryTimeDivider.lineFor(ms(2026, 9, 3, 1, 0), null, now, zone, withRegrounding = true))
    }

    @Test
    fun regrounding_sameDayTier_givesElapsedOnly_noConversionTable() {
        // §4.2 同日档模板：「——以上对话发生在{旧日期标签} 前后，已隔{时长}」；J5 同日不给换算表。
        // 03:00 → 同日 21:00 = 18 小时（MOST_OF_DAY）；formatDuration 自带「约」→ 模板里不再写「约」。
        val now = inst(2026, 9, 3, 21, 5)
        val line = HistoryTimeDivider.lineFor(ms(2026, 9, 3, 21, 0), ms(2026, 9, 3, 3, 0), now, zone, withRegrounding = true)
        assertEquals("【时间 · 今天 21:00——以上对话发生在今天 03:00 前后，已隔约 18 小时】", line)
        assertFalse("同日档不给时间词原点句", line!!.contains("都是从那天算的"))
        assertFalse("E16：场边界长版不追加停顿间隔", line.contains("距离上条消息"))
        assertFalse("时长自带「约」，模板不得再写一个", line.contains("约约"))
    }

    @Test
    fun regrounding_crossDayTier_givesOriginSentenceWithTwoDates() {
        // 图纸 §12 跨日档模板（2026-09-06 措辞修订·原点句·取代 §4.2 三词换算表）：「——以上对话发生在{旧日期标签} 前后，距今 {N} 天；
        // 那段话里的"今天""刚才""今晚""明天""等会儿"都是从那天算的，不是从现在算的："今晚"就是{旧短日期}晚，"明天"就是{旧短日期+1}」。
        // 复刻真实翻车：前天 23:10 的话，今天 02:34 又开口。
        val now = inst(2026, 9, 3, 2, 36)
        val line = HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 8, 31, 23, 10), now, zone, withRegrounding = true)
        assertEquals(
            "【时间 · 今天 02:34——以上对话发生在8月31日 周一 23:10 前后，距今 3 天；" +
                "那段话里的\"今天\"\"刚才\"\"今晚\"\"明天\"\"等会儿\"都是从那天算的，不是从现在算的：" +
                "\"今晚\"就是8月31日晚，\"明天\"就是9月1日】",
            line,
        )
        // 硬约束（§4.2 / ReplyParser echo 正则）：整串单行、中间不得出现「】」。
        assertFalse("注记不得含换行", line!!.contains("\n"))
        assertEquals("整串只在末尾出现一个「】」", line.length - 1, line.indexOf("】"))
    }

    @Test
    fun regrounding_overnightTier_conversionAnchoredOnYesterday() {
        // 跨夜档（昨天 14:00 → 今天 02:34 = 12.5h 跨 1 日）同样给原点句，N = 旧话日期到今天的自然日差 = 1。
        val now = inst(2026, 9, 3, 2, 36)
        val line = HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 9, 2, 14, 0), now, zone, withRegrounding = true)
        assertEquals(
            "【时间 · 今天 02:34——以上对话发生在昨天 14:00 前后，距今 1 天；" +
                "那段话里的\"今天\"\"刚才\"\"今晚\"\"明天\"\"等会儿\"都是从那天算的，不是从现在算的：" +
                "\"今晚\"就是9月2日晚，\"明天\"就是9月3日】",
            line,
        )
    }

    @Test
    fun regrounding_sameSceneGap_fallsBackToPauseMarker() {
        // E3：2–6 小时（FEW_HOURS）算同一场——即便被要求加注记也不出长版，退回停顿标记（四期：带间隔）。
        val now = inst(2026, 9, 3, 15, 0)
        val line = HistoryTimeDivider.lineFor(ms(2026, 9, 3, 14, 0), ms(2026, 9, 3, 11, 0), now, zone, withRegrounding = true)
        assertEquals("【时间 · 今天 14:00 · 距离上条消息过去了约 3 小时】", line)
    }

    @Test
    fun regrounding_defaultOff_bytewiseIdenticalToShortForm() {
        // B3 回归锁（四期让位）：不传 withRegrounding 时，跨日大间隔出带间隔的停顿标记（E17：更早的 ≥6h 缝）。
        val now = inst(2026, 9, 3, 2, 36)
        assertEquals(
            HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 8, 31, 23, 10), now, zone, withRegrounding = false),
            HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 8, 31, 23, 10), now, zone),
        )
        assertEquals(
            "【时间 · 今天 02:34 · 距离上条消息过去了约 2 天】",
            HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 8, 31, 23, 10), now, zone),
        )
    }

    @Test
    fun regrounding_longFormStillStrippableByReplyParserEchoRegex() {
        // E8·REDLINES 三处安全设施之一：长版注记必须仍被 ReplyParser 的 echo 正则**整行**命中，
        // 否则模型模仿吐回来就直漏进气泡入库。同时钉死两条格式硬约束：不含 `】`、不含换行。
        val now = inst(2026, 9, 3, 2, 36)
        val longForm = HistoryTimeDivider.lineFor(ms(2026, 9, 3, 2, 34), ms(2026, 8, 31, 23, 10), now, zone, withRegrounding = true)!!
        val match = echoRegex.find(longForm)
        assertEquals("整行命中（剥完不留残渣）", longForm, match?.value)
        assertEquals("剥离后应为空串", "", echoRegex.replace(longForm, ""))
        val inner = longForm.removePrefix(HistoryTimeDivider.OPEN).removeSuffix(HistoryTimeDivider.CLOSE)
        assertFalse("注记内容不得含 】", inner.contains("】"))
        assertFalse("注记必须单行", longForm.contains("\n"))
        assertTrue("长版对 isDivider 仍为真（悬空清理照旧生效）", HistoryTimeDivider.isDivider(longForm))
    }

    @Test
    fun pauseMarker_keepsDividerSafetyShape() {
        // REDLINES §1【时间 · 】三处安全设施：停顿标记仍单行、【时间 · 开头、】结尾、中间无 】→ isDivider 真、echo 整行剥净。
        val now = inst(2026, 6, 25, 22, 10)
        val marker = HistoryTimeDivider.lineFor(ms(2026, 6, 25, 21, 52), ms(2026, 6, 25, 21, 40), now, zone)!!
        assertEquals("【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】", marker)
        assertTrue(HistoryTimeDivider.isDivider(marker))
        assertEquals("", echoRegex.replace(marker, ""))
        assertFalse(marker.removePrefix(HistoryTimeDivider.OPEN).removeSuffix(HistoryTimeDivider.CLOSE).contains("】"))
        assertFalse("「约」只来自 formatGapPrecise，不许约约", marker.contains("约约"))
    }

    // MARK: - 复刻 dump 真实穿帮场景

    @Test
    fun dumpCase_afternoonToMidnight_insertsDivider() {
        // 「快下午三点了」(6/25 14:56) → 「你看看几点了」(6/26 00:15)，now=6/26 00:17（深夜）。
        // 跨夜 → 插分割线，让 LLM 看到时间已跳到今天凌晨，不再把昨天下午当此刻（四期：带间隔约 9 小时）。
        val now = inst(2026, 6, 26, 0, 17)
        assertEquals(
            "【时间 · 今天 00:15 · 距离上条消息过去了约 9 小时】",
            HistoryTimeDivider.lineFor(ms(2026, 6, 26, 0, 15), ms(2026, 6, 25, 14, 56), now, zone),
        )
    }
}
