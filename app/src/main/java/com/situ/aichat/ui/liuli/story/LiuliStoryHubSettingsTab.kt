package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryCharacterRoleEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryEditableField
import com.situ.aichat.story.StoryFieldValueLabel
import com.situ.aichat.story.StoryGlobalCraftValues
import com.situ.aichat.ui.designsystem.AppProfileIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.page.LiuliDangerRow
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRadioRow
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliRowTitleColumn
import com.situ.aichat.ui.liuli.page.LiuliTextActionRow
import com.situ.aichat.ui.liuli.page.LiuliToggleRow
import com.situ.aichat.ui.liuli.page.LiuliValueRow
import com.situ.aichat.ui.story.HubTagChip
import com.situ.aichat.ui.story.HubValueLabel
import com.situ.aichat.ui.story.StoryHubSettingsCallbacks
import com.situ.aichat.ui.story.StorySerialOps
import com.situ.aichat.ui.story.StorySettingsDraft
import com.situ.aichat.ui.story.rememberStoryPrompts
import com.situ.aichat.ui.story.storyChapterChoicesOn
import com.situ.aichat.ui.story.storyDraftChase
import com.situ.aichat.ui.story.storyDraftWithChase
import com.situ.aichat.ui.story.storyHubCraftFieldIsNew
import com.situ.aichat.ui.story.storyHubCraftFields
import com.situ.aichat.ui.story.storyReminderLabelRes
import com.situ.aichat.ui.story.storySceneSnapshotOn
import com.situ.aichat.ui.story.storySerialOps
import com.situ.aichat.ui.story.storyUnlockTimeText

/**
 * 琉璃书页「设定」Tab（琉璃 2.0 卷六·三·上 §4.10）：四组放进一个 item（先例 = 日记设置页），全部换琉璃分组卡与行族；
 * 判据 / 取值 / 回调一律调暖陶共用件，组内条件（世界观开关门控、追更才出两行、完结 / 暂停操作钮）同暖陶。
 */
internal fun LazyListScope.liuliStoryHubSettingsItems(
    story: StoryEntity,
    draft: StorySettingsDraft,
    roles: List<StoryCharacterRoleEntity>,
    globals: StoryGlobalCraftValues,
    hasWorldBooks: Boolean,
    reminderEnabled: Boolean,
    templateCount: Int,
    callbacks: StoryHubSettingsCallbacks,
) {
    item(key = "settings_groups") {
        // 纯 Column：组间 24 = LiuliGroup 自带的 `padding(bottom = groupGap)`（再叠 spacedBy 会成 48·先例日记设置页·复核 R1 核准 D-3）。
        Column {
            LiuliStoryCraftGroup(story, globals, callbacks.onOpenField)
            LiuliStoryRolesGroup(story.id, roles, callbacks.onSaveRole, callbacks.onDeleteRole, callbacks.onDraftPersona)
            LiuliStoryToggleGroup(story, hasWorldBooks, callbacks)
            LiuliStorySerialGroup(story, draft, reminderEnabled, templateCount, callbacks)
        }
    }
}

/** 写法组：七个文本设定，每行一枚三态值标（跨脸复用 [HubValueLabel]）+ 可挂 NEW，点开进统一编辑页。 */
@Composable
private fun LiuliStoryCraftGroup(story: StoryEntity, globals: StoryGlobalCraftValues, onOpenField: (StoryEditableField) -> Unit) {
    val prompts = rememberStoryPrompts(story)
    LiuliGroup(header = stringResource(R.string.story_hub_group_craft)) {
        storyHubCraftFields().forEachIndexed { i, field ->
            LiuliStoryValueLabelRow(
                title = stringResource(field.titleRes),
                isNew = storyHubCraftFieldIsNew(field),
                label = field.valueLabel(story, globals, prompts),
                onClick = { onOpenField(field) },
                divider = i > 0,
            )
        }
    }
}

/** 写法行：标题（可挂 NEW）+ 值标 + chevron，在行基线上拼。 */
@Composable
private fun LiuliStoryValueLabelRow(title: String, isNew: Boolean, label: StoryFieldValueLabel, onClick: () -> Unit, divider: Boolean) {
    LiuliRowBase(onClick = onClick, divider = divider) {
        LiuliRowTitleColumn(title, null)
        if (isNew) {
            Spacer(Modifier.width(8.dp))
            HubTagChip(stringResource(R.string.story_hub_tag_new), highlighted = true)
        }
        Spacer(Modifier.width(LiuliPageGeometry.tileGap))
        Spacer(Modifier.weight(1f))
        HubValueLabel(label)
        Spacer(Modifier.width(8.dp))
        Icon(AppProfileIcons.ChevronRight, contentDescription = null, tint = AppTheme.colors.text.tertiary, modifier = Modifier.size(STORY_CHEVRON))
    }
}

/** 生成开关组：章末选项 / 场景快照 / 世界观（只在绑定角色挂了设定集时出）。 */
@Composable
private fun LiuliStoryToggleGroup(story: StoryEntity, hasWorldBooks: Boolean, cb: StoryHubSettingsCallbacks) {
    val prompts = rememberStoryPrompts(story)
    LiuliGroup(header = stringResource(R.string.story_hub_group_toggles)) {
        LiuliToggleRow(
            title = stringResource(R.string.story_toggle_choices), subtitle = stringResource(R.string.story_toggle_choices_sub),
            checked = storyChapterChoicesOn(prompts), onCheckedChange = cb.onChapterChoicesChange, divider = false,
        )
        LiuliToggleRow(
            title = stringResource(R.string.story_toggle_snapshot), subtitle = stringResource(R.string.story_toggle_snapshot_sub),
            checked = storySceneSnapshotOn(prompts), onCheckedChange = cb.onSceneSnapshotChange,
        )
        if (hasWorldBooks) {
            LiuliToggleRow(
                title = stringResource(R.string.story_settings_world_title), subtitle = stringResource(R.string.story_settings_world_sub),
                checked = story.worldInfoEnabled, onCheckedChange = cb.onWorldInfoChange,
            )
        }
    }
}

