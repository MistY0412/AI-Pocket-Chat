package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.dao.WorldBookSummary
import com.situ.aichat.data.local.entity.WorldBookEntity
import com.situ.aichat.ui.character.WorldBookCardState
import com.situ.aichat.ui.character.worldBookCardState
import com.situ.aichat.ui.designsystem.AppFeatureIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliTileTone
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRadioRow
import com.situ.aichat.ui.liuli.page.LiuliToggleRow
import com.situ.aichat.ui.liuli.page.LiuliValueRow
import com.situ.aichat.ui.worldbook.WorldBookBindingViewModel
import kotlinx.coroutines.launch

/** 停用书行的淡显（同暖陶 0.55·仍可选）。 */
private const val DISABLED_BOOK_ALPHA = 0.55f

/**
 * 世界观组外壳（§4.11·仅编辑）：照暖陶 `WorldBookBindingSection` 自取 [WorldBookBindingViewModel]；进组刷新「是否已加入世界」，
 * 点入口前直读库守卫（同屏先开加入世界、状态陈旧时也挡住开弹层·复核 R1 🟡-2）。
 */
@Composable
internal fun LiuliWorldBookGroup(onManageBooks: () -> Unit, viewModel: WorldBookBindingViewModel = hiltViewModel()) {
    val boundBooks by viewModel.boundBooks.collectAsStateWithLifecycle()
    val joinedWorld by viewModel.joinedWorld.collectAsStateWithLifecycle()
    val nativeOrigin by viewModel.nativeOrigin.collectAsStateWithLifecycle()
    val selectableBooks by viewModel.selectableBooks.collectAsStateWithLifecycle()
    val globalBooks by viewModel.globalBooks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshJoinedWorld() }
    LiuliWorldBookGroupContent(
        boundBooks = boundBooks,
        joinedWorld = joinedWorld,
        nativeOrigin = nativeOrigin,
        selectableBooks = selectableBooks,
        globalBooks = globalBooks,
        sheetVisible = showSheet && !joinedWorld,
        onOpen = { scope.launch { if (!viewModel.isJoinedWorldNow()) showSheet = true } },
        onDismissSheet = { showSheet = false },
        onSelectSole = viewModel::selectSole,
        onToggle = viewModel::toggle,
        onManageBooks = {
            showSheet = false
            onManageBooks()
        },
    )
}

/** 世界观组内容（无状态·可单测）：五态入口行 + 选择弹层（单选主脸 / 叠加多本）。 */
@Composable
internal fun LiuliWorldBookGroupContent(
    boundBooks: List<WorldBookSummary>,
    joinedWorld: Boolean,
    nativeOrigin: Boolean,
    selectableBooks: List<WorldBookSummary>,
    globalBooks: List<WorldBookEntity>,
    sheetVisible: Boolean,
    onOpen: () -> Unit,
    onDismissSheet: () -> Unit,
    onSelectSole: (String?) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onManageBooks: () -> Unit,
) {
    LiuliGroup(header = stringResource(R.string.wb_binding_section), footer = stringResource(R.string.wb_binding_footer)) {
        when (val card = worldBookCardState(nativeOrigin = nativeOrigin, joinedWorld = joinedWorld, bound = boundBooks)) {
            WorldBookCardState.NativeLocked, WorldBookCardState.WorldLocked -> LiuliValueRow(
                title = stringResource(
                    if (card == WorldBookCardState.NativeLocked) R.string.wb_binding_native_locked else R.string.wb_binding_world_locked,
                ),
                value = "",
                icon = AppFeatureIcons.Worldbook,
                tileColor = LiuliTileTone.Lilac,
                enabled = false,
                divider = false,
            )
            else -> LiuliNavRow(
                title = when (card) {
                    is WorldBookCardState.Single -> card.name
                    is WorldBookCardState.Multi -> stringResource(R.string.wb_binding_multi, card.firstName, card.extra)
                    else -> stringResource(R.string.wb_binding_none)
                },
                onClick = onOpen,
                icon = AppFeatureIcons.Worldbook,
                tileColor = LiuliTileTone.Lilac,
                subtitle = (card as? WorldBookCardState.Single)?.let { stringResource(R.string.wb_book_meta_unbound, it.entryCount) },
                divider = false,
            )
        }
    }
    if (sheetVisible) {
        LiuliWorldBookSheet(boundBooks, selectableBooks, globalBooks, onDismissSheet, onSelectSole, onToggle, onManageBooks)
    }
}

