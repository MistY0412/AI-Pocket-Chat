@file:OptIn(ExperimentalMaterial3Api::class)

package com.situ.aichat.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.story.StoryChapterLength
import com.situ.aichat.story.StoryEditableField
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppTextField
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 书页「连载与管理」组里的**创作设定七行 + 存为我的模板行**（卷二：自退役的
 * `StorySettingsCreativeGroup` **只搬不改**——行、弹窗、草稿心智、题材空白兜底全部逐字照旧）。
 *
 * 七字段（题材/文风/人称/章长/聊天影响/世界观/剧情方向）仍走 [StorySettingsDraft]，离开书页由 `persist()`
 * 一次性落库；题材是自由文本 sheet（自定义题材合法），其余四项是封闭枚举单选弹窗。
 * 单独成文件是为了守住 [storyHubSettingsItems] 那一侧的行数上限（CLAUDE.md §2）。
 */
@Composable
internal fun ColumnScope.StoryHubCreativeRows(
    d: StorySettingsDraft,
    update: ((StorySettingsDraft) -> StorySettingsDraft) -> Unit,
    templateCount: Int,
    defaultTemplateName: String,
    onSaveTemplate: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf<HubCreativeChoice?>(null) }
    var sheet by remember { mutableStateOf<HubCreativeTextField?>(null) }
    var namingTemplate by remember { mutableStateOf(false) }
    var atTemplateLimit by remember { mutableStateOf(false) }

    storyCreativeRows(d).forEachIndexed { i, row ->
        if (i > 0) RowDivider()
        NavRow(stringResource(row.titleRes), row.value) {
            when (val t = row.target) {
                is StoryCreativeTarget.Choice -> dialog = t.choice
                is StoryCreativeTarget.Text -> sheet = t.field
            }
        }
    }
    // 图纸四：存为「我的模板」——存的就是这一组的东西，落在组末最顺。到顶时点了直接弹上限提示。
    RowDivider()
    NavRow(stringResource(R.string.story_save_template_row), "") {
        if (storyTemplateAtLimit(templateCount)) atTemplateLimit = true else namingTemplate = true
    }

    dialog?.let { c -> HubChoiceDialog(storyCreativeChoiceSpec(c, d, update)) { dialog = null } }

    sheet?.let { f -> StoryTextEditorSheet(storyCreativeTextSpec(f, d, update)) { sheet = null } }

    if (namingTemplate) {
        HubSaveTemplateDialog(
            defaultName = defaultTemplateName,
            onConfirm = { onSaveTemplate(it); namingTemplate = false },
            onDismiss = { namingTemplate = false },
        )
    }

    if (atTemplateLimit) {
        AppDialog(
            onDismissRequest = { atTemplateLimit = false },
            title = stringResource(R.string.story_save_template_row),
            body = stringResource(R.string.story_save_template_limit),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = { atTemplateLimit = false },
        )
    }
}

/** 命名弹窗（造型照既有 `RenameDialog`：标题 + 说明 + 单行输入 + 保存/取消；名字空白时保存键禁用·E9）。 */
@Composable
private fun HubSaveTemplateDialog(defaultName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(defaultName)) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.story_save_template_row),
        confirmText = stringResource(R.string.action_save),
        onConfirm = { onConfirm(value.text) },
        confirmEnabled = storyTemplateNameValid(value.text),
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.story_save_template_msg), style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary)
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = stringResource(R.string.story_save_template_name_label),
                )
            }
        },
    )
}

/** 行尾值摘要：空 → 「未填写」；否则首 12 字（换行折成空格，长文补省略号）——与设定 Tab 值标同一口径单源。 */
internal fun creativeRowSummary(text: String, emptyLabel: String): String {
    if (text.isBlank()) return emptyLabel
    return StoryEditableField.flattenEcho(text)
}

/** 章长存的是字数（Int 列），四档枚举按 words 反查；查不到按中档显示（同既有回显口径）。 */
internal fun chapterLengthOf(words: Int): StoryChapterLength =
    StoryChapterLength.entries.firstOrNull { it.words == words } ?: StoryChapterLength.MEDIUM

/** 单选弹窗（造型照既有 `ReminderChooserDialog`：标题 + 选项行列表 + 取消）。 */
@Composable
private fun HubChoiceDialog(spec: StoryChoiceSpec, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = spec.title,
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            Column {
                spec.options.forEach { o ->
                    ChoiceOptionRow(o.label, selected = o.selected) { o.onSelect(); onDismiss() }
                }
            }
        },
    )
}
