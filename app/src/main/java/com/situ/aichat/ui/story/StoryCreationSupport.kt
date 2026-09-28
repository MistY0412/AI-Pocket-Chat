package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.story.StoryCreationCatalog
import com.situ.aichat.story.StoryCreationLogic
import com.situ.aichat.story.StoryNarrativePerson
import com.situ.aichat.story.StoryRoleType
import com.situ.aichat.story.StoryWritingTechniques

/** 六个可弹层编辑的文本字段（原 StoryCreationScreen private enum 逐字搬来改 internal·含 4000 上限原注释）。 */
internal enum class StoryEditField(val titleRes: Int, val placeholderRes: Int, val subtitleRes: Int?, val maxLength: Int?) {
    // 世界观/剧情方向上限 4000（2026-08-04 用户拍板·由 2000 放宽）：书页后编辑路本就不限长（HubCreativeTextField
    // 传 maxLength=null），此处只是创建时闸口，放宽不产生「后编辑被截」的不一致。
    WORLD(R.string.story_field_world_title, R.string.story_field_world_placeholder, null, 4000),
    PLOT(R.string.story_field_plot_title, R.string.story_field_plot_placeholder, null, 4000),
    GENRE_TECH(R.string.story_field_genre_tech_title, R.string.story_field_genre_tech_placeholder, R.string.story_field_genre_tech_subtitle, null),
    WRITER(R.string.story_field_writer_title, R.string.story_field_writer_placeholder, R.string.story_field_writer_subtitle, null),
    RULES(R.string.story_field_rules_title, R.string.story_field_rules_placeholder, R.string.story_field_rules_subtitle, null),
    PERSONA(R.string.story_field_persona_title, R.string.story_field_persona_placeholder, null, 2000),
}

internal fun StoryCreationForm.valueFor(field: StoryEditField) = when (field) {
    StoryEditField.WORLD -> worldSetting
    StoryEditField.PLOT -> plotDirection
    StoryEditField.GENRE_TECH -> customGenreTechniques
    StoryEditField.WRITER -> customWriterIdentity
    StoryEditField.RULES -> customWritingRules
    StoryEditField.PERSONA -> customUserPersona
}

internal fun StoryCreationForm.withField(field: StoryEditField, value: String) = when (field) {
    StoryEditField.WORLD -> copy(worldSetting = value)
    StoryEditField.PLOT -> copy(plotDirection = value)
    StoryEditField.GENRE_TECH -> copy(customGenreTechniques = value)
    StoryEditField.WRITER -> copy(customWriterIdentity = value)
    StoryEditField.RULES -> copy(customWritingRules = value)
    StoryEditField.PERSONA -> copy(customUserPersona = value)
}

/** 人称三项（原创建屏与书页人称弹窗两处同序：第二人称（默认）→ 第一 → 第三）。 */
internal val storyNarrativeOptions: List<String> = listOf(StoryNarrativePerson.SECOND, StoryNarrativePerson.FIRST, StoryNarrativePerson.THIRD)

/** 人设来源两项（原 :300）。 */
internal val storyPersonaSources: List<UserPersonaSource> = listOf(UserPersonaSource.PROFILE, UserPersonaSource.CUSTOM)

/** 参考模板选项（原 :242）：null = 不参考 + 十个题材。 */
internal fun storyReferenceGenreOptions(): List<String?> = listOf<String?>(null) + StoryCreationCatalog.genres

/** 「开始创作」可点（原 :113–120·纯·T1）：D-7：本书专属角色（非空名的那些）同样满足「至少一个角色」——纯专属角色也能开书。 */
internal fun storyCreationCanCreate(form: StoryCreationForm): Boolean = StoryCreationLogic.canCreateStory(
    form.isCustomGenre,
    form.customGenreName,
    form.includeUserRole,
    form.selectedRoles.size,
    form.customRoles.count { it.name.isNotBlank() },
)

