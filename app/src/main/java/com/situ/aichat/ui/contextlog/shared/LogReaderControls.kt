package com.situ.aichat.ui.contextlog.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.contextlog.ContextLogReaderUiState
import com.situ.aichat.ui.contextlog.model.LogReaderControlsState
import com.situ.aichat.ui.contextlog.model.LogReaderFilter
import com.situ.aichat.ui.contextlog.model.LogReaderItem
import com.situ.aichat.ui.contextlog.model.LogReaderSearch
import com.situ.aichat.ui.contextlog.model.LogReaderSource
import com.situ.aichat.ui.contextlog.model.LogReaderView
import com.situ.aichat.ui.contextlog.model.readerNav
import com.situ.aichat.ui.contextlog.model.readerRowOf

/**
 * 阅读器页面开关（四期·图纸五 §3.10 锁定）：筛选 / 显示全部 / 展开的消息 / 翻转过的目录节 / 搜索态 / 关键词 / 只看有命中 / 当前处。
 * 每个字段一个可保存状态（进程死亡后恢复）；筛选以枚举名存。
 */
@Stable
class LogReaderControls internal constructor(
    private val filterName: MutableState<String>,
    showAllState: MutableState<Boolean>,
    expandedState: MutableState<List<Int>>,
    sectionsState: MutableState<List<Int>>,
    searchingState: MutableState<Boolean>,
    queryState: MutableState<String>,
    onlyHitsState: MutableState<Boolean>,
    currentState: MutableState<Int>,
) {
    var filter: LogReaderFilter
        get() = LogReaderFilter.valueOf(filterName.value)
        set(value) { filterName.value = value.name }
    var showAll by showAllState
    var expanded by expandedState
    var sectionsToggled by sectionsState
    var searching by searchingState
    var query by queryState
    var onlyHits by onlyHitsState
    var current by currentState

    fun enterSearch() {
        searching = true
    }

    fun exitSearch() {
        searching = false
        query = ""
        current = 0
    }

    /**
     * 页面真正在用的搜索结果：只在搜索态且关键词非空白时算数。VM 的结果要晚几帧才跟着清空——退出搜索那几帧里
     * 不许再拿旧结果高亮（§4.6「不在搜 → 原样文字」）、也不许按旧结果把列表拽去第一处（复核 R1 🟡-1）。
     */
    fun activeSearch(search: LogReaderSearch?): LogReaderSearch? = search.takeIf { searching && query.isNotBlank() }

    /** 交给纯逻辑的快照；搜索结果经 [activeSearch]。 */
    fun snapshot(search: LogReaderSearch?): LogReaderControlsState = LogReaderControlsState(
        filter = filter, showAll = showAll, expanded = expanded.toSet(), sectionsToggled = sectionsToggled.toSet(),
        search = activeSearch(search), onlyHits = onlyHits,
    )
}

@Composable
fun rememberLogReaderControls(): LogReaderControls {
    val filter = rememberSaveable { mutableStateOf(LogReaderFilter.ALL.name) }
    val showAll = rememberSaveable { mutableStateOf(false) }
    val expanded = rememberSaveable { mutableStateOf(listOf<Int>()) }
    val sections = rememberSaveable { mutableStateOf(listOf<Int>()) }
    val searching = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    val onlyHits = rememberSaveable { mutableStateOf(true) }
    val current = rememberSaveable { mutableIntStateOf(0) }
    return LogReaderControls(filter, showAll, expanded, sections, searching, query, onlyHits, current)
}

/** 列表前的固定项（两张脸按此顺序出·key = 枚举名小写）。 */
enum class LogReaderFixed { CAPTION, OVERVIEW, CHIPS, ONLY_HITS, ADAPTATION, FALLBACK, DETAIL_OFF, SEARCH_EMPTY }

/** 固定项清单（§3.10 锁定）：记录不存在 → 无；详细关 → 说明 / 改写说明 / 详细关提示；普通态 / 搜索态各一套。 */
fun readerFixedItems(state: ContextLogReaderUiState, c: LogReaderControls, rowsEmpty: Boolean): List<LogReaderFixed> {
    if (state.entry == null) return emptyList()
    val view = state.view ?: return listOf(LogReaderFixed.CAPTION, LogReaderFixed.ADAPTATION, LogReaderFixed.DETAIL_OFF)
    if (!c.searching) {
        return listOf(
            LogReaderFixed.CAPTION, LogReaderFixed.OVERVIEW, LogReaderFixed.CHIPS,
            if (view.source == LogReaderSource.REQUEST) LogReaderFixed.ADAPTATION else LogReaderFixed.FALLBACK,
        )
    }
    if (c.query.isBlank()) return listOf(LogReaderFixed.CHIPS)
    return listOf(LogReaderFixed.CHIPS, LogReaderFixed.ONLY_HITS) + if (rowsEmpty && c.onlyHits) listOf(LogReaderFixed.SEARCH_EMPTY) else emptyList()
}

/** 滚到第 [index] 项：开「减少动态效果」时瞬移，否则列表自带的平滑滚动（§3.10 第 5 条）。 */
suspend fun LazyListState.readerScrollTo(index: Int, reduceMotion: Boolean) {
    if (reduceMotion) scrollToItem(index) else animateScrollToItem(index)
}

/**
 * 搜索联动（§3.10 锁定）：① 关键词推给 VM（进程死亡恢复后也由这里推回）；② 结果或筛选一变当前处归 0；
 * ③ 只在命中列表或当前处变化时滚到那一处（搜索态里点展开 / 收起不把列表拽回）；④ 返回键只在搜索态被拦下（退出搜索）。
 * [rowStart] = 列表里第一条消息行之前的项数。
 */
@Composable
fun LogReaderEffects(
    c: LogReaderControls, view: LogReaderView?, search: LogReaderSearch?, items: List<LogReaderItem>,
    rowStart: Int, listState: LazyListState, onQueryChange: (String) -> Unit,
) {
    LaunchedEffect(c.searching, c.query) { onQueryChange(if (c.searching) c.query else "") }
    val active = c.activeSearch(search)
    LaunchedEffect(active, c.filter) { c.current = 0 }
    val nav = readerNav(active, view, c.filter)
    val latestItems by rememberUpdatedState(items)
    val latestStart by rememberUpdatedState(rowStart)
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(nav, c.current) {
        nav.getOrNull(c.current)?.let { hit ->
            readerRowOf(latestItems, hit).takeIf { it >= 0 }?.let { listState.readerScrollTo(latestStart + it, reduceMotion) }
        }
    }
    BackHandler(enabled = c.searching) { c.exitSearch() }
}
