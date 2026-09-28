package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryArchiveDigest
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryArchiveUiState
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-D1（琉璃 2.0 卷六·三·上 §7）：结局档案——档案内容在、三钮与 ✕ 回调、三钮几何（分享 50 高 · 两玻璃钮同行等宽）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryArchiveDetailPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var shares = 0
    private var exports = 0
    private var continues = 0
    private var closes = 0

    private fun show() {
        val story = StoryEntity(id = "s1", title = "对面楼的灯", genre = "都市", writingStyle = "轻松幽默", status = StoryStatus.COMPLETED)
        val digest = StoryArchiveDigest(chapterCount = 3, choiceCount = 1, dayCount = 2, startMillis = 0L, endMillis = 172_800_000L, quote = "她转过身。", endingType = null)
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryArchiveDetailPage(
                        StoryArchiveUiState(story, digest, emptyList()),
                        onClose = { closes++ },
                        onShare = { shares++ },
                        onExport = { exports++ },
                        onContinueWriting = { continues++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun D1_档案内容在_三钮与关闭各回调一次() {
        show()
        compose.onNodeWithText("对面楼的灯").assertExists()
        compose.onNodeWithText("3 话 · 1 次选择 · 2 天").assertExists()
        compose.onNodeWithText("「她转过身。」").assertExists()
        compose.onNodeWithText("✦ 生成分享长图").performClick()
        compose.onNodeWithText("导出全文 · .txt").performClick()
        compose.onNodeWithText("✎ 继续写这个故事").performClick()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1, 1), listOf(shares, exports, continues, closes))
    }

    @Test fun D1_分享钮高50_两玻璃钮同行等宽() {
        show()
        val share = compose.onNodeWithText("✦ 生成分享长图").getUnclippedBoundsInRoot()
        assertEquals(50f, (share.bottom - share.top).value, 0.5f)
        val export = compose.onNodeWithText("导出全文 · .txt").getUnclippedBoundsInRoot()
        val cont = compose.onNodeWithText("✎ 继续写这个故事").getUnclippedBoundsInRoot()
        assertEquals(export.top.value, cont.top.value, 0.5f)
        assertEquals((export.right - export.left).value, (cont.right - cont.left).value, 0.5f)
    }
}
