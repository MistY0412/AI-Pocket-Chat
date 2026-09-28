package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.TrendRange
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppMenu
import com.situ.aichat.ui.designsystem.AppMenuItem
import com.situ.aichat.ui.designsystem.AppSegmentedControl
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.AppTopBarAction
import com.situ.aichat.ui.designsystem.AppTopBarIcons

/**
 * 上下文日志首页（四期·图纸四 §4.1·暖陶）：三分段（按对话 / 全部流水 / 趋势·记住上次停留）+ 「⋯」菜单（保留设置 / 清空全部）。
 * 取代原 `ContextLogListScreen`（其列表原样并进「全部流水」）。
 */
@Composable
fun ContextLogHomeScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContextLogHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContextLogHomeContent(
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

/** 纯参数内容层（测试直接喂状态·Robolectric 下 hiltViewModel 掐死整屏）。 */
@Composable
internal fun ContextLogHomeContent(
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = AppTheme.colors.surface.base,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_context_log_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
                actions = {
                    Box {
                        AppTopBarAction(AppTopBarIcons.More, stringResource(R.string.clog_more), onClick = { menuOpen = true })
                        AppMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            AppMenuItem(stringResource(R.string.clog_menu_settings), onClick = { menuOpen = false; onOpenSettings() })
                            AppMenuItem(stringResource(R.string.clog_menu_clear), onClick = { menuOpen = false; confirmClear = true }, danger = true)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppSpacing.screenGutter, end = AppSpacing.screenGutter, top = AppSpacing.s, bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGapInGroup),
        ) {
            item(key = "home-tabs") {
                AppSegmentedControl(
                    options = LogHomeTab.entries.toList(),
                    selected = state.tab,
                    onSelect = onSelectTab,
                    modifier = Modifier.fillMaxWidth(),
                ) { tabLabel(it) }
            }
            if (state.loaded) {
                when (state.tab) {
                    LogHomeTab.CONVERSATION -> conversationTabItems(state, onShowFailures, onOpenCharacter)
                    LogHomeTab.FLOW -> flowTabItems(state, onCategory, onReason, onShowFailures, onOpenEntry)
                    LogHomeTab.TREND -> trendTabItems(state.trend, onRange)
                }
            }
        }
    }

    if (confirmClear) {
        AppDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(R.string.clog_clear_title),
            body = stringResource(R.string.clog_clear_body),
            confirmText = stringResource(R.string.clog_clear_confirm),
            onConfirm = { confirmClear = false; onClearAll() },
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.clog_cancel),
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
internal fun tabLabel(tab: LogHomeTab): String = stringResource(
    when (tab) {
        LogHomeTab.CONVERSATION -> R.string.clog_tab_conversation
        LogHomeTab.FLOW -> R.string.clog_tab_flow
        LogHomeTab.TREND -> R.string.clog_tab_trend
    },
)
