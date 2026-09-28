package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-C1–C3（琉璃 2.0 卷六·三·上 §7）：章节空态三种 / 去做选择与继续阅读条件 / 紫光点 / 锁定章不可点。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryChapterListPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var retries = 0
    private var settings = 0
    private val opened = mutableListOf<String>()
    private val now = 1_700_000_000_000L

    private fun show(story: StoryEntity?, chapters: List<StoryChapterEntity>, lastRead: String? = null) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryChapterListPage(
                        story, chapters, generatingPhase = null, lastReadChapterId = lastRead, advancedFromChapterNumber = null, now = now,
                        onBack = {}, onOpenChapter = { opened += it }, onOpenSettings = { settings++ }, onRetry = { retries++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun story(status: String) = StoryEntity(id = "s1", title = "对面楼的灯", status = status)

    @Test fun C1_无章失败出重新生成_无章等待不出() {
        show(story(StoryStatus.GENERATION_FAILED), emptyList())
        compose.onNodeWithText("重新生成").performClick()
        compose.waitForIdle()
        assertEquals(1, retries)
    }

    @Test fun C1_无章等待无钮() {
        show(story(StoryStatus.SERIALIZING), emptyList())
        compose.onAllNodesWithText("重新生成").assertCountEquals(0)
    }

    @Test fun C2_待选章出去做选择_续读同章不出继续阅读_新与回显在_设定钮() {
        val ch1 = StoryChapterEntity(id = "c1", storyId = "s1", chapterNumber = 1, title = "十一点零七分", hasChoice = true, userChoice = "打开门")
        val ch2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯", hasChoice = true)
        show(story(StoryStatus.WAITING_CHOICE), listOf(ch1, ch2), lastRead = "c1")
        compose.onNodeWithText("去做选择 · 第2章").performClick()
        compose.waitForIdle()
        assertEquals(listOf("c2"), opened)
        compose.onAllNodesWithText("继续阅读", substring = true).assertCountEquals(0)
        compose.onNodeWithText("第 2 话", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("新", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("▶ 当时你选了：打开门", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("查看设定").performClick()
        compose.waitForIdle()
        assertEquals(1, settings)
    }

    @Test fun C2_已读无选择章是紫光点_恰一个_直径11() {
        val ch1 = StoryChapterEntity(id = "c1", storyId = "s1", chapterNumber = 1, title = "十一点零七分")
        val ch2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯")
        show(story(StoryStatus.SERIALIZING), listOf(ch1, ch2), lastRead = "c1")
        compose.onAllNodesWithTag(LIULI_CHAPTER_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)
        val dot = compose.onNodeWithTag(LIULI_CHAPTER_DOT_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(11f, (dot.right - dot.left).value, 0.5f)
        assertEquals(11f, (dot.bottom - dot.top).value, 0.5f)
    }

    @Test fun C3_锁定章出倒计时且点不开() {
        val ch2 = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "今晚的灯")
        val ch3 = StoryChapterEntity(id = "c3", storyId = "s1", chapterNumber = 3, title = "明晚的灯", unlockAt = now + 2 * 3_600_000L)
        show(story(StoryStatus.SERIALIZING), listOf(ch2, ch3))
        compose.onNodeWithText("后解锁", substring = true, useUnmergedTree = true).assertExists()
        // 正向对照：已解锁章的卡点得开（证明点击真落到了卡上）。
        compose.onNodeWithText("今晚的灯", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("c2"), opened)
        compose.onNodeWithText("明晚的灯", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("c2"), opened)
    }
}
