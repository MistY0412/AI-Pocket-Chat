package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.prompt.ZodiacCalculator
import com.situ.aichat.ui.character.AgeMode
import com.situ.aichat.ui.character.CharacterEditState
import com.situ.aichat.ui.character.yearsSince
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliChip
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliInputRow
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliSegmentRow
import com.situ.aichat.ui.liuli.page.LiuliTextActionRow
import com.situ.aichat.ui.liuli.page.LiuliValueRow
import java.text.DateFormat
import java.util.Date

/** 多行文本框最小高（§4.4 锁定值·同契约 §6.5「多行组 96」）。 */
internal val TEXT_AREA_MIN = 96.dp

/** 多行字段行（§4.4）：说明标签在上、[LiuliField] 多行框居中、字段说明小字在下。 */
@Composable
internal fun LiuliTextAreaRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    hint: String?,
    divider: Boolean = true,
) {
    val colors = AppTheme.colors
    LiuliRowBase(
        divider = divider,
        minHeight = 0.dp,
        verticalPadding = LiuliPageGeometry.groupPadH,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = AppTypography.secondary, color = colors.text.secondary)
            LiuliField(value, onValueChange, placeholder = placeholder, singleLine = false, minHeight = TEXT_AREA_MIN)
            hint?.let { Text(it, style = AppTypography.caption, color = colors.text.secondary) }
        }
    }
}

/** 基础信息组（§4.4）：名字 / 性别 / 生日（+ 星座 + 清除）/ 年龄模式 / 固定年龄 或 当前年龄。 */
@Composable
internal fun LiuliEditBasicGroup(
    state: CharacterEditState,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    onOpenBirthday: () -> Unit,
    onClearBirthday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_basic),
        footer = stringResource(R.string.char_footer_basic),
    ) {
        LiuliInputRow(
            label = stringResource(R.string.char_field_name),
            value = state.name,
            onValueChange = { v -> onUpdate { it.copy(name = v) } },
            placeholder = stringResource(R.string.char_hint_name),
            divider = false,
        )
        LiuliInputRow(
            label = stringResource(R.string.char_field_gender),
            value = state.gender,
            onValueChange = { v -> onUpdate { it.copy(gender = v) } },
            placeholder = stringResource(R.string.char_hint_gender),
        )
        val millis = state.birthdayMillis
        if (millis == null) {
            LiuliNavRow(
                title = stringResource(R.string.char_field_birthday),
                value = stringResource(R.string.char_birthday_unset),
                onClick = onOpenBirthday,
            )
        } else {
            val zodiac = remember(millis) { ZodiacCalculator.zodiacSign(millis) }
            LiuliNavRow(
                title = stringResource(R.string.char_field_birthday),
                value = DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis)),
                subtitle = if (zodiac.isNotEmpty()) "${stringResource(R.string.char_field_zodiac)}：$zodiac" else null,
                onClick = onOpenBirthday,
            )
            LiuliTextActionRow(stringResource(R.string.char_birthday_clear), onClick = onClearBirthday)
        }
        LiuliSegmentRow(
            title = stringResource(R.string.char_age_label),
            options = listOf(AgeMode.GROWING, AgeMode.FIXED),
            selected = state.ageModeRaw,
            label = { stringResource(if (it == AgeMode.GROWING) R.string.char_age_growing else R.string.char_age_fixed) },
            onSelect = { mode -> onUpdate { it.copy(ageModeRaw = mode) } },
        )
        if (state.ageModeRaw == AgeMode.FIXED) {
            LiuliInputRow(
                label = stringResource(R.string.char_field_fixed_age),
                value = state.fixedAge,
                onValueChange = { v -> onUpdate { it.copy(fixedAge = v.filter(Char::isDigit)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        } else if (millis != null) {
            val age = remember(millis) { yearsSince(millis) }
            if (age > 0) {
                LiuliValueRow(
                    title = stringResource(R.string.char_age_current),
                    value = stringResource(R.string.char_age_value, age),
                )
            }
        }
    }
}

/** 关系组（§4.4）：关系字段 + 快捷标签横排（点即写入·选中态跟字段一致）；脚注编辑 / 新建两句。 */
@Composable
internal fun LiuliEditRelationshipGroup(
    state: CharacterEditState,
    isEditing: Boolean,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_relationship_def),
        footer = stringResource(if (isEditing) R.string.char_footer_relationship_edit else R.string.char_footer_relationship_create),
    ) {
        LiuliInputRow(
            label = stringResource(R.string.char_section_relationship_def),
            value = state.relationshipName,
            onValueChange = { v -> onUpdate { it.copy(relationshipName = v) } },
            placeholder = stringResource(R.string.char_hint_relationship),
            divider = false,
        )
        val quickTags = stringArrayResource(R.array.char_relationship_quick_tags)
        LiuliRowBase(minHeight = 0.dp, verticalPadding = 10.dp) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickTags.forEach { tag ->
                    LiuliChip(
                        selected = state.relationshipName == tag,
                        onClick = { onUpdate { s -> s.copy(relationshipName = tag) } },
                        label = tag,
                    )
                }
            }
        }
    }
}

