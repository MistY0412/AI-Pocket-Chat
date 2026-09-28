package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.story.ArchiveGridCell
import com.situ.aichat.ui.story.StoryArchiveAllViewModel
import com.situ.aichat.ui.story.StoryShelfMenuScrim
import com.situ.aichat.ui.story.storyMenuStale

/** 网格三列 · 列缝 14 · 行缝 18（= 暖陶网格）。 */
private const val ARCHIVE_COLUMNS = 3
private val ARCHIVE_COL_GAP = 14.dp
private val ARCHIVE_ROW_GAP = 18.dp

/**
 * 琉璃全部结局（琉璃 2.0 卷六·三·上 §4.3）：与暖陶 [com.situ.aichat.ui.story.StoryArchiveAllScreen] 共用同一个 VM 与
 * 网格格子内容件；大标题页壳 + 「每行一个 item、行内等分」的三列（大标题件自带 gutter、收起判据只认列表·§0.2-3），
 * 玻璃长按菜单 + 琉璃删除确认；压暗层两张脸共用。
 */
@Composable
internal fun LiuliStoryArchiveAllScreen(
    onBack: () -> Unit,
    onOpenArchive: (String) -> Unit,
    viewModel: StoryArchiveAllViewModel = hiltViewModel(),
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    LiuliStoryArchiveAllPage(archived, onBack, onOpenArchive, onDelete = viewModel::deleteStory, rememberLazyListState())
}

/** 无 VM 的全部结局页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryArchiveAllPage(
    archived: List<StoryEntity>,
    onBack: () -> Unit,
    onOpenArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    listState: LazyListState,
) {
    var menuStoryId by remember { mutableStateOf<String?>(null) }
    // 删除确认只存 id 不存对象快照（PITFALLS 1b）：渲染时从当前流解析，书没了弹窗自然消失。
    var deleteId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalAppHaptics.current
    LaunchedEffect(archived) { if (storyMenuStale(menuStoryId, archived)) menuStoryId = null }

    val title = stringResource(R.string.story_archive_all_title)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LiuliPage(title, onBack, rememberLargeTitleCollapsed(listState)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = navBarBottom + LiuliPageGeometry.pageBottom),
            verticalArrangement = Arrangement.spacedBy(ARCHIVE_ROW_GAP),
        ) {
            item(key = "large-title") { LiuliLargeTitle(title) }
            item(key = "_count") {
                Text(
                    stringResource(R.string.story_archive_all_count, archived.size),
                    style = AppTypography.caption,
                    color = AppTheme.colors.text.tertiary,
                    modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter),
                )
            }
            items(archived.chunked(ARCHIVE_COLUMNS), key = { row -> "row-" + row.first().id }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = LiuliPageGeometry.gutter),
                    horizontalArrangement = Arrangement.spacedBy(ARCHIVE_COL_GAP),
                ) {
                    row.forEach { story ->
                        // 包 Box 当菜单锚：菜单贴本格封面展开。
                        Box(Modifier.weight(1f)) {
                            ArchiveGridCell(story, onClick = { onOpenArchive(story.id) }, onLongPress = { haptics.light(); menuStoryId = story.id })
                            LiuliPopupMenu(
                                expanded = menuStoryId == story.id,
                                onDismiss = { menuStoryId = null },
                                items = listOf(
                                    LiuliMenuEntry(stringResource(R.string.story_menu_delete), danger = true, onClick = { menuStoryId = null; deleteId = story.id }),
                                ),
                            )
                        }
                    }
                    // 末行不满：等宽空位补齐，格宽不变（E38）。
                    repeat(ARCHIVE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // 长按菜单期背景轻压暗（拍板②·两张脸共用）。
        StoryShelfMenuScrim(visible = menuStoryId != null)
    }

    deleteId?.let { id ->
        // 从当前流按 id 解析（PITFALLS 1b）；archived 流本身只含已完结。
        archived.firstOrNull { it.id == id }?.let { t ->
            LiuliStoryArchivedDeleteDialog(t, onConfirm = { onDelete(t.id); deleteId = null }, onDismiss = { deleteId = null })
        }
    }
}
