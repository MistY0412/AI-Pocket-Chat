package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.diagnostics.perf.FrameSceneObserver
import com.situ.aichat.diagnostics.perf.PerfScenes
import com.situ.aichat.story.StoryNarrativePerson
import com.situ.aichat.story.StoryReaderTypography
import com.situ.aichat.story.StoryVisualPerformance
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.story.StoryReadyChapter
import com.situ.aichat.ui.story.StoryReaderEffects
import com.situ.aichat.ui.story.StoryReaderSheets
import com.situ.aichat.ui.story.StoryReaderViewModel
import com.situ.aichat.ui.story.animateScrollToReaderEnd
import com.situ.aichat.ui.story.rememberStoryLockClock
import com.situ.aichat.ui.story.rememberStoryReaderHaptics
import com.situ.aichat.ui.story.rememberStoryReaderRenderItems
import com.situ.aichat.ui.story.rememberStoryReaderSheetsState
import kotlinx.coroutines.launch

/** 琉璃阅读器要画的数据（无 VM 页的入参·测试直接造）。 */
@Immutable
internal data class LiuliReaderData(
    val story: StoryEntity?,
    val chapters: List<StoryChapterEntity>,
    val currentChapter: StoryChapterEntity?,
    val currentChapterId: String,
    val userRoleName: String?,
    val selectedChoiceText: String?,
    val pendingActive: Boolean,
    val pendingRemaining: Int,
    val readingAnimationsEnabled: Boolean,
    val fontSizeIndex: Int,
    val recapSummary: String?,
    val hasPreviousDraft: Boolean,
    val readyChapter: StoryReadyChapter?,
)

/** 琉璃阅读器的全部 VM 回调（触感包装同暖陶屏）。 */
internal class LiuliReaderCallbacks(
    val onBack: () -> Unit,
    val onOpenBookHub: (storyId: String) -> Unit,
    val onGoToChat: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onContinueArc: () -> Unit,
    val onToggleAnimations: (Boolean) -> Unit,
    val onSetFontSizeIndex: (Int) -> Unit,
    val onSubmitChoice: (String) -> Unit,
    val onCancelPendingChoice: () -> Unit,
    val onRate: (Int?) -> Unit,
    val onFlow: () -> Unit,
    val onOpenReadyChapter: () -> Unit,
)

/**
 * 琉璃故事阅读器（琉璃 2.0 卷六·三·下甲·「读的时候」的第二张脸）。
 *
 * 与暖陶 [com.situ.aichat.ui.story.StoryReaderScreen] 共用同一个 VM 与 `StoryReader*Support / Surfaces` 共用件；
 * 本入口只收流、声明「生成完成不自动翻章」（R2）、组装数据 / 回调类交给 [LiuliStoryReaderBody]。
 * 生成进度只以 State 形态下传（R2 性能：根部不读值）。弹层与对话框经 [LiuliStoryReaderSheetFace] 画琉璃长相，整块住玻璃宿主内容层（下·乙）。
 */
