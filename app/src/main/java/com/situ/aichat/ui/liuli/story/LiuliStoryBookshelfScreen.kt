package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryGenerationTaskManager
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBarIcons
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageCircleAction
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.story.NewStoryCard
import com.situ.aichat.ui.story.StoryArchiveSection
import com.situ.aichat.ui.story.StoryBookshelfEffects
import com.situ.aichat.ui.story.StoryBookshelfViewModel
import com.situ.aichat.ui.story.StoryCard
import com.situ.aichat.ui.story.StoryCardLogic
import com.situ.aichat.ui.story.StoryCardMenuAction
import com.situ.aichat.ui.story.StoryEmptyState
import com.situ.aichat.ui.story.StoryShelfMenuScrim
import com.situ.aichat.ui.story.dispatch
import com.situ.aichat.ui.story.menuActionLabel
import com.situ.aichat.ui.story.storyActiveById
import com.situ.aichat.ui.story.storyArchivedById
import com.situ.aichat.ui.story.storyMenuStale
import com.situ.aichat.ui.story.storyShelfSplit
import com.situ.aichat.ui.theme.LocalIsDarkTheme

private val SHELF_GAP = 12.dp // 书卡间距（= 暖陶 spacedBy 12）；下两行 = 暖陶菜单锚盒（贴卡右缘、标题排下方展开）
private val CARD_MENU_TOP = 48.dp
private val CARD_MENU_END = 12.dp

/** 书架要画的数据（无 VM 页的入参·测试直接造）。 */
@Immutable
internal data class LiuliShelfData(
    val stories: List<StoryEntity>,
    val generations: Map<String, StoryGenerationTaskManager.GenerationProgress>,
    val lastReadNumbers: Map<String, Int>,
)

/** 书架的全部回调（与暖陶同一批 VM 方法、同一批实参）。 */
internal class LiuliShelfCallbacks(
    val onBack: () -> Unit,
    val onCreate: () -> Unit,
    val onOpenStory: (String) -> Unit,
    val onContinueReading: (StoryEntity) -> Unit,
    val onRetry: (StoryEntity) -> Unit,
    val onTogglePause: (StoryEntity) -> Unit,
    val onArchive: (String) -> Unit,
    val onOpenSettings: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onOpenArchive: (String) -> Unit,
    val onViewAllArchive: () -> Unit,
)

/**
 * 琉璃书架（琉璃 2.0 卷六·三·上 §4.2·设计稿 S1）：与暖陶 [com.situ.aichat.ui.story.StoryBookshelfScreen] 共用同一个 VM、
 * 进页事 / 两区拆分 / 兜底判据 / 菜单分派 / 书卡与档案区内容件，只换外壳与材质：琉璃页壳（大标题 + 收起胶囊 + 玻璃「+」）、
 * 半透明书卡、玻璃长按菜单与对话框；长按压暗层两张脸共用（拍板②）。
 */
@Composable
internal fun LiuliStoryBookshelfScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenChapter: (String) -> Unit,
    onCreateStory: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onViewAllArchive: () -> Unit,
    viewModel: StoryBookshelfViewModel = hiltViewModel(),
) {
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val activeGenerations by viewModel.activeGenerations.collectAsStateWithLifecycle()
    val lastReadNumbers by viewModel.lastReadChapterNumbers.collectAsStateWithLifecycle()

    StoryBookshelfEffects(viewModel, onOpenChapter, onOpenStory)

    LiuliStoryBookshelfPage(
        LiuliShelfData(stories, activeGenerations, lastReadNumbers),
        LiuliShelfCallbacks(
            onBack, onCreateStory, onOpenStory,
            viewModel::continueReading, viewModel::retryGeneration, viewModel::togglePause, viewModel::archiveStory,
            onOpenSettings, viewModel::deleteStory, onOpenArchive, onViewAllArchive,
        ),
        rememberLazyListState(),
    )
}

