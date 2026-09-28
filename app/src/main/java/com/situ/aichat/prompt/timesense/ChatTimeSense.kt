package com.situ.aichat.prompt.timesense

import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.data.model.MessageKind
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * 在线文字聊天的「时间感」事实（时间感知四期·图纸一 §3.5 ① 锁定·纯函数·不查库）。
 *
 * 只从 `PromptBuilder.buildMessages` 已有的 `sortedMessages`（≤500 条）现算，只在「在线文字聊天、非延迟补生成」时算
 * （语音通话 / 线下 / 忙碌回复 / 恢复 / 余温一律 null → 现在卡不接新行）：
 * - [latestGapSeconds]：最后一条计数消息是用户的时，它距前一条过去了多久（渲染见 [TimeSenseLines]）。
 * - [rhythmRangeText]：用户近 30 天的消息大多落在哪个钟点窗口；**非 null = 此刻属反常时段**（此刻前后
 *   [RHYTHM_NEAR_MINUTES] 分钟内从没来过）。本轮末尾连发的用户消息不计入样本——「凌晨 4 点连发几条」只有第一轮会提。
 */
data class ChatTimeSense(
    val latestGapSeconds: Long?,
    /** 非 null = 此刻属反常时段；值如 "20:00–01:00"（en dash）。 */
    val rhythmRangeText: String?,
) {
    companion object {
        const val RHYTHM_LOOKBACK_DAYS = 30L
        const val RHYTHM_MIN_MESSAGES = 30
        const val RHYTHM_MIN_DAYS = 5
        const val RHYTHM_NEAR_MINUTES = 60
        const val RHYTHM_COVERAGE = 0.8
        const val RHYTHM_MAX_WINDOW_HOURS = 16

        private const val ROLE_USER = "user"
        private const val MINUTES_PER_DAY = 1440
        private const val MILLIS_PER_DAY = 86_400_000L

        fun from(sortedMessages: List<MessageEntity>, now: Instant, zone: ZoneId): ChatTimeSense {
            // 1. 计数消息：有正文、非宠物、非系统耳语。
            val counted = sortedMessages
                .filter { it.content.isNotBlank() && !it.isPetMessage && MessageKind.fromRaw(it.messageKindRaw) != MessageKind.SYSTEM_HINT }
                .sortedBy { it.timestamp }
            if (counted.isEmpty()) return ChatTimeSense(latestGapSeconds = null, rhythmRangeText = null)

            // 2. 这条距上条。
            val latestGap = if (counted.last().roleRaw == ROLE_USER && counted.size >= 2) {
                ((counted.last().timestamp - counted[counted.lastIndex - 1].timestamp) / 1000).coerceAtLeast(0)
            } else {
                null
            }
            return ChatTimeSense(latestGapSeconds = latestGap, rhythmRangeText = rhythmRange(counted, now, zone))
        }

        /** 3. 作息：样本 = 去掉末尾连发的用户消息后、近 30 天的用户消息。 */
        private fun rhythmRange(counted: List<MessageEntity>, now: Instant, zone: ZoneId): String? {
            val tailSize = counted.takeLastWhile { it.roleRaw == ROLE_USER }.size
            val since = now.toEpochMilli() - RHYTHM_LOOKBACK_DAYS * MILLIS_PER_DAY
            val sample = counted.dropLast(tailSize).filter { it.roleRaw == ROLE_USER && it.timestamp >= since }
            if (sample.size < RHYTHM_MIN_MESSAGES) return null
            val localTimes = sample.map { Instant.ofEpochMilli(it.timestamp).atZone(zone) }
            if (localTimes.map { it.toLocalDate() }.distinct().size < RHYTHM_MIN_DAYS) return null

            val nowLocal = now.atZone(zone)
            val m = nowLocal.hour * 60 + nowLocal.minute
            val nearNow = localTimes.any { t ->
                val d = abs(t.hour * 60 + t.minute - m)
                min(d, MINUTES_PER_DAY - d) <= RHYTHM_NEAR_MINUTES
            }
            if (nearNow) return null

            val hist = IntArray(24)
            localTimes.forEach { hist[it.hour]++ }
            val need = ceil(RHYTHM_COVERAGE * sample.size).toInt()
            for (len in 1..24) {
                var bestStart = 0
                var bestSum = -1
                for (h in 0..23) {
                    var sum = 0
                    for (k in 0 until len) sum += hist[(h + k) % 24]
                    if (sum > bestSum) { bestSum = sum; bestStart = h } // 并列取最小 h
                }
                if (bestSum >= need) {
                    if (len > RHYTHM_MAX_WINDOW_HOURS) return null
                    return "%02d:00–%02d:00".format(Locale.ROOT, bestStart, (bestStart + len) % 24)
                }
            }
            return null
        }
    }
}
