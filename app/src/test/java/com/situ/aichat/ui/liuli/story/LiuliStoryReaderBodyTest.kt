package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryContentParser
import com.situ.aichat.story.StoryGenPhase
import com.situ.aichat.story.StoryGenerationTaskManager.GenerationProgress
import com.situ.aichat.story.StoryReaderRenderItem
import com.situ.aichat.story.StoryReaderTypography
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.story.StoryVisualPerformance
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryReadyChapter
import com.situ.aichat.ui.story.StoryReaderHaptics
import com.situ.aichat.ui.story.rememberStoryReaderSheetsState
import com.situ.aichat.ui.liuli.glass.LiuliGlassHostState
import com.situ.aichat.ui.liuli.glass.LocalLiuliGlassHost
import com.situ.aichat.ui.liuli.glass.LocalLiuliWindowGlassSource
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-B：琉璃阅读器页 [LiuliStoryReaderBody]（无 VM·直接造数据）。
 * B1 正常章 / B2 锁定章正文不进列表 / B3 在写仍可读 / B4 写好了 · 翻开 / B5 反悔窗口坞让位 / 章未加载不崩；
 * B6 / B7（chunk 4）= 写作中选项与推进区主胶囊上锁（同时钉 enabled 语义，防「点没落上」的假绿）。
 * 「正文不在」类断言另钉列表真实项数（防懒加载没排到那一项造成的假绿）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderBodyTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_790_000_000_000L
    private val content = "第一段是铺垫，雨是从傍晚开始下的。\n\n今晚它没有亮。"
    private val story = StoryEntity(id = "s1", title = "对面楼的灯", status = StoryStatus.WAITING_CHOICE)
    private val c1 = StoryChapterEntity(id = "c1", storyId = "s1", chapterNumber = 1, title = "十一点零七分", content = content)
    private val c2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯", content = content)

    private class Spy {
        var openReady = 0
        val submitted = mutableListOf<String>()
        var flow = 0
    }

    private lateinit var list: LazyListState

    private fun data(
        chapter: StoryChapterEntity? = c2,
        chapters: List<StoryChapterEntity> = listOf(c1, c2),
        story: StoryEntity? = this.story,
        ready: StoryReadyChapter? = null,
        pendingActive: Boolean = false,
        selected: String? = null,
    ) = LiuliReaderData(
        story = story, chapters = chapters, currentChapter = chapter, currentChapterId = chapter?.id.orEmpty(),
        userRoleName = null, selectedChoiceText = selected, pendingActive = pendingActive, pendingRemaining = 3,
        readingAnimationsEnabled = false, fontSizeIndex = 1, recapSummary = null, hasPreviousDraft = false, readyChapter = ready,
    )

    private fun callbacks(spy: Spy) = LiuliReaderCallbacks(
        onBack = {}, onOpenBookHub = {}, onGoToChat = {}, onPrev = {}, onNext = {}, onContinueArc = {},
        onToggleAnimations = {}, onSetFontSizeIndex = {}, onSubmitChoice = { spy.submitted += it },
        onCancelPendingChoice = {}, onRate = {}, onFlow = { spy.flow++ }, onOpenReadyChapter = { spy.openReady++ },
    )

    private fun show(
        data: LiuliReaderData,
        spy: Spy = Spy(),
        generation: State<GenerationProgress?> = mutableStateOf(null),
        lockClock: State<Long>? = null,
        dialogs: @Composable () -> Unit = {},
    ) {
        val items = data.currentChapter?.let { StoryReaderRenderItem.make(StoryContentParser.parse(it.content)) } ?: emptyList()
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    val listState = rememberLazyListState()
                    list = listState
                    LiuliStoryReaderBody(
                        data = data,
                        generation = generation,
                        lockClock = lockClock,
                        renderItems = items,
                        typography = StoryReaderTypography.forIndex(1),
                        performance = StoryVisualPerformance.current(readingAnimationsEnabled = false, reduceMotion = true),
                        listState = listState,
                        sheets = rememberStoryReaderSheetsState(),
                        haptics = StoryReaderHaptics(null),
                        narrativePerson = "second",
                        callbacks = callbacks(spy),
                        dialogs = dialogs,
                    )
                }
            }
        }
    }

    private fun scrollToBody() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("今晚它没有亮", substring = true))
    }

    private fun writing(n: Int) = GenerationProgress(0.4, StoryGenPhase.WRITING, "正在写正文", "对面楼的灯", n)

    @Test
    fun b1_normalChapter_rendersBodyAndCapsule() {
        show(data())
        compose.onNodeWithText("对面楼的灯").assertIsDisplayed()
        scrollToBody()
        compose.onNodeWithText("今晚它没有亮", substring = true).assertExists()
    }

    @Test
    fun b2_lockedChapter_bodyNeverInList_lockCardShown() {
        val locked = c2.copy(unlockAt = now + 2 * 3_600_000L)
        show(data(chapter = locked), lockClock = mutableLongStateOf(now))
        compose.onNodeWithText("到达解锁时间后即可阅读").assertIsDisplayed()
        compose.runOnIdle { assertEquals("锁定章列表只有章首 + 灰条", 2, list.layoutInfo.totalItemsCount) }
        compose.onNodeWithText("今晚它没有亮", substring = true).assertDoesNotExist()
    }

    @Test
    fun b3_generating_cardShown_bodyStillReadable() {
        show(data(), generation = mutableStateOf(writing(3)))
        compose.onNodeWithText("正在创作第3章…").assertIsDisplayed()
        scrollToBody()
        compose.onNodeWithText("今晚它没有亮", substring = true).assertExists()
    }

    @Test
    fun b4_readyChapter_openButton() {
        val spy = Spy()
        show(data(ready = StoryReadyChapter("c3", 3)), spy)
        compose.onNodeWithText("第3章写好了 · 翻开 ›").performClick()
        assertEquals(1, spy.openReady)
    }

    @Test
    fun b5_pendingChoice_undoBarShown_dockYields() {
        show(data(pendingActive = true, selected = "甲"))
        compose.onNodeWithText("已选择：甲").assertIsDisplayed()
        compose.onNodeWithText("%", substring = true).assertDoesNotExist()
    }

    @Test
    fun b5_control_noPending_dockShowsProgress() {
        show(data())
        compose.onNodeWithText("%", substring = true).assertExists()
    }

    private val choiceChapter = c2.copy(hasChoice = true, choicePrompt = "她在等你开口。", choiceOptions = """["甲","乙"]""")

    @Test
    fun b6_openChoice_submitsWhenIdle() {
        val spy = Spy()
        show(data(chapter = choiceChapter, chapters = listOf(c1, choiceChapter)), spy)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("甲"))
        compose.onNodeWithText("甲").assertIsEnabled().performClick()
        assertEquals(listOf("甲"), spy.submitted)
    }

    @Test
    fun b6_openChoice_lockedWhileGenerating() {
        val spy = Spy()
        show(data(chapter = choiceChapter, chapters = listOf(c1, choiceChapter)), spy, generation = mutableStateOf(writing(3)))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("甲"))
        compose.onNodeWithText("甲").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), spy.submitted)
    }

    private val answered = choiceChapter.copy(userChoice = "甲")

    @Test
    fun b7_continueZone_flowWhenIdle() {
        val spy = Spy()
        show(data(chapter = answered, chapters = listOf(c1, answered), story = story.copy(status = StoryStatus.SERIALIZING)), spy)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("继续写下一章"))
        compose.onNodeWithText("继续写下一章").assertIsEnabled().performClick()
        assertEquals(1, spy.flow)
    }

    @Test
    fun b7_continueZone_lockedWhileGenerating() {
        val spy = Spy()
        show(
            data(chapter = answered, chapters = listOf(c1, answered), story = story.copy(status = StoryStatus.SERIALIZING)),
            spy,
            generation = mutableStateOf(writing(3)),
        )
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("继续写下一章"))
        compose.onNodeWithText("继续写下一章").assertIsNotEnabled().performClick()
        assertEquals(0, spy.flow)
    }

    @Test
    fun chapterNotLoaded_noCrash_capsuleAndCoverPresent() {
        show(data(chapter = null, chapters = emptyList(), story = null))
        compose.onNodeWithContentDescription("故事菜单").assertIsDisplayed()
        compose.onNodeWithText("开始阅读").assertIsDisplayed()
        // 卷六·三·下乙 D-11：章还在加载时章末一项都不出（章 null → 走向为空 → 修前会出自然发展态推进区与 ⋯ 行）。
        compose.onNodeWithContentDescription("本章操作").assertDoesNotExist()
        compose.onNodeWithText("让故事自然发展").assertDoesNotExist()
    }

    /**
     * 复核 R1 🟡-1：弹层与对话框整块住玻璃宿主的**内容层**——拿得到跨窗口取景源（通透 / 标准档弹层是真玻璃，
     * 输入框的淡框看得见·同上半卷书页），又拿不到同窗口宿主（放 overlay 会漏进弹层窗口）。
     * 负向对照：槽放到宿主外 → 取景源为 null；放进 overlay → 同窗口宿主非空。两种都当场红。
     */
    @Test
    fun dialogsSlot_insideHostContent_hasWindowGlassSource_notOverlayHost() {
        var windowSource: LiuliGlassHostState? = null
        var overlayHost: LiuliGlassHostState? = null
        var composed = false
        show(
            data(),
            dialogs = {
                composed = true
                windowSource = LocalLiuliWindowGlassSource.current
                overlayHost = LocalLiuliGlassHost.current
            },
        )
        compose.runOnIdle {
            assertEquals("正向证据：槽确实被组合了", true, composed)
            assertNotNull("弹层要拿到跨窗口取景源（否则浅色通透档是不透明白卡、输入框看不见）", windowSource)
            assertNull("弹层不许住 overlay（同窗口宿主会漏进弹层窗口）", overlayHost)
        }
    }
}
