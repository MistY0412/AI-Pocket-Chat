package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.model.UserStoryTemplatePayload
import com.situ.aichat.story.StoryChapterLength
import com.situ.aichat.story.StoryCreationCatalog

/** 四个封闭枚举字段（单选弹窗）。——原 StoryBookHubCreativeRows private enum 搬来改 internal（琉璃 2.0 卷六·三）。 */
internal enum class HubCreativeChoice { STYLE, PERSON, LENGTH, INFLUENCE }

/** 三个自由文本字段（[StoryTextEditorSheet]）。世界观/剧情方向复用创建屏同一对标题与占位词条。 */
internal enum class HubCreativeTextField(val titleRes: Int, val placeholderRes: Int) {
    GENRE(R.string.story_settings_genre_row, R.string.story_settings_genre_placeholder),
    WORLD(R.string.story_field_world_title, R.string.story_field_world_placeholder),
    PLOT(R.string.story_field_plot_title, R.string.story_field_plot_placeholder),
}

/** 创作设定一行点开的是哪一种（单选弹窗 / 文本弹层）。 */
internal sealed interface StoryCreativeTarget {
    data class Choice(val choice: HubCreativeChoice) : StoryCreativeTarget
    data class Text(val field: HubCreativeTextField) : StoryCreativeTarget
}

internal data class StoryCreativeRow(@StringRes val titleRes: Int, val value: String, val target: StoryCreativeTarget)

/** 创作设定七行（原 :50–64 逐行：标题 / 值式 / 点开目标·顺序锁定）。 */
@Composable
internal fun storyCreativeRows(d: StorySettingsDraft): List<StoryCreativeRow> {
    val unfilled = stringResource(R.string.story_create_unfilled)
    return listOf(
        StoryCreativeRow(R.string.story_settings_genre_row, d.genre.ifBlank { unfilled }, StoryCreativeTarget.Text(HubCreativeTextField.GENRE)),
        StoryCreativeRow(R.string.story_settings_style_row, d.writingStyle.ifBlank { unfilled }, StoryCreativeTarget.Choice(HubCreativeChoice.STYLE)),
        StoryCreativeRow(R.string.story_settings_person_row, narrativeName(d.narrativePerson), StoryCreativeTarget.Choice(HubCreativeChoice.PERSON)),
        StoryCreativeRow(R.string.story_settings_field_length, chapterLengthName(chapterLengthOf(d.chapterLengthPreference)), StoryCreativeTarget.Choice(HubCreativeChoice.LENGTH)),
        StoryCreativeRow(R.string.story_settings_field_influence, chatInfluenceName(d.chatInfluenceWeight), StoryCreativeTarget.Choice(HubCreativeChoice.INFLUENCE)),
        StoryCreativeRow(R.string.story_settings_world_row, creativeRowSummary(d.worldSetting, unfilled), StoryCreativeTarget.Text(HubCreativeTextField.WORLD)),
        StoryCreativeRow(R.string.story_settings_plot_row, creativeRowSummary(d.plotDirection, unfilled), StoryCreativeTarget.Text(HubCreativeTextField.PLOT)),
    )
}

/** 单选弹窗的一项：标签 / 是否当前值 / 选中后写回草稿。 */
internal data class StoryChoiceOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

internal data class StoryChoiceSpec(val title: String, val options: List<StoryChoiceOption>)

/** 四个单选弹窗的规格（原 `when (dialog)` 四分支逐式：标题 / 选项序 / 当前值 / 标签 / 写回）。 */
@Composable
internal fun storyCreativeChoiceSpec(
    choice: HubCreativeChoice,
    d: StorySettingsDraft,
    update: ((StorySettingsDraft) -> StorySettingsDraft) -> Unit,
): StoryChoiceSpec = when (choice) {
    HubCreativeChoice.STYLE -> StoryChoiceSpec(
        stringResource(R.string.story_settings_style_row),
        StoryCreationCatalog.writingStyles.map { v -> StoryChoiceOption(v, v == d.writingStyle) { update { it.copy(writingStyle = v) } } },
    )
    HubCreativeChoice.PERSON -> StoryChoiceSpec(
        stringResource(R.string.story_settings_person_row),
        // 顺序照创建屏高级表单：第二人称（默认）→ 第一人称 → 第三人称
        storyNarrativeOptions.map { v -> StoryChoiceOption(narrativeName(v), v == d.narrativePerson) { update { it.copy(narrativePerson = v) } } },
    )
    HubCreativeChoice.LENGTH -> StoryChoiceSpec(
        stringResource(R.string.story_settings_field_length),
        StoryChapterLength.entries.map { v ->
            StoryChoiceOption(chapterLengthName(v), v == chapterLengthOf(d.chapterLengthPreference)) { update { it.copy(chapterLengthPreference = v.words) } }
        },
    )
    HubCreativeChoice.INFLUENCE -> StoryChoiceSpec(
        stringResource(R.string.story_settings_field_influence),
        StoryCreationCatalog.chatInfluenceWeights.map { v -> StoryChoiceOption(chatInfluenceName(v), v == d.chatInfluenceWeight) { update { it.copy(chatInfluenceWeight = v) } } },
    )
}

/** 三个自由文本字段的弹层规格（原 `sheet?.let` 块逐式·含两句原注释）。 */
@Composable
internal fun storyCreativeTextSpec(
    field: HubCreativeTextField,
    d: StorySettingsDraft,
    update: ((StorySettingsDraft) -> StorySettingsDraft) -> Unit,
): StoryTextSheetSpec = StoryTextSheetSpec(
    title = stringResource(field.titleRes),
    subtitle = when (field) {
        // 十个预设只作参考：题材本身是自由文本（自定义题材合法存在）
        HubCreativeTextField.GENRE -> stringResource(R.string.story_settings_genre_sub, StoryCreationCatalog.genres.joinToString(" / "))
        else -> null
    },
    placeholder = stringResource(field.placeholderRes),
    initialText = when (field) {
        HubCreativeTextField.GENRE -> d.genre
        HubCreativeTextField.WORLD -> d.worldSetting
        HubCreativeTextField.PLOT -> d.plotDirection
    },
    maxLength = null,
    fillDefaultLabel = null,
    fillDefault = null,
    onConfirm = { value ->
        update {
            when (field) {
                // 题材空白不在这里兜底：persist 侧统一回退原值（绝不落空题材·图纸 §3.1）
                HubCreativeTextField.GENRE -> it.copy(genre = value.trim())
                HubCreativeTextField.WORLD -> it.copy(worldSetting = value)
                HubCreativeTextField.PLOT -> it.copy(plotDirection = value)
            }
        }
    },
)

/** 存模板到顶（原存模板行点击判据·纯·T1）。 */
internal fun storyTemplateAtLimit(templateCount: Int): Boolean = templateCount >= UserStoryTemplatePayload.MAX_USER_TEMPLATES
