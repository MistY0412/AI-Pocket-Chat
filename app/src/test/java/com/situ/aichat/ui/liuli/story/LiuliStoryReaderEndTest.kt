package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-E1–E5：章末选择区 / 快评 / ⋯ 行（图纸 §4.5）。
 * 快评的单选语义在透明点击面上（与字同格的兄弟节点·同 `LiuliSegmented`），故选中态按 `isSelectable` 次序断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderEndTest {

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

    private fun chapter(userChoice: String? = null) = StoryChapterEntity(
        id = "c2", storyId = "s1", chapterNumber = 2, hasChoice = true,
        choicePrompt = "她在等你开口。", choiceOptions = """["甲","乙","丙"]""", userChoice = userChoice,
    )

    private class ChoiceSpy {
        val submitted = mutableListOf<String>()
        var custom = 0
    }

    private fun showChoice(spy: ChoiceSpy, userChoice: String? = null, locked: Boolean = false) = show {
        LiuliStoryChoiceSection(
            chapter(userChoice), isDark = false, narrativePerson = "second", userRoleName = null,
            selectedChoiceText = userChoice, locked = locked,
            onSubmit = { spy.submitted += it }, onOpenCustomInput = { spy.custom++ },
        )
    }

    @Test
    fun e1_openChoice_lettersSubmitAndCustom() {
        val spy = ChoiceSpy()
        showChoice(spy)
        compose.onNodeWithText("她在等你开口。").assertIsDisplayed()
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithText("B").assertIsDisplayed()
        compose.onNodeWithText("C").assertIsDisplayed()
        compose.onNodeWithText("乙").performClick()
        assertEquals(listOf("乙"), spy.submitted)
        compose.onNodeWithText("自由输入…").performClick()
        assertEquals(1, spy.custom)
    }

    @Test
    fun e2_answered_noResubmit_feedbackShown() {
        val spy = ChoiceSpy()
        showChoice(spy, userChoice = "乙")
        compose.onNodeWithText("甲").performClick()
        assertTrue(spy.submitted.isEmpty())
        compose.onNodeWithText("已选择：乙").assertIsDisplayed()
    }

    @Test
    fun e3_lockedWhileGenerating_optionsAndCustomDoNothing() {
        val spy = ChoiceSpy()
        showChoice(spy, locked = true)
        compose.onNodeWithText("甲").performClick()
        compose.onNodeWithText("自由输入…").performClick()
        assertTrue(spy.submitted.isEmpty())
        assertEquals(0, spy.custom)
    }

    @Test
    fun e4_rating_promptSelectCancelAndTiers() {
        val rates = mutableListOf<Int?>()
        var rating by mutableStateOf<Int?>(null)
        show { LiuliStoryRatingRow(rating, isDark = false) { rates += it; rating = it } }
        compose.onNodeWithText("这一章怎么样？").assertIsDisplayed()
        compose.onNodeWithText("还行").performClick()
        assertEquals(listOf<Int?>(2), rates)

        val tiers = compose.onAllNodes(isSelectable())
        tiers[0].assertIsNotSelected()
        tiers[1].assertIsSelected()
        tiers[2].assertIsNotSelected()
        compose.onNodeWithText("已记下，下一章会参考").assertIsDisplayed()

        compose.onNodeWithText("还行").performClick()
        assertEquals(listOf(2, null), rates)
        compose.onNodeWithText("爽").performClick()
        compose.onNodeWithText("不行").performClick()
        assertEquals(listOf(2, null, 3, 1), rates)
    }

    @Test
    fun e5_actionsRow_menuItemsAndSummary() {
        var expanded by mutableStateOf(false)
        var summary = 0
        show {
            LiuliStoryChapterActionsRow(
                isDark = false, expanded = expanded, onExpandedChange = { expanded = it },
                canRewrite = true, canViewPreviousDraft = false, canEditSummary = true,
                onRewrite = {}, onViewPreviousDraft = {}, onEditChapterSummary = { summary++ },
            )
        }
        compose.onNodeWithContentDescription("本章操作").performClick()
        assertTrue(expanded)
        compose.onNodeWithText("这章换一版").assertIsDisplayed()
        compose.onNodeWithText("看上一版").assertDoesNotExist()
        compose.onNodeWithText("编辑本章小结").performClick()
        assertEquals(1, summary)
        assertFalse(expanded)
    }
}
