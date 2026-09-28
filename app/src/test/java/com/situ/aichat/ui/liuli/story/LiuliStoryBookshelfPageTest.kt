package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.story.StoryUpdateMode
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

/**
 * T2-B1–B3（琉璃 2.0 卷六·三·上 §7）：直接驱动无 VM 的 [LiuliStoryBookshelfPage]——空态两个入口、在读卡长按菜单（随状态）
 * + 删除 / 归档确认、点书名进章节、档案区长按删除。期望从暖陶行为独立反推。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryBookshelfPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var creates = 0
    private val opened = mutableListOf<String>()
    private val archived = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private val callbacks = LiuliShelfCallbacks(
        onBack = {},
        onCreate = { creates++ },
        onOpenStory = { opened += it },
        onContinueReading = {},
        onRetry = {},
        onTogglePause = {},
        onArchive = { archived += it },
        onOpenSettings = {},
        onDelete = { deleted += it },
        onOpenArchive = {},
        onViewAllArchive = {},
    )

    private fun show(stories: List<StoryEntity>) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryBookshelfPage(LiuliShelfData(stories, emptyMap(), emptyMap()), callbacks, rememberLazyListState())
                }
            }
        }
        compose.waitForIdle()
    }

    private val lamp = StoryEntity(id = "s1", title = "对面楼的灯", genre = "都市", status = StoryStatus.SERIALIZING, updateMode = StoryUpdateMode.CHASE)
    private val done = StoryEntity(id = "s2", title = "雨停之后", genre = "都市", status = StoryStatus.COMPLETED)

    private fun longPress(text: String) {
        compose.onNodeWithText(text, useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    @Test fun B1_空书架出空态且文字钮与圆钮都去开新故事() {
        show(emptyList())
        compose.onNodeWithText("还没有故事").assertExists()
        compose.onNodeWithText("开新故事").performClick()
        compose.waitForIdle()
        assertEquals(1, creates)
        compose.onNodeWithContentDescription("创建新故事").performClick()
        compose.waitForIdle()
        assertEquals(2, creates)
    }

    @Test fun B2_连载追更书长按四项_删除与归档都经确认_点书名进章节() {
        show(listOf(lamp))
        longPress("对面楼的灯")
        listOf("暂停连载", "完结归档", "查看设定", "删除故事").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("删除故事").performClick()
        compose.waitForIdle()
        // 确认框正文 = 书名（书卡上也有一处书名）。
        compose.onAllNodesWithText("对面楼的灯").assertCountEquals(2)
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertEquals(listOf("s1"), deleted)

        longPress("对面楼的灯")
        compose.onNodeWithText("完结归档").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("放入档案").performClick()
        compose.waitForIdle()
        assertEquals(listOf("s1"), archived)

        compose.onNodeWithText("对面楼的灯", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("s1"), opened)
    }

    @Test fun B3_档案区长按删除经确认() {
        show(listOf(lamp, done))
        compose.onNodeWithText("档案").assertExists()
        longPress("雨停之后")
        compose.onNodeWithText("删除故事").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertEquals(listOf("s2"), deleted)
    }
}
