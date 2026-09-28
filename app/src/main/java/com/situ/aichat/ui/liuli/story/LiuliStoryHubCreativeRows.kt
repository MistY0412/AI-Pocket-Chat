package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.story.HubCreativeChoice
import com.situ.aichat.ui.story.HubCreativeTextField
import com.situ.aichat.ui.story.StoryCreativeTarget
import com.situ.aichat.ui.story.StorySettingsDraft
import com.situ.aichat.ui.story.storyCreativeChoiceSpec
import com.situ.aichat.ui.story.storyCreativeRows
import com.situ.aichat.ui.story.storyCreativeTextSpec
import com.situ.aichat.ui.story.storyTemplateAtLimit

/**
 * 琉璃书页「连载与管理」组里的创作设定七行 + 存为我的模板（琉璃 2.0 卷六·三·上 §4.11）：行模型 / 单选规格 / 文本规格 /
 * 模板上限全部调暖陶共用件；琉璃只换行（导航行·值在右）、单选框、文本弹层、命名框与上限提示。
 */
@Composable
internal fun ColumnScope.LiuliStoryHubCreativeRows(
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

    // 组内首行不画发丝（行族约定·首行 divider = false）。
    storyCreativeRows(d).forEachIndexed { i, row ->
        LiuliNavRow(
            title = stringResource(row.titleRes),
            value = row.value,
            onClick = {
                when (val t = row.target) {
                    is StoryCreativeTarget.Choice -> dialog = t.choice
                    is StoryCreativeTarget.Text -> sheet = t.field
                }
            },
            divider = i > 0,
        )
    }
    LiuliNavRow(
        title = stringResource(R.string.story_save_template_row),
        onClick = { if (storyTemplateAtLimit(templateCount)) atTemplateLimit = true else namingTemplate = true },
    )

    dialog?.let { c -> LiuliStoryChoiceDialog(storyCreativeChoiceSpec(c, d, update)) { dialog = null } }
    sheet?.let { f -> LiuliStoryTextEditorSheet(storyCreativeTextSpec(f, d, update)) { sheet = null } }
    if (namingTemplate) {
        LiuliStoryNameDialog(
            title = stringResource(R.string.story_save_template_row),
            message = stringResource(R.string.story_save_template_msg),
            initialName = defaultTemplateName,
            onConfirm = { onSaveTemplate(it); namingTemplate = false },
            onDismiss = { namingTemplate = false },
        )
    }
    if (atTemplateLimit) {
        LiuliStoryAlert(stringResource(R.string.story_save_template_row), stringResource(R.string.story_save_template_limit)) { atTemplateLimit = false }
    }
}
