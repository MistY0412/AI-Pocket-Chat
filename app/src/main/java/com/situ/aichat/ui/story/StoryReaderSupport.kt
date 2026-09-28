package com.situ.aichat.ui.story

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.situ.aichat.BuildConfig
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryArcPlanning
import com.situ.aichat.story.StoryContentParser
import com.situ.aichat.story.StoryReaderRenderItem
import com.situ.aichat.story.StoryStatus
import kotlinx.coroutines.delay

// 阅读器两张脸共用的状态 / 派生量 / 进页事（琉璃 2.0 卷六·三·下甲 chunk 1·自 StoryReaderScreen 只搬不改）。
// 暖陶主屏与琉璃 LiuliStoryReaderScreen 都调这里；琉璃版不许另写一份同式。

/** 章节导航与「本章操作」两道门（原 StoryReaderScreen :190–196 与 BottomCapsule 三个实参式·纯·T1）。 */
internal data class StoryReaderNav(
    val chapterIndex: Int,
    val isLatestChapter: Boolean,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val showContinueArc: Boolean,
    val canRewrite: Boolean,
    val canViewPreviousDraft: Boolean,
)

internal fun storyReaderNav(
    chapters: List<StoryChapterEntity>,
    currentChapterId: String,
    currentChapter: StoryChapterEntity?,
    storyStatus: String?,
    isGenerating: Boolean,
    hasPreviousDraft: Boolean,
): StoryReaderNav {
    val chapterIndex = chapters.indexOfFirst { it.id == currentChapterId }
    val isLatestChapter = currentChapter?.id == chapters.lastOrNull()?.id
    return StoryReaderNav(
        chapterIndex = chapterIndex,
        isLatestChapter = isLatestChapter,
        hasPrev = chapterIndex > 0,
        hasNext = chapterIndex in 0 until (chapters.size - 1),
        showContinueArc = chapterIndex == chapters.lastIndex && storyStatus == StoryStatus.COMPLETED,
        // 卷三 §3.3：章末「本章操作」行与顶栏 ⋮ 同源的两个门——旧值原样提为局部变量（同一算法不写两处）。
        // C3 §0.2-6：单槽只在重写发生处有意义 ⇒ 与 canRewrite 同位，外加「槽里真有货」。
        canRewrite = isLatestChapter && !isGenerating && chapters.isNotEmpty(),
        canViewPreviousDraft = isLatestChapter && !isGenerating && hasPreviousDraft,
    )
}

/** 「收尾中 · 本弧第 K/L 章」的两个数字（原 :198–213 逐式·含原注释·纯·T1）：无收尾计划 → null。 */
internal fun storyFinaleProgress(story: StoryEntity?): StoryFinaleProgress? = story?.let { s ->
    s.finaleEndingType?.let {
        val total = StoryArcPlanning.effectiveArcLength(
            StoryArcPlanning.parseArcPlannedLength(s.storyOutline),
            isFinale = true,
        )
        // 终章弧大纲还没落库时 arcStart 还指着上一条普通弧 → 一律先显示第 1 章，别报个吓人的大数。
        val index = if (s.storyOutline.isNullOrEmpty()) {
            1
        } else {
            StoryArcPlanning.arcIndex(s.currentArcStartChapter, (s.cachedLatestChapterNumber ?: 0))
                .coerceIn(1, total)
        }
        StoryFinaleProgress(current = index, total = total)
    }
}

/** 正文渲染项（原 :121–132 逐式·含原注释·remember 键同原：章 id + 正文）。 */
@Composable
internal fun rememberStoryReaderRenderItems(currentChapter: StoryChapterEntity?): List<StoryReaderRenderItem> =
    remember(currentChapter?.id, currentChapter?.content) {
        currentChapter?.let {
            // 观测点只在 debug 包收集：release 传 null 走 StoryContentParser 的「零额外工作」快路。
            val diagnostics = if (BuildConfig.DEBUG) mutableListOf<String>() else null
            val blocks = StoryContentParser.parse(it.content, diagnostics)
            if (!diagnostics.isNullOrEmpty()) {
                // §7/§11 观测点：渲染期解析剥离计数（只打标签名+位置，正文内容绝不进日志）
                Log.i("StoryReader", "解析剥离 ${diagnostics.size} 处 $diagnostics")
            }
            StoryReaderRenderItem.make(blocks)
        } ?: emptyList()
    }

/**
 * 进页事（原 :101–116、:134–146 逐字·含原注释）+ 本卷新增一条「生成完成后要不要自动翻章」声明
 * （[autoJumpOnGenerated]：暖陶 true = 现行为、琉璃 false = R2「写好了 · 翻开」·切皮肤各自声明不串）。
 */
@Composable
internal fun StoryReaderEffects(
    viewModel: StoryReaderViewModel,
    currentChapter: StoryChapterEntity?,
    listState: LazyListState,
    haptics: StoryReaderHaptics,
    autoJumpOnGenerated: Boolean,
) {
    val context = LocalContext.current
    // P15.2 #5：阅读期间保持屏幕常亮（长章节沉浸阅读不触屏不自动息屏；iOS 未做，安卓地板非天花板）。
    // 复用 VoiceCallScreen 同款模板，离开阅读器即恢复。
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // 「就此完结」结果一次性提示（成功入档 / 生成中拒绝·照书架同款）。
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { resId -> Toast.makeText(context, resId, Toast.LENGTH_SHORT).show() }
    }
    val chapterKey = currentChapter?.id
    // 切章：待选章选择反馈触感。
    LaunchedEffect(chapterKey) {
        val ch = currentChapter ?: return@LaunchedEffect
        if (ch.hasChoice && ch.userChoice == null) haptics.selection()
    }
    // 滚动持久化（恢复 / 防抖保存 / 切章 flush·搬去 StoryReaderScrollPersistence，行为不变）。
    StoryReaderScrollPersistence(
        chapterKey = chapterKey,
        currentChapter = currentChapter,
        listState = listState,
        viewModel = viewModel,
    )
    // 卷六·三·下甲 R2：生成完成后翻不翻章由脸声明。
    LaunchedEffect(autoJumpOnGenerated) { viewModel.setAutoJumpOnGenerated(autoJumpOnGenerated) }
}

