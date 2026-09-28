package com.situ.aichat.ui.liuli.moments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentCommentEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.moments.MomentCommentComposerState
import com.situ.aichat.ui.moments.MomentReplyTarget
import com.situ.aichat.ui.theme.AIPocketChatTheme
import com.situ.aichat.util.StringListJson
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-M1–M6（琉璃 2.0 卷六·二 §7）：直接驱动无 VM 的 [LiuliMomentDetailPage]——帖已删、评论楼层与二级缩进、回复目标、
 * 空白不能发、删评论权限、悬浮评论条几何。期望从暖陶行为与图纸几何独立反推（Robolectric 导航栏 / 键盘恒 0）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliMomentDetailPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private val characters = mapOf("c1" to CharacterEntity(uuid = "c1", name = "小满", creationDate = 1))
    private val composer = MomentCommentComposerState()
    private var sends = 0
    private val deleted = mutableListOf<String>()

    // 小满的帖：我的评论（顶层）← 小满回复我（二级）
    private val post = MomentPostWithRelations(
        post = MomentPostEntity(uuid = "p", content = "今天的晚霞像橘子汽水", timestamp = now - 7_200_000, authorTypeRaw = "character", characterUuid = "c1"),
        comments = listOf(
            MomentCommentEntity(uuid = "u1", content = "第二张颜色太温柔了", timestamp = now - 5_400_000, authorTypeRaw = "user", postUuid = "p"),
            MomentCommentEntity(
                uuid = "a1", content = "那就送你第二张", timestamp = now - 4_800_000, authorTypeRaw = "character", characterUuid = "c1",
                replyToName = "我", postUuid = "p", parentCommentUuid = "u1",
            ),
        ),
        likes = emptyList(),
    )

    private fun show(p: MomentPostWithRelations?, chars: Map<String, CharacterEntity> = characters) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliMomentDetailPage(
                        post = p,
                        characters = chars,
                        userProfile = UserProfileEntity(nickname = "阿满"),
                        composer = composer,
                        onBack = {},
                        onSend = { sends++ },
                        onDeleteComment = { deleted += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun M1_帖已删居中一句且评论条照旧在() {
        show(null)
        compose.onNodeWithText("该动态已被删除").assertExists()
        compose.onNodeWithText("写评论…", useUnmergedTree = true).assertExists()
    }

    @Test fun M2_评论楼层与二级缩进28() {
        show(post)
        compose.onNodeWithText("评论 (2)").assertExists()
        val top = compose.onNodeWithText("第二张颜色太温柔了", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val reply = compose.onNodeWithText("那就送你第二张", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(28f, (reply.left - top.left).value, 0.5f)
    }

    @Test fun M3_点评论出回复chip再点取消() {
        show(post)
        compose.onNodeWithText("那就送你第二张", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("回复 @小满").assertExists()
        compose.onNodeWithText("回复…", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("回复 @小满").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("回复 @小满").assertDoesNotExist()
        compose.onNodeWithText("写评论…", useUnmergedTree = true).assertExists()
    }

    @Test fun M4_空白不能发有字才发() {
        show(post)
        compose.onNodeWithContentDescription("发送").assertIsNotEnabled()
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitForIdle()
        assertEquals(0, sends)
        compose.onNode(hasSetTextAction()).performTextInput("好呀")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("发送").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, sends)
    }

    @Test fun M5_本人评论可删角色评论只能回复() {
        show(post)
        compose.onNodeWithText("第二张颜色太温柔了", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        // 楼层里「回复 @我」那行本就有一个「回复」字，菜单再添一个。
        assertEquals(2, compose.onAllNodesWithText("回复").fetchSemanticsNodes().size)
        compose.onNodeWithText("删除评论").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertEquals(listOf("u1"), deleted)

        compose.onNodeWithText("那就送你第二张", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithText("回复").fetchSemanticsNodes().size)
        compose.onNodeWithText("删除评论").assertDoesNotExist()
    }

    /**
     * 上 xhdpi：Robolectric 假字高恒 32px（PITFALLS §1d），1x 密度下多行输入框被撑到 56dp、比发送钮高，
     * 「条上下 = 发送钮 ∓ 4」的量法就不成立；2x 下字高 16dp，输入框回到 44 最小高（下面先断它 ≤ 48 再外推）。
     */
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
    @Test fun M6_评论条左右底各离12且高56() {
        composer.replyTarget = MomentReplyTarget("a1", "小满")
        show(post)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val px = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        // 条内距锁定 左右 8 / 上下 4（LiuliFloatingBar）：条左 = chip 左 − 8；条右 = 发送钮 48 触达框右 + 8；条上下 = 发送钮上下 ∓ 4。
        val chip = compose.onNodeWithText("回复 @小满").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("发送").fetchSemanticsNode().boundsInRoot
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertEquals("发送钮触达 48", 48f, (send.bottom - send.top) / px, 0.5f)
        assertTrue("输入框不高于发送钮（外推前提）", (field.bottom - field.top) / px <= 48f)
        assertEquals(12f, chip.left / px - 8f - root.left.value, 0.5f)
        assertEquals(12f, root.right.value - (send.right / px + 8f), 0.5f)
        assertEquals(12f, root.bottom.value - (send.bottom / px + 4f), 0.5f)
        assertEquals(56f, (send.bottom - send.top) / px + 8f, 0.5f)
    }

    /** T2-5（朋友圈发布页·乙 §7·E22）：琉璃详情——用户帖提醒 [c1, ghost, c2] → 「提醒了 小满、阿澈」；原用例的 AI 帖没有那一行。 */
    @Test fun 乙_用户帖提醒了谁一行() {
        val mine = MomentPostWithRelations(
            post = MomentPostEntity(
                uuid = "p9", content = "今天的晚霞像橘子汽水", timestamp = now - 60_000, authorTypeRaw = "user",
                mentionedCharacterUuidsJson = StringListJson.encode(listOf("c1", "ghost", "c2")),
            ),
            comments = emptyList(),
            likes = emptyList(),
        )
        show(mine, characters + ("c2" to CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 1)))
        compose.onNodeWithText("提醒了 小满、阿澈", useUnmergedTree = true).assertExists()
    }

    @Test fun 乙_AI帖没有提醒行() {
        show(post)
        compose.onNodeWithText("今天的晚霞像橘子汽水", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("提醒了", substring = true, useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }
}
