package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogAnalysisReader
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogMapCut
import com.situ.aichat.ui.contextlog.model.LogReaderSearch
import com.situ.aichat.ui.contextlog.model.LogReaderSource
import com.situ.aichat.ui.contextlog.model.LogReaderView
import com.situ.aichat.ui.contextlog.model.buildContextMap
import com.situ.aichat.ui.contextlog.model.buildReaderView
import com.situ.aichat.ui.contextlog.model.searchReader
import com.situ.aichat.ui.contextlog.model.segmentsPurgedOf
import com.situ.aichat.ui.contextlog.model.sentFromRendered
import com.situ.aichat.ui.contextlog.model.sentFromRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 上下文阅读器状态（四期·图纸五 §3.1）。 */
data class ContextLogReaderUiState(
    val loaded: Boolean = false,
    val entry: LogEntryEntity? = null,
    /** 服务商显示名；null = 旧记录。 */
    val providerLabel: String? = null,
    /** sendAdaptationJson 空 → null。 */
    val adaptation: LogSendAdaptation? = null,
    /** null = 实际请求与改写前全文都没存（详细记录关）或都解不出。 */
    val view: LogReaderView? = null,
    /** 形状 token 之和（详细关提示「约 N tk」用）；没有形状 → null。 */
    val shapeTokens: Int? = null,
)

/**
 * 上下文阅读器 VM（四期·图纸五 §3.1·只服务 `contextLog/sent/{id}`）：路由带 id，一次性读；请求解码 / 目录切分 / 下标对照 / 分块
 * 与搜索都在 Dispatchers.Default（单条可达 20 万字）。条目页 VM 不背这些——一轮详情 / 地图 / 失败页用不上。
 */
@HiltViewModel
class ContextLogReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val logDao: LogDao,
    private val analysisReader: LogAnalysisReader,
    private val json: Json,
) : ViewModel() {
    private val entryId: Long = savedStateHandle.get<String>(ContextLogEntryViewModel.ARG_ID)?.toLongOrNull() ?: -1L
    private val query = MutableStateFlow("")

    val state: StateFlow<ContextLogReaderUiState> =
        flow { emit(load()) }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextLogReaderUiState())

    /** 当前搜索结果；没在搜 / 关键词去首尾空白后为空 / 没有视图 → null。 */
    val search: StateFlow<LogReaderSearch?> =
        combine(state, query) { s, q -> s.view?.let { v -> q.trim().takeIf { it.isNotEmpty() }?.let { searchReader(v, it) } } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(text: String) { query.value = text }

    private suspend fun load(): ContextLogReaderUiState {
        val entry = logDao.getById(entryId) ?: return ContextLogReaderUiState(loaded = true)
        return buildReaderUiState(
            entry = entry,
            comparison = analysisReader.compareWithPrevious(entry),
            shape = analysisReader.shapeOf(entry),
            segments = analysisReader.segmentsOf(entry.contextSegmentsJson),
            adaptation = analysisReader.adaptationOf(entry),
            json = json,
        )
    }
}

/**
 * 阅读器纯装配（§3.1 按序 1–5）：红框文案的唯一出处 = 地图；有实际请求就读它，否则退到 App 发送前拼好的全文（兜底视图），
 * 两样都没有 → view = null（详细记录关）。
 */
internal fun buildReaderUiState(
    entry: LogEntryEntity,
    comparison: CacheComparison,
    shape: LogRequestShape?,
    segments: List<ContextSegment>,
    adaptation: LogSendAdaptation?,
    json: Json,
): ContextLogReaderUiState {
    // 1.
    val map = shape?.let { buildContextMap(it, segments, comparison, segmentsPurgedOf(entry)) }
    val frame = map?.items?.filterIsInstance<LogMapCut>()?.firstOrNull()
    // 2. 有请求体却没有改写记录（理论上同时写入）时按原样发送解读（原图纸四 §11 TODO 口径·图纸五 E40 已明文覆盖）。
    val sent = entry.requestJson.takeIf { it.isNotEmpty() }?.let { sentFromRequest(json, it, asIs = adaptation?.asIs ?: true) }
    // 3.
    val (base, source) = when {
        sent != null -> sent to LogReaderSource.REQUEST
        entry.fullContext.isNotEmpty() -> sentFromRendered(entry.fullContext) to LogReaderSource.RENDERED
        else -> null to LogReaderSource.RENDERED
    }
    // 4.
    val view = base?.let {
        buildReaderView(
            it, source, shape, segments, comparison, frame, adaptation,
            truncated = source == LogReaderSource.RENDERED && entry.fullContext.contains(LEGACY_CLIP_MARKER),
        )
    }
    // 5.
    return ContextLogReaderUiState(
        loaded = true,
        entry = entry,
        providerLabel = entry.providerType?.let(LogFormat::providerName),
        adaptation = adaptation,
        view = view,
        shapeTokens = shape?.tokens?.sum(),
    )
}
