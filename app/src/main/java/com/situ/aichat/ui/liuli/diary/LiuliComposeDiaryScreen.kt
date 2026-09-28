package com.situ.aichat.ui.liuli.diary

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.diary.ComposeDateHead
import com.situ.aichat.ui.diary.ComposeDiaryViewModel
import com.situ.aichat.ui.diary.DiaryPaper
import com.situ.aichat.ui.diary.DiaryTranscribingPill
import com.situ.aichat.ui.diary.ImageThumbs
import com.situ.aichat.ui.diary.MaterialChipsRow
import com.situ.aichat.ui.diary.MoodRow
import com.situ.aichat.ui.diary.TearLine
import com.situ.aichat.ui.diary.rememberComposeDiaryPageState
import com.situ.aichat.ui.diary.rememberComposeDiaryWash
import com.situ.aichat.ui.diary.rememberDiaryDailyPrompt
import com.situ.aichat.ui.diary.showsMaterialChips
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliSnackbarHost
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageCircleAction
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 琉璃写日记（琉璃 2.0 卷六·一 §4.4·设计稿 D3）：与暖陶 [com.situ.aichat.ui.diary.ComposeDiaryScreen] 共用同一个 VM、
 * 同一个页级状态件与全部器物件（日期头 + 邮票 / 撕票线 / 心情行 / 素材芯片 / 横线纸 / 拍立得 / 说一段）；
 * 换成柔光底上一块固定的稿纸框 + 悬浮玻璃底条 + 琉璃弹层 / 对话框。**不加 BackHandler**（系统返回按导航默认·同暖陶）。
 */
@Composable
internal fun LiuliComposeDiaryScreen(
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
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val reduceMotion = rememberReduceMotion()
    val page = rememberComposeDiaryPageState(viewModel, onClose, onNavigateToApiConfig)
    val prompt = rememberDiaryDailyPrompt()
    val wash = rememberComposeDiaryWash(state.moodEmoji, fallback = LiuliMaterials.paperTop(dark))

    LiuliPage(
        // 只在收起胶囊里用·本页恒不收起。
        title = stringResource(R.string.diary_nav_title),
        onBack = page::attemptClose,
        collapsed = false,
        leading = {
            LiuliPageCircleAction(
                onClick = page::attemptClose,
                contentDescription = stringResource(R.string.action_cancel),
                icon = Icons.Filled.Close,
            )
        },
        bottomBar = {
            LiuliComposeDiaryBottom(voiceRecording, voiceLevel, voiceDurationMs, voiceCancelling) {
                LiuliComposeDiaryBar(
                    state = state,
                    onAddImage = page::pickImages,
                    onToggleVisibility = { page.toggleVisibility(state.visibility) },
                    onAiAssist = { page.showGuideSheet = true },
                    onSaveDraft = page::saveDraft,
                    onRecord = { page.record(haptics) },
                    onStartVoice = { viewModel.voice.startVoice() },
                    onVoiceDrag = { viewModel.voice.updateVoiceDrag(it) },
                    onFinishVoice = { viewModel.voice.finishVoice() },
                )
            }
        },
    ) {
        LiuliComposePaper {
            ComposeDateHead(
                timestamp = state.timestamp,
                moodEmoji = state.moodEmoji,
                wash = wash,
                scale = page.headScale.value,
                reduceMotion = reduceMotion,
                moodSelectTick = page.moodSelectTick,
            )
            TearLine(notchColor = LiuliMaterials.paperTop(dark))
            MoodRow(selectedEmoji = state.moodEmoji, reduceMotion = reduceMotion, onToggle = page::toggleMood)
            if (state.showsMaterialChips(materialChips)) MaterialChipsRow(chips = materialChips, onPick = page::pickMaterial)
            DiaryPaper(
                content = state.content,
                prompt = prompt,
                isGenerating = state.isGenerating,
                reduceMotion = reduceMotion,
                aiAssistAvailable = !state.isExchangeLetter,
                onContentChange = viewModel::setContent,
                onAiStart = { page.showGuideSheet = true },
            )
            if (isTranscribing) DiaryTranscribingPill(reduceMotion = reduceMotion)
            if (state.images.isNotEmpty()) ImageThumbs(images = state.images, onRemove = viewModel::removeImage)
        }
        LiuliSnackbarHost(
            page.snackbarHostState,
            Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = LiuliPageGeometry.floatingBarReserve),
        )
    }

    if (page.showGuideSheet) LiuliThreeQuestionGuideSheet(onDismiss = { page.showGuideSheet = false }, onGenerate = page::generate)
    if (page.showDiscard) {
        LiuliDialog(
            onDismissRequest = { page.showDiscard = false },
            title = stringResource(R.string.diary_compose_discard_title),
            confirmText = stringResource(R.string.diary_compose_discard_confirm),
            onConfirm = page::discardAndClose,
            confirmDanger = true,
            dismissText = stringResource(R.string.diary_compose_discard_keep),
            onDismiss = { page.showDiscard = false },
        )
    }
}
