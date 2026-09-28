package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentLikeEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.page.LIULI_BOTTOM_EDGE_TAG
import com.situ.aichat.ui.theme.AIPocketChatTheme
import com.situ.aichat.util.StringListJson
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-L1–L6（琉璃 2.0 卷六·二 §7）：直接驱动无 VM 的 [LiuliMomentsListPage]——空态两个入口、未读横幅、长按菜单、
 * 点卡 / 点作者名、屏底带高度、刷新转圈位置。期望从暖陶行为与图纸几何独立反推（Robolectric 状态栏 / 导航栏恒 0）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliMomentsListPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val xiaoman = CharacterEntity(uuid = "c1", name = "小满", creationDate = 1)

    private fun post(uuid: String, content: String, userLiked: Boolean = false) = MomentPostWithRelations(
        post = MomentPostEntity(uuid = uuid, content = content, timestamp = System.currentTimeMillis() - 3_600_000, authorTypeRaw = "character", characterUuid = "c1"),
        comments = emptyList(),
        likes = if (userLiked) listOf(MomentLikeEntity(authorTypeRaw = "user", postUuid = uuid)) else emptyList(),
    )

    private var composes = 0
    private var notifications = 0
    private var refreshes = 0
    private val opened = mutableListOf<String>()
    private val authors = mutableListOf<String>()
    private val toggled = mutableListOf<String>()
    private val deletes = mutableListOf<String>()

    private val callbacks = LiuliMomentsFeedCallbacks(
        onBack = {},
        onCompose = { composes++ },
        onOpenPost = { opened += it },
        onOpenNotifications = { notifications++ },
        onOpenCharacterMoments = { authors += it },
        onRefresh = { refreshes++ },
        onToggleLike = { toggled += it.post.uuid },
        onRequestDelete = { deletes += it },
    )

    private fun show(
        feed: List<MomentPostWithRelations> = emptyList(),
        unread: Int = 0,
        refreshing: Boolean = false,
        characters: Map<String, CharacterEntity> = mapOf(xiaoman.uuid to xiaoman),
    ) {
        val data = LiuliMomentsFeedData(
            feed = feed,
            characters = characters,
            userName = "阿满",
            userAvatarPath = null,
            unreadCount = unread,
            refreshing = refreshing,
        )
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliMomentsListPage(data, callbacks, rememberLazyListState(), remember { SnackbarHostState() })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun L1_空圈子出空态且文字钮与圆钮都去发动态() {
        show()
        compose.onNodeWithText("朋友圈还没有动态").assertExists()
        compose.onNodeWithText("发布动态").performClick()
        compose.waitForIdle()
        assertEquals(1, composes)
        compose.onNodeWithContentDescription("发布动态").performClick()
        compose.waitForIdle()
        assertEquals(2, composes)
    }

    @Test fun L2_未读两条出横幅点了去消息页() {
        show(feed = listOf(post("p1", "今天的晚霞")), unread = 2)
        compose.onNodeWithText("2 条新消息").performClick()
        compose.waitForIdle()
        assertEquals(1, notifications)
    }

    @Test fun L2_未读零不出横幅() {
        show(feed = listOf(post("p1", "今天的晚霞")), unread = 0)
        compose.onAllNodesWithText("条新消息", substring = true).assertCountEquals(0)
    }

    @Test fun L3_长按出菜单可删可赞() {
        show(feed = listOf(post("p1", "今天的晚霞")))
        compose.onNodeWithText("今天的晚霞", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("点赞").assertExists()
        compose.onNodeWithText("删除动态").performClick()
        compose.waitForIdle()
        assertEquals(listOf("p1"), deletes)
        compose.onNodeWithText("今天的晚霞", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("点赞").performClick()
        compose.waitForIdle()
        assertEquals(listOf("p1"), toggled)
    }

    @Test fun L3_已赞的帖菜单是取消点赞() {
        show(feed = listOf(post("p2", "周末去露营", userLiked = true)))
        compose.onNodeWithText("周末去露营", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("取消点赞").assertExists()
        compose.onAllNodesWithText("点赞").assertCountEquals(0)
    }

    @Test fun L4_点正文进详情点作者名进某人的动态() {
        show(feed = listOf(post("p1", "今天的晚霞")))
        compose.onNodeWithText("今天的晚霞", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("p1"), opened)
        compose.onNodeWithText("小满", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("c1"), authors)
        assertEquals(listOf("p1"), opened)
    }

    @Test fun L5_屏底带恰一条高92() {
        show(feed = listOf(post("p1", "今天的晚霞")))
        compose.onAllNodesWithTag(LIULI_BOTTOM_EDGE_TAG).assertCountEquals(1)
        val band = compose.onNodeWithTag(LIULI_BOTTOM_EDGE_TAG).getUnclippedBoundsInRoot()
        assertEquals(92f, (band.bottom - band.top).value, 0.5f)
    }

    @Test fun L6_刷新中转圈居中在大标题带() {
        show(feed = listOf(post("p1", "今天的晚霞")), refreshing = true)
        val spinner = compose.onNodeWithTag(LIULI_MOMENTS_REFRESH_TAG).getUnclippedBoundsInRoot()
        // 顶 = 导航行 44 + 2 + (40 − 24) / 2 = 54；中心 = 54 + 12。
        assertEquals(66f, ((spinner.top + spinner.bottom) / 2).value, 1f)
    }

    /** T2-5（朋友圈发布页·乙 §7·E22）：琉璃圈子卡片——用户帖提醒 [c1, ghost, c2] → 「提醒了 小满、阿澈」；同屏 AI 帖没有那一行。 */
    @Test fun 乙_用户帖提醒了谁一行() {
        val ache = CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 1)
        val mine = MomentPostWithRelations(
            post = MomentPostEntity(
                uuid = "p9", content = "今天的晚霞像橘子汽水", timestamp = System.currentTimeMillis() - 60_000, authorTypeRaw = "user",
                mentionedCharacterUuidsJson = StringListJson.encode(listOf("c1", "ghost", "c2")),
            ),
            comments = emptyList(),
            likes = emptyList(),
        )
        show(feed = listOf(mine, post("p1", "周末去露营")), characters = mapOf(xiaoman.uuid to xiaoman, ache.uuid to ache))
        compose.onNodeWithText("提醒了 小满、阿澈", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("周末去露营", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("提醒了", substring = true, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun L6_不刷新没有转圈() {
        show(feed = listOf(post("p1", "今天的晚霞")), refreshing = false)
        compose.onAllNodesWithTag(LIULI_MOMENTS_REFRESH_TAG).assertCountEquals(0)
    }
}
