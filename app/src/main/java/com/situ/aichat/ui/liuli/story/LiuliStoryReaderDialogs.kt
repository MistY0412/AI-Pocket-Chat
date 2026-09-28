package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.story.StoryEndingType
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.story.ReaderDialog
import com.situ.aichat.ui.story.STATES_MAX_HEIGHT
import com.situ.aichat.ui.story.SUGGEST_GOLD_FILL_ALPHA
import com.situ.aichat.ui.story.SUGGEST_GOLD_LINE_ALPHA
import com.situ.aichat.ui.story.storyCharacterStatesText
import com.situ.aichat.ui.story.storyContinueAlertBodyRes
import com.situ.aichat.ui.story.storyDialogInputConfirm
import com.situ.aichat.ui.story.storyEndingQuickOptions
import com.situ.aichat.ui.story.storyRewriteInstruction
import com.situ.aichat.ui.theme.LocalIsDarkTheme

// 琉璃阅读器 ⋮ / 章末弹窗族（琉璃 2.0 卷六·三·下乙 §4.2）：与暖陶 ReaderDialogs 同十一支、同文案同次序同条件；
// 外壳 = LiuliDialog，竖排选项钮 = LiuliButton 满宽，字走玻璃色。接线在 StoryReaderDialogHost，这里只画。

/** 琉璃弹窗族（形参与暖陶 [com.situ.aichat.ui.story.ReaderDialogs] 同名同序）。 */
@Composable
internal fun LiuliStoryReaderDialogs(
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
    val onGlass = LiuliTheme.onGlass
    when (dialog) {
        ReaderDialog.Continue -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_alert_continue_title),
            body = stringResource(storyContinueAlertBodyRes(currentHasPendingChoice)),
            confirmText = stringResource(R.string.story_alert_continue_confirm),
            onConfirm = onForceContinue,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )

        // 建议卡「就此完结」的确认（ST11 §4.3）：复用书架长按「完结归档」的同一套文案与样式——
        // 同一个动作（都走 StoryArchiver），用户看到的问法就该一模一样。
        ReaderDialog.ArchiveConfirm -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_archive_confirm_title),
            body = stringResource(R.string.story_archive_confirm_msg, storyTitle.orEmpty()),
            confirmText = stringResource(R.string.story_archive_confirm_action),
            onConfirm = onFinishStory,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )

        ReaderDialog.FinaleMethod -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_finale_sheet_title),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            content = {
                LiuliFinaleMethodOption(
                    stringResource(R.string.story_finale_option_graceful), stringResource(R.string.story_finale_option_graceful_desc),
                    recommended = true, onClick = onPickGracefulFinale,
                )
                Spacer(Modifier.height(9.dp))
                LiuliFinaleMethodOption(
                    stringResource(R.string.story_finale_option_immediate), stringResource(R.string.story_finale_option_immediate_desc),
                    recommended = false, onClick = onPickImmediateEnding,
                )
            },
        )

        ReaderDialog.FinaleCancelConfirm -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_finale_cancel),
            body = stringResource(R.string.story_finale_cancel_confirm),
            confirmText = stringResource(R.string.story_finale_cancel),
            onConfirm = onCancelFinale,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
        )

        ReaderDialog.EndingPicker -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_ending_picker_title),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            content = {
                Text(stringResource(R.string.story_ending_picker_msg), style = AppTypography.dialogBody, color = onGlass.secondary)
                Spacer(Modifier.height(12.dp))
                storyEndingQuickOptions.forEach { (labelRes, type) ->
                    LiuliButton(onClick = { onEnding(type, null) }, style = LiuliButtonStyle.Text, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(labelRes))
                    }
                }
                LiuliButton(onClick = onOpenEndingCustom, style = LiuliButtonStyle.Text, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.story_ending_custom))
                }
            },
        )

        ReaderDialog.EndingCustom -> LiuliStoryTextInputDialog(
            title = stringResource(R.string.story_ending_custom_title),
            message = stringResource(R.string.story_ending_custom_msg),
            hint = stringResource(R.string.story_ending_custom_hint),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { onEnding(StoryEndingType.CUSTOM, it) },
            onDismiss = onDismiss,
        )

        // ST10-4：竖排双钮（主=跳过直写结局·次=真的带用户滚到选择区，不再是只关弹窗的假按钮）。
        ReaderDialog.EndingPending -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_ending_pending_title),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            content = {
                Text(stringResource(R.string.story_ending_pending_msg), style = AppTypography.dialogBody, color = onGlass.secondary)
                Spacer(Modifier.height(12.dp))
                LiuliButton(onClick = onSkipChoiceThenEnding, style = LiuliButtonStyle.Prominent, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.story_ending_pending_skip))
                }
                Spacer(Modifier.height(8.dp))
                LiuliButton(onClick = onGoToChoice, style = LiuliButtonStyle.Glass, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.story_ending_pending_go))
                }
            },
        )

        ReaderDialog.RewriteConfirm -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_rewrite_title),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            content = {
                Text(stringResource(R.string.story_rewrite_msg), style = AppTypography.dialogBody, color = onGlass.secondary)
                Spacer(Modifier.height(12.dp))
                LiuliButton(onClick = { onRewrite(null) }, style = LiuliButtonStyle.Text, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.story_rewrite_direct))
                }
                LiuliButton(onClick = onOpenRewriteInstruction, style = LiuliButtonStyle.Text, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.story_rewrite_with_instruction))
                }
            },
        )

        ReaderDialog.RewriteInstruction -> LiuliStoryTextInputDialog(
            title = stringResource(R.string.story_rewrite_instruction_title),
            message = stringResource(R.string.story_rewrite_instruction_msg),
            hint = stringResource(R.string.story_rewrite_instruction_hint),
            confirmLabel = stringResource(R.string.story_rewrite_instruction_confirm),
            onConfirm = { onRewrite(storyRewriteInstruction(it)) },
            onDismiss = onDismiss,
        )

        ReaderDialog.ChapterSummary -> LiuliStoryTextInputDialog(
            title = stringResource(R.string.story_reader_menu_summary),
            message = stringResource(R.string.story_summary_edit_msg),
            hint = stringResource(R.string.story_summary_edit_hint),
            confirmLabel = stringResource(R.string.action_save),
            initialText = chapterSummary.orEmpty(),
            onConfirm = onSaveChapterSummary,
            onDismiss = onDismiss,
        )

        ReaderDialog.CharacterStates -> LiuliDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.story_reader_menu_states),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = onDismiss,
            content = {
                Text(
                    storyCharacterStatesText(characterStates),
                    style = AppTypography.dialogBody,
                    color = onGlass.secondary,
                    modifier = Modifier.heightIn(max = STATES_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                )
            },
        )

        null -> Unit
    }
}

