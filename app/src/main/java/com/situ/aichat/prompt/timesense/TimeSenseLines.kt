package com.situ.aichat.prompt.timesense

import com.situ.aichat.prompt.HistoryTimeDivider
import com.situ.aichat.prompt.TimeAnchorFormatter
import java.time.Instant
import java.time.ZoneId

/**
 * 现在卡里的时间感文案行（时间感知四期·图纸一 §3.5 ③ / §4 M3–M6 **逐字锁定**）：由 `TimeAnchorFormatter.buildTimeAnchor`
 * 的 `sense` 参数装配进现在卡（非 null 只在在线文字聊天）。
 *
 * - [calendarLines]：`今天：…` / `近几天：…`（[SpecialDays] 单源，各自「；」连接）。
 * - [gapLine]：`这条消息距离上条过去了约 X`——与历史停顿标记同阈（[HistoryTimeDivider.GAP_THRESHOLD_SECONDS]），
 *   「约」只来自 [TimeAnchorFormatter.formatGapPrecise]。**不输出任何「这次 / 这一段从几点聊到现在」**（09-27 撤回·
 *   实测无益且把模型的时间线带歪）。
 * - [rhythmLine]：作息反常时的一句事实。
 */
data class TimeSenseLines(val calendarLines: List<String>, val gapLine: String?, val rhythmLine: String?) {
    companion object {
        const val PAUSE_DAMPENER = "几分钟到半小时的停顿是正常聊天节奏，一般不用特意提。"
        const val CALENDAR_ONCE_NOTE = "节日、生日这类日子自然提一次就够，别每条都提。"

        /** [now] / [zone] 是锁定签名的一部分（原供已撤回的「这次从几点聊到现在」行用），当前渲染不读它们。 */
        fun build(sense: ChatTimeSense, days: SpecialDays.Inputs, now: Instant, zone: ZoneId): TimeSenseLines {
            val calendarLines = buildList {
                SpecialDays.chatTodayItems(days).takeIf { it.isNotEmpty() }?.let { add("今天：" + it.joinToString("；")) }
                SpecialDays.chatNearItems(days).takeIf { it.isNotEmpty() }?.let { add("近几天：" + it.joinToString("；")) }
            }
            val gap = sense.latestGapSeconds
            val gapLine = if (gap != null && gap >= HistoryTimeDivider.GAP_THRESHOLD_SECONDS) {
                "这条消息距离上条过去了" + TimeAnchorFormatter.formatGapPrecise(gap)
            } else {
                null
            }
            val rhythmLine = sense.rhythmRangeText?.let { "${days.userLabel}过去一个月的消息大多在 $it 之间，这个钟点前后还没来找过你" }
            return TimeSenseLines(calendarLines, gapLine, rhythmLine)
        }
    }
}
