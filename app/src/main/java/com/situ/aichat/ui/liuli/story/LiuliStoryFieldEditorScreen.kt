package com.situ.aichat.ui.liuli.story

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmented
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliSaveBar
import com.situ.aichat.ui.liuli.page.liuliSaveBarInset
import com.situ.aichat.ui.liuli.page.rememberScrollCollapsed
import com.situ.aichat.ui.story.CharCounter
import com.situ.aichat.ui.story.InheritedPreview
import com.situ.aichat.ui.story.StoryFieldEditorInvalidEffect
import com.situ.aichat.ui.story.StoryFieldEditorState
import com.situ.aichat.ui.story.StoryFieldEditorViewModel
import com.situ.aichat.ui.story.StoryFieldMode
import com.situ.aichat.ui.story.storyFieldEditorNeedsConfirm
import com.situ.aichat.ui.story.storyFieldEditorSubtitle
import com.situ.aichat.ui.story.storyFieldModeLabelRes
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

/** 正文区最小高 220 · 项缝 12（= 暖陶）。 */
private val FIELD_MIN = 220.dp
private val FIELD_GAP = 12.dp

/**
 * 琉璃字段编辑（琉璃 2.0 卷六·三·上 §4.8）：与暖陶 [com.situ.aichat.ui.story.StoryFieldEditorScreen] 共用同一个 VM、
 * 非法键退出 / 返回判据 / 副标题 / 三态文案与继承预览、计数内容件；大标题 = 字段名随滚收起，
 * 底部玻璃保存栏跟键盘（左「恢复默认」有出厂值才出、右「保存」）。有改动返回先问（BackHandler 同暖陶）。
 */
@Composable
internal fun LiuliStoryFieldEditorScreen(
    onBack: () -> Unit,
    viewModel: StoryFieldEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    StoryFieldEditorInvalidEffect(viewModel.invalid, onBack)

    fun leave() {
        if (storyFieldEditorNeedsConfirm(state)) confirmDiscard = true else onBack()
    }
    BackHandler { leave() }

    LiuliStoryFieldEditorPage(
        title = stringResource(viewModel.titleRes),
        state = state,
        saving = saving,
        onLeave = { leave() }, // lambda 不用 ::leave（PITFALLS §1d 捕获过期·复核 R1 🔵-2）
        onSetMode = viewModel::setMode,
        onSetText = viewModel::setText,
        onApplyPreset = viewModel::applyPreset,
        onRequestRestore = { confirmRestore = true },
        onSave = { scope.launch { if (viewModel.save()) onBack() } },
    )

    if (confirmDiscard) {
        LiuliDialog(
            onDismissRequest = { confirmDiscard = false }, title = stringResource(R.string.story_field_discard_title),
            confirmText = stringResource(R.string.story_field_discard_yes), onConfirm = { confirmDiscard = false; onBack() }, confirmDanger = true,
            dismissText = stringResource(R.string.story_field_discard_no), onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmRestore) {
        LiuliDialog(
            onDismissRequest = { confirmRestore = false }, title = stringResource(R.string.story_field_restore_title),
            body = stringResource(R.string.story_field_restore_body),
            confirmText = stringResource(R.string.story_field_restore_default), onConfirm = { confirmRestore = false; viewModel.restoreDefault() }, confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel), onDismiss = { confirmRestore = false },
        )
    }
    error?.let { LiuliStoryAlert(stringResource(R.string.story_settings_save_failed), it, viewModel::dismissError) }
}

/** 无 VM 的字段编辑页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryFieldEditorPage(
    title: String,
    state: StoryFieldEditorState?,
    saving: Boolean,
    onLeave: () -> Unit,
    onSetMode: (StoryFieldMode) -> Unit,
    onSetText: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onRequestRestore: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val dark = LocalIsDarkTheme.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val saveBarInset = liuliSaveBarInset
    LiuliPage(
        title,
        onBack = onLeave,
        collapsed = rememberScrollCollapsed(scrollState),
        bottomBar = if (state != null) {
            {
                // 跟键盘：纯文本编辑页，把保存藏到键盘后面会退化功能（同暖陶底部行在键盘之上·§0.2-10）。
                LiuliSaveBar(Modifier.imePadding()) {
                    if (state.factoryDefault != null) {
                        LiuliButton(onRequestRestore, style = LiuliButtonStyle.Glass) { Text(stringResource(R.string.story_field_restore_default)) }
                    }
                    Spacer(Modifier.weight(1f))
                    LiuliButton(onSave, style = LiuliButtonStyle.Prominent, enabled = !saving) { Text(stringResource(R.string.action_save)) }
                }
            }
        } else {
            null
        },
    ) {
        val s = state ?: return@LiuliPage
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .contentMaxWidth()
                .padding(top = LiuliPageGeometry.navRow, bottom = navBarBottom + saveBarInset + LiuliPageGeometry.pageBottom),
        ) {
            LiuliLargeTitle(title)
            Column(
                Modifier.padding(horizontal = LiuliPageGeometry.gutter).padding(top = LiuliPageGeometry.titleGap),
                verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
            ) {
                storyFieldEditorSubtitle(s)?.let {
                    Text(it, style = AppTypography.settingsRowSubtitle, color = AppTheme.colors.text.secondary)
                }
                if (s.showModeSegment) {
                    LiuliSegmented(
                        options = listOf(StoryFieldMode.FOLLOW, StoryFieldMode.CUSTOM, StoryFieldMode.OFF),
                        selected = s.mode,
                        label = { stringResource(storyFieldModeLabelRes(it)) },
                        onSelect = onSetMode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    s.mode == StoryFieldMode.FOLLOW -> InheritedPreview(s.inheritedText, surface = Modifier.liuliStoryNote(dark))
                    s.mode == StoryFieldMode.OFF -> Text(
                        stringResource(R.string.story_field_off_hint),
                        style = AppTypography.secondary,
                        color = AppTheme.colors.text.tertiary,
                    )
                    else -> {
                        if (s.showPresetChips) LiuliStoryPresetChips(R.string.story_field_chips_hint, onApplyPreset)
                        LiuliField(value = s.text, onValueChange = onSetText, singleLine = false, minHeight = FIELD_MIN, modifier = Modifier.fillMaxWidth())
                        s.maxChars?.let { max -> CharCounter(s.text.length, max) }
                        if (s.isArchive) {
                            Text(
                                stringResource(R.string.story_field_archive_note),
                                style = AppTypography.caption.copy(fontSize = 10.5.sp),
                                color = AppTheme.colors.text.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}
