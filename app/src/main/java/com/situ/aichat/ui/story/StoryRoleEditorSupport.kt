package com.situ.aichat.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryCharacterRoleEntity
import com.situ.aichat.story.StoryRoleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 角色定位三段（原 StoryRoleEditorSheet 的 `listOf(PROTAGONIST, SUPPORTING, ANTAGONIST)`·两张脸 / 创建屏共用）。 */
internal val storyRoleTypes: List<String> = listOf(StoryRoleType.PROTAGONIST, StoryRoleType.SUPPORTING, StoryRoleType.ANTAGONIST)

/**
 * 角色编辑弹层的全部入参（原 StoryRoleEditorSheet 十三个形参逐个·权限矩阵由构造器按行来源给·图纸 §3.2）：
 * - **本书专属角色**：名字/定位/描述/移出 全开
 * - **关联聊天角色**：名字只读（名字归 `CharacterEntity` 本体管），定位/描述可改、可移出本书
 * - **「我」（isUserRole）**：只开描述——用户角色的存在性由创建时的「我也参演」语义管着，人称段依赖它，设定页不拆台
 */
internal data class StoryRoleEditorConfig(
    val initialName: String,
    val initialType: String,
    val initialDescription: String,
    /** 「私下反差」初值；[showPersona] 为 false 时忽略。 */
    val initialPersona: String,
    /** 是否显示反差栏（「我」这一行与创建屏都不显示·图纸 J5/§4.5）。 */
    val showPersona: Boolean,
    /**
     * AI 起草回调（拿弹层里**当前**的名字与人设去起草）；null = 不给起草钮
     * （创建屏恒 null；书页在没有故事创作 API 配置时也传 null）。返回 null = 起草失败。
     */
    val onDraftPersona: (suspend (name: String, description: String) -> String?)?,
    val isNew: Boolean,
    val nameEditable: Boolean,
    /** 名字不可改时的解释文案（两种锁定原因不同：关联聊天角色 / 「我」这一行）；可改时传 null。 */
    val nameLockedHint: String?,
    val typeEditable: Boolean,
    /** null = 不给移出/删除口（新建态、「我」这一行）。 */
    val onRemove: (() -> Unit)?,
    /** 已落库的行（设定页）移出前先弹确认；创建屏还没落库，直接从表单移除。 */
    val removeNeedsConfirm: Boolean,
    val onSave: (name: String, type: String, description: String, persona: String) -> Unit,
)

/** 书页·编辑已落库的行（原 StoryRolesSection `editing?.let` 块逐式·含原注释）。 */
@Composable
internal fun storyRoleEditConfig(
    role: StoryCharacterRoleEntity,
    onDraftPersona: (suspend (role: StoryCharacterRoleEntity, name: String, description: String) -> String?)?,
    onSave: (StoryCharacterRoleEntity) -> Unit,
    onDelete: (String) -> Unit,
): StoryRoleEditorConfig = StoryRoleEditorConfig(
    initialName = role.roleName,
    initialType = role.roleType,
    initialDescription = role.roleDescription.orEmpty(),
    initialPersona = role.intimatePersona.orEmpty(),
    // 反差是女主侧设定，「我」这一行不给（图纸 §4.5）
    showPersona = !role.isUserRole,
    onDraftPersona = onDraftPersona?.let { draft -> { name, desc -> draft(role, name, desc) } },
    isNew = false,
    // 权限矩阵（图纸 §3.2）：名字归聊天角色本体管；「我」这一行只开描述
    nameEditable = !role.isUserRole && role.characterId == null,
    nameLockedHint = when {
        role.isUserRole -> stringResource(R.string.story_role_editor_name_locked_user)
        role.characterId != null -> stringResource(R.string.story_role_editor_name_locked)
        else -> null
    },
    typeEditable = !role.isUserRole,
    onRemove = if (role.isUserRole) null else ({ onDelete(role.id) }),
    removeNeedsConfirm = true,
    onSave = { name, type, description, persona ->
        onSave(
            role.copy(
                roleName = name,
                roleType = type,
                roleDescription = description.ifBlank { null },
                // 「我」那一行没有反差栏，原值原样带过去，绝不被空草稿清掉
                intimatePersona = if (role.isUserRole) role.intimatePersona else persona.trim().ifBlank { null },
            ),
        )
    },
)

/** 书页·新增（原 `if (adding)` 块逐式·含原注释）。 */
internal fun storyRoleAddConfig(
    storyId: String,
    onDraftPersona: (suspend (role: StoryCharacterRoleEntity, name: String, description: String) -> String?)?,
    onSave: (StoryCharacterRoleEntity) -> Unit,
): StoryRoleEditorConfig = StoryRoleEditorConfig(
    initialName = "",
    initialType = StoryRoleType.SUPPORTING,
    initialDescription = "",
    initialPersona = "",
    showPersona = true,
    // 新角色还没落库，起草只吃弹层里当前填的名字与人设（characterId 恒 null）
    onDraftPersona = onDraftPersona?.let { draft -> { name, desc -> draft(StoryCharacterRoleEntity(storyId = storyId), name, desc) } },
    isNew = true,
    nameEditable = true,
    nameLockedHint = null,
    typeEditable = true,
    onRemove = null,
    removeNeedsConfirm = false,
    onSave = { name, type, description, persona ->
        onSave(
            StoryCharacterRoleEntity(
                storyId = storyId,
                roleName = name,
                roleType = type,
                roleDescription = description.ifBlank { null },
                isUserRole = false,
                characterId = null,
                intimatePersona = persona.trim().ifBlank { null },
            ),
        )
    },
)

