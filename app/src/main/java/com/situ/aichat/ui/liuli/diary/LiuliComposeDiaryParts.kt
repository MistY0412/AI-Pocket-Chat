package com.situ.aichat.ui.liuli.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.chat.VoiceRecordingOverlay
import com.situ.aichat.ui.diary.ComposeActionBarItems
import com.situ.aichat.ui.diary.ComposeDiaryState
import com.situ.aichat.ui.diary.canAddImage
import com.situ.aichat.ui.diary.canSave
import com.situ.aichat.ui.diary.hasContent
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliPaperMaterial
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.liuli.page.LiuliFloatingBar
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.theme.LocalIsDarkTheme

// 琉璃写日记的稿纸框 / 底部（录音卡 + 底条）/ 底条内件（琉璃 2.0 卷六·一 §4.4·设计稿 D3）。

/** 纸与底条同宽（左右 12）· 纸底离底条 12 · 纸内距 16 / 顶 16 / 底 24（= 暖陶页底）· 纸内项间 18（= 暖陶）。 */
private val PAPER_INSET = 12.dp
private val PAPER_BAR_GAP = 12.dp
private val PAPER_PAD_H = 16.dp
private val PAPER_PAD_TOP = 16.dp
private val PAPER_PAD_BOTTOM = 24.dp
private val PAPER_ITEM_GAP = 18.dp
/** 录音卡离底条 8。 */
private val VOICE_CARD_GAP = 8.dp
/** 「记下」横内距 16（= 暖陶）。 */
private val RECORD_PAD_H = 16.dp

/**
 * 稿纸框：一块固定在「✕ 之下、底条之上」的纸，内容在纸里滚。纸框随键盘缩——底边 = 导航栏与键盘取大 + 80
 * （底条占 68 + 纸底离条 12），正在写的那行由纸内滚动带进可视区，不会被玻璃底条挡住。
 */
@Composable
internal fun LiuliComposePaper(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = LocalIsDarkTheme.current
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(
                start = PAPER_INSET,
                end = PAPER_INSET,
                top = LiuliPageGeometry.navRow + LiuliPageGeometry.titleGap,
                bottom = LiuliPageGeometry.floatingBarReserve + PAPER_BAR_GAP,
            )
            .liuliPaperMaterial(LiuliShapes.medium, dark),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAPER_PAD_H)
                .padding(top = PAPER_PAD_TOP, bottom = PAPER_PAD_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(PAPER_ITEM_GAP),
            content = content,
        )
    }
}

/** 底部：按住录音时一张玻璃录音卡浮在底条正上方（随底条升降），其下是悬浮底条。 */
@Composable
internal fun LiuliComposeDiaryBottom(
    voiceRecording: Boolean,
    voiceLevel: Float,
    voiceDurationMs: Long,
    voiceCancelling: Boolean,
    bar: @Composable () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    Column(Modifier.fillMaxWidth()) {
        if (voiceRecording) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LiuliPageGeometry.floatingBarInset)
                    .padding(bottom = VOICE_CARD_GAP)
                    .liuliGlass(LiuliShapes.overlay, dark = dark, role = LiuliGlassRole.Panel),
            ) {
                VoiceRecordingOverlay(level = voiceLevel, durationMs = voiceDurationMs, cancelling = voiceCancelling)
            }
        }
        bar()
    }
}

/** 悬浮玻璃底条：左组四钮 · 先存着 · 主色「记下」（内件与暖陶动作条同一份 [ComposeActionBarItems]）。 */
@Composable
internal fun LiuliComposeDiaryBar(
    state: ComposeDiaryState,
    onAddImage: () -> Unit,
    onToggleVisibility: () -> Unit,
    onAiAssist: () -> Unit,
    onSaveDraft: () -> Unit,
    onRecord: () -> Unit,
    onStartVoice: () -> Unit,
    onVoiceDrag: (Float) -> Unit,
    onFinishVoice: () -> Unit,
) {
    LiuliFloatingBar {
        ComposeActionBarItems(
            canSave = state.canSave,
            hasContent = state.hasContent,
            canAddImage = state.canAddImage,
            isGenerating = state.isGenerating,
            aiAssistAvailable = !state.isExchangeLetter,
            visibility = state.visibility,
            onAddImage = onAddImage,
            onToggleVisibility = onToggleVisibility,
            onAiAssist = onAiAssist,
            onSaveDraft = onSaveDraft,
            onStartVoice = onStartVoice,
            onVoiceDrag = onVoiceDrag,
            onFinishVoice = onFinishVoice,
            record = {
                LiuliButton(
                    onClick = onRecord,
                    style = LiuliButtonStyle.Prominent,
                    enabled = state.canSave,
                    contentPadding = PaddingValues(horizontal = RECORD_PAD_H),
                ) { Text(stringResource(R.string.diary_compose_record), maxLines = 1) }
            },
        )
    }
}
