package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.shared.LogContextMapColumn
import com.situ.aichat.ui.contextlog.shared.cutBodyText
import com.situ.aichat.ui.contextlog.shared.mapCaptionText
import com.situ.aichat.ui.contextlog.shared.providerHitMissText
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.logMapColors

/** 省钱入口阈值：命中 < 40%（或服务商没报）且省钱模式关、来源 = 对话时才出（§4.4·锁定值）。 */
internal const val SAVER_NAV_HIT_BELOW = 40

/** 省钱入口显示规则（两张脸共用）。 */
internal fun showSaverNav(state: ContextLogEntryUiState): Boolean {
    val e = state.entry ?: return false
    val rate = state.cacheRatePercent
    return (rate == null || rate < SAVER_NAV_HIT_BELOW) && !state.cacheSaverEnabled && e.source == LogSource.CHAT
}

/** 上下文地图（四期·图纸四 §4.4·暖陶）：说明行 → 地图卡（顶部说明 + 纵列）→ 服务商报告卡（+ 省钱入口）。 */
@Composable
fun ContextLogMapScreen(
    onBack: () -> Unit,
    onOpenSaver: () -> Unit,
    viewModel: ContextLogEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContextLogMapContent(state, onBack, onOpenSaver)
}

@Composable
internal fun ContextLogMapContent(state: ContextLogEntryUiState, onBack: () -> Unit, onOpenSaver: () -> Unit) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = AppTheme.colors.surface.base,
        topBar = { AppTopBar(title = stringResource(R.string.clog_map_title), onBack = onBack, lifted = listState.canScrollBackward) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppSpacing.screenGutter, end = AppSpacing.screenGutter, top = AppSpacing.s, bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGapInGroup),
        ) {
            if (!state.loaded) return@LazyColumn
            val e = state.entry
            if (e == null) {
                item(key = "missing") { MissingText() }
                return@LazyColumn
            }
            val map = state.map
            if (map == null) {
                item(key = "old") { LogCard { Text(stringResource(R.string.clog_map_old), style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary) } }
                return@LazyColumn
            }
            item(key = "caption") {
                Text(mapCaptionText(e, map.totalTokens), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
            }
            item(key = "map") {
                LogCard {
                    val note = map.note
                    if (note == CacheBreakKind.NO_PREVIOUS || note == CacheBreakKind.NO_DATA) {
                        Text(cutBodyText(note, null), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
                    }
                    if (map.segmentsPurged) {
                        Text(stringResource(R.string.clog_map_purged), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
                    }
                    LogContextMapColumn(map, logMapColors())
                }
            }
            item(key = "provider") {
                LogCard(title = stringResource(R.string.clog_provider_title)) {
                    val hitMiss = providerHitMissText(e)
                    if (hitMiss != null) {
                        LogKvRow(stringResource(R.string.clog_provider_hit), hitMiss.first)
                        LogKvRow(stringResource(R.string.clog_provider_miss), hitMiss.second)
                    } else {
                        Text(stringResource(R.string.clog_provider_none), style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary)
                    }
                    if (showSaverNav(state)) {
                        Spacer(Modifier.size(AppSpacing.xs))
                        LogNavRow(stringResource(R.string.clog_saver_nav), onClick = onOpenSaver)
                    }
                }
            }
        }
    }
}
