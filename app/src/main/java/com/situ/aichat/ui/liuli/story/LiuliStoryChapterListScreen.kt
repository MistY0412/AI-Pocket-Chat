package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.isUnlocked
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageCircleAction
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.story.ArcSectionHeader
import com.situ.aichat.ui.story.StoryChapterBookHead
import com.situ.aichat.ui.story.StoryChapterListEffects
import com.situ.aichat.ui.story.StoryChapterListEmptyState
import com.situ.aichat.ui.story.StoryChapterListViewModel
import com.situ.aichat.ui.story.StoryChapterNodeKind
import com.situ.aichat.ui.story.StoryChapterRowText
import com.situ.aichat.ui.story.TimelineNode
import com.situ.aichat.ui.story.rememberArcHeadAnchors
import com.situ.aichat.ui.story.rememberStoryMinuteClock
import com.situ.aichat.ui.story.storyChapterContinueTarget
import com.situ.aichat.ui.story.storyChapterListSummary
import com.situ.aichat.ui.story.storyChapterNodeKind
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 章卡内距（横 = 组内距 16 · 竖 12 = 卷六·二通知行）与卡缝 12（取代暖陶行底 22）。 */
private val CHAPTER_CARD_PAD_H = LiuliPageGeometry.groupPadH
private val CHAPTER_CARD_PAD_V = 12.dp
private val CHAPTER_CARD_GAP = 12.dp
/** 节点顶 = 暖陶 2 + 卡内顶距 12 = 14：保持节点对章号行的相对位置。 */
private val CHAPTER_NODE_TOP = 2.dp + CHAPTER_CARD_PAD_V
/** 左轴 23 · 轴缝 12 · 线 1.5 · 点 11（= 暖陶）。 */
private val CHAPTER_AXIS = 23.dp
private val CHAPTER_AXIS_GAP = 12.dp
private val CHAPTER_LINE = 1.5.dp
private val CHAPTER_DOT = 11.dp
/** 紫光点光晕 = 11 + 2 × 3（设计稿 `.nd b` 3px 外光圈）· 15%；连接线 35% → 8%（设计稿 `.nd i`）。 */
private val CHAPTER_HALO = 17.dp
private const val CHAPTER_HALO_ALPHA = 0.15f
private const val CHAPTER_LINE_TOP_ALPHA = 0.35f
private const val CHAPTER_LINE_BOTTOM_ALPHA = 0.08f
private val QUICK_GAP = 12.dp // 快捷钮缝 12 · 底 24（= 暖陶）
private val QUICK_BOTTOM = 24.dp

/** 已读紫光点的测试标记（生产期零影响）。 */
internal const val LIULI_CHAPTER_DOT_TAG = "liuliChapterDot"

/**
 * 琉璃章节（琉璃 2.0 卷六·三·上 §4.5·设计稿 S2）：与暖陶 [com.situ.aichat.ui.story.StoryChapterListScreen] 共用同一个 VM、
 * 进页事 / 分钟时钟 / 派生量 / 弧头 / 节点判据与书头、行文字、空态内容件；常驻胶囊「← 章节 ⚙」，
 * 每话一张半透明卡（卡是点击面·锁住的章不可点），已读节点换紫光点、连接线换主色竖渐变。
 */
@Composable
internal fun LiuliStoryChapterListScreen(
    onBack: () -> Unit,
    onStoryGone: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: StoryChapterListViewModel = hiltViewModel(),
) {
    val story by viewModel.story.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val generatingPhase by viewModel.generatingPhase.collectAsStateWithLifecycle()
    val lastReadChapterId by viewModel.lastReadChapterId.collectAsStateWithLifecycle()
    val advancedFromChapterNumber by viewModel.advancedFromChapterNumber.collectAsStateWithLifecycle()
    StoryChapterListEffects(viewModel, onStoryGone)
    val now by rememberStoryMinuteClock()
    LiuliStoryChapterListPage(
        story, chapters, generatingPhase, lastReadChapterId, advancedFromChapterNumber, now, onBack, onOpenChapter, onOpenSettings, onRetry = viewModel::retryGeneration,
    )
}

