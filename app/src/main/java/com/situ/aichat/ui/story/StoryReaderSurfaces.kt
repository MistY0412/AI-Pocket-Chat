package com.situ.aichat.ui.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.story.StoryChoiceClassifier
import com.situ.aichat.story.StoryReaderRenderItem
import com.situ.aichat.story.StoryReaderTypography
import com.situ.aichat.story.StoryVisualPerformance

// 阅读器两张脸共用的纸面列表项 + 弹层态 + 弹层与对话框整块（琉璃 2.0 卷六·三·下甲 chunk 1·自 StoryReaderScreen 只搬不改）。

/** 阅读器列表的纸面三类项（原 StoryReaderScreen :239–283 逐字·含原注释·两张脸共用）：封面 → 上回说到 → 正文块。 */
internal fun LazyListScope.storyReaderPaperItems(
    currentChapter: StoryChapterEntity?,
    storyId: String?,
    recapSummary: String?,
    renderItems: List<StoryReaderRenderItem>,
    isDark: Boolean,
    typography: StoryReaderTypography,
    performance: StoryVisualPerformance,
    containerHeight: Dp,
) {
    val chapterKey = currentChapter?.id
    item(key = "cover") {
        ChapterCover(
            chapter = currentChapter,
            isDark = isDark,
            containerHeight = containerHeight,
            modifier = Modifier
                .widthIn(max = StoryReaderLayout.maxContentWidth)
                .padding(horizontal = StoryReaderLayout.horizontalPadding),
        )
    }
    // 卷三 C3：「上回说到」——隔了一阵回来才出（判定见 StoryReaderViewModel.recapSummary），封面之后正文之前。
    recapSummary?.let { summary ->
        item(key = "recap") {
            // 收起只记在本次进程内（图纸 J5）：重开 App 若仍超阈值会重新展开，语义 = 「又隔了一阵」。
            var recapExpanded by rememberSaveable(storyId, chapterKey) { mutableStateOf(true) }
            Box(
                Modifier
                    .widthIn(max = StoryReaderLayout.maxContentWidth)
                    .padding(horizontal = StoryReaderLayout.horizontalPadding)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                StoryRecapStrip(
                    summary = summary,
                    expanded = recapExpanded,
                    isDark = isDark,
                    onToggle = { recapExpanded = it },
                )
            }
        }
    }
    // key 含章节 id：渲染项 id 是章内局部（0,1,2…），切章须避免跨章 key 碰撞复用旧块（致 reveal/onVisible/特效不重触发）。
    items(renderItems, key = { "$chapterKey#${it.id}" }) { item ->
        StoryRenderBlock(
            item = item,
            animationsEnabled = performance.allowsRevealAnimations,
            animatedTextEnabled = performance.allowsAnimatedText,
            isDark = isDark,
            typography = typography,
            modifier = Modifier
                .widthIn(max = StoryReaderLayout.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = StoryReaderLayout.horizontalPadding),
        )
    }
}

/**
 * 阅读器页内弹层态（原 :172–188 七个 remember 逐个·含原注释·两张脸共用）。
 * [dialog] / [pendingGraceful] 保持 MutableState 形态（[StoryReaderDialogHost] 吃 MutableState 桥接·零改）。
 */
@Stable
internal class StoryReaderSheetsState {
    val dialog: MutableState<ReaderDialog?> = mutableStateOf(null)
    var showCustomChoice by mutableStateOf(false)
    /** 「查看上一版」弹层（C3·图纸三 §4 画面②）；槽为空或 JSON 损坏时菜单项本就不出现。 */
    var showPreviousDraft by mutableStateOf(false)
    /** 「本章操作」浮层展开态（卷三 §3.4）：它是锚定浮层不是弹窗族，故不进 [ReaderDialog] 枚举；
     *  托管在屏侧是因为承载它的列表项滚出可视区会被回收，state 留在项里会丢。 */
    var showChapterActionsMenu by mutableStateOf(false)
    /** 导演台展开态（卷三 §3.4）：sheet 不属弹窗族，同样不进 [ReaderDialog] 枚举。 */
    var showDirector by mutableStateOf(false)
    /** 递增即让推进区输入卡呼吸一次（建议卡「还想继续写」滚过来后的指路信号·ST11 §4.2）。 */
    var breatheTrigger by mutableIntStateOf(0)
    // 收尾方式标志（卷二 §4.4 画面④）：结局类型三选是两条路共用的第二步，用它区分选完之后是
    // 「定收尾计划」（从容收尾）还是「立即写结局章」（老 requestEnding）。任何取消路径复位。
    // （「跳过选择」延迟提交标志只在弹窗接线块内读写，随之搬进 StoryReaderDialogHost。）
    val pendingGraceful: MutableState<Boolean> = mutableStateOf(false)
}

