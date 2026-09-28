package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.model.UserStoryTemplatePayload
import com.situ.aichat.story.StoryGlobalCraftValues
import com.situ.aichat.story.StoryStatus
import com.situ.aichat.story.StoryUpdateMode
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.StoryHubSettingsCallbacks
import com.situ.aichat.ui.story.StorySettingsDraft
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-H1–H5（琉璃 2.0 卷六·三·上 §7）：书页头部继续阅读 / 档案 Tab 节拍卡与重排 / 设定 Tab 四组与条件 / 存模板到顶。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryBookHubPageTest {

    @get:Rule
    val compose = createComposeRule()

    private var continues = 0
    private var regens = 0
    private var deletes = 0
    private val tabs = mutableListOf<Int>()
    private val choiceToggles = mutableListOf<Boolean>()

    private val callbacks = StoryHubSettingsCallbacks(
        onOpenField = {}, onOpenGlobalSettings = {}, onUpdateDraft = {}, onSaveRole = {}, onDeleteRole = {}, onDraftPersona = null,
        onChapterChoicesChange = { choiceToggles += it }, onSceneSnapshotChange = {}, onWorldInfoChange = {}, onReminderChange = {},
        onSaveTemplate = {}, onArchive = {}, onDelete = { deletes++ }, onContinue = {}, onRestart = {},
    )

    private fun draft(mode: String) = StorySettingsDraft(
        updateMode = mode, unlockHour = 7, unlockMinute = 5, genre = "都市", writingStyle = "轻松幽默", narrativePerson = "second",
        chapterLengthPreference = 1500, chatInfluenceWeight = "medium", worldSetting = "", plotDirection = "",
    )

    private fun show(
        chapters: Int = 2,
        tab: Int = 0,
        regenerating: Boolean = false,
        mode: String = StoryUpdateMode.FREE,
        templateCount: Int = 0,
        status: String = StoryStatus.WAITING_CHOICE,
    ) {
        val story = StoryEntity(id = "s1", title = "对面楼的灯", status = status, cachedChapterCount = chapters, cachedLatestChapterNumber = chapters.takeIf { it > 0 })
        val data = LiuliHubData(story, draft(mode), emptyList(), StoryGlobalCraftValues(null, null, null), false, false, templateCount, regenerating)
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryBookHubPage(
                        data, tab, onTabChange = { tabs += it }, onClose = {}, onContinue = { continues++ }, onOpenField = {}, onOpenBeats = {},
                        onRegenerateOutline = { regens++ }, settingsCallbacks = callbacks, listState = rememberLazyListState(),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun H1_有章出继续阅读() {
        show(chapters = 2)
        compose.onAllNodesWithText("对面楼的灯").onFirst().assertExists()
        compose.onNodeWithText("继续阅读").performClick()
        compose.waitForIdle()
        assertEquals(1, continues)
    }

    @Test fun H1_空书不出继续阅读() {
        show(chapters = 0)
        compose.onAllNodesWithText("继续阅读").assertCountEquals(0)
    }

    @Test fun H2_档案Tab节拍卡空态_点设定切Tab() {
        show(tab = 0)
        compose.onNodeWithText("下一章节拍").assertExists()
        compose.onNodeWithText("还没有预排").assertExists()
        compose.onAllNodesWithText("设定").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), tabs)
    }

    @Test fun H3_重排中灰字不可点() {
        show(regenerating = true)
        compose.onAllNodesWithText("按最新剧情重排").assertCountEquals(0)
        compose.onNodeWithText("正在重排…").assertExists()
    }

    @Test fun H3_重排经确认() {
        show(regenerating = false)
        compose.onNodeWithText("按最新剧情重排").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("重排").performClick()
        compose.waitForIdle()
        assertEquals(1, regens)
    }

    @Test fun H4_设定Tab四组_无设定集不出世界观_自由模式无解锁时间_开关与删除() {
        show(tab = 1)
        listOf("写法", "参演角色", "生成开关", "连载与管理").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("世界观设定参与生成").assertCountEquals(0)
        compose.onAllNodesWithText("每日解锁时间").assertCountEquals(0)
        compose.onNodeWithText("章末给选项", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), choiceToggles)
        compose.onNodeWithText("删除本书").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").performClick()
        compose.waitForIdle()
        assertEquals(1, deletes)
    }

    @Test fun H4_追更模式出解锁时间两位补零() {
        show(tab = 1, mode = StoryUpdateMode.CHASE)
        compose.onNodeWithText("每日解锁时间", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("07:05", useUnmergedTree = true).assertExists()
    }

    @Test fun H5_模板满额点存模板出上限提示() {
        show(tab = 1, templateCount = UserStoryTemplatePayload.MAX_USER_TEMPLATES)
        compose.onNodeWithText("存为我的模板").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("最多存 20 个模板，删掉一些再存").assertExists()
    }

    /**
     * 复核 R1 🔵-1 回归钉：连载组（LiuliGroup 自带底距 24）到卡外「续写」钮的版位缝 = 24，不再叠 spacedBy(10) 成 34。
     * 钮的语义框 = 触达 48 的版位（minimumInteractiveComponentSize 占版），组脚注是组里最后一个节点、无底内距。
     */
    @Test fun H4_完结书_连载组到卡外操作钮缝24() {
        show(tab = 1, status = StoryStatus.COMPLETED)
        compose.onNodeWithText("续写这个故事").performScrollTo()
        compose.waitForIdle()
        val footer = compose.onNodeWithText("这些设定喂给 AI 写后面的章节", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("续写这个故事").getUnclippedBoundsInRoot()
        assertEquals(24f, (button.top - footer.bottom).value, 0.5f)
    }
}
