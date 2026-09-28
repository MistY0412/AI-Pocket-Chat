package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.ui.contextlog.ContextLogEntryUiState
import com.situ.aichat.ui.contextlog.ContextLogEntryViewModel
import com.situ.aichat.ui.contextlog.shared.LogContextMapColumn
import com.situ.aichat.ui.contextlog.shared.cutBodyText
import com.situ.aichat.ui.contextlog.shared.mapCaptionText
import com.situ.aichat.ui.contextlog.shared.providerHitMissText
import com.situ.aichat.ui.contextlog.showSaverNav
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.logMapColors
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry

/** 上下文地图（四期·图纸四 §4.4·琉璃）：内容同暖陶——说明行 → 地图组（顶部说明 + 纵列）→ 服务商报告组（+ 省钱入口·同一条规则）。 */
@Composable
fun LiuliContextLogMapScreen(
    onBack: () -> Unit,
    onOpenSaver: () -> Unit,
    viewModel: ContextLogEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiuliContextLogMapContent(state, onBack, onOpenSaver)
}

@Composable
internal fun LiuliContextLogMapContent(state: ContextLogEntryUiState, onBack: () -> Unit, onOpenSaver: () -> Unit) {
    LiuliLogPage(stringResource(R.string.clog_map_title), onBack) {
        if (!state.loaded) return@LiuliLogPage
        val e = state.entry
        if (e == null) {
            item(key = "missing") { LiuliLogEmpty(stringResource(R.string.clog_entry_missing)) }
            return@LiuliLogPage
        }
        val map = state.map
        if (map == null) {
            item(key = "old") {
                LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                    LiuliLogFreeRow(first = true) { Text(stringResource(R.string.clog_map_old), style = AppTypography.secondary, color = AppTheme.colors.text.secondary) }
                }
            }
            return@LiuliLogPage
        }
        item(key = "caption") { LiuliLogCaption(mapCaptionText(e, map.totalTokens)) }
        item(key = "map") {
            LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                LiuliLogFreeRow(first = true) {
                    Column {
                        val note = map.note
                        if (note == CacheBreakKind.NO_PREVIOUS || note == CacheBreakKind.NO_DATA) {
                            Text(cutBodyText(note, null), style = AppTypography.caption, color = AppTheme.colors.text.secondary)
                        }
                        if (map.segmentsPurged) {
                            Text(stringResource(R.string.clog_map_purged), style = AppTypography.caption, color = AppTheme.colors.text.secondary)
                        }
                        LogContextMapColumn(map, logMapColors())
                    }
                }
            }
        }
        item(key = "provider") {
            LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter), header = stringResource(R.string.clog_provider_title)) {
                val hitMiss = providerHitMissText(e)
                if (hitMiss != null) {
                    LiuliLogKvRow(stringResource(R.string.clog_provider_hit), hitMiss.first, divider = false)
                    LiuliLogKvRow(stringResource(R.string.clog_provider_miss), hitMiss.second)
                } else {
                    LiuliLogFreeRow(first = true) { Text(stringResource(R.string.clog_provider_none), style = AppTypography.secondary, color = AppTheme.colors.text.secondary) }
                }
                if (showSaverNav(state)) LiuliNavRow(stringResource(R.string.clog_saver_nav), onClick = onOpenSaver)
            }
        }
    }
}
