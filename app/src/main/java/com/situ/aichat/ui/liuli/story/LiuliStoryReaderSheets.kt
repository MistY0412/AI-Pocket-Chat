package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.story.PROSE_MAX_HEIGHT
import com.situ.aichat.ui.story.STORY_CUSTOM_CHOICE_FOCUS_DELAY_MS
import com.situ.aichat.ui.story.rememberStoryPrevDraftProse
import com.situ.aichat.ui.story.storyCustomChoiceSubmitText
import com.situ.aichat.ui.story.storyPrevDraftTitle
import kotlinx.coroutines.delay

// 琉璃阅读器自由输入 / 上一版两弹层（琉璃 2.0 卷六·三·下乙 §4.3）：玻璃弹层题头（标题 + 副标 + 关闭圆）取代暖陶正文里的标题行，
// 其余结构、数值、判据同暖陶（判据 / 取值全调 StoryReaderDialogSupport）。

/** 琉璃自由输入弹层（暖陶 [com.situ.aichat.ui.story.StoryCustomChoiceSheet] 同结构：多行输入 + 字数；trim 后为空确认置灰）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliStoryCustomChoiceSheet(prompt: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val submitText = storyCustomChoiceSubmitText(text)
    val onGlass = LiuliTheme.onGlass
    LaunchedEffect(Unit) {
        delay(STORY_CUSTOM_CHOICE_FOCUS_DELAY_MS) // 等弹层呈现完再弹键盘，避免布局跳动（同暖陶）
        runCatching { focusRequester.requestFocus() }
    }
    LiuliSheetShell(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = stringResource(R.string.story_custom_choice_title),
    ) {
        // 修饰链 = 暖陶原链（LiuliSheetShell 约定）。
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(prompt, style = AppTypography.listName, fontFamily = FontFamily.Serif, color = onGlass.primary)
            Text(hint, style = AppTypography.secondary, color = onGlass.secondary)
            // a11y 同暖陶：输入框读「输入自定义选择」。
            val fieldA11y = stringResource(R.string.story_choice_free_input_a11y)
            LiuliField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.story_custom_choice_placeholder),
                singleLine = false,
                minHeight = 160.dp,
                // 封顶后字段内部滚动：长走向指令不再把确认键顶出屏幕（同暖陶）
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldA11y }.focusRequester(focusRequester),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.story_custom_choice_footer), style = AppTypography.secondary, color = onGlass.secondary.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.story_custom_choice_count, text.length), style = AppTypography.secondary, color = onGlass.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                LiuliButton(onClick = onDismiss, style = LiuliButtonStyle.Text) { Text(stringResource(R.string.action_cancel)) }
                LiuliButton(onClick = { submitText?.let { onConfirm(it); onDismiss() } }, style = LiuliButtonStyle.Prominent, enabled = submitText != null) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    }
}

/** 琉璃「上一版」只读回翻弹层（暖陶 [com.situ.aichat.ui.story.StoryPreviousDraftSheet] 同结构：正文过清洗封顶滚动 + 满宽「换回这一版」→ 确认）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliStoryPreviousDraftSheet(draft: StoryChapterDraft, onRestore: () -> Unit, onDismiss: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val prose = rememberStoryPrevDraftProse(draft)
    LiuliSheetShell(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = storyPrevDraftTitle(draft),
        subtitle = stringResource(R.string.story_prev_draft_sub),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 正文只读：衬线体与阅读器正文同族；封顶后内部滚动，底部「换回这一版」永远够得着（同暖陶）。
            Text(
                prose,
                style = AppTypography.body,
                fontFamily = FontFamily.Serif,
                color = LiuliTheme.onGlass.primary,
                modifier = Modifier.fillMaxWidth().heightIn(max = PROSE_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            )
            LiuliButton(onClick = { confirming = true }, style = LiuliButtonStyle.Prominent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.story_prev_draft_restore))
            }
        }
    }
    if (confirming) {
        LiuliDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.story_prev_draft_confirm_title),
            body = stringResource(R.string.story_prev_draft_confirm_msg),
            confirmText = stringResource(R.string.story_prev_draft_confirm_action),
            onConfirm = { confirming = false; onRestore() },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { confirming = false },
        )
    }
}