/** 连载与管理组：创作七行 + 存模板 + 追更（开了才出解锁时间与提醒）+ 归档 + 删除 + 全局写作偏好；卡外完结 / 暂停操作钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiuliStorySerialGroup(story: StoryEntity, d: StorySettingsDraft, reminderEnabled: Boolean, templateCount: Int, cb: StoryHubSettingsCallbacks) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderChooser by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val chase = storyDraftChase(d)

    // 纯 Column：组到卡外操作钮的缝 = LiuliGroup 自带的 24（再叠 spacedBy(10) 会成 34·同 D-3 口径·复核 R1 🔵-1）。
    Column {
        LiuliGroup(header = stringResource(R.string.story_hub_group_serial), footer = stringResource(R.string.story_settings_creative_footer)) {
            LiuliStoryHubCreativeRows(d, cb.onUpdateDraft, templateCount, story.title, cb.onSaveTemplate)
            LiuliToggleRow(
                title = stringResource(R.string.story_settings_mode_chase), subtitle = stringResource(R.string.story_settings_chase_sub),
                checked = chase, onCheckedChange = { on -> cb.onUpdateDraft { storyDraftWithChase(it, on) } },
            )
            if (chase) {
                LiuliValueRow(title = stringResource(R.string.story_settings_unlock_time), value = storyUnlockTimeText(d), onClick = { showTimePicker = true })
                LiuliValueRow(
                    title = stringResource(R.string.story_settings_reminder), value = stringResource(storyReminderLabelRes(reminderEnabled)),
                    onClick = { showReminderChooser = true },
                )
            }
            LiuliNavRow(title = stringResource(R.string.story_hub_archive_row), onClick = cb.onArchive)
            LiuliDangerRow(title = stringResource(R.string.story_hub_delete_row), onClick = { confirmDelete = true })
            LiuliTextActionRow(title = stringResource(R.string.story_hub_global_prefs_row), onClick = cb.onOpenGlobalSettings)
        }
        storySerialOps(story)?.let { ops -> LiuliStorySerialOps(ops, cb.onContinue, cb.onRestart) }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = d.unlockHour, initialMinute = d.unlockMinute, is24Hour = true)
        LiuliDialog(
            onDismissRequest = { showTimePicker = false },
            title = stringResource(R.string.story_settings_unlock_time),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = { cb.onUpdateDraft { it.copy(unlockHour = pickerState.hour, unlockMinute = pickerState.minute) }; showTimePicker = false },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showTimePicker = false },
            // 时间轮暂借 M3 `TimePicker`（卷五 A-4 ⑥ 豁免先例·LiuliDiarySettingsScreen）。
            content = { TimePicker(state = pickerState) },
        )
    }
    if (showReminderChooser) {
        LiuliDialog(
            onDismissRequest = { showReminderChooser = false },
            title = stringResource(R.string.story_settings_reminder),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showReminderChooser = false },
            content = {
                // notifyWhenSelected：点当前项也回调并关框（同暖陶 ChoiceOptionRow·§11 D-4）。
                LiuliRadioRow(
                    title = stringResource(R.string.story_settings_reminder_on), selected = reminderEnabled,
                    onSelect = { cb.onReminderChange(true); showReminderChooser = false }, divider = false, notifyWhenSelected = true,
                )
                LiuliRadioRow(
                    title = stringResource(R.string.action_close), selected = !reminderEnabled,
                    onSelect = { cb.onReminderChange(false); showReminderChooser = false }, notifyWhenSelected = true,
                )
            },
        )
    }
    if (confirmDelete) {
        LiuliDialog(
            onDismissRequest = { confirmDelete = false }, title = stringResource(R.string.story_hub_delete_title), body = stringResource(R.string.story_hub_delete_body),
            confirmText = stringResource(R.string.action_delete), onConfirm = { confirmDelete = false; cb.onDelete() }, confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel), onDismiss = { confirmDelete = false },
        )
    }
}

/** 卡外连载操作钮：完结 = 主色继续 + 玻璃重开 + 脚注；暂停 = 主色恢复 + 脚注（条件与文案同暖陶）。 */
@Composable
private fun LiuliStorySerialOps(ops: StorySerialOps, onContinue: () -> Unit, onRestart: () -> Unit) {
    val footer: @Composable (Int) -> Unit = { Text(stringResource(it), style = AppTypography.secondary, color = AppTheme.colors.text.secondary) }
    Column(verticalArrangement = Arrangement.spacedBy(HUB_ITEM_GAP)) {
        if (ops == StorySerialOps.COMPLETED) {
            LiuliButton(onContinue, Modifier.fillMaxWidth(), style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.story_settings_continue)) }
            LiuliButton(onRestart, Modifier.fillMaxWidth(), style = LiuliButtonStyle.Glass) { Text(stringResource(R.string.story_settings_restart)) }
            footer(R.string.story_settings_ops_footer_completed)
        } else {
            LiuliButton(onContinue, Modifier.fillMaxWidth(), style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.story_settings_resume)) }
            footer(R.string.story_settings_ops_footer_paused)
        }
    }
}
