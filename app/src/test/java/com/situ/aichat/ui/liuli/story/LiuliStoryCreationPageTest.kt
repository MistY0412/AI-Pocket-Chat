package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryCreationForm
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-K1–K3（琉璃 2.0 卷六·三·上 §7）：创建页可建判据 / 自定义题材组 / 我也参演带昵称 / 选角出定位 / 高级展开。页内状态承接 update。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryCreationPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var creates = 0

    private fun show(characters: List<CharacterEntity> = emptyList(), nickname: String = "") {
        compose.setContent {
            var form by remember { mutableStateOf(StoryCreationForm()) }
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryCreationPage(
                        form, characters, nickname = nickname, bio = "", creating = false,
                        update = { t -> form = t(form) }, onCreate = { creates++ }, onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun K1_无人可演不可建_打开我也参演后可建() {
        show()
        compose.onNodeWithText("开始创作").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("我也参演").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("开始创作").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, creates)
    }

    @Test fun K2_自定义题材出提示词组_我也参演带昵称() {
        show(nickname = "阿满")
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("自定义提示词").assertExists()
        compose.onNodeWithText("我也参演").performScrollTo().performClick()
        compose.waitForIdle()
        // 标签「用户角色名」是框外的独立文字：按「可编辑框里的字 = 阿满」取角色名框（原默认名「我」已被昵称替换）。
        compose.onNodeWithText("用户角色名").assertExists()
        compose.onNode(hasSetTextAction() and hasText("阿满")).performScrollTo().assertTextContains("阿满")
        compose.onAllNodes(hasSetTextAction() and hasText("我")).assertCountEquals(0)
    }

    @Test fun K3_选角色出定位且首个为主角_退选收起_高级展开出世界观与剧情() {
        show(characters = listOf(CharacterEntity(uuid = "c1", name = "林夏", creationDate = 0L), CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 0L)))
        compose.onNodeWithText("林夏").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("主角").assertIsSelected()
        compose.onNodeWithText("林夏").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("主角").assertCountEquals(0)
        compose.onNodeWithText("高级设置").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("世界观描述", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("剧情方向", useUnmergedTree = true).assertExists()
    }
}