/** 选择弹层（§4.11）：已叠加多本或点了「叠加」→ 多选逐本绑 / 不绑；否则单选（选一本 = 收敛为这一本）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiuliWorldBookSheet(
    boundBooks: List<WorldBookSummary>,
    selectableBooks: List<WorldBookSummary>,
    globalBooks: List<WorldBookEntity>,
    onDismiss: () -> Unit,
    onSelectSole: (String?) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onManageBooks: () -> Unit,
) {
    val colors = AppTheme.colors
    var multiMode by remember { mutableStateOf(false) }
    val effectiveMulti = multiMode || boundBooks.size > 1
    val boundUuids = boundBooks.map { it.book.uuid }.toSet()
    LiuliSheetShell(onDismissRequest = onDismiss, title = stringResource(R.string.wb_binding_sheet_title)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                // 卷五 §11 D-5（复核 R1 核准）：§4.11 用 LiuliRadioRow 做单选；它默认「点已选中项不回调」，暖陶点已选中项照样
                // selectSole + 关弹层 → 传 notifyWhenSelected = true 保持暖陶行为（§11 D-5）。
                if (!effectiveMulti) {
                    LiuliRadioRow(
                        title = stringResource(R.string.wb_binding_not_use),
                        selected = boundUuids.isEmpty(),
                        onSelect = {
                            onSelectSole(null)
                            onDismiss()
                        },
                        divider = false,
                        notifyWhenSelected = true,
                    )
                }
                selectableBooks.forEachIndexed { i, summary ->
                    val disabled = !summary.book.enabled
                    val subtitle = stringResource(R.string.wb_book_meta_unbound, summary.entryCount) +
                        if (disabled) " · ${stringResource(R.string.wb_book_disabled)}" else ""
                    val rowModifier = if (disabled) Modifier.alpha(DISABLED_BOOK_ALPHA) else Modifier
                    val bound = summary.book.uuid in boundUuids
                    if (effectiveMulti) {
                        LiuliToggleRow(
                            title = summary.book.name,
                            checked = bound,
                            onCheckedChange = { onToggle(summary.book.uuid, it) },
                            modifier = rowModifier,
                            subtitle = subtitle,
                            divider = i > 0,
                        )
                    } else {
                        LiuliRadioRow(
                            title = summary.book.name,
                            selected = bound,
                            onSelect = {
                                onSelectSole(summary.book.uuid)
                                onDismiss()
                            },
                            modifier = rowModifier,
                            subtitle = subtitle,
                            notifyWhenSelected = true,
                        )
                    }
                }
            }
            if (globalBooks.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = LiuliPageGeometry.gutter + LiuliPageGeometry.groupPadH, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colors.text.secondary, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.wb_binding_global_note, globalBooks.joinToString("、") { "「${it.name}」" }),
                        style = AppTypography.caption,
                        color = colors.text.secondary,
                    )
                }
            }
            if (!effectiveMulti) {
                LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                    LiuliNavRow(
                        stringResource(R.string.wb_binding_stack),
                        onClick = { multiMode = true },
                        subtitle = stringResource(R.string.wb_binding_stack_sub),
                        divider = false,
                    )
                }
            }
            LiuliButton(onClick = onManageBooks, style = LiuliButtonStyle.Text, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.wb_binding_manage))
            }
        }
    }
}
