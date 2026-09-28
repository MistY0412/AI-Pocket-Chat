package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryGenPhase
import com.situ.aichat.story.StoryGenerationTaskManager.GenerationProgress
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryReadyChapter
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-O1–O4：写作中进度卡（R2）/ 写好了钮 / 锁定层（图纸 §4.4）。
 * 锁卡时钟由测试传固定值（不起真 1Hz 时钟·§7）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderOverlaysTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    private fun progress(n: Int) = GenerationProgress(
        progress = 0.4, genPhase = StoryGenPhase.WRITING, phase = "正在写正文", storyTitle = "对面楼的灯", chapterNumber = n,
    )

    @Test
    fun o1_generationCard_titlePhaseKeepReading_goChat() {
        var goChat = 0
        show { LiuliStoryGenerationCard(mutableStateOf(progress(3))) { goChat++ } }
        compose.onNodeWithText("正在创作第3章…").assertIsDisplayed()
        compose.onNodeWithText("正在写正文").assertIsDisplayed()
        compose.onNodeWithText("可以接着读这一章").assertIsDisplayed()
        compose.onNodeWithContentDescription("去聊天，生成完成后通知我").performClick()
        assertEquals(1, goChat)
    }

    @Test
    fun o2_noGeneration_cardAbsent() {
        show { LiuliStoryGenerationCard(mutableStateOf<GenerationProgress?>(null)) {} }
        compose.onNodeWithText("正在创作", substring = true).assertDoesNotExist()
        compose.onNodeWithText("可以接着读这一章").assertDoesNotExist()
    }

    @Test
    fun o3_readyButton_opens() {
        var opened = 0
        show { LiuliStoryReadyButton(StoryReadyChapter("c3", 3), onOpen = { opened++ }) }
        compose.onNodeWithText("第3章写好了 · 翻开 ›").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun o4_lockLayer_chapterTitleRemainingHint() {
        val now = 1_790_000_000_000L
        val ch = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯", unlockAt = now + 2 * 3_600_000L)
        show { LiuliStoryLockLayer(ch, mutableLongStateOf(now)) }
        compose.onNodeWithText("第2章").assertIsDisplayed()
        compose.onNodeWithText("七楼的灯").assertIsDisplayed()
        compose.onNodeWithText("🔒 2小时0分后解锁").assertIsDisplayed()
        compose.onNodeWithText("到达解锁时间后即可阅读").assertIsDisplayed()
    }
}
