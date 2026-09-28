@file:OptIn(ExperimentalMaterial3Api::class)

package com.situ.aichat.ui.story

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.story.StoryRoleType
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppSegmentedControl
import com.situ.aichat.ui.designsystem.AppSheet
import com.situ.aichat.ui.designsystem.AppTextArea
import com.situ.aichat.ui.designsystem.AppTextField
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 角色编辑弹层（图纸二 D1·2026-08-01 过审 mockup 画面②）——**设定页与创建屏共用**：
 * 角色名 + 角色定位（三段）+ 人设描述，底部「保存」，编辑既有行时另给一个移出/删除口。
 *
 * 权限矩阵由调用方按行的来源传入（图纸 §3.2）：
 * - **本书专属角色**：名字/定位/描述/移出 全开
 * - **关联聊天角色**：名字只读（名字归 `CharacterEntity` 本体管），定位/描述可改、可移出本书
 * - **「我」（isUserRole）**：只开描述——用户角色的存在性由创建时的「我也参演」语义管着，人称段依赖它，设定页不拆台
 *
 * 自建角色的外貌/口癖/称呼一律写进**一段自由描述**（2026-08-01 用户拍板：不分字段、零迁移），
 * 提示词侧零改动即可消费（`StoryPromptSections` 对 characterId=null 的行本就只读 roleDescription）。
 *
 * 故事二期卷二加一栏「私下反差」（提案 §5.1/§6.3·mockup 屏 5）：写她私下与人前的反差，落
 * `StoryCharacterRoleEntity.intimatePersona`。「我」那一行不显示（反差是女主侧设定）；创建屏也不显示
 * （角色还没落库、上下文太薄，AI 起草与该栏一并留给书页·图纸 J5）。
 */
@Composable
internal fun StoryRoleEditorSheet(config: StoryRoleEditorConfig, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = rememberStoryRoleEditorState(config)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AppSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (config.isNew) R.string.story_role_editor_title_new else R.string.story_role_editor_title_edit),
                style = AppTheme.typography.titleMedium,
            )

            AppTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = stringResource(R.string.story_role_editor_name),
                enabled = config.nameEditable,
                supportingText = if (config.nameEditable) null else config.nameLockedHint,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.story_role_editor_type), style = AppTheme.typography.label, color = AppTheme.colors.text.primary)
            AppSegmentedControl(
                options = storyRoleTypes,
                selected = state.type,
                onSelect = { state.type = it },
                enabled = config.typeEditable,
                modifier = Modifier.fillMaxWidth(),
                label = { value -> stringResource(roleTypeLabelRes(value)) },
            )

            Text(stringResource(R.string.story_role_editor_desc), style = AppTheme.typography.label, color = AppTheme.colors.text.primary)
            AppTextArea(
                value = state.description,
                onValueChange = { state.description = it },
                placeholder = stringResource(R.string.story_role_editor_desc_hint),
                minHeight = 120.dp,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            if (config.showPersona) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.story_role_persona_label),
                        style = AppTheme.typography.label,
                        color = AppTheme.colors.text.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    HubTagChip(stringResource(R.string.story_hub_tag_new), highlighted = true)
                    Spacer(Modifier.weight(1f))
                    if (config.onDraftPersona != null) {
                        DraftPersonaButton(state.drafting) {
                            state.startDraft(config, scope) { Toast.makeText(context, R.string.story_role_persona_failed, Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
                AppTextArea(
                    value = state.persona,
                    onValueChange = { state.persona = it },
                    placeholder = stringResource(R.string.story_role_persona_placeholder),
                    minHeight = 100.dp,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.story_role_persona_hint),
                    style = AppTheme.typography.caption.copy(fontSize = 10.5.sp),
                    color = AppTheme.colors.text.tertiary,
                )
            }

            AppButton(
                onClick = { state.save(config, onDismiss) },
                style = AppButtonStyle.Primary,
                // 空名字的角色没法被提示词引用，保存置灰（E11·见 canSave）
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            if (config.onRemove != null) {
                AppButton(
                    onClick = { state.requestRemove(config, onDismiss) },
                    style = AppButtonStyle.Text,
                    danger = true,
                ) {
                    Text(stringResource(if (config.removeNeedsConfirm) R.string.story_role_editor_remove else R.string.action_delete))
                }
            }
        }
    }

    if (state.confirmRemove && config.onRemove != null) {
        AppDialog(
            onDismissRequest = { state.confirmRemove = false },
            title = stringResource(R.string.story_role_editor_remove_title),
            body = stringResource(R.string.story_role_editor_remove_body),
            confirmText = stringResource(R.string.story_role_editor_remove),
            onConfirm = { state.confirmRemoveAndClose(config, onDismiss) },
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { state.confirmRemove = false },
        )
    }
}

/** ✦ AI 起草胶囊钮：起草中换文案并禁用（防重入·E7）。 */
@Composable
private fun DraftPersonaButton(drafting: Boolean, onClick: () -> Unit) {
    AppButton(
        onClick = onClick,
        style = AppButtonStyle.Tonal,
        enabled = !drafting,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            stringResource(if (drafting) R.string.story_role_persona_drafting else R.string.story_role_persona_draft),
            style = AppTheme.typography.caption.copy(fontSize = 11.sp),
        )
    }
}

/** 角色定位文案（创建屏 `RoleTypeSelector` 同一组词条）。 */
internal fun roleTypeLabelRes(type: String): Int = when (type) {
    StoryRoleType.PROTAGONIST -> R.string.story_role_protagonist
    StoryRoleType.ANTAGONIST -> R.string.story_role_antagonist
    else -> R.string.story_role_supporting
}
