package com.situ.aichat.ui.liuli.story

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.PersonaPresets
import com.situ.aichat.ui.designsystem.AppProfileIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliChip
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.liuli.designsystem.liuliCardMaterial
import com.situ.aichat.ui.liuli.page.LiuliMenuRow
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRadioRow
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.story.StoryChoiceSpec
import com.situ.aichat.ui.story.storyTemplateNameValid
import com.situ.aichat.ui.story.storyTextEditTitleMuted

// 故事屏（琉璃 2.0 卷六·三·上 §4.1）：本卷常量 / 材质 / 共用琉璃小件。

/** 故事书卡 / 书头 / 档案节 / 章节卡 / 摘句卡的卡面：琉璃半透明卡（圆角 20 = 卷六·二动态卡同值）。 */
internal fun Modifier.liuliStoryCard(dark: Boolean): Modifier = liuliCardMaterial(LiuliShapes.medium, dark)

/** 继承预览 / 小笺底（= 卷六·二 `liuliMomentNote`：segTrack · 圆角 10）。 */
internal fun Modifier.liuliStoryNote(dark: Boolean): Modifier = clip(LiuliShapes.small).background(LiuliMaterials.segTrack(dark))

/** chevron（= `LiuliNavRow` 同件同尺寸：`AppProfileIcons.ChevronRight` 12 · `text.tertiary`）。 */
internal val STORY_CHEVRON = 12.dp

/** 单钮提示框：取代暖陶五处「保存 / 创建失败」「出错」单钮 `AppDialog`。 */
@Composable
internal fun LiuliStoryAlert(title: String, body: String, onDismiss: () -> Unit) {
    LiuliDialog(onDismissRequest = onDismiss, title = title, body = body, confirmText = stringResource(R.string.action_confirm), onConfirm = onDismiss)
}

/** 已完结书删除确认（文案同暖陶 `StoryArchivedDeleteDialog`：正文带书名 · 红确认钮）。 */
@Composable
internal fun LiuliStoryArchivedDeleteDialog(story: StoryEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LiuliDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.story_hub_delete_title),
        body = stringResource(R.string.story_archived_delete_body, story.title),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        confirmDanger = true,
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
    )
}

/** 模板命名 / 重命名框（光标在首·同暖陶；名字全空白时保存禁用）：用在模板重命名（[message] = null）与书页存模板。 */
@Composable
internal fun LiuliStoryNameDialog(title: String, message: String?, initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initialName)) }
    LiuliDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = stringResource(R.string.action_save),
        onConfirm = { onConfirm(value.text) },
        confirmEnabled = storyTemplateNameValid(value.text),
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it, style = AppTypography.secondary, color = LiuliTheme.onGlass.secondary) }
                LiuliField(value = value, onValueChange = { value = it }, label = stringResource(R.string.story_save_template_name_label), singleLine = true)
            }
        },
    )
}

/** 写作身份三档预设（取代暖陶 Tonal 钮排·三档文案单源 [PersonaPresets]）：点一下把全文填进草稿。 */
@Composable
internal fun LiuliStoryPresetChips(@StringRes hintRes: Int, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonaPresets.all.forEach { (labelRes, text) ->
                LiuliChip(selected = false, onClick = { onPick(text) }, label = stringResource(labelRes), role = Role.Button)
            }
        }
        Text(stringResource(hintRes), style = AppTypography.caption, color = AppTheme.colors.text.tertiary)
    }
}

/** 单选框（取代暖陶 `HubChoiceDialog`·同一份规格）：点任一项（含当前项）写回并关框——`notifyWhenSelected` 同暖陶 ChoiceOptionRow（§11 D-4）。 */
@Composable
internal fun LiuliStoryChoiceDialog(spec: StoryChoiceSpec, onDismiss: () -> Unit) {
    LiuliDialog(
        onDismissRequest = onDismiss,
        title = spec.title,
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            spec.options.forEachIndexed { i, o ->
                LiuliRadioRow(title = o.label, selected = o.selected, onSelect = { o.onSelect(); onDismiss() }, divider = i > 0, notifyWhenSelected = true)
            }
        },
    )
}

/** 文本行（取代暖陶 `TextEditRow`）：标题（无值且非状态式时变淡）+ 两行值预览 + chevron；16 / 21 / 13 / 2 / 8 = `LiuliNavRow` 同值。 */
@Composable
internal fun LiuliStoryTextEditRow(title: String, value: String, onClick: () -> Unit, divider: Boolean, showValueAsStatus: Boolean = false) {
    val colors = AppTheme.colors
    val hasValue = value.isNotEmpty()
    LiuliRowBase(
        onClick = onClick,
        minHeight = if (hasValue) LiuliPageGeometry.rowTwoLine else LiuliPageGeometry.rowMin,
        verticalPadding = if (hasValue) LiuliPageGeometry.rowTwoLinePad else 0.dp,
        divider = divider,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTypography.body.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400),
                color = if (storyTextEditTitleMuted(value, showValueAsStatus)) colors.text.secondary else colors.text.primary,
            )
            if (hasValue) {
                Text(
                    value,
                    style = AppTypography.secondary.copy(fontSize = 13.sp),
                    color = colors.text.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(AppProfileIcons.ChevronRight, contentDescription = null, tint = colors.text.tertiary, modifier = Modifier.size(STORY_CHEVRON))
    }
}

/** 下拉行（取代暖陶 `LabeledDropdown`）：自持展开态；点条目后菜单自动关。 */
@Composable
internal fun <T> LiuliStoryMenuRow(
    title: String,
    options: List<T>,
    selected: T,
    display: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    divider: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    LiuliMenuRow(
        title = title,
        value = display(selected),
        options = options.map { o -> LiuliMenuEntry(display(o), selected = o == selected, onClick = { onSelect(o) }) },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        divider = divider,
    )
}
