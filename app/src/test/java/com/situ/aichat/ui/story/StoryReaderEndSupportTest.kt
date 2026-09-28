package com.situ.aichat.ui.story

import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.story.StoryChoiceClassifier
import com.situ.aichat.story.StoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 琉璃 2.0 卷六·三·下甲 T1-2：章末各件两张脸共用的判据 / 取值（图纸 §3.3 + §3.11）。
 * 期望值从原暖陶式子独立反推（完结门 / 推进区避让 / 快评再点取消 / 三档次序）。
 */
class StoryReaderEndSupportTest {

    private val opts = """["甲","乙"]"""
    private fun ch(
        hasChoice: Boolean = true,
        userChoice: String? = null,
        aiSuggestedEnding: Boolean = false,
    ) = StoryChapterEntity(
        id = "c2", storyId = "s1", chapterNumber = 2,
        hasChoice = hasChoice, choiceOptions = if (hasChoice) opts else null,
        userChoice = userChoice, aiSuggestedEnding = aiSuggestedEnding,
    )

    // ── storyChoiceChapter（ST10-4 完结门）──

    @Test
    fun choiceChapter_followsCompletionGate() {
        assertNull(storyChoiceChapter(ch(hasChoice = false), StoryStatus.SERIALIZING))
        val answered = ch(userChoice = "甲")
        assertSame(answered, storyChoiceChapter(answered, StoryStatus.COMPLETED))
        assertNull(storyChoiceChapter(ch(), StoryStatus.COMPLETED))
        val open = ch()
        assertSame(open, storyChoiceChapter(open, null))
        assertSame(open, storyChoiceChapter(open, StoryStatus.SERIALIZING))
        assertNull(storyChoiceChapter(null, StoryStatus.SERIALIZING))
    }

    // ── storyShowsEndingSuggest ──

    @Test
    fun endingSuggest_onlyLatestNonCompletedWithAiMark() {
        assertFalse(storyShowsEndingSuggest(null, isLatestChapter = true, storyStatus = StoryStatus.SERIALIZING))
        assertTrue(storyShowsEndingSuggest(ch(aiSuggestedEnding = true), true, StoryStatus.WAITING_CHOICE))
        assertFalse(storyShowsEndingSuggest(ch(aiSuggestedEnding = true), true, StoryStatus.COMPLETED))
        assertFalse(storyShowsEndingSuggest(ch(aiSuggestedEnding = true), false, StoryStatus.SERIALIZING))
        assertFalse(storyShowsEndingSuggest(ch(aiSuggestedEnding = false), true, StoryStatus.SERIALIZING))
    }

    // ── storyShowsContinueZone ──

    @Test
    fun continueZone_yieldsToOpenChoice_andCompletion() {
        assertTrue(storyShowsContinueZone(ch(hasChoice = false), true, StoryStatus.SERIALIZING))
        assertFalse(storyShowsContinueZone(ch(), true, StoryStatus.WAITING_CHOICE))
        assertTrue(storyShowsContinueZone(ch(userChoice = "甲"), true, StoryStatus.WAITING_CHOICE))
        assertFalse(storyShowsContinueZone(ch(hasChoice = false), true, StoryStatus.COMPLETED))
        assertFalse(storyShowsContinueZone(ch(hasChoice = false), false, StoryStatus.SERIALIZING))
        // D-11：章还在加载时不出（卷六·三·下乙·原式在此会出，两张脸同修）。
        assertFalse(storyShowsContinueZone(null, true, StoryStatus.SERIALIZING))
    }

    // ── storyContinueZoneState ──

    @Test
    fun continueZoneState_threeModes() {
        assertEquals(StoryContinueZoneState(ContinueZoneMode.NATURAL_FLOW, null), storyContinueZoneState(ch()))
        assertEquals(StoryContinueZoneState(ContinueZoneMode.NEXT_CHAPTER, null), storyContinueZoneState(ch(userChoice = "甲")))
        assertEquals(
            StoryContinueZoneState(ContinueZoneMode.NEXT_CHAPTER, null),
            storyContinueZoneState(ch(userChoice = StoryChoiceClassifier.NATURAL_FLOW_CHOICE)),
        )
        assertEquals(
            StoryContinueZoneState(ContinueZoneMode.BY_DIRECTION, "让她先开口"),
            storyContinueZoneState(ch(userChoice = "让她先开口")),
        )
    }

    // ── 资源映射 ──

    @Test
    fun labelResources_mapOneToOne() {
        assertEquals(R.string.story_continue_flow, storyContinueFlowLabelRes(ContinueZoneMode.NATURAL_FLOW))
        assertEquals(R.string.story_continue_next_chapter, storyContinueFlowLabelRes(ContinueZoneMode.NEXT_CHAPTER))
        assertEquals(R.string.story_continue_by_direction, storyContinueFlowLabelRes(ContinueZoneMode.BY_DIRECTION))
        assertEquals(R.string.story_continue_draft_tag_user, storyDraftTagRes(true))
        assertEquals(R.string.story_continue_draft_tag_ai, storyDraftTagRes(false))
        assertEquals(R.string.story_rating_ask, storyRatingPromptRes(null))
        assertEquals(R.string.story_rating_done, storyRatingPromptRes(2))
    }

    // ── 快评 / 选项小式 ──

    @Test
    fun ratingNext_secondTapOnSameTierCancels() {
        assertEquals(2, storyRatingNext(null, 2))
        assertNull(storyRatingNext(2, 2))
        assertEquals(2, storyRatingNext(3, 2))
    }

    @Test
    fun choiceLetterAndDimming() {
        assertEquals("A", storyChoiceLetter(0))
        assertEquals("C", storyChoiceLetter(2))
        assertFalse(storyChoiceDimmed(null, "甲"))
        assertFalse(storyChoiceDimmed("甲", "甲"))
        assertTrue(storyChoiceDimmed("甲", "乙"))
    }

    @Test
    fun ratingTiers_orderGoodOkBad_mapsThreeTwoOne() {
        assertEquals(
            listOf(R.string.story_rating_good to 3, R.string.story_rating_ok to 2, R.string.story_rating_bad to 1),
            storyRatingTiers,
        )
    }
}