/** 无 VM 的书架页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryBookshelfPage(data: LiuliShelfData, callbacks: LiuliShelfCallbacks, listState: LazyListState) {
    var menuStoryId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<StoryEntity?>(null) }
    // 归档 / 归档卡删除确认只存 id 不存对象快照（PITFALLS 1b）：渲染时从当前流解析，书没了弹窗自然消失。
    var archiveTargetId by remember { mutableStateOf<String?>(null) }
    var archivedDeleteId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalAppHaptics.current
    val dark = LocalIsDarkTheme.current

    // 菜单开着时书被并行删除 / 换区：兜底清态，否则压暗层卡住不走（同暖陶）。
    LaunchedEffect(data.stories) { if (storyMenuStale(menuStoryId, data.stories)) menuStoryId = null }

    val split = storyShelfSplit(data.stories)
    val title = stringResource(R.string.story_nav_title)
    val scrolledPast = rememberLargeTitleCollapsed(listState)
    val collapsed = scrolledPast && data.stories.isNotEmpty()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LiuliPage(
        title, callbacks.onBack, collapsed,
        actions = {
            LiuliPageCircleAction(onClick = callbacks.onCreate, contentDescription = stringResource(R.string.story_create_new), icon = AppTopBarIcons.Add)
        },
    ) {
        if (data.stories.isEmpty()) {
            Column(Modifier.fillMaxSize().contentMaxWidth().padding(top = LiuliPageGeometry.navRow)) {
                LiuliLargeTitle(title)
                StoryEmptyState(Modifier.weight(1f)) {
                    LiuliButton(onClick = callbacks.onCreate, style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.story_new_story_title)) }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentMaxWidth(),
                contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = navBarBottom + LiuliPageGeometry.pageBottom),
                verticalArrangement = Arrangement.spacedBy(SHELF_GAP),
            ) {
                item(key = "large-title") { LiuliLargeTitle(title) }
                item(key = "_tagline") {
                    val tagline = stringResource(R.string.story_shelf_subtitle)
                    Text(tagline, style = AppTypography.secondary, color = AppTheme.colors.text.secondary, modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter))
                }
                items(split.active, key = { it.id }) { story ->
                    Box(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                        StoryCard(
                            story, data.generations[story.id], data.lastReadNumbers[story.id],
                            onOpenStory = { callbacks.onOpenStory(story.id) },
                            onLongPress = { haptics.light(); menuStoryId = story.id },
                            onContinueReading = { callbacks.onContinueReading(story) },
                            onRetry = { callbacks.onRetry(story) },
                            surface = Modifier.liuliStoryCard(dark),
                        )
                        // 菜单锚：同暖陶那一只锚盒（贴卡片右缘、标题排下方展开）。
                        Box(Modifier.align(Alignment.TopEnd).padding(top = CARD_MENU_TOP, end = CARD_MENU_END)) {
                            LiuliPopupMenu(
                                expanded = menuStoryId == story.id,
                                onDismiss = { menuStoryId = null },
                                items = StoryCardLogic.menuActions(story.status, story.updateMode).map { action ->
                                    LiuliMenuEntry(stringResource(menuActionLabel(action)), danger = action == StoryCardMenuAction.DELETE) {
                                        menuStoryId = null
                                        action.dispatch(
                                            onTogglePause = { callbacks.onTogglePause(story) },
                                            onArchive = { archiveTargetId = story.id },
                                            onOpenSettings = { callbacks.onOpenSettings(story.id) },
                                            onDelete = { deleteTarget = story },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "_newstory") { NewStoryCard(onClick = callbacks.onCreate, modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter)) }
                if (split.archived.isNotEmpty()) {
                    item(key = "_archive") {
                        StoryArchiveSection(
                            split.archived,
                            onOpen = callbacks.onOpenArchive,
                            onViewAll = callbacks.onViewAllArchive,
                            menuStoryId = menuStoryId,
                            onCardLongPress = { haptics.light(); menuStoryId = it },
                            onMenuDismiss = { menuStoryId = null },
                            onDeleteRequest = { menuStoryId = null; archivedDeleteId = it },
                            modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter),
                            menu = { story ->
                                val delete = LiuliMenuEntry(stringResource(R.string.story_menu_delete), danger = true) { menuStoryId = null; archivedDeleteId = story.id }
                                LiuliPopupMenu(expanded = menuStoryId == story.id, onDismiss = { menuStoryId = null }, items = listOf(delete))
                            },
                        )
                    }
                }
            }
        }
        // 长按菜单期背景轻压暗（拍板②·两张脸共用）：放在内容层、列表之后，不压 overlay。
        StoryShelfMenuScrim(visible = menuStoryId != null)
    }

    archiveTargetId?.let { id ->
        storyActiveById(data.stories, id)?.let { t ->
            LiuliDialog(
                onDismissRequest = { archiveTargetId = null }, title = stringResource(R.string.story_archive_confirm_title),
                body = stringResource(R.string.story_archive_confirm_msg, t.title),
                confirmText = stringResource(R.string.story_archive_confirm_action), onConfirm = { callbacks.onArchive(t.id); archiveTargetId = null },
                dismissText = stringResource(R.string.action_cancel), onDismiss = { archiveTargetId = null },
            )
        }
    }

    deleteTarget?.let { t ->
        LiuliDialog(
            onDismissRequest = { deleteTarget = null }, title = stringResource(R.string.story_menu_delete), body = t.title,
            confirmText = stringResource(R.string.story_action_delete), onConfirm = { callbacks.onDelete(t.id); deleteTarget = null }, confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel), onDismiss = { deleteTarget = null },
        )
    }

    archivedDeleteId?.let { id ->
        storyArchivedById(data.stories, id)?.let { t ->
            LiuliStoryArchivedDeleteDialog(t, onConfirm = { callbacks.onDelete(t.id); archivedDeleteId = null }, onDismiss = { archivedDeleteId = null })
        }
    }
}
