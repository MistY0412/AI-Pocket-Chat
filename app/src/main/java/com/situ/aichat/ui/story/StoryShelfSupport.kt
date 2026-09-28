package com.situ.aichat.ui.story

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryStatus
import kotlinx.coroutines.flow.Flow

/** 一次性 Toast 事件（琉璃 2.0 卷六·三：书架 / 书页 / 模板墙三处同式合一·两张脸共用）：收到 string res 就弹短 Toast。 */
@Composable
internal fun StoryToastEvents(events: Flow<Int>) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        events.collect { resId -> Toast.makeText(context, resId, Toast.LENGTH_SHORT).show() }
    }
}

/** 书架三件进页事（原 StoryBookshelfScreen :90–111 逐字·两张脸共用）。 */
@Composable
internal fun StoryBookshelfEffects(
    viewModel: StoryBookshelfViewModel,
    onOpenChapter: (String) -> Unit,
    onOpenStory: (String) -> Unit,
) {
    // 归档结果一次性提示（成功入档 / 生成中拒绝）。
    StoryToastEvents(viewModel.toastEvents)
    LaunchedEffect(Unit) { viewModel.refreshReadingProgress() }
    // 续读异步结果 → 导航（有可读章进阅读器，否则退回章节列表）。
    LaunchedEffect(Unit) {
        viewModel.resumeTarget.collect { target ->
            when (target) {
                is StoryResumeTarget.Reader -> onOpenChapter(target.chapterId)
                is StoryResumeTarget.ChapterList -> onOpenStory(target.storyId)
            }
        }
    }
}

/** 书架两区（原 :49–50 两式·纯·T1）：已完结 = 档案区，其余 = 在读区。 */
internal data class StoryShelfSplit(val active: List<StoryEntity>, val archived: List<StoryEntity>)

internal fun storyShelfSplit(stories: List<StoryEntity>): StoryShelfSplit = StoryShelfSplit(
    active = stories.filter { it.status != StoryStatus.COMPLETED },
    archived = stories.filter { it.status == StoryStatus.COMPLETED },
)

/**
 * 菜单开着时书没了（原 :112–114 / StoryArchiveAllScreen :61–63 同式·纯·T1）：条目连菜单一起从树上消失、
 * onDismiss 不再回调——该兜底清态，否则 scrim 卡住不走。
 */
internal fun storyMenuStale(menuStoryId: String?, stories: List<StoryEntity>): Boolean =
    menuStoryId != null && stories.none { it.id == menuStoryId }

/** 归档确认的目标（原 :205 式·只存 id·PITFALLS 1b）：书没了或已完结 → null，弹窗自然消失。 */
internal fun storyActiveById(stories: List<StoryEntity>, id: String): StoryEntity? =
    stories.firstOrNull { it.id == id && it.status != StoryStatus.COMPLETED }

/** 已完结删除确认的目标（原 :233 式）。 */
internal fun storyArchivedById(stories: List<StoryEntity>, id: String): StoryEntity? =
    stories.firstOrNull { it.id == id && it.status == StoryStatus.COMPLETED }

/** 书架卡菜单分派（原 :167–174 `when`·纯·T1）：暂停与恢复同走「切换暂停」。 */
internal fun StoryCardMenuAction.dispatch(
    onTogglePause: () -> Unit,
    onArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) = when (this) {
    StoryCardMenuAction.PAUSE, StoryCardMenuAction.RESUME -> onTogglePause()
    StoryCardMenuAction.ARCHIVE -> onArchive()
    StoryCardMenuAction.SETTINGS -> onOpenSettings()
    StoryCardMenuAction.DELETE -> onDelete()
}
