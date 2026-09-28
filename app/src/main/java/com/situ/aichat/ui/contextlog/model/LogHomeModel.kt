package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.diagnostics.LogTrend
import java.time.ZoneId

/** 日志首页三分段（四期·图纸四 §3.2）；[raw] 存进 `SettingsPreferences.contextLogHomeTab`，未知串 / 空串 → 按对话。 */
enum class LogHomeTab(val raw: String) {
    CONVERSATION("conversation"), FLOW("flow"), TREND("trend");

    companion object {
        fun fromRaw(raw: String?): LogHomeTab = entries.firstOrNull { it.raw == raw } ?: CONVERSATION
    }
}

/** 一轮的主调用来源（其余来源 = 这一轮带出的后台调用 / 后台小方块）。 */
val MAIN_SOURCES = setOf(LogSource.CHAT, LogSource.VOICE_CALL, LogSource.RECOVERY_REPLY, LogSource.OFFLINE_AFTERGLOW)

/** 「不属于具体角色」的系统任务键（路由 `contextLog/character/system`）。 */
const val SYSTEM_KEY = "system"

/** 角色已删 / 老记录只有名字时的键前缀：`"name:" + 角色名`。 */
const val NAME_KEY_PREFIX = "name:"

enum class LogDayKind { TODAY, YESTERDAY, EARLIER }

data class LogTodayStats(val calls: Int, val failures: Int, val cacheRatePercent: Int?, val totalTokens: Long)

/** 首页「按对话」一行（角色或系统任务）；[topSources] / [calls] 只对系统键有值（角色行恒空表 / 0）。 */
data class LogCharacterRowModel(
    val key: String, val name: String, val avatarPath: String?,
    val lastActivityMillis: Long, val dayKind: LogDayKind,
    val turns: Int, val voiceTurns: Int, val background: Int,
    val cacheRatePercent: Int?, val failures: Int,
    val topSources: List<String>, val calls: Int,
)

data class LogConversationTabState(
    val empty: Boolean = true,
    val today: LogTodayStats? = null,
    val characters: List<LogCharacterRowModel> = emptyList(),
    val system: LogCharacterRowModel? = null,
)

/**
 * 一行日志归哪个角色（§3.2 锁定·按序）：① uuid 且角色在 → uuid；② uuid 但角色已删 → 名空为系统键、否则 `name:名`；
 * ③ 名空 → 系统键；④ 第一个同名角色 → 其 uuid；⑤ 否则 `name:名`。
 */
fun ownerKeyOf(row: LogListRow, characters: List<CharacterEntity>): String {
    val uuid = row.characterUuid
    if (uuid != null) {
        if (characters.any { it.uuid == uuid }) return uuid
        return if (row.characterName.isEmpty()) SYSTEM_KEY else NAME_KEY_PREFIX + row.characterName
    }
    if (row.characterName.isEmpty()) return SYSTEM_KEY
    characters.firstOrNull { it.name == row.characterName }?.let { return it.uuid }
    return NAME_KEY_PREFIX + row.characterName
}

/** 键对应的显示名：系统键 = 空串（界面用「系统任务」）；`name:` 键 = 冒号后原名；uuid = 角色名（查无 = 空串）。 */
fun nameForKey(key: String, characters: List<CharacterEntity>): String = when {
    key == SYSTEM_KEY -> ""
    key.startsWith(NAME_KEY_PREFIX) -> key.removePrefix(NAME_KEY_PREFIX)
    else -> characters.firstOrNull { it.uuid == key }?.name.orEmpty()
}

/**
 * 「按对话」装配（§3.2 锁定）：每个键只数它**最近活动那一天**的行——轮数（同 turnId 算一轮、无 turnId 一行一轮）、
 * 语音通话次数、后台次数、加权命中率、失败数；系统键另算最多的两个来源与总次数。今天四数取按天汇总表 [todayTotals]。
 */
fun buildConversationTab(
    rows: List<LogListRow>,
    characters: List<CharacterEntity>,
    todayTotals: LogTrend.Totals,
    nowMillis: Long,
    zone: ZoneId,
): LogConversationTabState {
    val byKey = rows.groupBy { ownerKeyOf(it, characters) }
    val models = byKey.map { (key, keyRows) -> rowModel(key, keyRows, characters, nowMillis, zone) }
    return LogConversationTabState(
        empty = rows.isEmpty(),
        today = LogTodayStats(todayTotals.calls, todayTotals.failures, todayTotals.cacheRatePercent, todayTotals.totalTokens),
        characters = models.filter { it.key != SYSTEM_KEY }.sortedByDescending { it.lastActivityMillis },
        system = models.firstOrNull { it.key == SYSTEM_KEY },
    )
}

private fun rowModel(
    key: String,
    keyRows: List<LogListRow>,
    characters: List<CharacterEntity>,
    nowMillis: Long,
    zone: ZoneId,
): LogCharacterRowModel {
    val last = keyRows.maxOf { it.timestampMillis }
    val lastDay = LogFormat.localDate(last, zone)
    val day = keyRows.filter { LogFormat.localDate(it.timestampMillis, zone) == lastDay }
    val isSystem = key == SYSTEM_KEY
    val chatMains = day.filter { it.source in MAIN_SOURCES && it.source != LogSource.VOICE_CALL }
    val voiceMains = day.filter { it.source == LogSource.VOICE_CALL }
    val cached = day.filter { it.isSuccess && it.cacheHitTokens + it.cacheMissTokens > 0 }
    return LogCharacterRowModel(
        key = key,
        name = nameForKey(key, characters),
        avatarPath = characters.firstOrNull { it.uuid == key }?.avatarPath,
        lastActivityMillis = last,
        dayKind = LogFormat.dayKind(last, nowMillis, zone),
        turns = turnCount(chatMains),
        voiceTurns = turnCount(voiceMains),
        background = day.count { it.source !in MAIN_SOURCES },
        cacheRatePercent = LogFormat.cacheRate(cached.sumOf { it.cacheHitTokens.toLong() }, cached.sumOf { it.cacheMissTokens.toLong() }),
        failures = day.count { !it.isSuccess },
        topSources = if (isSystem) topTwoSources(day) else emptyList(),
        calls = if (isSystem) day.size else 0,
    )
}

/** 不同 turnId 个数 + 没有 turnId 的行数（老记录一行算一轮）。 */
private fun turnCount(mains: List<LogListRow>): Int =
    mains.mapNotNull { it.turnId }.toSet().size + mains.count { it.turnId == null }

/** 行数最多的两个来源：次数降序、并列按来源名升序。 */
private fun topTwoSources(day: List<LogListRow>): List<String> =
    day.groupingBy { it.source }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(2)
        .map { it.key }