/** 角色设定组（§4.4）：身份（单行）+ 性格特点 / 外貌描述 / 背景故事（多行·各带字段说明）。 */
@Composable
internal fun LiuliEditSetupGroup(
    state: CharacterEditState,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_setup),
        footer = stringResource(R.string.char_footer_setup),
    ) {
        LiuliInputRow(
            label = stringResource(R.string.char_field_occupation),
            value = state.occupation,
            onValueChange = { v -> onUpdate { it.copy(occupation = v) } },
            placeholder = stringResource(R.string.char_hint_occupation),
            divider = false,
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_personality),
            value = state.personalityDescription,
            onValueChange = { v -> onUpdate { it.copy(personalityDescription = v) } },
            placeholder = stringResource(R.string.char_hint_personality),
            hint = stringResource(R.string.char_footer_personality),
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_appearance),
            value = state.appearanceDescription,
            onValueChange = { v -> onUpdate { it.copy(appearanceDescription = v) } },
            placeholder = stringResource(R.string.char_hint_appearance),
            hint = stringResource(R.string.char_footer_appearance),
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_backstory),
            value = state.backstory,
            onValueChange = { v -> onUpdate { it.copy(backstory = v) } },
            placeholder = stringResource(R.string.char_hint_backstory),
            hint = stringResource(R.string.char_footer_backstory),
        )
    }
}

/** 交流风格组（§4.4）：口头禅（单行）+ 说话风格 / 典型对话 / 兴趣爱好（多行）。 */
@Composable
internal fun LiuliEditCommunicationGroup(
    state: CharacterEditState,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_communication),
        footer = stringResource(R.string.char_footer_communication),
    ) {
        LiuliInputRow(
            label = stringResource(R.string.char_field_catchphrases),
            value = state.catchphrases,
            onValueChange = { v -> onUpdate { it.copy(catchphrases = v) } },
            placeholder = stringResource(R.string.char_hint_catchphrases),
            divider = false,
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_speaking),
            value = state.speakingStyle,
            onValueChange = { v -> onUpdate { it.copy(speakingStyle = v) } },
            placeholder = stringResource(R.string.char_hint_speaking),
            hint = stringResource(R.string.char_footer_speaking),
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_examples),
            value = state.exampleDialogues,
            onValueChange = { v -> onUpdate { it.copy(exampleDialogues = v) } },
            placeholder = stringResource(R.string.char_hint_examples),
            hint = stringResource(R.string.char_footer_examples),
        )
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_interests),
            value = state.initialInterests,
            onValueChange = { v -> onUpdate { it.copy(initialInterests = v) } },
            placeholder = stringResource(R.string.char_hint_interests),
            hint = stringResource(R.string.char_footer_interests),
        )
    }
}

/** 高级组（§4.4）：自定义设定（多行·脚注即说明）。 */
@Composable
internal fun LiuliEditAdvancedGroup(
    state: CharacterEditState,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_advanced),
        footer = stringResource(R.string.char_footer_system_prompt),
    ) {
        LiuliTextAreaRow(
            label = stringResource(R.string.char_field_system_prompt),
            value = state.systemPrompt,
            onValueChange = { v -> onUpdate { it.copy(systemPrompt = v) } },
            placeholder = stringResource(R.string.char_hint_system_prompt),
            hint = null,
            divider = false,
        )
    }
}
