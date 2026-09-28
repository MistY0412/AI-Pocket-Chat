package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.situ.aichat.story.StoryGenerationTaskManager
import com.situ.aichat.story.StoryReaderRenderItem
import com.situ.aichat.story.StoryReaderTypography
import com.situ.aichat.story.StoryVisualPerformance
import com.situ.aichat.story.isUnlocked
import com.situ.aichat.ui.liuli.glass.LiuliGlassHost
import com.situ.aichat.ui.liuli.page.LiuliBottomScrollEdge
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliTopScrollEdge
import com.situ.aichat.ui.liuli.page.liuliPageEdgeBelowStatus
import com.situ.aichat.ui.story.StoryMoodBackground
import com.situ.aichat.ui.story.StoryMoodPalette
import com.situ.aichat.ui.story.StoryReaderHaptics
import com.situ.aichat.ui.story.StoryReaderSheetsState
import com.situ.aichat.ui.story.StoryReadingOverlay
import com.situ.aichat.ui.story.StoryUndoBar
import com.situ.aichat.ui.story.animateScrollToReaderEnd
import com.situ.aichat.ui.story.rememberStoryReaderChromeState
import com.situ.aichat.ui.story.rememberStoryReaderProgress
import com.situ.aichat.ui.story.storyFinaleProgress
import com.situ.aichat.ui.story.storyReaderNav
import com.situ.aichat.ui.story.storyReaderPaperItems
import com.situ.aichat.ui.story.storyReaderSeekIndex
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

/**
 * 琉璃阅读器页（琉璃 2.0 卷六·三·下甲 §4.2·无 VM·测试直接造 [LiuliReaderData]）。
 *
 * 玻璃宿主：content = 纸（不透明渐变）+ 对比遮罩 + 列表；overlay（后声明的压上面）= 顶 / 底渐进带（遮挡色 = 纸色）→
 * 锁定层 → 进度卡槽（写作中卡 / 写好了钮）→ 进度坞 → 撤销条 → 胶囊。**不用 `LiuliPage`**（它会画柔光底与导航行）。
 * 根部只经 `derivedStateOf` 读「在不在写」「锁没锁」；生成进度值 / 阅读进度 / 剩余时间都在各自小件里读。
 * [dialogs]（卷六·三·下乙复核 R1）：弹层与对话框整块在 **content** 里调——拿到跨窗口取景源，通透 / 标准档是真玻璃
 * （同上半卷书页在 `LiuliPage` 内容层里调弹层）；不放 overlay（那里的 `LocalLiuliGlassHost` 会漏进弹层窗口）。
 */