@Composable
internal fun rememberStoryReaderSheetsState(): StoryReaderSheetsState = remember { StoryReaderSheetsState() }

/**
 * 阅读器弹层与对话框整块（原 :423–478 逐字·含原注释·两张脸共用·琉璃脸经 [face] 接入（下·乙））。
 * `onGoToChoice` 由调用方给（要用屏侧的 scope 与 listState）。
 */
@Composable
internal fun StoryReaderSheets(
    sheets: StoryReaderSheetsState,
    viewModel: StoryReaderViewModel,
    story: StoryEntity?,
    chapters: List<StoryChapterEntity>,
    currentChapter: StoryChapterEntity?,
    narrativePerson: String,
    userRoleName: String?,
    previousDraft: StoryChapterDraft?,
    askNext: Boolean,
    error: StoryReaderError?,
    haptics: StoryReaderHaptics,
    onGoToChoice: () -> Unit,
    /** 画哪张脸（琉璃 2.0 卷六·三·下乙：默认 = 暖陶；琉璃传 LiuliStoryReaderSheetFace）。接线全在本函数，脸只管画。 */
    face: StoryReaderSheetFace = StoryReaderWarmSheetFace,
) {
    // 自由输入面板。
    if (sheets.showCustomChoice) {
        face.CustomChoiceSheet(
            prompt = currentChapter?.choicePrompt ?: storyDefaultChoicePrompt(narrativePerson, userRoleName),
            hint = storyCustomChoiceHint(narrativePerson, userRoleName),
            onConfirm = { haptics.light(); viewModel.submitChoice(it) },
            onDismiss = { sheets.showCustomChoice = false },
        )
    }

    // 导演台（卷三 §4.4 + 已存走向 2026-08-06 §4.5）：走向栏双路——没答过走 submitChoice 创建路（逐字节同旧路），
    // 已答过走覆盖直写 + 撤回；节拍栏走单条定向写。三条写各自独立提交。
    if (sheets.showDirector) {
        // 导演台只从末章推进区打开（zone 仅末章渲染）⇒ 走向取当前章即末章；哨兵不当预填文本（准创建态）。
        val latestUserChoice = currentChapter?.userChoice
        face.DirectorSheet(
            beats = story?.pendingChapterBeats,
            beatsUserEdited = story?.pendingBeatsUserEdited == true,
            savedDirection = latestUserChoice?.takeUnless { StoryChoiceClassifier.isSentinel(it) },
            directionCommitted = latestUserChoice != null,
            onSubmitFlow = { haptics.light(); viewModel.submitChoice(it) },
            onOverwriteDirection = { haptics.light(); viewModel.overwriteDirection(it) },
            onWithdrawDirection = { haptics.light(); viewModel.withdrawDirection() },
            onSaveBeats = viewModel::saveChapterBeats,
            onRestoreAiBeats = viewModel::restoreAiBeats,
            onDismiss = { sheets.showDirector = false },
        )
    }

    // C3「上一版」回翻弹层（换回后重读章 → 自然回顶部）。
    previousDraft?.takeIf { sheets.showPreviousDraft }?.let { draft ->
        face.PreviousDraftSheet(
            draft = draft,
            onRestore = { sheets.showPreviousDraft = false; haptics.light(); viewModel.restorePreviousDraft() },
            onDismiss = { sheets.showPreviousDraft = false },
        )
    }

    // VM 态触发的两个提示弹窗（生成下一章确认 / 错误·搬去 StoryReaderDialogHost，行为不变）。
    StoryReaderAlerts(askNext = askNext, error = error, viewModel = viewModel, face = face)

    // ⋮ 菜单弹窗族（接线宿主搬去 StoryReaderDialogHost，行为不变；共享态经 MutableState 桥接）。
    StoryReaderDialogHost(
        dialogState = sheets.dialog,
        pendingGracefulState = sheets.pendingGraceful,
        currentChapter = currentChapter,
        story = story,
        chapters = chapters,
        onGoToChoice = onGoToChoice,
        viewModel = viewModel,
        face = face,
    )
}
