package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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

/** T2-A1（琉璃 2.0 卷六·三·上 §7）：全部结局三列等分——计数 / 末行不满格宽不变（E38）/ 点开 / 长按删除。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryArchiveAllPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val titles = listOf("窗里的人", "第十一点", "晚班电梯", "雨停之后")
    private val books = titles.mapIndexed { i, t -> StoryEntity(id = "b$i", title = t, genre = "都市", writingStyle = "轻松幽默", status = StoryStatus.COMPLETED) }
    private val opened = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryArchiveAllPage(books, onBack = {}, onOpenArchive = { opened += it }, onDelete = { deleted += it }, listState = rememberLazyListState())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun A1_计数与四本书名在_第二行单格与第一格同左缘() {
        show()
        compose.onNodeWithText("4 部已完结").assertExists()
        titles.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        val first = compose.onNodeWithText("窗里的人", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val fourth = compose.onNodeWithText("雨停之后", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(first.left.value, fourth.left.value, 0.5f)
        // 格宽不变：第二行只有一格，宽仍 = 第一行的格宽。
        assertEquals((first.right - first.left).value, (fourth.right - fourth.left).value, 0.5f)
        assertEquals(true, fourth.top > first.bottom - 0.5.dp)
    }

    @Test fun A1_点书名进档案_长按删除经确认() {
        show()
        compose.onNodeWithText("第十一点", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("b1"), opened)
        compose.onNodeWithText("晚班电梯", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("删除故事").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertEquals(listOf("b2"), deleted)
    }
}
