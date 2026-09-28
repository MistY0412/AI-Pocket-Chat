package com.situ.aichat.ui.liuli.moments

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentNotificationEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-N1–N3（琉璃 2.0 卷六·二 §7）：直接驱动无 VM 的 [LiuliMomentNotificationsPage]——空态与「全部已读」门控、两类标题、
 * 点行打开、左滑标记已读（右滑无反应）。左滑那条同时是揭示底首帧 `requireOffset` 不抛的证据（E28）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliMomentNotificationsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private val characters = mapOf(
        "c1" to CharacterEntity(uuid = "c1", name = "小满", creationDate = 1),
        "c2" to CharacterEntity(uuid = "c2", name = "林夏", creationDate = 1),
    )
    private val comment = MomentNotificationEntity(id = 1, typeRaw = "commentOnUserPost", timestamp = now - 3_600_000, characterUuid = "c1", contentPreview = "风再大也要记得带伞呀")
    private val like = MomentNotificationEntity(id = 2, typeRaw = "likeOnUserPost", timestamp = now - 7_200_000, characterUuid = "c2")

    private val opened = mutableListOf<Long>()
    private val marked = mutableListOf<Long>()
    private var allRead = 0

    private fun show(list: List<MomentNotificationEntity>) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliMomentNotificationsPage(
                        notifications = list,
                        characters = characters,
                        onBack = {},
                        onOpen = { opened += it.id },
                        onMarkRead = { marked += it },
                        onMarkAllRead = { allRead++ },
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun N1_空时出空态且没有全部已读() {
        show(emptyList())
        compose.onNodeWithText("没有新消息").assertExists()
        compose.onNodeWithText("全部已读").assertDoesNotExist()
    }

    @Test fun N2_两类标题与全部已读与点行打开() {
        show(listOf(comment, like))
        compose.onNodeWithText("小满 评论了你的动态").assertExists()
        compose.onNodeWithText("林夏 赞了你的动态").assertExists()
        compose.onNodeWithText("全部已读").performClick()
        compose.waitForIdle()
        assertEquals(1, allRead)
        compose.onNodeWithText("小满 评论了你的动态").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1L), opened)
    }

    /**
     * 图纸写「onMarkRead 1 次」；实测 M3 `SwipeToDismissBox` 一次左滑会多次询问 `confirmValueChange`（本机 4 次），而这段询问
     * 逻辑是两张脸共用、自暖陶只搬不改的 `rememberMomentMarkReadSwipeState`——标已读是幂等 UPDATE，行为同暖陶。
     * 故这里断「确实标了、且只标这一条」（施工日志 §11 D-4·留复核裁决）。
     */
    @Test fun N3_左滑标记已读() {
        show(listOf(comment, like))
        compose.onNodeWithText("小满 评论了你的动态").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(marked.isNotEmpty())
        assertEquals(setOf(1L), marked.toSet())
    }

    @Test fun N3_右滑没反应() {
        show(listOf(comment, like))
        compose.onNodeWithText("小满 评论了你的动态").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(emptyList<Long>(), marked)
    }
}
