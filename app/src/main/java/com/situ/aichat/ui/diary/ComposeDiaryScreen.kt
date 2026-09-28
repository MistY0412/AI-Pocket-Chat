package com.situ.aichat.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.chat.VoiceRecordingOverlay
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppSnackbarHost
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.grainSurface

/**
 * 日记撰写 / 编辑界面（U1 重设计·契约 [FABLE5_DIARY_COMPOSE_REDESIGN_PROPOSAL.md]）：从「表单」改为「一页纸」——
 * 票据日期头 + 心情色回声（M1）/ 无边框纸面书写区 + 每日引导语（M2/M3）/ 心情微提示 + lively 轻弹（M4/刀②）/
 * 「让 TA 帮你起个头」协作 + 生成 breathing（M5/刀③）/ 底部陶土「记下」+ 发布落定仪式（M6/M7·刀④）。
 * 子组件见 [ComposeDiaryComponents]。功能一件不少，VM 行为零改，仅换呈现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDiaryScreen(
    onClose: () -> Unit,
    onNavigateToApiConfig: () -> Unit = {},
    viewModel: ComposeDiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val materialChips by viewModel.materialChips.collectAsStateWithLifecycle()
    // J6 说一段：录音三态（录/电平/计时）+ 取消态 + 转写中。
    val voiceRecording by viewModel.voice.voiceRecording.collectAsStateWithLifecycle()
    val voiceLevel by viewModel.voice.voiceLevel.collectAsStateWithLifecycle()
    val voiceDurationMs by viewModel.voice.voiceDurationMs.collectAsStateWithLifecycle()
    val voiceCancelling by viewModel.voice.voiceCancelling.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.voice.isTranscribing.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val haptics = LocalAppHaptics.current
    val reduceMotion = rememberReduceMotion()
    val page = rememberComposeDiaryPageState(viewModel, onClose, onNavigateToApiConfig)
    val prompt = rememberDiaryDailyPrompt()
    val wash = rememberComposeDiaryWash(state.moodEmoji, fallback = colors.surface.base)

    Scaffold(
        containerColor = colors.surface.base,
        snackbarHost = { AppSnackbarHost(page.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { page.attemptClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = colors.accent.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.base),
            )
        },
        bottomBar = {
            ComposeActionBar(
                canSave = state.canSave,
                hasContent = state.hasContent,
                canAddImage = state.canAddImage,
                isGenerating = state.isGenerating,
                aiAssistAvailable = !state.isExchangeLetter,
                visibility = state.visibility,
                onAddImage = page::pickImages,
                onToggleVisibility = { page.toggleVisibility(state.visibility) },
                onAiAssist = { page.showGuideSheet = true },
                onSaveDraft = page::saveDraft,
                onRecord = { page.record(haptics) },
                onStartVoice = { viewModel.voice.startVoice() },
                onVoiceDrag = { viewModel.voice.updateVoiceDrag(it) },
                onFinishVoice = { viewModel.voice.finishVoice() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // J2 grain 纸感（顺序锁死：background→grain→scroll·此页此前缺席全 App 质感单源）。
                    .background(colors.surface.base)
                    .grainSurface()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ComposeDateHead(timestamp = state.timestamp, moodEmoji = state.moodEmoji, wash = wash, scale = page.headScale.value, reduceMotion = reduceMotion, moodSelectTick = page.moodSelectTick)
                TearLine()
                // 🔵-1：用户点 MoodPill 才 tick++（→邮票盖章+触觉）；load/恢复/AI 回填走 VM 直改 state·tick 不动·静置。
                MoodRow(selectedEmoji = state.moodEmoji, reduceMotion = reduceMotion, onToggle = page::toggleMood)
                // J5「今天的素材」：仅空态且有素材时现（放 MoodRow 与 DiaryPaper 之间）；点击置入起笔句（句尾带换行·走 J1 镜像）。
                if (state.showsMaterialChips(materialChips)) {
                    MaterialChipsRow(chips = materialChips, onPick = page::pickMaterial)
                }
                DiaryPaper(
                    content = state.content,
                    prompt = prompt,
                    isGenerating = state.isGenerating,
                    reduceMotion = reduceMotion,
                    aiAssistAvailable = !state.isExchangeLetter,
                    onContentChange = viewModel::setContent,
                    onAiStart = { page.showGuideSheet = true },
                )
                // J6 转写中：纸面下方一行「正在落笔…」（松手落笔·转写文追加进正文）。
                if (isTranscribing) {
                    DiaryTranscribingPill(reduceMotion = reduceMotion)
                }
                if (state.images.isNotEmpty()) {
                    ImageThumbs(images = state.images, onRemove = viewModel::removeImage)
                }
            }
            // J6 录音浮层：按住录音时挂屏根·浮于动作栏上方（共享件 VoiceRecordingOverlay 零碰只消费）。
            if (voiceRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(AppTheme.shapes.large)
                        .background(colors.surface.raised),
                ) {
                    VoiceRecordingOverlay(level = voiceLevel, durationMs = voiceDurationMs, cancelling = voiceCancelling)
                }
            }
        }
    }

    if (page.showGuideSheet) {
        ThreeQuestionGuideSheet(
            onDismiss = { page.showGuideSheet = false },
            onGenerate = page::generate,
        )
    }

    if (page.showDiscard) {
        AppDialog(
            onDismissRequest = { page.showDiscard = false },
            title = stringResource(R.string.diary_compose_discard_title),
            confirmText = stringResource(R.string.diary_compose_discard_confirm),
            onConfirm = page::discardAndClose,
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.diary_compose_discard_keep),
            onDismiss = { page.showDiscard = false },
        )
    }
}