@Composable
internal fun LiuliStoryReaderScreen(
    onBack: () -> Unit,
    onGoToChat: () -> Unit,
    onOpenBookHub: (storyId: String) -> Unit,
    viewModel: StoryReaderViewModel = hiltViewModel(),
) {
    FrameSceneObserver(PerfScenes.STORY_READER)
    val story by viewModel.story.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val currentChapterId by viewModel.currentChapterId.collectAsStateWithLifecycle()
    val userRoleName by viewModel.userRoleName.collectAsStateWithLifecycle()
    val selectedChoiceText by viewModel.selectedChoiceText.collectAsStateWithLifecycle()
    val pendingActive by viewModel.pendingActive.collectAsStateWithLifecycle()
    val pendingRemaining by viewModel.pendingRemainingSeconds.collectAsStateWithLifecycle()
    val askNext by viewModel.askGenerateNextChapter.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val readingAnimationsEnabled by viewModel.readingAnimationsEnabled.collectAsStateWithLifecycle()
    val fontSizeIndex by viewModel.fontSizeIndex.collectAsStateWithLifecycle()
    val recapSummary by viewModel.recapSummary.collectAsStateWithLifecycle()
    val previousDraft by viewModel.previousDraft.collectAsStateWithLifecycle()
    val readyChapter by viewModel.readyChapter.collectAsStateWithLifecycle()
    // R2 性能：拿 State 不解包，值只在进度卡里读。
    val generation = viewModel.activeGeneration.collectAsStateWithLifecycle()
    val typography = remember(fontSizeIndex) { StoryReaderTypography.forIndex(fontSizeIndex) }

    val reduceMotion = rememberReduceMotion()
    val performance = remember(readingAnimationsEnabled, reduceMotion) {
        StoryVisualPerformance.current(readingAnimationsEnabled, reduceMotion)
    }
    val haptics = rememberStoryReaderHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    StoryReaderEffects(viewModel, currentChapter, listState, haptics, autoJumpOnGenerated = false)

    val renderItems = rememberStoryReaderRenderItems(currentChapter)
    val sheets = rememberStoryReaderSheetsState()
    val lockClock = rememberStoryLockClock(currentChapter)
    val narrativePerson = story?.narrativePerson ?: StoryNarrativePerson.SECOND

    LiuliStoryReaderBody(
        data = LiuliReaderData(
            story = story,
            chapters = chapters,
            currentChapter = currentChapter,
            currentChapterId = currentChapterId,
            userRoleName = userRoleName,
            selectedChoiceText = selectedChoiceText,
            pendingActive = pendingActive,
            pendingRemaining = pendingRemaining,
            readingAnimationsEnabled = readingAnimationsEnabled,
            fontSizeIndex = fontSizeIndex,
            recapSummary = recapSummary,
            hasPreviousDraft = previousDraft != null,
            readyChapter = readyChapter,
        ),
        generation = generation,
        lockClock = lockClock,
        renderItems = renderItems,
        typography = typography,
        performance = performance,
        listState = listState,
        sheets = sheets,
        haptics = haptics,
        narrativePerson = narrativePerson,
        callbacks = LiuliReaderCallbacks(
            onBack = onBack,
            onOpenBookHub = onOpenBookHub,
            onGoToChat = onGoToChat,
            onPrev = viewModel::goPrevious,
            onNext = viewModel::goNext,
            onContinueArc = viewModel::continueCompletedStory,
            // 开关/字号拨动带轻触觉（菜单本体保持纯视觉，反馈统一在屏级回调·同暖陶）。
            onToggleAnimations = { haptics.light(); viewModel.setReadingAnimations(it) },
            onSetFontSizeIndex = { haptics.selection(); viewModel.setFontSizeIndex(it) },
            onSubmitChoice = { haptics.light(); viewModel.submitChoice(it) },
            onCancelPendingChoice = { haptics.light(); viewModel.cancelPendingChoice() },
            onRate = viewModel::rateChapter,
            onFlow = viewModel::forceContinue,
            onOpenReadyChapter = { haptics.light(); viewModel.openReadyChapter() },
        ),
        // 弹层与对话框整块住玻璃宿主的内容层（卷六·三·下乙复核 R1）：通透 / 标准档是真玻璃，同上半卷书页的弹层。
        dialogs = {
            StoryReaderSheets(
                sheets = sheets,
                viewModel = viewModel,
                story = story,
                chapters = chapters,
                currentChapter = currentChapter,
                narrativePerson = narrativePerson,
                userRoleName = userRoleName,
                previousDraft = previousDraft,
                askNext = askNext,
                error = error,
                haptics = haptics,
                onGoToChoice = {
                    // 「带我去做选择」（ST10-4）：不再是只关弹窗的假按钮——关闭并滚到章末选择区（列表末项；
                    // 程序滚动经既有 isScrolling 联动自动隐入沉浸 chrome）。
                    sheets.dialog.value = null
                    scope.launch { listState.animateScrollToReaderEnd() }
                },
                face = LiuliStoryReaderSheetFace,
            )
        },
    )
}
