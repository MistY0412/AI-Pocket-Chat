package com.situ.aichat.ui.story

import androidx.compose.runtime.Composable
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.ui.designsystem.AppDialog

// 阅读器弹层与对话框的「脸」（琉璃 2.0 卷六·三·下乙 chunk 1）：接线（写库回调 / 哨兵过滤 / 结局两步流转 / 跳过延迟提交）
// 只在 StoryReaderSheets / StoryReaderAlerts / StoryReaderDialogHost 一处，脸只管画。暖陶 = [StoryReaderWarmSheetFace]
// （逐字转调原件·像素与行为不变）；琉璃 = ui/liuli/story 的 LiuliStoryReaderSheetFace。方法形参与原件逐个同名（接线处具名实参原样）。

/** 阅读器弹层与对话框的一张脸：自由输入 / 导演台 / 上一版三弹层 + 两个 VM 提示框 + ⋮ 弹窗族。 */
internal interface StoryReaderSheetFace {
    @Composable
    fun CustomChoiceSheet(prompt: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit)

    @Composable
    fun DirectorSheet(
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
    )

    @Composable
    fun PreviousDraftSheet(draft: StoryChapterDraft, onRestore: () -> Unit, onDismiss: () -> Unit)

    /** [StoryReaderAlerts] 的两个 VM 提示框（形参 = 暖陶 `AppDialog` 的同名子集）。 */
    @Composable
    fun ReaderAlert(
        onDismissRequest: () -> Unit,
        title: String,
        body: String,
        confirmText: String,
        onConfirm: () -> Unit,
        dismissText: String?,
        onDismiss: () -> Unit,
    )

    /** ⋮ 菜单弹窗族（形参 = [ReaderDialogs] 逐个同名）。 */
    @Composable
    fun Dialogs(
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
    )
}

/** 暖陶脸：逐字转调原件（与接入本接口前逐字节同渲染、同行为）。 */
internal object StoryReaderWarmSheetFace : StoryReaderSheetFace {
    @Composable
    override fun CustomChoiceSheet(prompt: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
        StoryCustomChoiceSheet(prompt = prompt, hint = hint, onConfirm = onConfirm, onDismiss = onDismiss)
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
        StoryDirectorSheet(
            beats = beats, beatsUserEdited = beatsUserEdited, savedDirection = savedDirection, directionCommitted = directionCommitted,
            onSubmitFlow = onSubmitFlow, onOverwriteDirection = onOverwriteDirection, onWithdrawDirection = onWithdrawDirection,
            onSaveBeats = onSaveBeats, onRestoreAiBeats = onRestoreAiBeats, onDismiss = onDismiss,
        )
    }

    @Composable
    override fun PreviousDraftSheet(draft: StoryChapterDraft, onRestore: () -> Unit, onDismiss: () -> Unit) {
        StoryPreviousDraftSheet(draft = draft, onRestore = onRestore, onDismiss = onDismiss)
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
        AppDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            body = body,
            confirmText = confirmText,
            onConfirm = onConfirm,
            dismissText = dismissText,
            onDismiss = onDismiss,
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
        ReaderDialogs(
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