/** 创建屏·编辑表单里的专属角色（原 StoryCustomRolesBlock 编辑块逐式·含原注释）。 */
internal fun storyCustomRoleEditConfig(
    draft: CustomRoleDraft,
    index: Int,
    onUpdate: (Int, CustomRoleDraft) -> Unit,
    onRemove: (Int) -> Unit,
): StoryRoleEditorConfig = StoryRoleEditorConfig(
    initialName = draft.name,
    initialType = draft.type,
    initialDescription = draft.description,
    initialPersona = "",
    // 创建屏不给反差栏与起草钮：角色还没落库、上下文太薄，这一栏留给书页（图纸 J5）
    showPersona = false,
    onDraftPersona = null,
    isNew = false,
    nameEditable = true,
    nameLockedHint = null,
    typeEditable = true,
    onRemove = { onRemove(index) },
    removeNeedsConfirm = false,
    onSave = { name, type, description, _ -> onUpdate(index, CustomRoleDraft(name, type, description)) },
)

/** 创建屏·新增（原新增块逐式）。 */
internal fun storyCustomRoleAddConfig(onAdd: (CustomRoleDraft) -> Unit): StoryRoleEditorConfig = StoryRoleEditorConfig(
    initialName = "",
    initialType = StoryRoleType.SUPPORTING,
    initialDescription = "",
    initialPersona = "",
    showPersona = false,
    onDraftPersona = null,
    isNew = true,
    nameEditable = true,
    nameLockedHint = null,
    typeEditable = true,
    onRemove = null,
    removeNeedsConfirm = false,
    onSave = { name, type, description, _ -> onAdd(CustomRoleDraft(name, type, description)) },
)

/** 书页角色行显示名（原 :62）：「我」那一行带后缀。 */
@Composable
internal fun storyRoleRowName(role: StoryCharacterRoleEntity): String =
    role.roleName + if (role.isUserRole) stringResource(R.string.story_settings_role_user_suffix) else ""

/** 书页角色行来源小字（原 :64–68）：「我」无；关联聊天角色 = 「聊天角色」；其余 = 「本书专属」。 */
@Composable
internal fun storyRoleSource(role: StoryCharacterRoleEntity): String? = when {
    role.isUserRole -> null
    role.characterId != null -> stringResource(R.string.story_roles_source_chat)
    else -> stringResource(R.string.story_roles_source_custom)
}

/**
 * 角色编辑弹层的页内态与四个动作（原 StoryRoleEditorSheet :89–94 六个 remember + 三个按钮 / 确认框的 onClick 逐式）。
 * **只存界面态、不存 [StoryRoleEditorConfig]**：四个动作每次由调用方传入**当前**的 config——态对象是 `remember` 住的，
 * 若把 config（里面是回调）也存进来，就会停在首帧那份（记忆 reference-compose-stale-capture）。
 */
@Stable
internal class StoryRoleEditorState(initialName: String, initialType: String, initialDescription: String, initialPersona: String) {
    var name by mutableStateOf(initialName)
    var type by mutableStateOf(initialType)
    var description by mutableStateOf(initialDescription)
    var persona by mutableStateOf(initialPersona)
    var drafting by mutableStateOf(false)
    var confirmRemove by mutableStateOf(false)

    /** 空名字的角色没法被提示词引用，保存置灰（E11）。 */
    val canSave: Boolean get() = name.isNotBlank()

    fun save(config: StoryRoleEditorConfig, onDismiss: () -> Unit) {
        config.onSave(name.trim(), type, description, persona)
        onDismiss()
    }

    /** 已落库的行先弹确认；创建屏直接移除并关弹层。 */
    fun requestRemove(config: StoryRoleEditorConfig, onDismiss: () -> Unit) {
        if (config.removeNeedsConfirm) {
            confirmRemove = true
        } else {
            config.onRemove?.invoke()
            onDismiss()
        }
    }

    fun confirmRemoveAndClose(config: StoryRoleEditorConfig, onDismiss: () -> Unit) {
        confirmRemove = false
        config.onRemove?.invoke()
        onDismiss()
    }

    /** ✦ AI 起草（原 onClick 协程逐式）：拿弹层里**当前**的名字与人设去起草；失败回调 [onFailed]（两张脸各自 Toast）。 */
    fun startDraft(config: StoryRoleEditorConfig, scope: CoroutineScope, onFailed: () -> Unit) {
        val draft = config.onDraftPersona ?: return
        drafting = true
        scope.launch {
            val drafted = draft(name.trim(), description)
            if (drafted != null) persona = drafted else onFailed()
            drafting = false
        }
    }
}

/** 初值只在弹层首次组合时取（同原六个无 key 的 remember）。 */
@Composable
internal fun rememberStoryRoleEditorState(config: StoryRoleEditorConfig): StoryRoleEditorState =
    remember { StoryRoleEditorState(config.initialName, config.initialType, config.initialDescription, config.initialPersona) }
