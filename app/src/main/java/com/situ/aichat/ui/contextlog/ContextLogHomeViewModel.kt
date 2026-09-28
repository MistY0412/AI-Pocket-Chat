package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.SettingsPreferences
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.dao.LogStatsDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.LogDailyStatEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.diagnostics.ContextLogService
import com.situ.aichat.diagnostics.FailureRateAlert
import com.situ.aichat.diagnostics.FailureRateAudit
import com.situ.aichat.diagnostics.LlmFailureClassifier
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogTrend
import com.situ.aichat.ui.contextlog.model.LogConversationTabState
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.LogTrendState
import com.situ.aichat.ui.contextlog.model.TrendRange
import com.situ.aichat.ui.contextlog.model.buildConversationTab
import com.situ.aichat.ui.contextlog.model.buildTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** 日志首页状态（四期·图纸四 §3.2）：三分段共用一份，[flow] = 原列表页状态原样（K2）。 */
data class ContextLogHomeUiState(
    val loaded: Boolean = false,
    val tab: LogHomeTab = LogHomeTab.CONVERSATION,
    val conversation: LogConversationTabState = LogConversationTabState(),
    val flow: ContextLogUiState = ContextLogUiState(),
    /**
     * 「按对话」的失败告警（复核 R1）：恒对全量算。[flow].alerts 在全部流水停在「失败」时按原列表语义隐去，
     * 按对话不该跟着消失。
     */
    val conversationAlerts: List<FailureRateAlert> = emptyList(),
    /** 告警来源 → 近 24h 该来源失败里最多的一类。 */
    val alertKinds: Map<String, LlmFailureKind> = emptyMap(),
    val reason: LlmFailureKind? = null,
    /** 仅 category == FAILED：各类失败条数（条数降序、并列按枚举序）。 */
    val reasonChips: List<Pair<LlmFailureKind, Int>> = emptyList(),
    /** [flow].entries 再按 [reason] 过滤。 */
    val flowEntries: List<LogListRow> = emptyList(),
    val trend: LogTrendState = LogTrendState(),
)

/**
 * 日志首页 VM（四期·图纸四 §3.2）：数据四源（最近 500 条 / 30 天汇总 / 角色 / 设置）+ 界面五源（记住的分段 / 路由覆盖 /
 * 类别 / 失败原因 / 趋势范围）两层 combine 后交 [buildLogHomeUiState]。路由带 `tab` 时覆盖一次并记住（E29）。
 */
