package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.story.StoryGenerationTaskManager
import com.situ.aichat.story.storyCleanTitle
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LiuliSpinner
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.liuli.glass.LiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.currentLiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.liuli.page.SCROLL_EDGE_OVERSCAN
import com.situ.aichat.ui.story.StoryPhaseBar
import com.situ.aichat.ui.story.StoryReadyChapter
import com.situ.aichat.ui.story.StoryReaderLayout
import com.situ.aichat.ui.story.storyUnlockRemainingText
import com.situ.aichat.ui.theme.LocalIsDarkTheme

// 琉璃阅读器浮层（琉璃 2.0 卷六·三·下甲 §4.4）：写作中进度卡（R2）/ 写好了钮 / 锁定层 / 锁定章灰条骨架。

/** 章末各项与骨架的同一条宽度链（= 暖陶章末各项）。 */
internal val READER_ITEM_MODIFIER = Modifier
    .widthIn(max = StoryReaderLayout.maxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = StoryReaderLayout.horizontalPadding)

/** 进度卡内距（草图 ⑥ `.gen`）。 */
private val GEN_PAD_H = 14.dp
private val GEN_PAD_V = 12.dp
private val GEN_SPINNER = 14.dp
private val GEN_HEAD_GAP = 8.dp
private val GEN_BAR_TOP = 10.dp
private val GEN_PHASE_TOP = 6.dp
/** 草图 `.phs` 下 8 减去文字钮自带的 4（40 视觉 / 48 触达）。 */
private val GEN_FOOT_TOP = 4.dp
private val GEN_LINK_PAD_H = 8.dp

/** 锁卡（= 暖陶 `LockedOverlay` 外距 32·草图 ⑦ `.lockc`）。 */
private val LOCK_CARD_INSET_H = 32.dp
private val LOCK_PAD_H = 16.dp
private val LOCK_PAD_V = 22.dp
private val LOCK_ICON_SP = 30.sp

/** 锁定章灰条（草图 ⑦·宽度 0 = 段落空行）。 */
private val SKELETON_TOP = 28.dp
private val SKELETON_BAR = 9.dp
private val SKELETON_GAP = 13.dp
private val SKELETON_RADIUS = 5.dp
private val SKELETON_WIDTHS = listOf(1f, 0.96f, 1f, 0.72f, 0f, 1f, 0.94f, 1f, 0.60f)

/** 写下一章时浮在坞上的玻璃进度卡（R2·正文照读）：[generation] 的值只在这里读，推送只重组本卡。 */
@Composable
internal fun LiuliStoryGenerationCard(
    generation: State<StoryGenerationTaskManager.GenerationProgress?>,
    onGoToChat: () -> Unit,
) {
    val gen = generation.value ?: return // 唯一读点：推送只重组本卡
    val dark = LocalIsDarkTheme.current
    val onGlass = LiuliTheme.onGlass
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = READER_DOCK_INSET_H)
            .liuliGlass(LiuliShapes.medium, dark, role = LiuliGlassRole.Panel)
            .padding(horizontal = GEN_PAD_H, vertical = GEN_PAD_V),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GEN_HEAD_GAP)) {
            LiuliSpinner(size = GEN_SPINNER)
            // 出现时 Polite 播报一次（只挂稳定标题·phase 高频切换绝不挂·同暖陶 P1-20）。
            Text(
                stringResource(R.string.story_reader_generating, gen.chapterNumber),
                style = AppTypography.label,
                color = onGlass.primary,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(GEN_BAR_TOP))
        StoryPhaseBar(genPhase = gen.genPhase, progress = gen.progress, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(GEN_PHASE_TOP))
        Text(gen.phase, style = AppTypography.caption, color = onGlass.secondary)
        Spacer(Modifier.height(GEN_FOOT_TOP))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.story_reader_generating_keep_reading),
                style = AppTypography.caption,
                color = onGlass.secondary,
                modifier = Modifier.weight(1f),
            )
            // a11y 措辞 = 「去聊天，生成完成后通知我」（覆盖可见短文案·同暖陶）。
            val goChatA11y = stringResource(R.string.story_reader_go_chat_a11y)
            LiuliButton(
                onClick = onGoToChat,
                style = LiuliButtonStyle.Text,
                contentPadding = PaddingValues(horizontal = GEN_LINK_PAD_H),
                modifier = Modifier.semantics { contentDescription = goChatA11y },
            ) { Text(stringResource(R.string.story_reader_go_chat)) }
        }
    }
}

