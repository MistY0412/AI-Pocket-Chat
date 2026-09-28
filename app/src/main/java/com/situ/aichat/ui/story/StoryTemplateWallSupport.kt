package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserStoryTemplateEntity
import com.situ.aichat.story.StoryCreationLogic
import com.situ.aichat.story.StoryRoleType

/** 模板墙 `_head` 区头文案（原 StoryTemplateWallScreen :129–131·纯·T1）：没有我的模板 = 墙副标题，有 = 「我的模板」。 */
@StringRes
internal fun storyWallHeadRes(myTemplates: List<UserStoryTemplateEntity>): Int =
    if (myTemplates.isEmpty()) R.string.story_wall_subtitle else R.string.story_my_templates_header

/** 模板名可存（原 TemplateNameDialog / HubSaveTemplateDialog 同式 `value.text.trim().isNotEmpty()`·纯·T1）。 */
internal fun storyTemplateNameValid(name: String): Boolean = name.trim().isNotEmpty()

/** 开书弹层的选角态（原 StoryOpenBookSheet :73–89 逐式·两张脸共用）：主演单选 + 配角多选 + 我也入场 + 配角区展开。 */
@Stable
internal class StoryOpenBookState(initialLeadId: String?) {
    var leadId by mutableStateOf(initialLeadId)
    var supportingIds by mutableStateOf(emptySet<String>())
    var includeUserRole by mutableStateOf(true)
    var showSupporting by mutableStateOf(false)

    val selectedRoles: Map<String, String>
        get() = buildMap {
            leadId?.let { put(it, StoryRoleType.PROTAGONIST) }
            supportingIds.forEach { put(it, StoryRoleType.SUPPORTING) }
        }

    val canStart: Boolean
        get() = StoryCreationLogic.canCreateStory(
            isCustomGenre = false,
            customGenreName = "",
            includeUserRole = includeUserRole,
            selectedCharacterCount = selectedRoles.size,
        )

    /** 点主演头像（原 :137–140）：再点一次取消；被点的人同时退出配角。 */
    fun toggleLead(uuid: String) {
        leadId = if (leadId == uuid) null else uuid
        supportingIds = supportingIds - uuid
    }

    /** 点配角头像（原 :171–173）。 */
    fun toggleSupporting(uuid: String) {
        supportingIds = if (supportingIds.contains(uuid)) supportingIds - uuid else supportingIds + uuid
    }

    /** 配角候选（原 :166）= 除主演外的所有角色。 */
    fun supportingCandidates(characters: List<CharacterEntity>): List<CharacterEntity> = characters.filter { it.uuid != leadId }
}

@Composable
internal fun rememberStoryOpenBookState(characters: List<CharacterEntity>): StoryOpenBookState =
    remember { StoryOpenBookState(characters.firstOrNull()?.uuid) }

/** 「加配角 ›」入口门槛（原 :145·纯·T1）。 */
internal fun storyOpenBookShowAddSupport(characters: List<CharacterEntity>): Boolean = characters.size >= 2
