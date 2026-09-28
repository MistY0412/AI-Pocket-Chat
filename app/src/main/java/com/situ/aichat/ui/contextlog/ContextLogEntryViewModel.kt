package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.repository.ConversationRepository
import com.situ.aichat.data.repository.MessageRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogAnalysisReader
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogReplayRequest
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.diagnostics.LogToolInfo
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.ui.contextlog.model.LogContextMap
import com.situ.aichat.ui.contextlog.model.LogFailureView
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogKid
import com.situ.aichat.ui.contextlog.model.LogMiniMapModel
import com.situ.aichat.ui.contextlog.model.LogTurnQuote
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import com.situ.aichat.ui.contextlog.model.MAIN_SOURCES
import com.situ.aichat.ui.contextlog.model.buildContextMap
import com.situ.aichat.ui.contextlog.model.buildFailureView
import com.situ.aichat.ui.contextlog.model.buildMiniMap
import com.situ.aichat.ui.contextlog.model.segmentsPurgedOf
import com.situ.aichat.ui.contextlog.model.turnAnchorOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.time.ZoneId
import javax.inject.Inject

/** 条目页状态（四期·图纸四 §3.4·一轮详情 / 地图 / 失败 / 全文共用；实际发送页已换阅读器 VM·图纸五）。 */
data class ContextLogEntryUiState(
    val loaded: Boolean = false,
    val entry: LogEntryEntity? = null,
    /** turnId 非空且来源 ∈ MAIN_SOURCES。 */
    val isTurn: Boolean = false,
    val conversationTitle: String? = null,
    val quote: LogTurnQuoteText? = null,
    /** 服务商显示名；null = 旧记录。 */
    val providerLabel: String? = null,
    val cacheRatePercent: Int? = null,
    val comparison: CacheComparison? = null,
    /** shapeJson 空 → null。 */
    val map: LogContextMap? = null,
    val miniMap: LogMiniMapModel? = null,
    /** sendAdaptationJson 空 → null。 */
    val adaptation: LogSendAdaptation? = null,
    val kids: List<LogKid> = emptyList(),
    val toolInfo: LogToolInfo? = null,
    /** 成功 → null。 */
    val failure: LogFailureView? = null,
    val detailEnabled: Boolean = false,
    val cacheSaverEnabled: Boolean = false,
    /** requestJson 非空且导得出（坏 JSON 导不出）。 */
    val canExportReplay: Boolean = false,
)

/**
 * 条目页 VM（四期·图纸四 §3.4·取代旧 `ContextLogDetailViewModel`）：路由带 id，一次性读（不随新日志刷新·§3.11）。
 */
@HiltViewModel
class ContextLogEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val logDao: LogDao,
    private val analysisReader: LogAnalysisReader,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<String>(ARG_ID)?.toLongOrNull() ?: -1L

    // 复核 R1：装配里要解码请求体 / 逆解析全文 / 试导出（单条可达 20 万字），不能在主线程做——整条流挪到 Default。
    val state: StateFlow<ContextLogEntryUiState> =
        flow { emit(load()) }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextLogEntryUiState())

    /** 导出文本与文件名（`requestJson` 空或坏 → null）。在 Dispatchers.Default 调。 */
    fun replayExport(entry: LogEntryEntity): Pair<String, String>? =
        LogReplayRequest.exportText(entry, System.currentTimeMillis())?.let { it to LogReplayRequest.fileName(entry) }

    private suspend fun load(): ContextLogEntryUiState {
        val entry = logDao.getById(entryId) ?: return ContextLogEntryUiState(loaded = true)
        val comparison = analysisReader.compareWithPrevious(entry)
        val rows = logDao.recent(ContextLogViewModel.RECENT_LIMIT).first()
        val title = entry.conversationUuid?.let { conversationRepository.get(it)?.title }
        val turnRows = entry.turnId?.let { t -> rows.filter { it.turnId == t } }.orEmpty()
        val mains = turnRows.filter { it.source in MAIN_SOURCES }.sortedWith(ROW_ORDER)
        val anchor = turnAnchorOf(mains) ?: entry.anchorMessageUuid
        val firstMainMillis = mains.firstOrNull()?.timestampMillis ?: entry.timestampMillis
        val anchors = listOfNotNull(anchor) + turnRows.filter { it.source == LogSource.IMAGE_UNDERSTANDING }.mapNotNull { it.anchorMessageUuid }
        val maxMillis = (turnRows.map { it.timestampMillis } + entry.timestampMillis).max()
        val briefs = readLogBriefs(messageRepository, anchors.distinct(), entry.conversationUuid, maxMillis + BRIEF_TAIL_MILLIS)
        val userMessages = anchor?.let { LogTurnQuote.turnUserMessages(briefs, it, firstMainMillis) }.orEmpty()
        val settings = settingsRepository.appSettings.first()
        return buildEntryUiState(
            entry = entry,
            comparison = comparison,
            shape = analysisReader.shapeOf(entry),
            segments = analysisReader.segmentsOf(entry.contextSegmentsJson),
            adaptation = analysisReader.adaptationOf(entry),
            toolInfo = LogToolInfo.decode(json, entry.toolInfoJson),
            conversationTitle = title,
            quote = LogTurnQuote.quoteOf(userMessages),
            kids = turnKidsOf(
                entry, turnRows, rows, userMessages.map { it.messageUUID }.toSet(),
                earlierSameAnchorFirstMains = earlierSameAnchorFirstMains(entry, rows, anchor, firstMainMillis),
                messageTimes = briefs.associate { it.messageUUID to it.timestamp },
            ),
            rows = rows,
            detailEnabled = settings.logDetailEnabled,
            cacheSaverEnabled = settings.cacheSaverEnabled,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )
    }

    companion object {
        const val ARG_ID = "id"
    }
}

