package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.story.StoryChoiceClassifier
import com.situ.aichat.story.unlockRemainingMinutes
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay

// 章末各件两张脸共用的判据 / 取值 / 动效态（琉璃 2.0 卷六·三·下甲 chunk 1·自 StoryChoiceSection / StoryChapterEndZone /
// StoryContinueZone / StoryDraftCard / StoryReaderChrome / StoryReaderScreen 只搬不改）。

/** 呼吸动效：全周期 300ms → 去程/回程各 150ms（PITFALLS 1d：往复 N ms 指全周期）。——原 StoryContinueZone private 常量搬来改 internal。 */
internal const val BREATHE_HALF_MS = 150

/** 选择区挂哪一章（原 StoryReaderScreen :322–323 式·纯·T1）：完结门 = [StoryReaderEndgameLogic.showChoiceSection]。 */
internal fun storyChoiceChapter(chapter: StoryChapterEntity?, storyStatus: String?): StoryChapterEntity? =
    chapter?.takeIf { StoryReaderEndgameLogic.showChoiceSection(it.hasChoice, it.userChoice, storyStatus.orEmpty()) }

/** 建议完结卡出不出（原 storyEndingSuggestItem :97–101 式·纯·T1）。 */
internal fun storyShowsEndingSuggest(chapter: StoryChapterEntity?, isLatestChapter: Boolean, storyStatus: String?): Boolean =
    chapter != null && StoryReaderEndgameLogic.showEndingSuggestCard(
        isLatestChapter = isLatestChapter,
        storyStatus = storyStatus.orEmpty(),
        aiSuggestedEnding = chapter.aiSuggestedEnding,
    )

/** 推进区出不出（原 storyContinueZoneItem :136–141 式·纯·T1）。章还在加载（null）时不出（卷六·三·下乙 D-11）。 */
internal fun storyShowsContinueZone(chapter: StoryChapterEntity?, isLatestChapter: Boolean, storyStatus: String?): Boolean =
    // 闸后 chapter 已智能转非空：原 `chapter?.hasChoice == true` / `chapter?.userChoice` 去掉 `?.`（逐值等价·否则报多余安全调用
    // 警告）——卷六·三·下乙施工 D-3，复核 R1 核准。
    chapter != null && StoryReaderEndgameLogic.showContinueZone(
        isLatestChapter = isLatestChapter,
        storyStatus = storyStatus.orEmpty(),
        hasChoice = chapter.hasChoice,
        userChoice = chapter.userChoice,
    )

/** 推进区显示成什么样（原 storyContinueZoneItem :143–150 两式·纯·T1）。 */
internal data class StoryContinueZoneState(val mode: ContinueZoneMode, val directionText: String?)

internal fun storyContinueZoneState(chapter: StoryChapterEntity?): StoryContinueZoneState {
    // 已存走向态 B（图纸 2026-08-06 §4.2）：走向 = 末章 userChoice 经分类器派生，零新 StateFlow / 零新 DB 列。
    val freeform = StoryChoiceClassifier.freeformDirective(chapter)
    return StoryContinueZoneState(StoryReaderEndgameLogic.continueZoneMode(chapter?.userChoice, freeform), freeform)
}

/** 推进区主胶囊文案（原 StoryContinueZone :272–277 `when` 逐支·纯·T1）。 */
@StringRes
internal fun storyContinueFlowLabelRes(mode: ContinueZoneMode): Int = when (mode) {
    ContinueZoneMode.NATURAL_FLOW -> R.string.story_continue_flow
    ContinueZoneMode.NEXT_CHAPTER -> R.string.story_continue_next_chapter
    ContinueZoneMode.BY_DIRECTION -> R.string.story_continue_by_direction
}

/** 草稿卡 tag（原 StoryDraftCard :64–67 式·纯·T1）：导演台改过 = 「你已指定」，否则「AI 预排」。 */
@StringRes
internal fun storyDraftTagRes(userEdited: Boolean): Int =
    if (userEdited) R.string.story_continue_draft_tag_user else R.string.story_continue_draft_tag_ai

/** 快评点一档后的新值（原 RatingPill :251 式·纯·T1）：再点已选中的那一档 = 取消（null·J3）。 */
internal fun storyRatingNext(current: Int?, tier: Int): Int? = if (current == tier) null else tier

