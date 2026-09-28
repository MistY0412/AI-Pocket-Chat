package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.local.entity.StoryCharacterRoleEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.story.StoryRoleType
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.storyRoleEditConfig
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-R1（琉璃 2.0 卷六·三·上 §7）：角色编辑弹层权限矩阵落到界面（「我」锁名无反差无移出 / 专属角色空名禁存、保存、确认后移出）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryRoleEditorSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<StoryCharacterRoleEntity>()
    private val deleted = mutableListOf<String>()

    private fun show(role: StoryCharacterRoleEntity) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryRoleEditorSheet(storyRoleEditConfig(role, onDraftPersona = null, onSave = { saved += it }, onDelete = { deleted += it })) {}
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun R1_我那一行_名不可编辑_无反差_无移出() {
        show(StoryCharacterRoleEntity(id = "r0", storyId = "s", roleName = "我", roleType = StoryRoleType.PROTAGONIST, isUserRole = true))
        // 禁用的输入框没有 SetText 动作：按「可编辑文本 = 我」取名字框。
        compose.onNode(hasText("我") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsNotEnabled()
        compose.onAllNodesWithText("私下反差").assertCountEquals(0)
        compose.onAllNodesWithText("从本书移出").assertCountEquals(0)
    }

    @Test fun R1_专属角色_空名禁存_填名保存_确认后移出() {
        show(StoryCharacterRoleEntity(id = "r2", storyId = "s", roleName = "阿澈"))
        val name = compose.onAllNodes(hasSetTextAction()).onFirst()
        name.performTextClearance()
        compose.waitForIdle()
        compose.onNodeWithText("保存").performScrollTo().assertIsNotEnabled()
        name.performTextInput("小夏")
        compose.waitForIdle()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("小夏"), saved.map { it.roleName })
        compose.onNodeWithText("从本书移出").performScrollTo().performClick()
        compose.waitForIdle()
        // 确认框的确认钮文案也是「从本书移出」：按按钮角色取它（面板本身也是可点的合并节点）。
        compose.onNode(
            hasText("从本书移出") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasAnyAncestor(hasText(REMOVE_TITLE)),
        ).performClick()
        compose.waitForIdle()
        assertEquals(listOf("r2"), deleted)
    }

    private companion object {
        const val REMOVE_TITLE = "从本书移出？"
    }
}
