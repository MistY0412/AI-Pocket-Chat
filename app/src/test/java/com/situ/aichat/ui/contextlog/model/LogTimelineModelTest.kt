package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.diagnostics.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** T1-3（四期·图纸四 §3.3 第 1–8 步）：分轮 / 代表行 / 重试 / 引用 / 图片理解归轮 / 后台 / 会话过滤 / 分节。 */
class LogTimelineModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: Int, h: Int, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, 9, d, h, m, s).atZone(zone).toInstant().toEpochMilli()
    private val now = at(27, 15)
    private val chars = listOf(CharacterEntity(uuid = "a", name = "林晚", creationDate = 0L), CharacterEntity(uuid = "b", name = "阿来", creationDate = 0L))

    private fun row(
        id: Long, ts: Long, source: String = LogSource.CHAT, turn: String? = null, anchor: String? = null,
        conv: String? = "c1", uuid: String = "a", ok: Boolean = true, failureKind: String? = null,
        prompt: Int = 0, completion: Int = 0, hit: Int = 0, miss: Int = 0, duration: Long? = null, estimated: Boolean = true,
    ) = LogListRow(
        id = id, timestampMillis = ts, characterName = if (uuid == "a") "林晚" else "阿来", characterUuid = uuid, source = source,
        turnId = turn, anchorMessageUuid = anchor, conversationUuid = conv, isSuccess = ok, failureKind = failureKind,
        promptTokens = prompt, completionTokens = completion, cacheHitTokens = hit, cacheMissTokens = miss,
        durationMillis = duration, isTokenEstimated = estimated,
    )

    private fun msg(uuid: String, ts: Long, role: String, content: String = "", image: String? = null) =
        LogMessageBrief(uuid, "c1", role, content, ts, image, false, "plain_text")

    private val briefs = listOf(
        msg("u1", at(27, 10), "user", "早呀"),
        msg("u1img", at(27, 10, 0, 2), "user", "猫", image = "img/1.jpg"),
        msg("a1", at(27, 10, 0, 10), "assistant"),
        msg("a2", at(27, 10, 0, 20), "assistant"),
        msg("u2", at(27, 11), "user", "[图片]", image = "img/2.jpg"),
        msg("u3", at(27, 11, 0, 5), "user", "看这个"),
        msg("a3", at(27, 11, 0, 20), "assistant"),
        msg("u4", at(27, 12), "user", "晚安"),
    )

    private val rows = listOf(
        // 记录时刻 = 调用完成那一刻（ContextLogService），所以晚于本轮最后一条用户消息 u1img（复核 R1 订正：原 10:00:01 早于 u1img）
        row(1, at(27, 10, 0, 8), turn = "T1", anchor = "u1", prompt = 100, completion = 20, hit = 60, miss = 40, duration = 6_800, estimated = false),
        row(2, at(27, 10, 0, 30), LogSource.MEMORY_SUMMARY, turn = "T1"),
        row(12, at(27, 10, 0, 5), LogSource.IMAGE_UNDERSTANDING, anchor = "u1img"), // 图在本轮中间
        row(3, at(27, 11, 0, 6), turn = "T2", anchor = "u2", ok = false, failureKind = "timeout"),
        row(4, at(27, 11, 0, 10), turn = "T2", anchor = "u2", ok = false, failureKind = "insufficient_balance"),
        row(5, at(27, 11, 0, 3), LogSource.IMAGE_UNDERSTANDING, anchor = "u2"), // 图是本轮第一条
        row(6, at(27, 11, 30), LogSource.IMAGE_UNDERSTANDING, anchor = "gone-msg"), // 图片消息已删
        row(7, at(27, 11, 20), LogSource.IMAGE_UNDERSTANDING, turn = "T9"), // 那轮没有主调用
        row(8, at(27, 9), prompt = 50), // 老记录
        row(9, at(27, 12, 30), LogSource.VOICE_CALL, turn = "TV"),
        row(10, at(27, 12, 40), LogSource.GROWTH_ANALYSIS, conv = null), // 无会话：每个会话视图都在
        row(13, at(27, 12, 50), turn = "T4", anchor = "deleted-u"), // 锚点消息已删
        row(11, at(26, 20), turn = "T3", conv = "c2"),
        row(20, at(27, 14), turn = "TB", uuid = "b"), // 别的角色
    )

    @Test
    fun scope_ownedConversationsSelectedVisible() {
        val scope = timelineScopeOf(rows, chars, "a", selectedConversation = null)
        assertEquals(false, scope.isSystem)
        assertEquals("按各自最新行降序", listOf("c1", "c2"), scope.conversationUuids)
        assertEquals("c1", scope.selected)
        assertEquals((1L..13L).toSet() - 11L, scope.visible.map { it.id }.toSet())
        assertEquals("不认识的会话 → 最新", "c1", timelineScopeOf(rows, chars, "a", "zzz").selected)
        assertEquals(setOf(11L, 10L), timelineScopeOf(rows, chars, "a", "c2").visible.map { it.id }.toSet())
        assertTrue(timelineScopeOf(rows, chars, SYSTEM_KEY, null).isSystem)
    }

    @Test
    fun anchors_turnAnchorsAndImageAnchors() {
        val scope = timelineScopeOf(rows, chars, "a", null)
        assertEquals(setOf("u1", "u2", "deleted-u", "u1img", "gone-msg"), anchorUuidsOf(scope.visible))
    }

    @Test
    fun timeline_c1_today() {
        val scope = timelineScopeOf(rows, chars, "a", null)
        val sections = buildCharacterTimeline(scope.visible, briefs, now, zone)
        assertEquals(1, sections.size)
        val today = sections.single()
        assertEquals(LogDayKind.TODAY, today.dayKind)
        assertEquals(LocalDate.of(2026, 9, 27), today.date)
        assertEquals(
            "项按 sortMillis 降序（卡取第一条主行时刻）",
            listOf("t:T4", "b:10", "t:TV", "b:6", "b:7", "t:T2", "t:T1", "r:8"),
            today.items.map { it.key },
        )
        val byKey = today.items.associateBy { it.key }

        val t1 = byKey.getValue("t:T1") as LogTurnCard
        assertEquals(1L, t1.openId)
        assertEquals(false, t1.openFailed)
        assertEquals(LogSource.CHAT, t1.mainSource)
        assertEquals(LogTurnQuoteText(listOf(LogMediaTag.IMAGE), "早呀 猫"), t1.quote)
        assertEquals("u1img 之后到下一条 user 前 2 条回复", 2, t1.replies)
        assertEquals(120, t1.tokens)
        assertEquals(false, t1.tokensEstimated)
        assertEquals(60, t1.cacheRatePercent)
        assertEquals(6_800L, t1.durationMillis)
        assertEquals(0, t1.retries)
        assertNull(t1.failureKind)
        assertEquals("子项 = 同轮后台 + 挂进来的图片理解，按时刻升序", listOf(12L, 2L), t1.kids.map { it.id })

        val t2 = byKey.getValue("t:T2") as LogTurnCard
        assertEquals("代表行 = 最后一条主行（E6）", 4L, t2.openId)
        assertEquals(true, t2.openFailed)
        assertEquals(1, t2.retries)
        assertEquals(LlmFailureKind.INSUFFICIENT_BALANCE, t2.failureKind)
        assertEquals(at(27, 11, 0, 6), t2.sortMillis)
        assertEquals(LogTurnQuoteText(listOf(LogMediaTag.IMAGE), "看这个"), t2.quote)
        assertEquals(1, t2.replies)
        assertEquals(listOf(5L), t2.kids.map { it.id })
        assertNull("服务商没报 → null", t2.cacheRatePercent)

        val t4 = byKey.getValue("t:T4") as LogTurnCard
        assertNull("锚点已删 → 无引用（E9）", t4.quote)
        assertNull(t4.replies)

        val tv = byKey.getValue("t:TV") as LogTurnCard
        assertNull("语音通话无锚点（E10）", tv.quote)
        assertEquals(LogSource.VOICE_CALL, tv.mainSource)

        val old = byKey.getValue("r:8") as LogTurnCard
        assertNull(old.quote)
        assertEquals(emptyList<LogKid>(), old.kids)
        assertEquals(50, old.tokens)

        val bg7 = byKey.getValue("b:7") as LogBackgroundRow
        assertEquals(LogSource.IMAGE_UNDERSTANDING, bg7.source)
    }

    @Test
    fun timeline_c2_bothDays_sectionsDescending() {
        val scope = timelineScopeOf(rows, chars, "a", "c2")
        val sections = buildCharacterTimeline(scope.visible, emptyList(), now, zone)
        assertEquals(listOf(LogDayKind.TODAY, LogDayKind.YESTERDAY), sections.map { it.dayKind })
        assertEquals(listOf("b:10"), sections[0].items.map { it.key })
        assertEquals(listOf("t:T3"), sections[1].items.map { it.key })
    }

    @Test
    fun mainCallRotatedAway_onlyKidsLeft_becomeBackground() {
        val only = listOf(row(1, at(27, 10), LogSource.MEMORY_SUMMARY, turn = "T1"), row(2, at(27, 10, 1), LogSource.RELATIONSHIP_ANALYSIS, turn = "T1"))
        val items = buildCharacterTimeline(only, emptyList(), now, zone).single().items
        assertEquals(listOf("b:2", "b:1"), items.map { it.key })
        assertTrue(items.all { it is LogBackgroundRow })
    }

    @Test
    fun allOldRecords_eachMainRowOwnCard_recoveryAndAfterglowToo() {
        val old = listOf(
            row(1, at(27, 9), conv = null), row(2, at(27, 10), conv = null),
            row(3, at(27, 11), LogSource.RECOVERY_REPLY, turn = "R"), row(4, at(27, 12), LogSource.OFFLINE_AFTERGLOW, turn = "O"),
        )
        val items = buildCharacterTimeline(old, emptyList(), now, zone).single().items
        assertEquals(listOf("t:O", "t:R", "r:2", "r:1"), items.map { it.key })
        assertEquals(LogSource.OFFLINE_AFTERGLOW, (items[0] as LogTurnCard).mainSource)
    }

    /**
     * 复核 R1：连着失败的两轮共用锚点 x1（x1 = 图片；第一轮 13:00:10 失败；x2 = 13:05 才发；第二轮 13:05:10 成功）——
     * 第一轮引用不含后发的 x2；图片理解挂在最早带上这张图的第一轮，不挂第二轮。
     */
    @Test
    fun sharedAnchor_quoteBounded_imageOnEarliestCard() {
        val b = listOf(
            msg("x1", at(27, 13), "user", "[图片]", image = "img/9.jpg"),
            msg("x2", at(27, 13, 5), "user", "在吗"),
            msg("ax", at(27, 13, 5, 20), "assistant"),
        )
        val r = listOf(
            row(31, at(27, 13, 0, 10), turn = "F1", anchor = "x1", ok = false, failureKind = "network"),
            row(32, at(27, 13, 5, 10), turn = "F2", anchor = "x1"),
            row(33, at(27, 13, 0, 5), LogSource.IMAGE_UNDERSTANDING, anchor = "x1"),
        )
        val cards = buildCharacterTimeline(r, b, now, zone).single().items.filterIsInstance<LogTurnCard>().associateBy { it.key }
        val f1 = cards.getValue("t:F1")
        val f2 = cards.getValue("t:F2")
        assertEquals("第一轮只看到它开始前发的图", LogTurnQuoteText(listOf(LogMediaTag.IMAGE), null), f1.quote)
        assertEquals(LogTurnQuoteText(listOf(LogMediaTag.IMAGE), "在吗"), f2.quote)
        assertEquals(listOf(33L), f1.kids.map { it.id })
        assertEquals(emptyList<LogKid>(), f2.kids)
    }
}
