package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.story.HubTagChip
import com.situ.aichat.ui.story.rememberStoryDirectorEditorState
import com.situ.aichat.ui.story.rememberStoryDirectorInitialBeats
import kotlinx.coroutines.launch

/**
 * 琉璃导演台（琉璃 2.0 卷六·三·下乙 §4.4）：与暖陶 [com.situ.aichat.ui.story.StoryDirectorSheet] 吃同一个页内态
 * （[com.situ.aichat.ui.story.StoryDirectorEditorState]：dirty 判据 / 保存分派 / 撤回二段式 / 弃改 / 恢复 AI），两栏、钮排、两道 Danger 确认同暖陶；
 * 标题与副行进玻璃弹层题头，题头关闭圆与下拉 / 返回同一条「改过先问」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliStoryDirectorSheet(
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
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialBeats = rememberStoryDirectorInitialBeats(beats)
    val state = rememberStoryDirectorEditorState(savedDirection, initialBeats)
    val onGlass = LiuliTheme.onGlass
    LiuliSheetShell(
        // 下拉 / 返回 / 题头关闭圆同一条：改过先问（关闭圆默认走 onDismissRequest）。
        onDismissRequest = { state.requestDismiss(savedDirection, initialBeats, onDismiss) },
        sheetState = sheetState,
        title = stringResource(R.string.story_director_title),
        subtitle = stringResource(R.string.story_director_sub),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 栏 A · 剧情走向（非空即最高优先走向）。已存走向时 label 行挂「已保存 · 待生成」tag（同暖陶）。
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.story_director_flow_label), style = AppTypography.secondary, color = onGlass.secondary)
                    if (savedDirection != null) HubTagChip(stringResource(R.string.story_continue_direction_tag), highlighted = true)
                }
                LiuliField(
                    value = state.flowText,
                    onValueChange = { state.flowText = it },
                    placeholder = stringResource(R.string.story_director_flow_hint),
                    singleLine = false,
                    minHeight = 96.dp,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 清空栏 A 保存 ≠ 撤回（那只是「没改」）——把取消走向的正确出口指出来（同暖陶）。
                if (savedDirection != null) {
                    Text(stringResource(R.string.story_director_flow_saved_hint), style = AppTypography.caption.copy(fontSize = 10.5.sp), color = onGlass.secondary)
                }
            }
            // 栏 B · 本章节拍（底稿 = AI 预排；改过即最高优先）。
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.story_director_beats_label), style = AppTypography.secondary, color = onGlass.secondary)
                    HubTagChip(
                        stringResource(if (beatsUserEdited) R.string.story_hub_tag_user_edited else R.string.story_hub_tag_ai_planned),
                        highlighted = beatsUserEdited,
                    )
                }
                LiuliField(
                    value = state.beatsText,
                    onValueChange = { state.beatsText = it },
                    placeholder = stringResource(R.string.story_director_beats_hint),
                    singleLine = false,
                    minHeight = 120.dp,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.story_director_beats_hint2), style = AppTypography.caption.copy(fontSize = 10.5.sp), color = onGlass.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // 撤回走向：只在真有已存走向时出现（哨兵态不给）；破坏性动作走 quiet 档不占主 CTA（同暖陶）。
                if (savedDirection != null) {
                    LiuliButton(onClick = { state.requestWithdraw() }, style = LiuliButtonStyle.Text, enabled = !state.saving) {
                        Text(stringResource(R.string.story_director_withdraw))
                    }
                }
                // 只在「已由你修改」时出现（同暖陶）。
                if (beatsUserEdited) {
                    LiuliButton(onClick = { state.restoreAiBeats(onRestoreAiBeats, onDismiss) }, style = LiuliButtonStyle.Glass, enabled = !state.saving) {
                        Text(stringResource(R.string.story_director_restore_ai))
                    }
                }
                Spacer(Modifier.weight(1f))
                LiuliButton(
                    onClick = { state.save(savedDirection, initialBeats, directionCommitted, onSubmitFlow, onOverwriteDirection, onSaveBeats, onDismiss) },
                    style = LiuliButtonStyle.Prominent,
                    enabled = !state.saving,
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    // 弃改确认（同暖陶三条词条）：「继续编辑」要把被拖走的弹层升回来。
    if (state.confirmDiscard) {
        val keepEditing = { state.confirmDiscard = false; scope.launch { sheetState.show() }; Unit }
        LiuliDialog(
            onDismissRequest = keepEditing,
            title = stringResource(R.string.story_field_discard_title),
            confirmText = stringResource(R.string.story_field_discard_yes),
            onConfirm = { state.discardAndClose(onDismiss) },
            confirmDanger = true,
            dismissText = stringResource(R.string.story_field_discard_no),
            onDismiss = keepEditing,
        )
    }
    // 撤回确认（同暖陶）：一点即永久删掉手写文本 → Danger 闸。
    if (state.confirmWithdraw) {
        val keepEditing = { state.confirmWithdraw = false; scope.launch { sheetState.show() }; Unit }
        LiuliDialog(
            onDismissRequest = keepEditing,
            title = stringResource(R.string.story_director_withdraw_title),
            body = stringResource(R.string.story_director_withdraw_body),
            confirmText = stringResource(R.string.story_director_withdraw_confirm),
            onConfirm = { state.confirmWithdrawAndClose(onWithdrawDirection, onDismiss) },
            confirmDanger = true,
            dismissText = stringResource(R.string.story_field_discard_no),
            onDismiss = keepEditing,
        )
    }
}
