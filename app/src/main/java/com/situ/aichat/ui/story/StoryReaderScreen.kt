package com.situ.aichat.ui.story

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.diagnostics.perf.FrameSceneObserver
import com.situ.aichat.diagnostics.perf.PerfScenes
import com.situ.aichat.story.StoryNarrativePerson
import com.situ.aichat.story.StoryReaderTypography
import com.situ.aichat.story.StoryVisualPerformance
import com.situ.aichat.story.isUnlocked
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

/**
 * 故事阅读器主屏（源自 iOS `StoryReaderView`）。三层：纸面背景 → 对比遮罩 → 正文滚动 → 底部章节导航胶囊；
 * 外加顶栏（章节胶囊 + 菜单）、生成中遮罩、锁态遮罩。
 * （2026-08-03 格式块精简：天气粒子层与屏幕特效 overlay 随氛围演出层整族退役。）
 */
@Composable
fun StoryReaderScreen(
    onBack: () -> Unit,
    onGoToChat: () -> Unit,
    /** 卷三 §4.6：⋮ 菜单瘦身后唯一保留的导航——跳这本书的「书页」（档案 / 设定双 Tab）。 */
    onOpenBookHub: (storyId: String) -> Unit,
    viewModel: StoryReaderViewModel = hiltViewModel(),
) {
    // 性能采集·尺 3（卷 0）：本屏在被观测名单里（M15 长章首帧到出字）。采集关时零成本。
    FrameSceneObserver(PerfScenes.STORY_READER)
    val story by viewModel.story.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val currentChapterId by viewModel.currentChapterId.collectAsStateWithLifecycle()
    val activeGeneration by viewModel.activeGeneration.collectAsStateWithLifecycle()
    val userRoleName by viewModel.userRoleName.collectAsStateWithLifecycle()
    val selectedChoiceText by viewModel.selectedChoiceText.collectAsStateWithLifecycle()
    val pendingActive by viewModel.pendingActive.collectAsStateWithLifecycle()
    val pendingRemaining by viewModel.pendingRemainingSeconds.collectAsStateWithLifecycle()
    val askNext by viewModel.askGenerateNextChapter.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val readingAnimationsEnabled by viewModel.readingAnimationsEnabled.collectAsStateWithLifecycle()
    val fontSizeIndex by viewModel.fontSizeIndex.collectAsStateWithLifecycle()
    val recapSummary by viewModel.recapSummary.collectAsStateWithLifecycle()
    // C3：本章槽里的上一版（VM 侧解码·失败视同无槽 E6）。菜单项显隐与回翻弹层共用。
    val previousDraft by viewModel.previousDraft.collectAsStateWithLifecycle()
    val typography = remember(fontSizeIndex) { StoryReaderTypography.forIndex(fontSizeIndex) }

    val isDark = LocalIsDarkTheme.current
    val reduceMotion = rememberReduceMotion()
    val performance = remember(readingAnimationsEnabled, reduceMotion) {
        StoryVisualPerformance.current(readingAnimationsEnabled, reduceMotion)
    }

    val haptics = rememberStoryReaderHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    StoryReaderEffects(viewModel, currentChapter, listState, haptics, autoJumpOnGenerated = true)

    val narrativePerson = story?.narrativePerson ?: StoryNarrativePerson.SECOND
    val chapterKey = currentChapter?.id
    val renderItems = rememberStoryReaderRenderItems(currentChapter)
    val progress = rememberStoryReaderProgress(listState, renderItems, hasRecap = recapSummary != null)
    val chrome = rememberStoryReaderChromeState(chapterKey, listState)
    val sheets = rememberStoryReaderSheetsState()
    var dialog by sheets.dialog
    var pendingGracefulFinale by sheets.pendingGraceful

    val isGenerating = activeGeneration != null
    val nav = storyReaderNav(chapters, currentChapterId, currentChapter, story?.status, isGenerating, previousDraft != null)
    // 「收尾中 · 本弧第 K/L 章」的两个数字（无收尾计划 → null → 推进区显示金调「准备收尾」胶囊）。
    val finaleProgress = storyFinaleProgress(story)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerHeight = maxHeight

        StoryMoodBackground(isDark)
        StoryReadingOverlay(isDark)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 轻触正文空白区切换工具栏显隐；落在选项按钮等子元素上的点击由其自行消费，不会触发此处。
                    detectTapGestures(onTap = { chrome.toggle() })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            // 顶部留状态栏高度：沉浸态下首行不被状态栏图标压住；上滑时正文仍会滚进透明状态栏区域显示。
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = 96.dp,
            ),
        ) {
            storyReaderPaperItems(
                currentChapter = currentChapter,
                storyId = story?.id,
                recapSummary = recapSummary,
                renderItems = renderItems,
                isDark = isDark,
                typography = typography,
                performance = performance,
                containerHeight = containerHeight,
            )
            // 建议完结卡（ST11 §3.4）：渲染顺序 正文 → 建议卡 → 选择区/推进区。与选择区可并存（矛盾输出）。
            storyEndingSuggestItem(
                chapter = currentChapter,
                isLatestChapter = nav.isLatestChapter,
                storyStatus = story?.status,
                isDark = isDark,
                onGracefulFinale = {
                    haptics.light()
                    pendingGracefulFinale = true
                    dialog = ReaderDialog.EndingPicker
                },
                onFinish = { haptics.light(); dialog = ReaderDialog.ArchiveConfirm },
                onKeepWriting = {
                    haptics.light()
                    sheets.breatheTrigger += 1
                    scope.launch { listState.animateScrollToReaderEnd() }
                },
            )
            // 卷三 §4.1（D-14 锁定次序）：建议卡之后、选择区之前——① 本章操作行 → ② 三档快评行。
            storyChapterEndZoneItems(
                chapter = currentChapter,
                isLatestChapter = nav.isLatestChapter,
                isDark = isDark,
                canRewrite = nav.canRewrite,
                canViewPreviousDraft = nav.canViewPreviousDraft,
                // §3.3：本章小结对任何章都可编辑（历史章也能改）；章还在加载（null）时不出（卷六·三·下乙 D-11）。
                canEditSummary = currentChapter != null,
                actionsExpanded = sheets.showChapterActionsMenu,
                onActionsExpandedChange = { sheets.showChapterActionsMenu = it },
                onRewrite = { dialog = ReaderDialog.RewriteConfirm },
                onViewPreviousDraft = { sheets.showPreviousDraft = true },
                onEditChapterSummary = { dialog = ReaderDialog.ChapterSummary },
                onRate = viewModel::rateChapter,
            )
            // 完结门（ST10-4）：已完结的书不再渲染**未答**选择——结局清洗前的历史脏数据里可能残留幽灵选择，
            // 一点会经 submitChoice 把书从已完结拉回连载中；已答选择照常显示（选择回顾态）。
            storyChoiceChapter(currentChapter, story?.status)?.let { ch ->
                item(key = "choice") {
                    StoryChoiceSection(
                        chapter = ch,
                        isDark = isDark,
                        narrativePerson = narrativePerson,
                        userRoleName = userRoleName,
                        selectedChoiceText = selectedChoiceText,
                        onSubmit = { haptics.light(); viewModel.submitChoice(it) },
                        onOpenCustomInput = { sheets.showCustomChoice = true },
                        modifier = Modifier
                            .widthIn(max = StoryReaderLayout.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = StoryReaderLayout.horizontalPadding),
                    )
                }
            }
            // 章末推进区（ST11 §3.4）：非完结末章、且无待答选择时的方向盘（治「无选项末章一片空白」）。
            storyContinueZoneItem(
                chapter = currentChapter,
                isLatestChapter = nav.isLatestChapter,
                storyStatus = story?.status,
                isDark = isDark,
                breatheTrigger = sheets.breatheTrigger,
                finaleProgress = finaleProgress,
                // 草稿卡（图纸 2026-08-05 U-2）：既有 story 读点直透传，零新 StateFlow、VM 零改动（J8）
                draftBeats = story?.pendingChapterBeats,
                draftUserEdited = story?.pendingBeatsUserEdited == true,
                // 卷三 §4.5：输入卡改指导演台（走向 + 节拍两栏）；选择区的自由输入入口不动，仍走 showCustomChoice。
                onWriteClick = { haptics.light(); sheets.showDirector = true },
                onFlowClick = viewModel::forceContinue,
                onFinaleClick = { dialog = ReaderDialog.FinaleMethod },
                onCancelFinaleClick = { dialog = ReaderDialog.FinaleCancelConfirm },
            )
        }

        BottomCapsule(
            hasPrev = nav.hasPrev,
            hasNext = nav.hasNext,
            showContinueArc = nav.showContinueArc,
            progressPercent = progress.percent.value,
            remainingMinutes = progress.remainingMinutes.value,
            // 反悔窗口内让位给底部撤销条（二者同占底部中央·互斥）。
            visible = chrome.visible && !pendingActive,
            onPrev = viewModel::goPrevious,
            onNext = viewModel::goNext,
            onContinueArc = viewModel::continueCompletedStory,
            isDark = isDark,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 章末选择的非阻塞撤销条（J2·屏级浮层·浮于内容之上不挡阅读）。
        StoryUndoBar(
            choiceText = selectedChoiceText.orEmpty(),
            remainingSeconds = pendingRemaining,
            visible = pendingActive && selectedChoiceText != null,
            onCancel = { haptics.light(); viewModel.cancelPendingChoice() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 锁态遮罩（追更未到点章节）：时钟规则见 rememberStoryLockClock。
        rememberStoryLockClock(currentChapter)?.let { clock ->
            val lockNow by clock
            currentChapter?.let { ch -> if (!ch.isUnlocked(lockNow)) LockedOverlay(ch, lockNow) }
        }

        activeGeneration?.let { gen -> GenerationOverlay(gen, onGoToChat) }

        // 顶栏受 chromeVisible 控制（点击唤出 / 滚动隐入沉浸）；生成中强制保留，确保遮罩下仍可返回 / 操作。
        AnimatedVisibility(
            visible = chrome.visible || isGenerating || chrome.menuOpen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                chapter = currentChapter,
                storyTitle = story?.title,
                isDark = isDark,
                readingAnimationsEnabled = readingAnimationsEnabled,
                fontSizeIndex = fontSizeIndex,
                onBack = onBack,
                // 卷三 §4.6：storyId 还没解析出来时整行不显示（空书页无意义）。
                onOpenBookHub = story?.id?.let { sid -> { onOpenBookHub(sid) } },
                // 开关/字号拨动带轻触觉（菜单本体保持纯视觉，反馈统一在屏级回调）。
                onToggleAnimations = { haptics.light(); viewModel.setReadingAnimations(it) },
                onSetFontSizeIndex = { haptics.selection(); viewModel.setFontSizeIndex(it) },
                onMenuExpandedChange = { chrome.menuOpen = it },
            )
        }
    }

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
            dialog = null
            scope.launch { listState.animateScrollToReaderEnd() }
        },
    )
}
