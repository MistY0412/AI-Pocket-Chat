package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.Composable
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.story.ReaderDialog
import com.situ.aichat.ui.story.StoryReaderSheetFace

// 琉璃阅读器弹层与对话框的脸（琉璃 2.0 卷六·三·下乙 §4.1）：由 LiuliStoryReaderScreen 经 StoryReaderSheets(face = …) 接入，
// 放在玻璃宿主之外（与上半卷书页等同位·§0.2-4）。接线（写库回调 / 哨兵过滤 / 两步流转）全在共用接线层。

/** 琉璃脸：五个方法各调琉璃件（接线在 StoryReaderSheets，这里只画）。 */
internal object LiuliStoryReaderSheetFace : StoryReaderSheetFace {
    @Composable
    override fun CustomChoiceSheet(prompt: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
        LiuliStoryCustomChoiceSheet(prompt, hint, onConfirm, onDismiss)
    }

    @Composable
    override fun DirectorSheet(
        beats: String?,
        beatsUserEdited: Boolean,
        savedDirection: String?,
        directionCommitted: Boolean,
        onSubmitFlow: (String) -> Unit,
        onOverwriteDirection: (String) -> Unit,
        onWithdrawDirection: () -> Unit,
        onSaveBeats: (String) -> Unit,
        onRestoreAiBeats: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        LiuliStoryDirectorSheet(
            beats = beats, beatsUserEdited = beatsUserEdited, savedDirection = savedDirection, directionCommitted = directionCommitted,
            onSubmitFlow = onSubmitFlow, onOverwriteDirection = onOverwriteDirection, onWithdrawDirection = onWithdrawDirection,
            onSaveBeats = onSaveBeats, onRestoreAiBeats = onRestoreAiBeats, onDismiss = onDismiss,
        )
    }

    @Composable
    override fun PreviousDraftSheet(draft: StoryChapterDraft, onRestore: () -> Unit, onDismiss: () -> Unit) {
        LiuliStoryPreviousDraftSheet(draft, onRestore, onDismiss)
    }

    @Composable
    override fun ReaderAlert(
        onDismissRequest: () -> Unit,
        title: String,
        body: String,
        confirmText: String,
        onConfirm: () -> Unit,
        dismissText: String?,
        onDismiss: () -> Unit,
    ) {
        LiuliDialog(
            onDismissRequest = onDismissRequest, title = title, body = body, confirmText = confirmText,
            onConfirm = onConfirm, dismissText = dismissText, onDismiss = onDismiss,
        )
    }

    @Composable
    override fun Dialogs(
        dialog: ReaderDialog?,
        currentHasPendingChoice: Boolean,
        storyTitle: String?,
        chapterSummary: String?,
        characterStates: String?,
        onSaveChapterSummary: (String) -> Unit,
        onDismiss: () -> Unit,
        onFinishStory: () -> Unit,
        onForceContinue: () -> Unit,
        onEnding: (String, String?) -> Unit,
        onSkipChoiceThenEnding: () -> Unit,
        onGoToChoice: () -> Unit,
        onOpenEndingCustom: () -> Unit,
        onOpenRewriteInstruction: () -> Unit,
        onRewrite: (String?) -> Unit,
        onPickGracefulFinale: () -> Unit,
        onPickImmediateEnding: () -> Unit,
        onCancelFinale: () -> Unit,
    ) {
        LiuliStoryReaderDialogs(
            dialog = dialog,
            currentHasPendingChoice = currentHasPendingChoice,
            storyTitle = storyTitle,
            chapterSummary = chapterSummary,
            characterStates = characterStates,
            onSaveChapterSummary = onSaveChapterSummary,
            onDismiss = onDismiss,
            onFinishStory = onFinishStory,
            onForceContinue = onForceContinue,
            onEnding = onEnding,
            onSkipChoiceThenEnding = onSkipChoiceThenEnding,
            onGoToChoice = onGoToChoice,
            onOpenEndingCustom = onOpenEndingCustom,
            onOpenRewriteInstruction = onOpenRewriteInstruction,
            onRewrite = onRewrite,
            onPickGracefulFinale = onPickGracefulFinale,
            onPickImmediateEnding = onPickImmediateEnding,
            onCancelFinale = onCancelFinale,
        )
    }
}