@Composable
internal fun LiuliStoryReaderBody(
    data: LiuliReaderData,
    generation: State<StoryGenerationTaskManager.GenerationProgress?>,
    lockClock: State<Long>?,
    renderItems: List<StoryReaderRenderItem>,
    typography: StoryReaderTypography,
    performance: StoryVisualPerformance,
    listState: LazyListState,
    sheets: StoryReaderSheetsState,
    haptics: StoryReaderHaptics,
    narrativePerson: String,
    callbacks: LiuliReaderCallbacks,
    dialogs: @Composable () -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val story = data.story
    val chapter = data.currentChapter
    // R2 性能：根部只读「在不在写」，进度值只在进度卡里读（生成时每秒 6–7 次的推送不再重组整页）。
    val isGenerating by remember { derivedStateOf { generation.value != null } }
    val nav = storyReaderNav(data.chapters, data.currentChapterId, chapter, story?.status, isGenerating, data.hasPreviousDraft)
    val finaleProgress = storyFinaleProgress(story)
    val progress = rememberStoryReaderProgress(listState, renderItems, hasRecap = data.recapSummary != null)
    val chrome = rememberStoryReaderChromeState(chapter?.id, listState)
    // 锁没锁：根部只在翻面时重组，剩余时间在锁卡里读。
    val locked by remember(chapter, lockClock) {
        derivedStateOf { chapter != null && lockClock != null && !chapter.isUnlocked(lockClock.value) }
    }
    val readyShown = data.readyChapter != null && !isGenerating
    var slotHeightPx by remember { mutableIntStateOf(0) }
    val slotReserve = if (isGenerating || readyShown) with(density) { slotHeightPx.toDp() } + READER_DOCK_GAP else 0.dp
    val capsuleVisible = chrome.visible || chrome.menuOpen || locked
    val dockVisible = (chrome.visible || chrome.seeking) && !data.pendingActive && !locked
    val bottomEdgeAbove = when {
        isGenerating || readyShown -> READER_DOCK_RESERVE + with(density) { slotHeightPx.toDp() } + LiuliPageGeometry.edgeTail
        dockVisible -> READER_DOCK_BOTTOM + READER_DOCK_HEIGHT + LiuliPageGeometry.edgeTail
        else -> LiuliPageGeometry.pageBottom
    }
    // 建议卡「还想继续写」：推进区入口卡呼吸一次 + 滚到末项（同暖陶）。
    val onKeepWriting: () -> Unit = {
        haptics.light()
        sheets.breatheTrigger += 1
        scope.launch { listState.animateScrollToReaderEnd() }
    }

    LiuliGlassHost(
        modifier = Modifier.fillMaxSize(),
        content = {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val containerHeight = maxHeight
                StoryMoodBackground(dark)
                StoryReadingOverlay(dark)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        // 轻触正文空白区切换外框显隐（同暖陶）。
                        detectTapGestures(onTap = { chrome.toggle() })
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = statusBarTop, bottom = navBarBottom + READER_DOCK_RESERVE + slotReserve),
                ) {
                    if (locked) {
                        // 锁定章：只放章首 + 灰条骨架——正文块 / 上回说到 / 章末项一律不进列表（正文一个字都不渲染）。
                        storyReaderPaperItems(chapter, story?.id, recapSummary = null, renderItems = emptyList(), dark, typography, performance, containerHeight)
                        item(key = "lockedSkeleton") { LiuliStoryLockedSkeleton(dark, READER_ITEM_MODIFIER) }
                    } else {
                        storyReaderPaperItems(chapter, story?.id, data.recapSummary, renderItems, dark, typography, performance, containerHeight)
                        liuliStoryReaderEndItems(
                            chapter, story, nav, isGenerating, dark, narrativePerson, data.userRoleName, data.selectedChoiceText,
                            finaleProgress, sheets, haptics, callbacks, onKeepWriting,
                        )
                    }
                }
            }
            dialogs()
        },
        overlay = {
            LiuliTopScrollEdge(
                statusBarTop,
                belowStatus = liuliPageEdgeBelowStatus(collapsed = capsuleVisible, hasSubBar = false),
                wash = StoryMoodPalette.colors(dark).first(),
            )
            LiuliBottomScrollEdge(navBarBottom, aboveNav = bottomEdgeAbove, wash = StoryMoodPalette.colors(dark).last())
            if (locked && chapter != null && lockClock != null) LiuliStoryLockLayer(chapter, lockClock)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = READER_DOCK_BOTTOM + READER_DOCK_HEIGHT + READER_DOCK_GAP)
                    .onSizeChanged { slotHeightPx = it.height },
            ) {
                if (isGenerating) {
                    LiuliStoryGenerationCard(generation, callbacks.onGoToChat)
                } else {
                    data.readyChapter?.let { r -> LiuliStoryReadyButton(r, callbacks.onOpenReadyChapter, Modifier.align(Alignment.Center)) }
                }
            }
            LiuliStoryReaderDock(
                visible = dockVisible,
                nav = nav,
                progress = progress,
                seekEnabled = chapter != null,
                onPrev = callbacks.onPrev,
                onNext = callbacks.onNext,
                onContinueArc = callbacks.onContinueArc,
                onSeek = { f ->
                    chrome.seeking = true
                    val index = storyReaderSeekIndex(f, listState.layoutInfo.totalItemsCount)
                    if (index >= 0) scope.launch { listState.scrollToItem(index) }
                },
                onSeekEnd = { chrome.seeking = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // 章末选择的非阻塞撤销条（J2·同暖陶·边到边再让出导航栏）。
            StoryUndoBar(
                choiceText = data.selectedChoiceText.orEmpty(),
                remainingSeconds = data.pendingRemaining,
                visible = data.pendingActive && data.selectedChoiceText != null,
                onCancel = callbacks.onCancelPendingChoice,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
            LiuliStoryReaderCapsule(
                visible = capsuleVisible,
                storyTitle = story?.title,
                chapter = chapter,
                readingAnimationsEnabled = data.readingAnimationsEnabled,
                fontSizeIndex = data.fontSizeIndex,
                onBack = callbacks.onBack,
                // storyId 还没解析出来时「书页」行不出（同暖陶）。
                onOpenBookHub = story?.id?.let { sid -> { callbacks.onOpenBookHub(sid) } },
                onToggleAnimations = callbacks.onToggleAnimations,
                onSetFontSizeIndex = callbacks.onSetFontSizeIndex,
                onMenuExpandedChange = { chrome.menuOpen = it },
            )
        },
    )
}