/** 无 VM 的章节页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryChapterListPage(
    story: StoryEntity?,
    chapters: List<StoryChapterEntity>,
    generatingPhase: String?,
    lastReadChapterId: String?,
    advancedFromChapterNumber: Int?,
    now: Long,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    LiuliPage(
        title = stringResource(R.string.story_chapter_nav_title),
        onBack = onBack,
        collapsed = true,
        actions = {
            LiuliPageCircleAction(onClick = onOpenSettings, contentDescription = stringResource(R.string.story_menu_settings), icon = Icons.Filled.Tune)
        },
    ) {
        if (chapters.isEmpty()) {
            StoryChapterListEmptyState(generatingPhase, story?.status, Modifier.padding(top = LiuliPageGeometry.navRow + LiuliPageGeometry.titleGap)) {
                LiuliButton(onClick = onRetry, style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.story_quick_regenerate)) }
            }
        } else {
            val summary = storyChapterListSummary(chapters, lastReadChapterId, advancedFromChapterNumber)
            val arcHeads = rememberArcHeadAnchors(chapters, story)
            val dark = LocalIsDarkTheme.current
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val gutter = LiuliPageGeometry.gutter
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize().contentMaxWidth(),
                contentPadding = PaddingValues(gutter, LiuliPageGeometry.navRow + LiuliPageGeometry.titleGap, gutter, navBarBottom + LiuliPageGeometry.pageBottom),
            ) {
                item(key = "header") { story?.let { StoryChapterBookHead(it, summary.lastReadNumber, summary.choiceCount, surface = Modifier.liuliStoryCard(dark)) } }
                item(key = "quick") { LiuliChapterQuickActions(summary.pending, storyChapterContinueTarget(summary.pending, summary.resume), onOpenChapter) }
                itemsIndexed(summary.newestFirst, key = { _, c -> c.id }) { index, chapter ->
                    // 弧边界插小节头（同暖陶·简史空且无进行中弧 → 恒空表）。
                    arcHeads[chapter.id]?.let { ArcSectionHeader(it) }
                    LiuliChapterTimelineRow(
                        chapter = chapter,
                        isLatest = chapter.id == summary.latestId,
                        isLast = index == summary.newestFirst.lastIndex,
                        lastReadNumber = summary.lastReadNumber,
                        now = now,
                        dark = dark,
                        onClick = { if (chapter.isUnlocked(now)) onOpenChapter(chapter.id) },
                    )
                }
            }
        }
    }
}

/** 快捷操作：有待选章 → 主色「去做选择」；续读章 ≠ 待选章 → 玻璃「继续阅读」；两者都无不画。 */
@Composable
private fun LiuliChapterQuickActions(pending: StoryChapterEntity?, cont: StoryChapterEntity?, onOpenChapter: (String) -> Unit) {
    if (pending == null && cont == null) return
    Column(Modifier.padding(bottom = QUICK_BOTTOM), verticalArrangement = Arrangement.spacedBy(QUICK_GAP)) {
        pending?.let {
            LiuliButton(onClick = { onOpenChapter(it.id) }, style = LiuliButtonStyle.Prominent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.story_quick_make_choice, it.chapterNumber))
            }
        }
        cont?.let {
            LiuliButton(onClick = { onOpenChapter(it.id) }, style = LiuliButtonStyle.Glass, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.story_quick_continue, it.chapterNumber))
            }
        }
    }
}

/** 时间线一行：左轴节点 + 竖渐变线（不可点）+ 右侧半透明章卡（卡是点击面，排在材质之后·锁住的章不可点）。 */
@Composable
private fun LiuliChapterTimelineRow(
    chapter: StoryChapterEntity,
    isLatest: Boolean,
    isLast: Boolean,
    lastReadNumber: Int,
    now: Long,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val accent = AppTheme.colors.accent.primary
    val unlocked = chapter.isUnlocked(now)
    val kind = storyChapterNodeKind(chapter, now, lastReadNumber)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CHAPTER_AXIS_GAP)) {
        Column(Modifier.width(CHAPTER_AXIS), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = CHAPTER_NODE_TOP)) { LiuliChapterNode(kind) }
            if (!isLast) {
                val line = Brush.verticalGradient(listOf(accent.copy(alpha = CHAPTER_LINE_TOP_ALPHA), accent.copy(alpha = CHAPTER_LINE_BOTTOM_ALPHA)))
                Box(Modifier.padding(top = 2.dp).width(CHAPTER_LINE).weight(1f).background(line))
            }
        }
        // 卡是点击面，排在材质（先 clip）之后 → 按压涟漪吃圆角；锁住的章不可点。
        val card = Modifier.fillMaxWidth().padding(bottom = CHAPTER_CARD_GAP).liuliStoryCard(dark)
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CHAPTER_CARD_PAD_H, vertical = CHAPTER_CARD_PAD_V)
        StoryChapterRowText(chapter, isLatest, unlocked, now, card)
    }
}

/** 节点：已读 = 紫光点（主色渐变实心 11 + 17 直径 15% 光晕）；其余三态原样复用暖陶 [TimelineNode]（皮下自动取琉璃色）。 */
@Composable
private fun LiuliChapterNode(kind: StoryChapterNodeKind) {
    if (kind != StoryChapterNodeKind.DOT) {
        TimelineNode(kind)
        return
    }
    val accent = AppTheme.colors.accent.primary
    Box(Modifier.size(CHAPTER_AXIS), contentAlignment = Alignment.Center) {
        Box(Modifier.size(CHAPTER_HALO).clip(CircleShape).background(accent.copy(alpha = CHAPTER_HALO_ALPHA)))
        Box(Modifier.size(CHAPTER_DOT).clip(CircleShape).background(LiuliMaterials.accentBrush).testTag(LIULI_CHAPTER_DOT_TAG))
    }
}
