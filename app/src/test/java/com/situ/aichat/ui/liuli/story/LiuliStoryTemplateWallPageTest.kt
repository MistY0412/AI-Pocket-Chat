package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserStoryTemplateEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryRoleType
import com.situ.aichat.story.StoryTemplate
import com.situ.aichat.story.StoryTemplates
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

/** T2-W1–W2（琉璃 2.0 卷六·三·上 §7）：模板墙区头 / 尾卡 / 我的模板长按菜单删除 / 开书弹层开始连载与改一改再开。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryTemplateWallPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val customs = mutableListOf<String?>()
    private val starts = mutableListOf<Triple<StoryTemplate, Map<String, String>, Boolean>>()
    private val deletes = mutableListOf<String>()
    private val callbacks = LiuliWallCallbacks(
        onBack = {},
        onOpenCustom = { customs += it },
        onStart = { t, roles, inc -> starts += Triple(t, roles, inc) },
        onRename = { _, _ -> },
        onDeleteTemplate = { deletes += it },
    )
    private val lin = CharacterEntity(uuid = "c1", name = "林夏", creationDate = 0L)

    private fun show(mine: List<UserStoryTemplateEntity> = emptyList()) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryTemplateWallPage(mine, listOf(lin), creating = false, callbacks = callbacks, listState = rememberLazyListState())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun W1_无我的模板_区头是墙副标题_尾卡进空白自定义() {
        show()
        compose.onNodeWithText("挑一套喜欢的开始 · 也可以完全自己写").assertExists()
        compose.onAllNodesWithText("我的模板").assertCountEquals(0)
        compose.onNodeWithText(StoryTemplates.all.first().title, useUnmergedTree = true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("自己从头写"))
        compose.onNodeWithText("自己从头写", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf<String?>(null), customs)
    }

    @Test fun W1_有我的模板_区头是我的模板_长按删除经确认() {
        show(listOf(UserStoryTemplateEntity(uuid = "u1", name = "我的甜文", createdAt = 0L, payloadJson = "{}")))
        compose.onNodeWithText("我的模板").assertExists()
        compose.onNodeWithText("我的甜文", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("重命名").assertExists()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        // 对话框面板本身也是可点的合并节点（Text = [删除, 正文]）：按「按钮」角色取确认钮。
        compose.onNode(hasText("删除") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("u1"), deletes)
    }

    @Test fun W2_点内置第一套_开始连载带首角色主演与我也入场_改一改再开带模板id() {
        show()
        val first = StoryTemplates.all.first()
        compose.onNodeWithText(first.title, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("开始连载").performClick()
        compose.waitForIdle()
        assertEquals(listOf(Triple(first, mapOf("c1" to StoryRoleType.PROTAGONIST), true)), starts)
        compose.onNodeWithText("改一改再开 ›").performClick()
        compose.waitForIdle()
        assertEquals(listOf<String?>(first.id), customs)
    }
}