private val ROW_ORDER = compareBy<LogListRow>({ it.timestampMillis }, { it.id })

/**
 * 同会话、与本轮同一锚点、比本轮更早开始的其它轮的第一条主调用时刻（连着失败的几轮共用锚点·复核 R1）：
 * 它们的用户消息集与本轮重叠，一张图若也在它们的集里，就归最早那轮（与角色页「挂进最早带上这张图的那一轮」同口径）。
 */
internal fun earlierSameAnchorFirstMains(entry: LogEntryEntity, rows: List<LogListRow>, anchor: String?, firstMainMillis: Long): List<Long> {
    if (anchor == null || entry.turnId == null) return emptyList()
    return rows.filter { it.turnId != null && it.turnId != entry.turnId && it.source in MAIN_SOURCES && it.conversationUuid == entry.conversationUuid }
        .groupBy { it.turnId }.values
        .map { it.sortedWith(ROW_ORDER) }
        .filter { turnAnchorOf(it) == anchor && it.first().timestampMillis < firstMainMillis }
        .map { it.first().timestampMillis }
}

/**
 * 一轮详情「这一轮带出的后台调用」（与角色页轮卡的子项同一口径·§3.3 第 5–6 步）：同轮非主调用行 + 没有 turnId、锚点落在
 * 本轮用户消息里的图片理解行，按 (时刻, id) 升序。非一轮（后台调用自己被点开）→ 空表。
 * 图片那张消息若在更早的同锚点轮开始前就发了（[messageTimes] 里它的时刻 ≤ 那轮第一条主调用），它归那一轮、这里不挂。
 */
internal fun turnKidsOf(
    entry: LogEntryEntity,
    turnRows: List<LogListRow>,
    rows: List<LogListRow>,
    userMessageUuids: Set<String>,
    earlierSameAnchorFirstMains: List<Long> = emptyList(),
    messageTimes: Map<String, Long> = emptyMap(),
): List<LogKid> {
    if (entry.turnId == null || entry.source !in MAIN_SOURCES) return emptyList()
    val sameTurn = turnRows.filter { it.source !in MAIN_SOURCES }
    val images = rows.filter {
        it.turnId == null && it.source == LogSource.IMAGE_UNDERSTANDING && it.conversationUuid == entry.conversationUuid &&
            it.anchorMessageUuid != null && it.anchorMessageUuid in userMessageUuids &&
            messageTimes[it.anchorMessageUuid].let { t -> t == null || earlierSameAnchorFirstMains.none { first -> t <= first } }
    }
    return (sameTurn + images).sortedWith(ROW_ORDER)
        .map { LogKid(it.id, it.source, it.timestampMillis, it.promptTokens + it.completionTokens, it.isTokenEstimated, !it.isSuccess) }
}

/** 条目页纯装配（§3.4 表）：地图 / 迷你地图 / 失败 / 导出可用性（实际发送 / 改写前已归阅读器·图纸五）。 */
internal fun buildEntryUiState(
    entry: LogEntryEntity,
    comparison: CacheComparison,
    shape: LogRequestShape?,
    segments: List<ContextSegment>,
    adaptation: LogSendAdaptation?,
    toolInfo: LogToolInfo?,
    conversationTitle: String?,
    quote: LogTurnQuoteText?,
    kids: List<LogKid>,
    rows: List<LogListRow>,
    detailEnabled: Boolean,
    cacheSaverEnabled: Boolean,
    nowMillis: Long,
    zone: ZoneId,
): ContextLogEntryUiState {
    val segmentsPurged = segmentsPurgedOf(entry)
    val map = shape?.let { buildContextMap(it, segments, comparison, segmentsPurged) }
    return ContextLogEntryUiState(
        loaded = true,
        entry = entry,
        isTurn = entry.turnId != null && entry.source in MAIN_SOURCES,
        conversationTitle = conversationTitle,
        quote = quote,
        providerLabel = entry.providerType?.let(LogFormat::providerName),
        cacheRatePercent = LogFormat.cacheRate(entry.cacheHitTokens.toLong(), entry.cacheMissTokens.toLong()),
        comparison = comparison,
        map = map,
        miniMap = map?.let(::buildMiniMap),
        adaptation = adaptation,
        kids = kids,
        toolInfo = toolInfo,
        failure = buildFailureView(entry, rows, nowMillis, zone),
        detailEnabled = detailEnabled,
        cacheSaverEnabled = cacheSaverEnabled,
        canExportReplay = entry.requestJson.isNotEmpty() && LogReplayRequest.exportText(entry, 0L) != null,
    )
}
