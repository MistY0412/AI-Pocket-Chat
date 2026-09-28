package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.diagnostics.LogTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** T1-2（四期·图纸四 §3.2）：归属五支 + 「按对话」装配。期望值从规格手算。 */
class LogHomeModelTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: Int, h: Int, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, 9, d, h, m, s).atZone(zone).toInstant().toEpochMilli()
    private val now = at(27, 15)

    private val a = CharacterEntity(uuid = "a", name = "林晚", creationDate = 0L, avatarPath = "a.jpg")
    private val b = CharacterEntity(uuid = "b", name = "阿来", creationDate = 0L)
    private val a2 = CharacterEntity(uuid = "a2", name = "林晚", creationDate = 0L)

    private var nextId = 1L
    private fun row(
        ts: Long, source: String = LogSource.CHAT, name: String = "", uuid: String? = null, turn: String? = null,
        ok: Boolean = true, hit: Int = 0, miss: Int = 0,
    ) = LogListRow(
        id = nextId++, timestampMillis = ts, characterName = name, characterUuid = uuid, source = source, turnId = turn,
        isSuccess = ok, cacheHitTokens = hit, cacheMissTokens = miss,
    )

    @Test
    fun ownerKeyOf_fiveBranches() {
        val chars = listOf(a, a2, b)
        assertEquals("① uuid 在", "a", ownerKeyOf(row(now, name = "林晚", uuid = "a"), chars))
        assertEquals("② uuid 已删·有名", "name:旧人", ownerKeyOf(row(now, name = "旧人", uuid = "gone"), chars))
        assertEquals("② uuid 已删·无名", SYSTEM_KEY, ownerKeyOf(row(now, name = "", uuid = "gone"), chars))
        assertEquals("③ 无 uuid 无名", SYSTEM_KEY, ownerKeyOf(row(now, name = ""), chars))
        assertEquals("④ 同名取第一个（E4）", "a", ownerKeyOf(row(now, name = "林晚"), chars))
        assertEquals("⑤ 查无此名", "name:路人", ownerKeyOf(row(now, name = "路人"), chars))
        assertEquals("没有任何角色（E2）", "name:林晚", ownerKeyOf(row(now, name = "林晚"), emptyList()))
    }

    @Test
    fun buildConversationTab_countsOnlyLastActiveDay() {
        val rows = listOf(
            // 林晚（今天）：t1 一轮带一次失败重试、t2、老记录一行一轮、语音通话一轮两行 + 老语音一行、后台两行
            row(at(27, 10), name = "林晚", uuid = "a", turn = "t1", hit = 60, miss = 40),
            row(at(27, 10, 1), name = "林晚", uuid = "a", turn = "t1", ok = false, hit = 5, miss = 5),
            row(at(27, 11), name = "林晚", uuid = "a", turn = "t2", hit = 30, miss = 70),
            row(at(27, 12), name = "林晚", uuid = "a"),
            row(at(27, 13), LogSource.VOICE_CALL, "林晚", "a", turn = "v1"),
            row(at(27, 13, 1), LogSource.VOICE_CALL, "林晚", "a", turn = "v1"),
            row(at(27, 13, 2), LogSource.VOICE_CALL, "林晚", "a"),
            row(at(27, 12, 30), LogSource.MEMORY_SUMMARY, "林晚", "a"),
            row(at(27, 10, 0, 30), LogSource.IMAGE_UNDERSTANDING, "林晚", "a"),
            row(at(26, 22), name = "林晚", uuid = "a", turn = "old", ok = false), // 昨天：不计
            // 阿来（只有昨天、服务商没报缓存）
            row(at(26, 20), name = "阿来", uuid = "b", turn = "t9"),
            // 已删角色（三天前）
            row(at(24, 9), name = "旧人", uuid = "gone"),
            // 系统任务（今天 5 行 + 更早 1 行）
            row(at(27, 1), LogSource.DIARY_GENERATION), row(at(27, 2), LogSource.DIARY_GENERATION),
            row(at(27, 3), LogSource.MOMENT_POST), row(at(27, 4), LogSource.MOMENT_POST),
            row(at(27, 5), LogSource.SCHEDULE_GENERATION), row(at(20, 5), LogSource.STORY_GENERATION),
        )
        val totals = LogTrend.Totals("2026-09-27", calls = 12, failures = 1, promptTokens = 1000, completionTokens = 200, cacheHitTokens = 300, cacheMissTokens = 700)
        val tab = buildConversationTab(rows, listOf(a, b), totals, now, zone)

        assertEquals(false, tab.empty)
        assertEquals(LogTodayStats(calls = 12, failures = 1, cacheRatePercent = 30, totalTokens = 1200), tab.today)
        assertEquals("按最后活动降序", listOf("a", "b", "name:旧人"), tab.characters.map { it.key })

        val lin = tab.characters[0]
        assertEquals("林晚", lin.name)
        assertEquals("a.jpg", lin.avatarPath)
        assertEquals(at(27, 13, 2), lin.lastActivityMillis)
        assertEquals(LogDayKind.TODAY, lin.dayKind)
        assertEquals("t1 + t2 + 老记录一行 = 3", 3, lin.turns)
        assertEquals("v1 + 老语音一行 = 2", 2, lin.voiceTurns)
        assertEquals(2, lin.background)
        assertEquals("(60 + 30) / (100 + 100) = 45%（失败行不计）", 45, lin.cacheRatePercent)
        assertEquals(1, lin.failures)
        assertEquals(emptyList<String>(), lin.topSources)
        assertEquals(0, lin.calls)

        val lai = tab.characters[1]
        assertEquals(LogDayKind.YESTERDAY, lai.dayKind)
        assertNull("服务商没报 → null（E13）", lai.cacheRatePercent)
        assertEquals(1, lai.turns)
        assertNull(lai.avatarPath)

        val gone = tab.characters[2]
        assertEquals("旧人", gone.name)
        assertEquals(LogDayKind.EARLIER, gone.dayKind)

        val sys = tab.system!!
        assertEquals("", sys.name)
        assertEquals("次数降序、并列按来源名升序", listOf(LogSource.DIARY_GENERATION, LogSource.MOMENT_POST), sys.topSources)
        assertEquals("只数今天 5 行", 5, sys.calls)
        assertEquals(5, sys.background)
        assertEquals(0, sys.turns)
    }

    @Test
    fun emptyRows_emptyTab() {
        val tab = buildConversationTab(emptyList(), listOf(a), LogTrend.Totals("2026-09-27", 0, 0, 0, 0, 0, 0), now, zone)
        assertTrue(tab.empty)
        assertEquals(emptyList<LogCharacterRowModel>(), tab.characters)
        assertNull(tab.system)
        assertEquals(LogTodayStats(0, 0, null, 0), tab.today)
    }

    @Test
    fun homeTab_fromRaw() {
        assertEquals(LogHomeTab.TREND, LogHomeTab.fromRaw("trend"))
        assertEquals(LogHomeTab.FLOW, LogHomeTab.fromRaw("flow"))
        assertEquals(LogHomeTab.CONVERSATION, LogHomeTab.fromRaw(""))
        assertEquals(LogHomeTab.CONVERSATION, LogHomeTab.fromRaw("乱写"))
        assertEquals(LogHomeTab.CONVERSATION, LogHomeTab.fromRaw(null))
    }
}
