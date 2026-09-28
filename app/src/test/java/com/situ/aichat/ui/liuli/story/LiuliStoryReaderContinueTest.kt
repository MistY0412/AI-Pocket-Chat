package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.ContinueZoneMode
import com.situ.aichat.ui.story.StoryContinueZoneState
import com.situ.aichat.ui.story.StoryFinaleProgress
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-N1–N6：推进区 / 草稿卡 / 走向卡 / 建议卡三钮（图纸 §4.6）。
 * N3 / N6 的全否定用例都配同屏正向用例（N1 / N6 前半）证明点击真落在件上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderContinueTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Column(Modifier.fillMaxSize().padding(top = 40.dp)) { content() }
                }
            }
        }
    }

    private class Spy {
        var write = 0
        var flow = 0
        var finale = 0
        var cancelFinale = 0
    }

    private fun showZone(
        spy: Spy,
        zone: StoryContinueZoneState = StoryContinueZoneState(ContinueZoneMode.NATURAL_FLOW, null),
        draft: String? = null,
        userEdited: Boolean = false,
        finale: StoryFinaleProgress? = null,
        locked: Boolean = false,
    ) = show {
        LiuliStoryContinueZone(
            isDark = false, breatheTrigger = 0, finaleProgress = finale, zone = zone,
            draftBeats = draft, draftUserEdited = userEdited, locked = locked,
            onWriteClick = { spy.write++ }, onFlowClick = { spy.flow++ },
            onFinaleClick = { spy.finale++ }, onCancelFinaleClick = { spy.cancelFinale++ },
        )
    }

    @Test
    fun n1_naturalFlow_pillInputAndFinale() {
        val spy = Spy()
        showZone(spy)
        compose.onNodeWithText("接下来").assertIsDisplayed()
        compose.onNodeWithText("让故事自然发展").performClick()
        compose.onNodeWithText("导演下一章").performClick()
        compose.onNodeWithText("准备收尾").performClick()
        assertEquals(1, spy.flow)
        assertEquals(1, spy.write)
        assertEquals(1, spy.finale)
    }

    @Test
    fun n2_byDirection_directionCardReplacesInput() {
        val spy = Spy()
        showZone(spy, zone = StoryContinueZoneState(ContinueZoneMode.BY_DIRECTION, "让她先开口"))
        compose.onNodeWithText("让她先开口").assertIsDisplayed()
        compose.onNodeWithText("你的走向").assertIsDisplayed()
        compose.onNodeWithText("导演下一章").assertDoesNotExist()
        compose.onNodeWithText("按走向继续写").assertIsDisplayed()
        compose.onNodeWithText("让她先开口").performClick()
        assertEquals(1, spy.write)
    }

    @Test
    fun n3_lockedWhileGenerating_allEntriesDoNothing() {
        val spy = Spy()
        showZone(spy, draft = "她会先开口。", locked = true)
        compose.onNodeWithText("让故事自然发展").performClick()
        compose.onNodeWithText("导演下一章").performClick()
        compose.onNodeWithText("她会先开口。").performClick()
        compose.onNodeWithText("准备收尾").performClick()
        assertEquals(0, spy.flow)
        assertEquals(0, spy.write)
        assertEquals(0, spy.finale)
    }

    @Test
    fun n4_draftCardTag_followsUserEdited() {
        showZone(Spy(), draft = "她会先开口。", userEdited = true)
        compose.onNodeWithText("下一章打算").assertIsDisplayed()
        compose.onNodeWithText("你已指定").assertIsDisplayed()
        compose.onNodeWithText("AI 预排").assertDoesNotExist()
    }

    @Test
    fun n4_draftCardTag_aiWhenNotEdited() {
        showZone(Spy(), draft = "她会先开口。", userEdited = false)
        compose.onNodeWithText("AI 预排").assertIsDisplayed()
        compose.onNodeWithText("你已指定").assertDoesNotExist()
    }

    @Test
    fun n5_finalePlanned_chipAndCancel() {
        val spy = Spy()
        showZone(spy, finale = StoryFinaleProgress(current = 1, total = 3))
        compose.onNodeWithText("收尾中 · 本弧第 1/3 章").assertIsDisplayed()
        compose.onNodeWithText("准备收尾").assertDoesNotExist()
        compose.onNodeWithText("取消收尾").performClick()
        assertEquals(1, spy.cancelFinale)
    }

    private fun showSuggest(enabled: Boolean, counts: IntArray) = show {
        LiuliStoryEndingSuggestActions(
            enabled = enabled,
            onGracefulFinale = { counts[0]++ },
            onFinish = { counts[1]++ },
            onKeepWriting = { counts[2]++ },
        )
    }

    @Test
    fun n6_suggestActions_threeButtons() {
        val counts = IntArray(3)
        showSuggest(enabled = true, counts)
        compose.onNodeWithText("从容收尾（再写几章，把伏笔一一收好）").performClick()
        compose.onNodeWithText("就此完结").performClick()
        compose.onNodeWithText("还想继续写").performClick()
        assertEquals(listOf(1, 1, 1), counts.toList())
    }

    @Test
    fun n6_suggestActions_disabledWhileGenerating() {
        val counts = IntArray(3)
        showSuggest(enabled = false, counts)
        compose.onNodeWithText("从容收尾（再写几章，把伏笔一一收好）").performClick()
        compose.onNodeWithText("就此完结").performClick()
        compose.onNodeWithText("还想继续写").performClick()
        assertEquals(listOf(0, 0, 0), counts.toList())
    }
}
