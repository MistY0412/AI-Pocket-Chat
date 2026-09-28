package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.contextlog.ContextLogHomeUiState
import com.situ.aichat.ui.contextlog.ContextLogHomeViewModel
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.TrendRange
import com.situ.aichat.ui.contextlog.tabLabel
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmented
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageCircleAction
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed

/**
 * 上下文日志首页（四期·图纸四 §4.1·琉璃）：与暖陶 `ContextLogHomeScreen` 共用 [ContextLogHomeViewModel]，内容 / 顺序 / 文案完全相同，
 * 只换外壳（大标题页 + 纸面分段 + 玻璃菜单 / 弹窗）。
 */
@Composable
fun LiuliContextLogHomeScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContextLogHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiuliContextLogHomeContent(
        state = state,
        onBack = onBack,
        onOpenCharacter = onOpenCharacter,
        onOpenEntry = onOpenEntry,
        onOpenSettings = onOpenSettings,
        onSelectTab = viewModel::selectTab,
        onCategory = viewModel::setCategory,
        onReason = viewModel::setReason,
        onRange = viewModel::setRange,
        onShowFailures = viewModel::showFailures,
        onClearAll = { viewModel.clearAll() },
    )
}

/** 纯参数内容层（可测）。 */
@Composable
internal fun LiuliContextLogHomeContent(
    state: ContextLogHomeUiState,
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTab: (LogHomeTab) -> Unit,
    onCategory: (LogCategory) -> Unit,
    onReason: (LlmFailureKind?) -> Unit,
    onRange: (TrendRange) -> Unit,
    onShowFailures: () -> Unit,
    onClearAll: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_context_log_title)
    val settingsText = stringResource(R.string.clog_menu_settings)
    val clearText = stringResource(R.string.clog_menu_clear)
    val bottomInset = LiuliPageGeometry.pageBottom + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LiuliPage(
        title = title,
        onBack = onBack,
        collapsed = rememberLargeTitleCollapsed(listState),
        actions = {
            Box {
                LiuliPageCircleAction(onClick = { menuOpen = true }, contentDescription = stringResource(R.string.clog_more), icon = Icons.Filled.MoreHoriz)
                LiuliPopupMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    items = listOf(
                        LiuliMenuEntry(settingsText) { menuOpen = false; onOpenSettings() },
                        LiuliMenuEntry(clearText, danger = true) { menuOpen = false; confirmClear = true },
                    ),
                    alignment = Alignment.TopEnd,
                    offset = DpOffset(0.dp, 48.dp),
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = bottomInset),
        ) {
            item(key = "large-title") { LiuliLargeTitle(title) }
            item(key = "home-tabs") {
                LiuliSegmented(
                    options = LogHomeTab.entries.toList(),
                    selected = state.tab,
                    label = { tabLabel(it) },
                    onSelect = onSelectTab,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LiuliPageGeometry.gutter).padding(bottom = AppSpacing.m),
                )
            }
            if (state.loaded) {
                when (state.tab) {
                    LogHomeTab.CONVERSATION -> liuliConversationTabItems(state, onShowFailures, onOpenCharacter)
                    LogHomeTab.FLOW -> liuliFlowTabItems(state, onCategory, onReason, onShowFailures, onOpenEntry)
                    LogHomeTab.TREND -> liuliTrendTabItems(state.trend, onRange)
                }
            }
        }
    }

    if (confirmClear) {
        LiuliDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(R.string.clog_clear_title),
            body = stringResource(R.string.clog_clear_body),
            confirmText = stringResource(R.string.clog_clear_confirm),
            onConfirm = { confirmClear = false; onClearAll() },
            confirmDanger = true,
            dismissText = stringResource(R.string.clog_cancel),
            onDismiss = { confirmClear = false },
        )
    }
}