/** 六个文本字段的弹层规格（原 `editingField?.let` 块逐式·含「只填风格原则」原注释）。 */
@Composable
internal fun storyEditFieldSheetSpec(
    field: StoryEditField,
    form: StoryCreationForm,
    update: ((StoryCreationForm) -> StoryCreationForm) -> Unit,
): StoryTextSheetSpec = StoryTextSheetSpec(
    title = stringResource(field.titleRes),
    subtitle = field.subtitleRes?.let { stringResource(it) },
    placeholder = stringResource(field.placeholderRes),
    initialText = form.valueFor(field),
    maxLength = field.maxLength,
    fillDefaultLabel = if (field == StoryEditField.WRITER || field == StoryEditField.RULES) stringResource(R.string.story_editor_fill_default) else null,
    fillDefault = when (field) {
        StoryEditField.WRITER -> { { StoryWritingTechniques.writerIdentity(form.writingStyle) } }
        // 只填风格原则：忌口由「文字忌口」字段单独负责，两者正交（修双重注入·提案 §5）
        StoryEditField.RULES -> { { StoryWritingTechniques.writingPrinciples } }
        else -> null
    },
    onConfirm = { value -> update { it.withField(field, value) } },
)

/** 题材 chip 选中（原 GenreSection :207·纯·T1）。 */
internal fun storyGenreChipSelected(form: StoryCreationForm, genre: String): Boolean = form.selectedGenre == genre && !form.isCustomGenre

/** 参考模板切换（原 :246–250 逐式·纯·T1）：选了题材 → 连带填入该题材技法全文；选「无」→ 只清参考、技法留着。 */
internal fun storyFormWithReferenceGenre(form: StoryCreationForm, genre: String?): StoryCreationForm =
    if (genre != null) {
        form.copy(referenceGenre = genre, customGenreTechniques = StoryWritingTechniques.genreTechniques(genre))
    } else {
        form.copy(referenceGenre = null)
    }

/** 「我也参演」开关（原 :281–284 逐式·纯·T1）：打开且有昵称 → 角色名带上昵称，否则名字不动。 */
internal fun storyFormWithIncludeUser(form: StoryCreationForm, on: Boolean, nickname: String): StoryCreationForm {
    val name = if (on && nickname.isNotBlank()) nickname else form.userRoleName
    return form.copy(includeUserRole = on, userRoleName = name)
}

/** 选 / 退一个聊天角色（原 CharacterRow 开关 :352–362 逐式·纯·T1）：选 = 按 [defaultRoleType] 定位；退 = 连带清掉定位与描述。 */
internal fun storyFormToggleCharacter(form: StoryCreationForm, uuid: String, on: Boolean): StoryCreationForm {
    val roles = form.selectedRoles.toMutableMap()
    val descs = form.roleDescriptions.toMutableMap()
    if (on) {
        roles[uuid] = defaultRoleType(form)
    } else {
        roles.remove(uuid)
        descs.remove(uuid)
    }
    return form.copy(selectedRoles = roles, roleDescriptions = descs)
}

/** 默认角色定位：首个选中角色（无其他角色 + 用户未参演）→ 主角，否则配角（1:1 iOS defaultRoleType）。 */
private fun defaultRoleType(form: StoryCreationForm): String =
    if (form.selectedRoles.isEmpty() && !form.includeUserRole) StoryRoleType.PROTAGONIST else StoryRoleType.SUPPORTING

/** 人设来源行的显示文案（原 :302·纯·T1）。 */
@StringRes
internal fun storyPersonaSourceLabelRes(source: UserPersonaSource): Int =
    if (source == UserPersonaSource.PROFILE) R.string.story_create_persona_profile else R.string.story_create_persona_custom

/** 文本行标题变淡（原 TextEditRow :499·纯·T1）：没值且不是「状态」式值时标题走次级色。 */
internal fun storyTextEditTitleMuted(value: String, showValueAsStatus: Boolean): Boolean = value.isEmpty() && !showValueAsStatus
