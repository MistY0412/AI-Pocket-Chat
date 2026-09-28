package com.situ.aichat.ui.liuli.story

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmented
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.story.HubTagChip
import com.situ.aichat.ui.story.StoryRoleEditorConfig
import com.situ.aichat.ui.story.rememberStoryRoleEditorState
import com.situ.aichat.ui.story.roleTypeLabelRes
import com.situ.aichat.ui.story.storyRoleTypes

/**
 * 琉璃角色编辑弹层（琉璃 2.0 卷六·三·上 §4.13）：与暖陶 [com.situ.aichat.ui.story.StoryRoleEditorSheet] 吃同一份配置（权限矩阵四套）
 * 与同一个页内态（保存 / 请求移出 / 确认移出 / AI 起草四动作）；玻璃弹层题头带关闭圆，「私下反差」提示在玻璃上走 onGlass 次级色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliStoryRoleEditorSheet(config: StoryRoleEditorConfig, onDismiss: () -> Unit) {
    val state = rememberStoryRoleEditorState(config)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val onGlass = LiuliTheme.onGlass
    LiuliSheetShell(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = stringResource(if (config.isNew) R.string.story_role_editor_title_new else R.string.story_role_editor_title_edit),
    ) {
        // 修饰链 = 暖陶原链（LiuliSheetShell 约定照抄）。
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiuliField(
                value = state.name,
                onValueChange = { state.name = it },
                label = stringResource(R.string.story_role_editor_name),
                enabled = config.nameEditable,
                supportingText = if (config.nameEditable) null else config.nameLockedHint,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.story_role_editor_type), style = AppTypography.label, color = onGlass.primary)
            LiuliSegmented(
                options = storyRoleTypes,
                selected = state.type,
                label = { stringResource(roleTypeLabelRes(it)) },
                onSelect = { state.type = it },
                enabled = config.typeEditable,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.story_role_editor_desc), style = AppTypography.label, color = onGlass.primary)
            LiuliField(
                value = state.description,
                onValueChange = { state.description = it },
                placeholder = stringResource(R.string.story_role_editor_desc_hint),
                singleLine = false,
                minHeight = 120.dp,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            if (config.showPersona) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.story_role_persona_label), style = AppTypography.label, color = onGlass.primary)
                    Spacer(Modifier.width(6.dp))
                    HubTagChip(stringResource(R.string.story_hub_tag_new), highlighted = true)
                    Spacer(Modifier.weight(1f))
                    if (config.onDraftPersona != null) {
                        LiuliButton(
                            onClick = { state.startDraft(config, scope) { Toast.makeText(context, R.string.story_role_persona_failed, Toast.LENGTH_SHORT).show() } },
                            style = LiuliButtonStyle.Glass,
                            enabled = !state.drafting,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                stringResource(if (state.drafting) R.string.story_role_persona_drafting else R.string.story_role_persona_draft),
                                style = AppTypography.caption.copy(fontSize = 11.sp),
                            )
                        }
                    }
                }
                LiuliField(
                    value = state.persona,
                    onValueChange = { state.persona = it },
                    placeholder = stringResource(R.string.story_role_persona_placeholder),
                    singleLine = false,
                    minHeight = 100.dp,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.story_role_persona_hint), style = AppTypography.caption.copy(fontSize = 10.5.sp), color = onGlass.secondary)
            }
            LiuliButton(onClick = { state.save(config, onDismiss) }, style = LiuliButtonStyle.Prominent, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
            if (config.onRemove != null) {
                LiuliButton(onClick = { state.requestRemove(config, onDismiss) }, style = LiuliButtonStyle.Text, danger = true) {
                    Text(stringResource(if (config.removeNeedsConfirm) R.string.story_role_editor_remove else R.string.action_delete))
                }
            }
        }
    }
    if (state.confirmRemove && config.onRemove != null) {
        LiuliDialog(
            onDismissRequest = { state.confirmRemove = false },
            title = stringResource(R.string.story_role_editor_remove_title),
            body = stringResource(R.string.story_role_editor_remove_body),
            confirmText = stringResource(R.string.story_role_editor_remove),
            onConfirm = { state.confirmRemoveAndClose(config, onDismiss) },
            confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { state.confirmRemove = false },
        )
    }
}
