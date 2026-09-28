package com.situ.aichat.ui.liuli.diary

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.local.entity.MonthlyReviewEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.prompt.diary.DiaryGuideAnswers
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.diary.DiaryInsights
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * T2-L10（琉璃 2.0 卷六·一 §4.2）：三枚琉璃弹层的内容与回调——统计三值三标签、回顾标题与正文、三问答案逐字回传。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliDiarySheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) { content() }
            }
        }
        compose.waitForIdle()
    }

    @Test fun 统计弹层三值三标签() {
        show { LiuliDiaryStatsSheet(DiaryInsights.Stats(streakDays = 5, publishedCount = 12, totalChars = 3456, moodCounts = listOf("😌" to 3))) {} }
        compose.onNodeWithText("回顾与统计").assertExists()
        // 统计卡整卡合成一句读屏（标签 值，逗号分隔）。
        compose.onNodeWithContentDescription("连续记录 5，已发布 12，总字数 3456").assertExists()
    }

    @Test fun 回顾弹层标题与正文() {
        val zone = ZoneId.systemDefault()
        val aug = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        show { LiuliDiaryReviewSheet(MonthlyReviewEntity(uuid = "r1", monthStartMillis = aug, content = "八月过得很安静。")) {} }
        compose.onNodeWithText("2026年8月 · 月度回顾").assertExists()
        compose.onNodeWithText("八月过得很安静。").assertExists()
    }

    @Test fun 三问答案逐字回传() {
        var got: DiaryGuideAnswers? = null
        show { LiuliThreeQuestionGuideSheet(onDismiss = {}, onGenerate = { got = it }) }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("下班淋了雨")
        fields[1].performTextInput("有点委屈")
        fields[2].performTextInput("想被抱一下")
        compose.onNodeWithText("帮我写一段").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DiaryGuideAnswers("下班淋了雨", "有点委屈", "想被抱一下"), got)
    }
}