/** 选项解码（原 StoryChoiceSection :80 式）。 */
@Composable
internal fun rememberStoryChoiceOptions(chapter: StoryChapterEntity): List<String> =
    remember(chapter.choiceOptions) { StoryChoiceClassifier.decodeChoiceOptions(chapter.choiceOptions) }

/** 选中选项的弹一下（原 StoryChoiceSection :82–90 逐式·含原注释）：返回 Animatable，调用方读 `.value`。 */
@Composable
internal fun rememberStoryChoicePopScale(selectedChoiceText: String?, isLocked: Boolean): Animatable<Float, AnimationVector1D> {
    // 选中选项的弹一下（1→1.04→1，= iOS spring bounce）。
    val popScale = remember { Animatable(1f) }
    LaunchedEffect(selectedChoiceText) {
        if (selectedChoiceText != null && !isLocked) {
            popScale.animateTo(1.04f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            delay(220)
            popScale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
        }
    }
    return popScale
}

/** 推进区入口卡呼吸（原 StoryContinueZone :205、:211–217 逐式·含原注释）。 */
@Composable
internal fun rememberStoryBreatheScale(breatheTrigger: Int): Animatable<Float, AnimationVector1D> {
    val reduceMotion = rememberReduceMotion()
    // 呼吸：首帧不放（trigger 初值不该让卡片自己抖一下），只在 trigger 真变化时跑一次去回程。
    val breathe = remember { Animatable(1f) }
    LaunchedEffect(breatheTrigger) {
        if (breatheTrigger == 0 || reduceMotion) return@LaunchedEffect
        breathe.animateTo(1.03f, tween(BREATHE_HALF_MS, easing = AppMotion.EaseInOut))
        breathe.animateTo(1f, tween(BREATHE_HALF_MS, easing = AppMotion.EaseInOut))
    }
    return breathe
}

/** 「🔒 X小时Y分后解锁」（原 StoryReaderChrome.LockedOverlay :291–297 逐式）。 */
@Composable
internal fun storyUnlockRemainingText(unlockAt: Long, now: Long): String {
    val mins = unlockRemainingMinutes(unlockAt, now)
    val remaining = if (mins >= 60) {
        stringResource(R.string.story_remaining_hm, mins / 60, mins % 60)
    } else {
        stringResource(R.string.story_remaining_m, mins)
    }
    return stringResource(R.string.story_unlock_in, remaining)
}

// ── 卷六·三·下甲 §3.11：写琉璃选择区 / 快评时补抽的四处带业务含义的式子（暖陶同步改调）──

/** 选择区题目（原 StoryChoiceSection :96 式）：AI 没给题目 → 按人称兜底。 */
@Composable
internal fun storyChoicePromptText(chapter: StoryChapterEntity, narrativePerson: String, userRoleName: String?): String =
    chapter.choicePrompt ?: storyDefaultChoicePrompt(narrativePerson, userRoleName)

/** 已答后的回显行（原 :119–125 条件 + 拼接）：没答 / 答了空串 → null。 */
@Composable
internal fun storyChoiceFeedbackText(chapter: StoryChapterEntity, narrativePerson: String, userRoleName: String?): String? =
    chapter.userChoice?.takeIf { it.isNotEmpty() }?.let { storyChoiceFeedbackPrefix(narrativePerson, userRoleName) + it }

/** 选项字母（原 :106 式·纯·T1）。 */
internal fun storyChoiceLetter(index: Int): String = ('A' + index).toString()

/** 选中后其余项变淡（原 :109 式·纯·T1）。 */
internal fun storyChoiceDimmed(selectedChoiceText: String?, option: String): Boolean =
    selectedChoiceText != null && selectedChoiceText != option

/** 快评提示行（原 ChapterRatingRow :211 式·纯·T1）：评过 = 「已记下」，没评 = 「这一章怎么样？」。 */
@StringRes
internal fun storyRatingPromptRes(rating: Int?): Int =
    if (rating != null) R.string.story_rating_done else R.string.story_rating_ask

/** 快评三档（原 ChapterRatingRow 三枚 RatingPill 的次序与映射：爽 3 / 还行 2 / 不行 1）。 */
internal val storyRatingTiers: List<Pair<Int, Int>> = listOf(
    R.string.story_rating_good to 3,
    R.string.story_rating_ok to 2,
    R.string.story_rating_bad to 1,
)
