package com.situ.aichat.prompt.timesense

import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.data.model.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T1-4（时间感知四期·图纸一 §3.5 ①·E19 / E21 / E30 / E31 / E39）：ChatTimeSense 纯函数。
 * 期望由 §3.5 算法手算（时区钉 Asia/Shanghai）。
 */
class ChatTimeSenseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private var seq = 0

    private fun t(d: Int, h: Int, m: Int, s: Int = 0): Long =
        LocalDateTime.of(2026, 9, d, h, m, s).atZone(zone).toInstant().toEpochMilli()

    private fun inst(d: Int, h: Int, m: Int): Instant = Instant.ofEpochMilli(t(d, h, m))

    private fun msg(role: String, ts: Long, content: String = "嗯", pet: Boolean = false, kind: MessageKind = MessageKind.PLAIN_TEXT) =
        MessageEntity(
            messageUUID = "m${seq++}", conversationUuid = "c", roleRaw = role, content = content, timestamp = ts,
            isPetMessage = pet, messageKindRaw = kind.raw,
        )

    // MARK: - 这条距上条（E19 / E21 / E39）

    @Test
    fun `E19_恰5分钟与4分59秒_给出秒数`() {
        val a = listOf(msg("user", t(1, 9, 0)), msg("assistant", t(1, 9, 1)), msg("user", t(1, 9, 6)))
        assertEquals(300L, ChatTimeSense.from(a, inst(1, 9, 7), zone).latestGapSeconds)
        val b = listOf(msg("user", t(1, 9, 0)), msg("assistant", t(1, 9, 1)), msg("user", t(1, 9, 5, 59)))
        assertEquals(299L, ChatTimeSense.from(b, inst(1, 9, 7), zone).latestGapSeconds)
    }

    @Test
    fun `E19_最后两条都是用户连发_按最后两条算`() {
        val list = listOf(msg("assistant", t(1, 9, 0)), msg("user", t(1, 9, 10)), msg("user", t(1, 9, 10, 30)))
        assertEquals(30L, ChatTimeSense.from(list, inst(1, 9, 11), zone).latestGapSeconds)
    }

    @Test
    fun `E21_最后一条计数消息不是用户的_无间隔`() {
        val list = listOf(msg("user", t(1, 9, 0)), msg("assistant", t(1, 12, 0)))
        assertNull(ChatTimeSense.from(list, inst(1, 12, 1), zone).latestGapSeconds)
        assertNull("只有一条用户消息", ChatTimeSense.from(listOf(msg("user", t(1, 9, 0))), inst(1, 9, 1), zone).latestGapSeconds)
    }

    @Test
    fun `计数消息过滤空白_宠物_系统耳语_且按时间排序`() {
        val list = listOf(
            msg("user", t(1, 9, 10)),
            msg("assistant", t(1, 9, 0)),
            msg("user", t(1, 9, 8), kind = MessageKind.SYSTEM_HINT),
            msg("assistant", t(1, 9, 9), pet = true),
            msg("assistant", t(1, 9, 9, 30), content = "   "),
        )
        assertEquals("跳过耳语 / 宠物 / 空白，距 9:00 的角色消息 10 分钟", 600L, ChatTimeSense.from(list, inst(1, 9, 11), zone).latestGapSeconds)
    }

    @Test
    fun `E39_首装冷启无消息_全部null不抛`() {
        val s = ChatTimeSense.from(emptyList(), inst(1, 9, 0), zone)
        assertNull(s.latestGapSeconds)
        assertNull(s.rhythmRangeText)
        val onlyNoise = listOf(msg("user", t(1, 9, 0), content = ""), msg("user", t(1, 9, 1), kind = MessageKind.SYSTEM_HINT))
        assertEquals(ChatTimeSense(null, null), ChatTimeSense.from(onlyNoise, inst(1, 9, 2), zone))
    }

    // MARK: - 作息（E30 / E31）

    /** 每条用户样本后跟一条角色回复（避免被当成本轮末尾连发）。 */
    private fun samples(times: List<Long>): List<MessageEntity> = times.sorted().flatMap { listOf(msg("user", it), msg("assistant", it + 60_000)) }

    /** 跨午夜分布：20 点 7 条 / 21–23 点各 6 条 / 0 点 7 条 = 32 条，任意 4 小时窗 = 25 < ceil(0.8×32)=26 → 5 小时窗。 */
    private fun lateNightSample(): List<Long> =
        (1..7).map { t(it, 20, 10) } + (1..6).map { t(it, 21, 10) } + (1..6).map { t(it, 22, 10) } +
            (1..6).map { t(it, 23, 10) } + (2..8).map { t(it, 0, 10) }

    @Test
    fun `E30_跨午夜窗口_20点到01点`() {
        val s = ChatTimeSense.from(samples(lateNightSample()), inst(20, 4, 0), zone)
        assertEquals("20:00–01:00", s.rhythmRangeText)
    }

    @Test
    fun `E31_本轮末尾连发的用户消息不计入样本`() {
        // 本轮 03:58 / 04:00 两条连发都在「此刻前后 60 分钟」内——若计入样本就会判「不反常」；不计入 → 仍反常。
        val history = samples(lateNightSample()) + msg("user", t(20, 3, 58)) + msg("user", t(20, 4, 0))
        assertEquals("20:00–01:00", ChatTimeSense.from(history, inst(20, 4, 0), zone).rhythmRangeText)
    }

    @Test
    fun `E30_此刻前后60分钟内有过样本_不算反常_含跨午夜环形距离`() {
        val withNear = samples(lateNightSample() + t(9, 3, 30))
        assertNull("03:30 距 04:00 仅 30 分钟", ChatTimeSense.from(withNear, inst(20, 4, 0), zone).rhythmRangeText)
        // 环形距离：下午样本 14–17 点各 8 条（对照组 → 14:00–18:00），再加一条 00:20；此刻 23:40 只经跨午夜才相距 40 分钟。
        val afternoon = (14..17).flatMap { h -> (1..8).map { d -> t(d, h, 10) } }
        assertEquals("对照：无 00:20 时照常判反常", "14:00–18:00", ChatTimeSense.from(samples(afternoon), inst(20, 23, 40), zone).rhythmRangeText)
        assertNull("00:20 距 23:40 环形 40 分钟", ChatTimeSense.from(samples(afternoon + t(9, 0, 20)), inst(20, 23, 40), zone).rhythmRangeText)
        // 恰 60 分钟也算「前后 60 分钟内」（<=）。
        assertNull(ChatTimeSense.from(samples(lateNightSample()), inst(20, 1, 10), zone).rhythmRangeText)
    }

    @Test
    fun `E30_样本不足_不出`() {
        val lessThan30 = samples(lateNightSample().take(29))
        assertNull("29 条 < 30", ChatTimeSense.from(lessThan30, inst(20, 4, 0), zone).rhythmRangeText)
        val fourDays = samples((1..4).flatMap { d -> (0 until 8).map { k -> t(d, 20, k * 5) } })
        assertNull("32 条但只覆盖 4 天", ChatTimeSense.from(fourDays, inst(20, 4, 0), zone).rhythmRangeText)
        val stale = samples(lateNightSample()).map { it.copy(timestamp = it.timestamp - 40L * 86_400_000) }
        assertNull("全在 30 天之外", ChatTimeSense.from(stale, inst(20, 4, 0), zone).rhythmRangeText)
    }

    @Test
    fun `E30_分布太散_需要超过16小时窗口_不出`() {
        // 8 个整点（0/3/6/…/21）各 4 条 = 32 条；need = 26 → 至少 7 格 → 跨 19 小时 > 16。此刻 01:30 离最近样本 90 分钟。
        val times = (0 until 8).flatMap { b -> (1..4).map { k -> t(k + b % 4, b * 3, 0) } }
        assertNull(ChatTimeSense.from(samples(times), inst(20, 1, 30), zone).rhythmRangeText)
    }
}
