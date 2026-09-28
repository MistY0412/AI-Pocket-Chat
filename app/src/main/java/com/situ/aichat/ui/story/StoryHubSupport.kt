package com.situ.aichat.ui.story

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.model.CustomStoryPrompts
import com.situ.aichat.story.StoryEditableField
import com.situ.aichat.story.StoryFieldKind
import com.situ.aichat.story.StoryGlobalCraftValues
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.story.StoryUpdateMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 书页三件进页事（原 StoryBookHubScreen :99–110 逐字·含原注释·两张脸共用）：Toast / 归档删除成功弹回 / 进过屏后书没了安全退出。 */
@Composable
internal fun StoryBookHubEffects(viewModel: StorySettingsViewModel, story: StoryEntity?, onStoryGone: () -> Unit) {
    StoryToastEvents(viewModel.toastEvents)
    // 归档成功 / 删除成功 → 这本书在书页里已经没得看了，弹回书架（= 创建流的既有回法）。
    LaunchedEffect(Unit) { viewModel.exitEvents.collect { onStoryGone() } }
    // 兜底：进过屏之后 story 变 null（别处把它删了）→ 安全退出，不停在空屏上（E8）。
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(story) {
        if (story != null) loadedOnce = true else if (loadedOnce) onStoryGone()
    }
}

/** 关闭（原 :112–114 逐字·含原注释 + BackHandler）：落库草稿后返回（在屏幕协程内 await）；保存失败不返回。 */
@Composable
internal fun rememberStoryHubClose(viewModel: StorySettingsViewModel, scope: CoroutineScope, onBack: () -> Unit): () -> Unit {
    // 关闭：落库草稿后返回（在屏幕协程内 await，避免 VM scope 被 pop 取消截断写入）；保存失败不返回。
    val close: () -> Unit = { scope.launch { if (viewModel.persist()) onBack() } }
    BackHandler { close() }
    return close
}

/** 头部副行（原 BookHeader :214–217 三式）：状态 → + 第 N 章 → + 本弧第 K 章。 */
@Composable
internal fun storyHubStatusLine(story: StoryEntity): String {
    val progress = storyHubProgress(story)
    var line = stringResource(storyStatusDisplayNameRes(story.status))
    progress.chapterNumber?.let { line = stringResource(R.string.story_hub_progress_chapter, line, it) }
    progress.arcIndex?.let { line = stringResource(R.string.story_hub_progress_arc, line, it) }
    return line
}

/** 双 Tab 文案（原 :141·纯·T1）。 */
@StringRes
internal fun storyHubTabLabelRes(index: Int): Int = if (index == 0) R.string.story_hub_tab_archive else R.string.story_hub_tab_settings

/** 全局三值（原 :164–168）。 */
internal fun storyHubGlobals(settings: AppSettings): StoryGlobalCraftValues = StoryGlobalCraftValues(
    sceneBeats = settings.storySceneBeats,
    tasteProfile = settings.storyTasteProfile,
    bannedExpressions = settings.storyBannedExpressions,
)

/** 设定 Tab 回调表接线（原 :172–188 逐字·含两句「由屏幕协程 await」原注释·两张脸共用）。 */
internal fun storyHubSettingsCallbacks(
    viewModel: StorySettingsViewModel,
    scope: CoroutineScope,
    hasCreationConfig: Boolean,
    onOpenField: (String) -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onBack: () -> Unit,
): StoryHubSettingsCallbacks = StoryHubSettingsCallbacks(
    onOpenField = { field -> onOpenField(field.key) },
    onOpenGlobalSettings = onOpenGlobalSettings,
    onUpdateDraft = viewModel::updateDraft,
    onSaveRole = viewModel::saveRole,
    onDeleteRole = viewModel::deleteRole,
    onDraftPersona = if (hasCreationConfig) viewModel::draftPersona else null,
    onChapterChoicesChange = viewModel::setChapterChoicesEnabled,
    onSceneSnapshotChange = viewModel::setSceneSnapshotEnabled,
    onWorldInfoChange = viewModel::setWorldInfoEnabled,
    onReminderChange = viewModel::setReminderEnabled,
    onSaveTemplate = viewModel::saveAsTemplate,
    onArchive = viewModel::archiveStory,
    onDelete = viewModel::deleteStory,
    onContinue = { scope.launch { viewModel.persist(); viewModel.continueOrResume(); onBack() } },
    onRestart = { scope.launch { viewModel.persist(); viewModel.restartStory(); onBack() } },
)

/** 写法七行的字段（原 CraftGroup :105·纯·T1）= 非档案类字段，注册表序。 */
internal fun storyHubCraftFields(): List<StoryEditableField> = StoryEditableField.entries.filter { it.kind != StoryFieldKind.ARCHIVE }

/** 写法行挂 NEW（原 :109·纯·T1）。 */
internal fun storyHubCraftFieldIsNew(field: StoryEditableField): Boolean =
    field == StoryEditableField.SCENE_BEATS || field == StoryEditableField.TASTE_PROFILE

/**
 * 本书 prompts 解码（原 CraftGroup / ToggleGroup 两处 remember 同式合一）：
 * 七行值标共用同一份解码结果：不缓存的话每次重组要把同一段 JSON 解七遍。
 */
@Composable
internal fun rememberStoryPrompts(story: StoryEntity): CustomStoryPrompts? =
    remember(story.customPromptsJson) { CustomStoryPrompts.decode(story.customPromptsJson) }

/** 章末给选项开关的当前值（原 ToggleGroup :142·纯·T1）：没解出 prompts = 关。 */
internal fun storyChapterChoicesOn(prompts: CustomStoryPrompts?): Boolean = prompts?.effectiveChapterChoices == true

/** 场景状态快照开关的当前值（原 :148·纯·T1）：没解出 prompts = 开。 */
internal fun storySceneSnapshotOn(prompts: CustomStoryPrompts?): Boolean = prompts?.effectiveSceneSnapshot != false

/** 追更模式（原 SerialManageGroup :179 / :192·纯·T1）。 */
internal fun storyDraftChase(d: StorySettingsDraft): Boolean = d.updateMode == StoryUpdateMode.CHASE

internal fun storyDraftWithChase(d: StorySettingsDraft, on: Boolean): StorySettingsDraft =
    d.copy(updateMode = if (on) StoryUpdateMode.CHASE else StoryUpdateMode.FREE)

/** 解锁时间行的值（原 :197·纯·T1）。 */
internal fun storyUnlockTimeText(d: StorySettingsDraft): String = "%02d:%02d".format(d.unlockHour, d.unlockMinute)

/** 更新提醒行的值（原 :202·纯·T1）。 */
@StringRes
internal fun storyReminderLabelRes(enabled: Boolean): Int = if (enabled) R.string.story_settings_reminder_on else R.string.action_close

/** 卡外连载操作钮（原 :213 条件·纯·T1）：完结 = 继续 + 重开；暂停 = 恢复；其余不出。 */
internal enum class StorySerialOps { COMPLETED, PAUSED }

internal fun storySerialOps(story: StoryEntity): StorySerialOps? = when (story.status) {
    StoryStatus.COMPLETED -> StorySerialOps.COMPLETED
    StoryStatus.PAUSED -> StorySerialOps.PAUSED
    else -> null
}
