package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.R
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T2-DS1–DS9（琉璃 2.0 卷六·三·下乙 §7）：琉璃导演台——照暖陶 `StoryDirectorSheetEditModeTest` 八例同名同断言
 * （保存路由 / 哨兵态 / 撤回二段式 / 没改不写库·全否定断言各配正向证据 `dismissCount == 1`），
 * 另加两例题头关闭圆的弃改（改过先问 · 没改直关）。经 [LiuliStoryReaderSheetFace.DirectorSheet] 入口。
 */
// ⚠️ qualifiers 带尺寸不是装饰：默认 320×470 屏会把按钮推出可视区，performClick 静默不命中（PITFALLS §1e）。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryDirectorSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    /** 栏 A 的输入框（面板里两个输入框按出现序：0 = 剧情走向、1 = 本章节拍）。 */
    private fun flowField() = compose.onAllNodes(hasSetTextAction())[0]

    private var submitted: String? = null
    private var overwritten: String? = null
    private var withdrawCount = 0
    private var dismissCount = 0

    private fun flowLabel() = app.getString(R.string.story_director_flow_label)
    private fun saveText() = app.getString(R.string.action_save)
    private fun withdrawText() = app.getString(R.string.story_director_withdraw)
    private fun tagText() = app.getString(R.string.story_continue_direction_tag)
    private fun savedHint() = app.getString(R.string.story_director_flow_saved_hint)
    private fun discardTitle() = app.getString(R.string.story_field_discard_title)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun clickButton(text: String) {
        compose.onNode(hasText(text) and isButton).performClick()
        compose.waitForIdle()
    }

    private fun setSheet(savedDirection: String?, directionCommitted: Boolean) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryReaderSheetFace.DirectorSheet(
                        beats = "AI 预排的节拍",
                        beatsUserEdited = false,
                        savedDirection = savedDirection,
                        directionCommitted = directionCommitted,
                        onSubmitFlow = { submitted = it },
                        onOverwriteDirection = { overwritten = it },
                        onWithdrawDirection = { withdrawCount++ },
                        onSaveBeats = {},
                        onRestoreAiBeats = {},
                        onDismiss = { dismissCount++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertAbsent(text: String, why: String) = assertTrue(
        why,
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
    )

    @Test
    fun DS1_编辑模式_预填已存走向并挂tag与覆盖提示() {
        setSheet(savedDirection = "旧走向：让她先回家", directionCommitted = true)

        compose.onNodeWithText("旧走向：让她先回家").assertIsDisplayed()
        compose.onNodeWithText(tagText()).assertIsDisplayed()
        compose.onNodeWithText(savedHint()).assertIsDisplayed()
    }

    @Test
    fun DS2_编辑模式_改文本保存_走覆盖写口且创建路零调用() {
        setSheet(savedDirection = "旧走向", directionCommitted = true)

        flowField().performTextClearance()
        flowField().performTextInput("  新走向：留在旅馆  ")
        clickButton(saveText())

        assertEquals("覆盖写口收到 trim 后的新文本", "新走向：留在旅馆", overwritten)
        assertNull("创建路一次都不许被调用", submitted)
        assertEquals("保存后关面板", 1, dismissCount)
    }

    @Test
    fun DS3_哨兵态_预填空且无tag无撤回_但保存走覆盖写口() {
        setSheet(savedDirection = null, directionCommitted = true)

        assertAbsent(tagText(), "哨兵不是用户写的走向，不挂「已保存 · 待生成」")
        assertAbsent(withdrawText(), "哨兵态不给撤回")
        assertAbsent(savedHint(), "没有已存走向文本，覆盖提示无从谈起")

        flowField().performTextInput("哨兵态下写的走向")
        clickButton(saveText())

        assertEquals("哨兵态保存必须走覆盖写口", "哨兵态下写的走向", overwritten)
        assertNull("否则 submitChoice 见哨兵非空照样 return = 洞还在", submitted)
    }

    @Test
    fun DS4_撤回_点按钮先弹确认_确认后才发撤回回调() {
        setSheet(savedDirection = "要撤掉的走向", directionCommitted = true)

        clickButton(withdrawText())
        compose.onNodeWithText(app.getString(R.string.story_director_withdraw_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.story_director_withdraw_body)).assertIsDisplayed()
        assertEquals("确认之前一次都不许发撤回", 0, withdrawCount)

        clickButton(app.getString(R.string.story_director_withdraw_confirm))

        assertEquals(1, withdrawCount)
        assertEquals("撤回后关面板", 1, dismissCount)
    }

    @Test
    fun DS5_撤回_确认弹窗点继续编辑_不发撤回也不关面板() {
        setSheet(savedDirection = "要撤掉的走向", directionCommitted = true)

        clickButton(withdrawText())
        clickButton(app.getString(R.string.story_field_discard_no))

        assertEquals("取消 = 什么都没发生", 0, withdrawCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun DS6_原样保存_栏A零写库() {
        setSheet(savedDirection = "原样走向", directionCommitted = true)

        clickButton(saveText())

        assertEquals("正向证据：保存确实点到了（否则下面两条全否定断言是假绿）", 1, dismissCount)
        assertNull("没改过 → 覆盖写口零调用", overwritten)
        assertNull("更不许误走创建路", submitted)
    }

    @Test
    fun DS7_清空后保存_栏A零写库() {
        setSheet(savedDirection = "原样走向", directionCommitted = true)

        flowField().performTextClearance()
        clickButton(saveText())

        assertEquals("正向证据：保存确实点到了", 1, dismissCount)
        assertNull("清空 ≠ 撤回：栏 A 不写库", overwritten)
        assertNull(submitted)
    }

    @Test
    fun DSC_创建模式_保存走既有submitChoice创建路_覆盖写口零调用() {
        setSheet(savedDirection = null, directionCommitted = false)

        compose.onNodeWithText(flowLabel()).assertIsDisplayed()
        assertAbsent(tagText(), "没答过时不挂 tag")
        assertAbsent(withdrawText(), "没答过时没有可撤回的东西")

        flowField().performTextInput("  第一次写的走向  ")
        clickButton(saveText())

        assertEquals("第一次写的走向", submitted)
        assertNull("创建路不许误走覆盖写口", overwritten)
    }

    @Test
    fun DS8_改过后点题头关闭圆_先弹弃改确认_放弃则零写库关闭() {
        setSheet(savedDirection = "旧走向", directionCommitted = true)

        flowField().performTextClearance()
        flowField().performTextInput("改了一半")
        compose.onNodeWithContentDescription(app.getString(R.string.action_close)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(discardTitle()).assertIsDisplayed()
        assertEquals("确认之前不关", 0, dismissCount)

        clickButton(app.getString(R.string.story_field_discard_yes))

        assertEquals(1, dismissCount)
        assertNull(overwritten)
        assertNull(submitted)
    }

    @Test
    fun DS9_没改点题头关闭圆_直接关_不弹弃改确认() {
        setSheet(savedDirection = "旧走向", directionCommitted = true)

        compose.onNodeWithContentDescription(app.getString(R.string.action_close)).performClick()
        compose.waitForIdle()

        assertEquals(1, dismissCount)
        assertAbsent(discardTitle(), "没改就不问")
    }
}
