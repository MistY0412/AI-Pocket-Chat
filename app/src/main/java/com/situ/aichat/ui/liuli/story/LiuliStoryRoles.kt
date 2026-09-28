package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryCharacterRoleEntity
import com.situ.aichat.ui.designsystem.AppProfileIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliTextActionRow
import com.situ.aichat.ui.story.CustomRoleDraft
import com.situ.aichat.ui.story.RoleTypeBadge
import com.situ.aichat.ui.story.storyCustomRoleAddConfig
import com.situ.aichat.ui.story.storyCustomRoleEditConfig
import com.situ.aichat.ui.story.storyRoleAddConfig
import com.situ.aichat.ui.story.storyRoleEditConfig
import com.situ.aichat.ui.story.storyRoleRowName
import com.situ.aichat.ui.story.storyRoleSource

/**
 * 琉璃参演角色两处（琉璃 2.0 卷六·三·上 §4.12）：书页「参演角色」组 + 创建屏「本书专属角色」组；
 * 行名 / 来源 / 权限矩阵四套配置一律调暖陶共用件，弹层换 [LiuliStoryRoleEditorSheet]。
 */
@Composable
internal fun LiuliStoryRolesGroup(
    storyId: String,
    roles: List<StoryCharacterRoleEntity>,
    onSave: (StoryCharacterRoleEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDraftPersona: (suspend (role: StoryCharacterRoleEntity, name: String, description: String) -> String?)?,
) {
    var editing by remember { mutableStateOf<StoryCharacterRoleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    LiuliGroup(header = stringResource(R.string.story_settings_roles), footer = stringResource(R.string.story_roles_footer)) {
        if (roles.isEmpty()) {
            LiuliRowBase(divider = false) {
                Text(stringResource(R.string.story_settings_no_roles), style = AppTypography.secondary, color = AppTheme.colors.text.secondary)
            }
        } else {
            roles.forEachIndexed { i, role ->
                LiuliStoryRoleRow(storyRoleRowName(role), role.roleType, storyRoleSource(role), onClick = { editing = role }, divider = i > 0)
            }
        }
        LiuliTextActionRow(title = stringResource(R.string.story_roles_add), onClick = { adding = true })
    }
    editing?.let { role -> LiuliStoryRoleEditorSheet(storyRoleEditConfig(role, onDraftPersona, onSave, onDelete)) { editing = null } }
    if (adding) LiuliStoryRoleEditorSheet(storyRoleAddConfig(storyId, onDraftPersona, onSave)) { adding = false }
}

/** 创建屏「本书专属角色」组（开书前攒在表单里·删除无需确认）。 */
@Composable
internal fun LiuliStoryCustomRolesGroup(
    customRoles: List<CustomRoleDraft>,
    onAdd: (CustomRoleDraft) -> Unit,
    onUpdate: (Int, CustomRoleDraft) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var editingIndex by remember { mutableIntStateOf(-1) }
    var adding by remember { mutableStateOf(false) }
    LiuliGroup(header = stringResource(R.string.story_create_custom_roles_label), footer = stringResource(R.string.story_create_custom_roles_footer)) {
        customRoles.forEachIndexed { i, d ->
            LiuliStoryRoleRow(d.name, d.type, stringResource(R.string.story_roles_source_custom), onClick = { editingIndex = i }, divider = i > 0)
        }
        LiuliTextActionRow(title = stringResource(R.string.story_create_add_custom_role), onClick = { adding = true }, divider = customRoles.isNotEmpty())
    }
    customRoles.getOrNull(editingIndex)?.let { d -> LiuliStoryRoleEditorSheet(storyCustomRoleEditConfig(d, editingIndex, onUpdate, onRemove)) { editingIndex = -1 } }
    if (adding) LiuliStoryRoleEditorSheet(storyCustomRoleAddConfig(onAdd)) { adding = false }
}

/** 角色行：名（单行）+ 定位徽章（跨脸复用 [RoleTypeBadge]）+ 来源小字 + chevron。 */
@Composable
private fun LiuliStoryRoleRow(name: String, roleType: String, source: String?, onClick: () -> Unit, divider: Boolean) {
    val c = AppTheme.colors
    LiuliRowBase(onClick = onClick, divider = divider) {
        Text(
            name,
            style = AppTypography.body.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.W400),
            color = c.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        RoleTypeBadge(roleType)
        source?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = AppTypography.caption, color = c.text.secondary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(AppProfileIcons.ChevronRight, contentDescription = null, tint = c.text.tertiary, modifier = Modifier.size(STORY_CHEVRON))
    }
}
