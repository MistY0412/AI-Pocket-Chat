package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryReaderNav
import com.situ.aichat.ui.story.StoryReaderProgress
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-R1–R5：琉璃阅读器外框——顶部胶囊（R3）/ ⋮ 玻璃菜单 / 底部可拖进度坞（图纸 §4.3）。
 * zh 串以 `values-zh-rCN` 原文为准（「故事菜单」「未命名章节」「%1$d%% · 还剩 %2$d 分钟」「开启续篇」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderChromeTest {

    @get:Rule
    val compose = createComposeRule()

    private val ch2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯")

    private fun show(content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    private class CapsuleSpy {
        var back = 0
        var bookHub = 0
        val toggles = mutableListOf<Boolean>()
        val fontSizes = mutableListOf<Int>()
        val menuChanges = mutableListOf<Boolean>()
    }

    private fun showCapsule(spy: CapsuleSpy, title: String? = "对面楼的灯", withBookHub: Boolean = true, animations: Boolean = true) = show {
        LiuliStoryReaderCapsule(
            visible = true,
            storyTitle = title,
            chapter = ch2,
            readingAnimationsEnabled = animations,
            fontSizeIndex = 1,
            onBack = { spy.back++ },
            onOpenBookHub = if (withBookHub) ({ spy.bookHub++ }) else null,
            onToggleAnimations = { spy.toggles += it },
            onSetFontSizeIndex = { spy.fontSizes += it },
            onMenuExpandedChange = { spy.menuChanges += it },
        )
    }

    @Test
    fun r1_capsuleShowsTitleAndChapter_backWorks() {
        val spy = CapsuleSpy()
        showCapsule(spy)
        compose.onNodeWithText("对面楼的灯").assertIsDisplayed()
        compose.onNodeWithText("· 第2章").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, spy.back)
    }

    @Test
    fun r2_blankTitle_fallsBackToUntitled() {
        showCapsule(CapsuleSpy(), title = "  ")
        compose.onNodeWithText("未命名章节").assertIsDisplayed()
    }

    @Test
    fun r3_menu_bookHub_toggle_fontSize_keepMenuOpen() {
        val spy = CapsuleSpy()
        showCapsule(spy, animations = true)
        compose.onNodeWithContentDescription("故事菜单").performClick()
        assertEquals(listOf(true), spy.menuChanges)

        compose.onNodeWithText("阅读动画").performClick()
        assertEquals(listOf(false), spy.toggles)
        compose.onNodeWithText("字号").assertIsDisplayed() // 拨开关不关菜单（活预览）

        compose.onNodeWithText("大").performClick()
        assertEquals(listOf(2), spy.fontSizes)
        compose.onNodeWithText("字号").assertIsDisplayed()

        compose.onNodeWithText("书页").performClick()
        assertEquals(1, spy.bookHub)
        assertEquals("点书页关菜单", false, spy.menuChanges.last())
    }

    @Test
    fun r3_noStoryId_noBookHubRow() {
        showCapsule(CapsuleSpy(), withBookHub = false)
        compose.onNodeWithContentDescription("故事菜单").performClick()
        compose.onNodeWithText("阅读动画").assertIsDisplayed()
        compose.onNodeWithText("书页").assertDoesNotExist()
    }

    private class DockSpy {
        var prev = 0
        var next = 0
        var arc = 0
        val seeks = mutableListOf<Float>()
        var seekEnds = 0
    }

    private fun nav(hasPrev: Boolean, hasNext: Boolean, arc: Boolean) = StoryReaderNav(
        chapterIndex = 1, isLatestChapter = !hasNext, hasPrev = hasPrev, hasNext = hasNext,
        showContinueArc = arc, canRewrite = false, canViewPreviousDraft = false,
    )

    private fun showDock(spy: DockSpy, nav: StoryReaderNav) = show {
        LiuliStoryReaderDock(
            visible = true,
            nav = nav,
            progress = StoryReaderProgress(mutableIntStateOf(62), mutableIntStateOf(3)),
            seekEnabled = true,
            onPrev = { spy.prev++ },
            onNext = { spy.next++ },
            onContinueArc = { spy.arc++ },
            onSeek = { spy.seeks += it },
            onSeekEnd = { spy.seekEnds++ },
        )
    }

    @Test
    fun r4_dock_progressLabel_prevDisabled_nextWorks() {
        val spy = DockSpy()
        showDock(spy, nav(hasPrev = false, hasNext = true, arc = false))
        compose.onNodeWithText("62% · 还剩 3 分钟").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一章").assertIsNotEnabled()
        compose.onNodeWithContentDescription("下一章").performClick()
        assertEquals(1, spy.next)
        assertEquals(0, spy.prev)
    }

    @Test
    fun r4_completedLastChapter_showsContinueArcInsteadOfNext() {
        val spy = DockSpy()
        showDock(spy, nav(hasPrev = true, hasNext = false, arc = true))
        compose.onNodeWithContentDescription("下一章").assertDoesNotExist()
        compose.onNodeWithText("开启续篇").performClick()
        assertEquals(1, spy.arc)
    }

    @Test
    fun r5_dragSeek_reportsFractionsThenEnd() {
        val spy = DockSpy()
        showDock(spy, nav(hasPrev = true, hasNext = true, arc = false))
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue("拖动至少回报一次：${spy.seeks}", spy.seeks.isNotEmpty())
        assertTrue("回报值都在 0..1：${spy.seeks}", spy.seeks.all { it in 0f..1f })
        assertEquals(1, spy.seekEnds)
    }
}