/** 收尾方式选项卡（暖陶 FinaleMethodOption 同结构同值·推荐卡金调两张脸同色；非推荐卡描边走玻璃分隔色）。 */
@Composable
private fun LiuliFinaleMethodOption(title: String, description: String, recommended: Boolean, onClick: () -> Unit) {
    val gold = AppTheme.colors.economy.gold
    val onGlass = LiuliTheme.onGlass
    val shape = AppTheme.shapes.medium // = 暖陶卡形
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (recommended) gold.copy(alpha = SUGGEST_GOLD_FILL_ALPHA) else Color.Transparent)
            .border(0.75.dp, if (recommended) gold.copy(alpha = SUGGEST_GOLD_LINE_ALPHA) else LiuliMaterials.divider(LocalIsDarkTheme.current), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = if (recommended) gold else onGlass.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = onGlass.secondary, fontSize = 12.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 多行输入弹窗（暖陶 TextInputDialog 同结构：说明 → 12 → 输入框 minHeight 80；确认 trim 后交出再关）。 */
@Composable
private fun LiuliStoryTextInputDialog(
    title: String,
    message: String,
    hint: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    LiuliDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = confirmLabel,
        onConfirm = { storyDialogInputConfirm(text, onConfirm, onDismiss) },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            Column {
                Text(message, style = AppTypography.dialogBody, color = LiuliTheme.onGlass.secondary)
                Spacer(Modifier.height(12.dp))
                LiuliField(value = text, onValueChange = { text = it }, placeholder = hint, singleLine = false, minHeight = 80.dp, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