/** 写完后的「第 N 章写好了 · 翻开 ›」（R2·点了才翻到新章）。 */
@Composable
internal fun LiuliStoryReadyButton(ready: StoryReadyChapter, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    LiuliButton(onClick = onOpen, modifier = modifier, style = LiuliButtonStyle.Glass) {
        Text(stringResource(R.string.story_reader_chapter_ready, ready.chapterNumber))
    }
}

/** 锁定章：整屏糊一层 + 居中玻璃锁卡（章号 / 章名 / 剩余时间 / 提示·文案同暖陶）；剩余时间在锁卡里读时钟。 */
@Composable
internal fun BoxScope.LiuliStoryLockLayer(chapter: StoryChapterEntity, clock: State<Long>) {
    val dark = LocalIsDarkTheme.current
    val onGlass = LiuliTheme.onGlass
    // 整屏糊开（正文本就没进列表·这层只糊章首与灰条）。不收点击：点它照样切外框（同暖陶锁态）。
    // 四边伸出屏外（同屏边带 [SCROLL_EDGE_OVERSCAN]）：玻璃边缘的描边落在屏外，不在屏幕四周画出一圈细框（复核 R1 🟡-1·
    // 装机实测通透档浅色一圈暗线、深色一圈亮线）；自研毛玻璃档不出屏（屏外无景，出屏会让边上糊不透·该档本就无框）。
    val overscan = if (currentLiuliGlassEngine() == LiuliGlassEngine.FROSTED_BLUR) 0.dp else SCROLL_EDGE_OVERSCAN
    Box(
        Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val over = overscan.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + over * 2, constraints.maxHeight + over * 2))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-over, -over) }
            }
            .liuliGlass(LiuliShapes.rect, dark, role = LiuliGlassRole.Panel),
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = LOCK_CARD_INSET_H)
            .fillMaxWidth()
            .liuliGlass(LiuliShapes.dialog, dark, role = LiuliGlassRole.Panel)
            .padding(horizontal = LOCK_PAD_H, vertical = LOCK_PAD_V),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = LOCK_ICON_SP, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.story_reader_chapter_n, chapter.chapterNumber), style = AppTypography.caption, color = onGlass.secondary)
        Spacer(Modifier.height(4.dp))
        Text(
            storyCleanTitle(chapter.title),
            style = AppTypography.titleSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
            color = onGlass.primary,
            textAlign = TextAlign.Center,
        )
        chapter.unlockAt?.let { unlockAt ->
            val now by clock // 每秒只重组这张卡
            Spacer(Modifier.height(8.dp))
            Text(
                storyUnlockRemainingText(unlockAt, now),
                style = AppTypography.label.copy(fontWeight = FontWeight.W600),
                color = AppTheme.colors.accent.text,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.story_reader_locked_hint), style = AppTypography.caption, color = onGlass.secondary, textAlign = TextAlign.Center)
    }
}

/** 锁定章正文位的灰条骨架（正文一个字都不渲染）。 */
@Composable
internal fun LiuliStoryLockedSkeleton(dark: Boolean, modifier: Modifier) {
    Column(
        modifier.padding(top = SKELETON_TOP).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(SKELETON_GAP),
    ) {
        SKELETON_WIDTHS.forEach { w ->
            if (w == 0f) {
                Spacer(Modifier.height(SKELETON_BAR))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(SKELETON_BAR)
                        .clip(RoundedCornerShape(SKELETON_RADIUS))
                        .background(StoryReaderLayout.ornamentColor(dark)),
                )
            }
        }
    }
}
