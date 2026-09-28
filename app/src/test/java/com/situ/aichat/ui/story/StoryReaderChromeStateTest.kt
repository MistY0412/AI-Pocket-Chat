package com.situ.aichat.ui.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-C1–C5：沉浸外框显隐状态机（两张脸共用·图纸 §3.1 [StoryReaderChromeState]）。
 * 宿主 = 50 项 × 100dp 的真 LazyColumn；时钟手推（autoAdvance = false）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class StoryReaderChromeStateTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var chrome: StoryReaderChromeState
    private lateinit var list: LazyListState

    private fun host() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val listState = rememberLazyListState()
            list = listState
            chrome = rememberStoryReaderChromeState("k", listState)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("list")) {
                items(50) { Box(Modifier.fillMaxWidth().height(100.dp)) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun swipeListUp() {
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        repeat(10) { compose.mainClock.advanceTimeBy(32) }
    }

    @Test
    fun c1_autoHidesAfterTwoAndAHalfSeconds() {
        host()
        assertTrue(chrome.visible)
        compose.mainClock.advanceTimeBy(2_400)
        assertTrue(chrome.visible)
        compose.mainClock.advanceTimeBy(200)
        assertFalse(chrome.visible)
    }

    @Test
    fun c2_userToggleWinsOverEntryTimer() {
        host()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { chrome.toggle() }
        assertFalse(chrome.visible)
        compose.mainClock.advanceTimeBy(1_600)
        assertFalse(chrome.visible)
        compose.runOnIdle { chrome.toggle() }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(chrome.visible)
    }

    @Test
    fun c3_openMenuKeepsChromePastTimer() {
        host()
        compose.runOnIdle { chrome.menuOpen = true }
        compose.mainClock.advanceTimeBy(2_600)
        assertTrue(chrome.visible)
    }

    /** 复核 R1 🟡-2：进章 2.5s 内就开拖进度坞——保底计时到点也不许收（对照 = C1 同一时刻会收）。 */
    @Test
    fun c6_seekingKeepsChromePastEntryTimer() {
        host()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { chrome.seeking = true }
        compose.mainClock.advanceTimeBy(1_600)
        assertTrue(chrome.visible)
    }

    @Test
    fun c4_scrollingHidesChrome() {
        host()
        assertTrue(chrome.visible)
        swipeListUp()
        assertTrue("列表真的滚动了（正向证据）", list.firstVisibleItemIndex > 0)
        assertFalse(chrome.visible)
    }

    @Test
    fun c5_seekingScrollDoesNotHideChrome() {
        host()
        compose.runOnIdle { chrome.seeking = true }
        swipeListUp()
        assertTrue("列表真的滚动了（正向证据）", list.firstVisibleItemIndex > 0)
        assertTrue(chrome.visible)
    }
}