/**
 * 沉浸式工具栏显隐状态机（原 :158–170 逐式·含原注释·两张脸共用）。
 * [seeking]（本卷新增）= 琉璃进度坞正在拖：拖动引起的滚动与进场 2.5s 保底计时都不许收起外框（后者 = 复核 R1 🟡-2：
 *  进章 2.5s 内就开拖，胶囊会在拖动中途自己收掉）；暖陶恒 false（行为逐字节同原）。
 */
@Stable
internal class StoryReaderChromeState {
    var visible by mutableStateOf(true)
    var userToggled by mutableStateOf(false)
    // ⋮ 菜单展开时顶栏强制保持：防 2.5s 保底计时把锚点藏掉、菜单悬空。
    var menuOpen by mutableStateOf(false)
    var seeking by mutableStateOf(false)

    /** 轻触正文空白区：在显 / 隐间切换（userToggled 防止进场保底计时覆盖用户的手动选择）。 */
    fun toggle() {
        userToggled = true
        visible = !visible
    }
}

@Composable
internal fun rememberStoryReaderChromeState(chapterKey: String?, listState: LazyListState): StoryReaderChromeState {
    val state = remember { StoryReaderChromeState() }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    // 沉浸式工具栏显隐（顶栏 + 底部翻页条统一受控）：进入/切章先展示，约 2.5s 未操作或一旦滚动即隐入沉浸；
    // 点击阅读区在显/隐间切换（chromeUserToggled 防止进场保底计时覆盖用户的手动选择）。
    LaunchedEffect(chapterKey) {
        state.userToggled = false
        state.visible = true
        delay(2_500)
        if (!state.userToggled && !state.menuOpen && !state.seeking) state.visible = false
    }
    LaunchedEffect(isScrolling) { if (isScrolling && !state.seeking) state.visible = false }
    return state
}

/** 阅读进度两量（原 :149–157 逐式·含原注释）：返回 State，读点由调用方定（琉璃把读点下沉进进度坞）。 */
@Stable
internal class StoryReaderProgress(val percent: State<Int>, val remainingMinutes: State<Int>)

@Composable
internal fun rememberStoryReaderProgress(
    listState: LazyListState,
    renderItems: List<StoryReaderRenderItem>,
    hasRecap: Boolean,
): StoryReaderProgress {
    // 阅读进度（底部胶囊「62% · 还剩 X 分钟」）：视口底边模型，取参在 StoryReaderProgressBridge——
    // 滚到底（章末矮项堆全可见）恒 100% + 0 分钟，胶囊按既有分支只显「100%」。
    val percent = remember { derivedStateOf { StoryReaderProgressBridge.percent(listState.layoutInfo) } }
    val remaining = remember(renderItems, hasRecap) {
        derivedStateOf { StoryReaderProgressBridge.remainingMinutes(listState.layoutInfo, hasRecap, renderItems) }
    }
    return StoryReaderProgress(percent, remaining)
}

/**
 * 锁态 1Hz 时钟（原 :384–393 逐式·含原注释）：只给「有解锁时刻」的章起——自由模式书 unlockAt 恒 null，
 * 整本书零时钟。**不许再加「未解锁才起」条件**：到点自动揭开正是靠这口时钟驱动重判，加了就永远揭不开。
 * 键取章 id → 切章重启时钟。返回 State，读点由调用方定。
 */
@Composable
internal fun rememberStoryLockClock(chapter: StoryChapterEntity?): State<Long>? {
    val ch = chapter?.takeIf { it.unlockAt != null } ?: return null
    return produceState(System.currentTimeMillis(), ch.id) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
}

/** 滚到列表末项（原 :300 / :475 两处同式）：建议卡「还想继续写」与「带我去做选择」共用。 */
internal suspend fun LazyListState.animateScrollToReaderEnd() {
    animateScrollToItem((layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
}

/** 进度坞拖到比例 [fraction] → 滚到第几项（顶对齐·纯·T1·琉璃新增）；列表没有项 → -1（调用方不滚）。 */
internal fun storyReaderSeekIndex(fraction: Float, totalItems: Int): Int {
    if (totalItems <= 0) return -1
    val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    return (f * totalItems).toInt().coerceIn(0, totalItems - 1)
}

/** 顶栏书名兜底（原 StoryReaderTopBar.TitleCapsule :188 式）：空白 → 「未命名」。 */
@Composable
internal fun storyReaderCapsuleTitle(storyTitle: String?): String =
    storyTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.story_reader_untitled)

/** 顶栏「· 第 N 章」（原 TitleCapsule :199 式）：章还没加载 → null。 */
@Composable
internal fun storyReaderChapterSuffix(chapter: StoryChapterEntity?): String? =
    chapter?.let { "· " + stringResource(R.string.story_reader_chapter_n, it.chapterNumber) }