@HiltViewModel
class ContextLogHomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    logDao: LogDao,
    logStatsDao: LogStatsDao,
    characterRepository: CharacterRepository,
    settingsRepository: SettingsRepository,
    private val settingsPreferences: SettingsPreferences,
    private val contextLog: ContextLogService,
) : ViewModel() {

    /** VM 构造时算定：跨午夜开着的页面「今天」仍是那一天，重进即更新（§3.11）。 */
    private val todayKey = LogTrend.dayKey(System.currentTimeMillis())
    private val tabOverride = MutableStateFlow(savedStateHandle.get<String>(ARG_TAB)?.let(LogHomeTab::fromRaw))
    private val category = MutableStateFlow(LogCategory.ALL)
    private val reason = MutableStateFlow<LlmFailureKind?>(null)
    private val range = MutableStateFlow(TrendRange.WEEK)

    init {
        tabOverride.value?.let { tab -> viewModelScope.launch { settingsPreferences.setContextLogHomeTab(tab.raw) } }
        // 路由参数只用一次（复核 R1）：不清掉的话进程被杀后恢复这一页，旧的 tab=trend 会再覆盖一遍用户后来选的分段。
        savedStateHandle[ARG_TAB] = null
    }

    private data class Data(val rows: List<LogListRow>, val stats: List<LogDailyStatEntity>, val characters: List<CharacterEntity>, val settings: AppSettings)
    private data class Ui(val remembered: String, val override: LogHomeTab?, val category: LogCategory, val reason: LlmFailureKind?, val range: TrendRange)

    val state: StateFlow<ContextLogHomeUiState> = combine(
        combine(
            logDao.recent(ContextLogViewModel.RECENT_LIMIT),
            logStatsDao.since(LogTrend.daysBefore(todayKey, 29)),
            characterRepository.observeAll(),
            settingsRepository.appSettings,
            ::Data,
        ),
        combine(settingsPreferences.contextLogHomeTab, tabOverride, category, reason, range, ::Ui),
    ) { d, u ->
        buildLogHomeUiState(
            d.rows, d.stats, d.characters, d.settings, u.remembered, u.override, u.category, u.reason, u.range,
            todayKey, System.currentTimeMillis(), ZoneId.systemDefault(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextLogHomeUiState())

    fun selectTab(tab: LogHomeTab) {
        tabOverride.value = tab
        viewModelScope.launch { settingsPreferences.setContextLogHomeTab(tab.raw) }
    }

    fun setCategory(c: LogCategory) {
        category.value = c
        if (c != LogCategory.FAILED) reason.value = null
    }

    fun setReason(kind: LlmFailureKind?) { reason.value = kind }

    fun setRange(r: TrendRange) { range.value = r }

    /** 告警条「查看失败」：跳到全部流水、选中「失败」、清掉原因筛选。 */
    fun showFailures() {
        selectTab(LogHomeTab.FLOW)
        category.value = LogCategory.FAILED
        reason.value = null
    }

    fun clearAll() = viewModelScope.launch { contextLog.clearAll() }

    companion object {
        const val ARG_TAB = "tab"
    }
}

/** 首页纯装配（§3.2 锁定）：`flow` 与直接调 [buildContextLogUiState] 结果相等（K2）；今天四数取汇总表。 */
internal fun buildLogHomeUiState(
    rows: List<LogListRow>,
    stats: List<LogDailyStatEntity>,
    characters: List<CharacterEntity>,
    settings: AppSettings,
    rememberedRaw: String,
    tabOverride: LogHomeTab?,
    category: LogCategory,
    reason: LlmFailureKind?,
    range: TrendRange,
    todayKey: String,
    nowMillis: Long,
    zone: ZoneId,
): ContextLogHomeUiState {
    val flow = buildContextLogUiState(rows, category, settings, nowMillis)
    val failed = category == LogCategory.FAILED
    val conversationAlerts = if (failed) buildContextLogUiState(rows, LogCategory.ALL, settings, nowMillis).alerts else flow.alerts
    return ContextLogHomeUiState(
        loaded = true,
        tab = tabOverride ?: LogHomeTab.fromRaw(rememberedRaw),
        conversation = buildConversationTab(rows, characters, LogTrend.perDay(stats, todayKey, todayKey).single(), nowMillis, zone),
        flow = flow,
        conversationAlerts = conversationAlerts,
        alertKinds = alertKindsOf(rows, conversationAlerts.map { it.source }, nowMillis),
        reason = reason,
        reasonChips = if (failed) countByKind(flow.entries) else emptyList(),
        flowEntries = if (failed && reason != null) flow.entries.filter { kindOf(it) == reason } else flow.entries,
        trend = buildTrend(stats, range, todayKey),
    )
}

private fun kindOf(row: LogListRow): LlmFailureKind? = LlmFailureClassifier.kindOfRow(row.isSuccess, row.failureKind, row.errorMessage)

/** 各类失败条数：条数降序、并列按枚举序。 */
private fun countByKind(rows: List<LogListRow>): List<Pair<LlmFailureKind, Int>> =
    rows.mapNotNull(::kindOf).groupingBy { it }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<LlmFailureKind, Int>> { it.second }.thenBy { it.first.ordinal })

/** 告警来源 → 近 24h 该来源失败里最多的一类（并列取枚举序靠前）。 */
private fun alertKindsOf(rows: List<LogListRow>, sources: List<String>, nowMillis: Long): Map<String, LlmFailureKind> {
    val since = nowMillis - FailureRateAudit.WINDOW_MILLIS
    return sources.mapNotNull { source ->
        countByKind(rows.filter { it.source == source && it.timestampMillis >= since && !it.isSuccess })
            .firstOrNull()?.let { source to it.first }
    }.toMap()
}
