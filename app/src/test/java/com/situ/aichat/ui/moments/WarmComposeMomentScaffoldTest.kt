package com.situ.aichat.ui.moments

import android.app.Activity
import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * T2-7（朋友圈发布页·乙 §7）：暖陶脸直接驱动共用骨架 [ComposeMomentScaffold]——页级状态直接构造（VM 用 MockK 假掉），
 * 时刻钉 2026-09-27 17:42（上海·实为周日·§11 D-2）。a–n 对应图纸 §5 E1–E20；锁定文本从图纸 §4.13 重新打字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class WarmComposeMomentScaffoldTest {

    @get:Rule
    val compose = createComposeRule()

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 9, 27, 17, 42, 0, 0, shanghai).toInstant().toEpochMilli()
    private val xiaoman = CharacterEntity(uuid = "c1", name = "小满", creationDate = 1)
    private val ache = CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 1)
    private val photos = listOf("/nope/a.jpg", "/nope/b.jpg", "/nope/c.jpg")

    private val stateFlow = MutableStateFlow(ComposeMomentState())
    private val vm = mockk<ComposeMomentViewModel>(relaxed = true).also {
        every { it.state } returns stateFlow
        every { it.hasUnsavedChanges } returns false
    }
    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var closed = 0
    private var adds = 0
    private val removed = mutableListOf<String>()
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val toggled = mutableListOf<String>()
    private var cleared = 0
    private val page = ComposeMomentPageState(vm, onClose = { closed++ }, launchImagePicker = { adds++ }, SnackbarHostState())
    private val actions = ComposeMomentActions(
        onContentChange = {},
        onRemoveImage = { removed += it },
        onMoveImage = { from, to -> moves += from to to },
        onToggleMention = { toggled += it },
        onClearMentions = { cleared++ },
        onStartVoice = {},
        onVoiceDrag = {},
        onFinishVoice = {},
    )

    private var ui by mutableStateOf(ui(ComposeMomentState()))

    private fun ui(
        state: ComposeMomentState,
        characters: List<CharacterEntity> = listOf(xiaoman, ache),
        availability: Map<String, MentionAvailability> = emptyMap(),
        voice: ComposeMomentVoiceUi = ComposeMomentVoiceUi(),
    ) = ComposeMomentUi(state, characters, availability, voice, now, shanghai)

    private fun show(value: ComposeMomentUi) {
        stateFlow.value = value.state
        ui = value
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.CLAY) {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    ComposeMomentScaffold(WarmComposeMomentFace, ui, page, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    /** a（E1）：空白进页——提示语、不聚焦、发布灰、字数环无数字。 */
    @Test fun a_空白进页() {
        show(ui(ComposeMomentState()))
        compose.onNodeWithText("此刻，想和 TA 们说点什么…", useUnmergedTree = true).assertExists()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onNodeWithText("发布").assertIsNotEnabled()
        compose.onNodeWithContentDescription("还能写 500 字").assertExists()
        compose.onAllNodesWithText("500", useUnmergedTree = true).assertCountEquals(0)
    }

    /** b（E2）：有字 → 发布可点 → VM 发布 + success 轻震。 */
    @Test fun b_有字可发布() {
        show(ui(ComposeMomentState(content = "今天晴")))
        compose.onNodeWithText("发布").assertIsEnabled().performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.publish(any()) }
        verify(exactly = 1) { haptics.success() }
    }

    /** c（E3）：501 字 → 发布灰、环旁「-1」、读屏「超出 1 字」。 */
    @Test fun c_超一字() {
        show(ui(ComposeMomentState(content = "字".repeat(501))))
        compose.onNodeWithText("发布").assertIsNotEnabled()
        compose.onNodeWithText("-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("超出 1 字").assertExists()
    }

    /** d（E5）：3 张图 → 三格 +「3/9」格；点「+」格 = 去选图。 */
    @Test fun d_三张图出加号格() {
        show(ui(ComposeMomentState(content = "晚霞", images = photos)))
        compose.onAllNodesWithContentDescription("移除图片").assertCountEquals(3)
        compose.onNodeWithText("3/9", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("3/9").performClick()
        compose.waitForIdle()
        assertEquals(1, adds)
    }

    /** e（E6）：9 张图 → 没有「+」格，工具栏加图钮禁用。 */
    @Test fun e_九张图不再能加() {
        show(ui(ComposeMomentState(content = "晚霞", images = List(9) { "/nope/$it.jpg" })))
        compose.onAllNodesWithText("/9", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("添加图片").assertIsNotEnabled()
    }

    /** f（E7）：点图格小叉 → 移除那一张；点图格 → 看大图。 */
    @Test fun f_小叉移除_点格看大图() {
        show(ui(ComposeMomentState(content = "晚霞", images = photos)))
        compose.onAllNodesWithContentDescription("移除图片")[1].performClick()
        compose.waitForIdle()
        assertEquals(listOf(photos[1]), removed)
        compose.onNodeWithContentDescription("第 1 张照片").performClick()
        compose.waitForIdle()
        assertEquals(photos[0], page.viewerPath)
    }

    /** g（E10）：提醒签文「提醒 小满、阿澈 看」；点签 → 刷新可用性并开弹层；点叉 → 全清。 */
    @Test fun g_提醒签() {
        show(ui(ComposeMomentState(content = "晚霞", mentions = listOf("c1", "c2"))))
        compose.onNodeWithText("提醒 小满、阿澈 看", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("取消提醒").performClick()
        compose.waitForIdle()
        assertEquals(1, cleared)
        compose.onNodeWithText("提醒 小满、阿澈 看").performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.refreshMentionAvailability() }
        assertTrue(page.showPicker)
    }

    /** g′（复核 R1 核 §11 D-3·§4.15 48dp）：点在签下沿之外（离小叉视觉中心 20dp·仍在 48 触达内）也能全清——胶囊的圆角裁切不裁触达。 */
    @Test fun g2_提醒签小叉48触达() {
        show(ui(ComposeMomentState(content = "晚霞", mentions = listOf("c1", "c2"))))
        val x = compose.onNodeWithContentDescription("取消提醒").getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            val cx = (x.left + x.right).toPx() / 2f
            val cy = (x.top + x.bottom).toPx() / 2f
            click(Offset(cx, cy + 20.dp.toPx()))
        }
        compose.waitForIdle()
        assertEquals(1, cleared)
        assertEquals(false, page.showPicker)
    }

    /** h（E11）：弹层——在睡 / 见面中出状态行；点行即时切换；完成文案随选中数。 */
    @Test fun h_选人弹层() {
        page.showPicker = true
        show(
            ui(
                ComposeMomentState(content = "晚霞", mentions = listOf("c1")),
                availability = mapOf("c1" to MentionAvailability.SLEEPING, "c2" to MentionAvailability.IN_MEETING),
            ),
        )
        compose.onNodeWithText("提醒谁看").assertExists()
        compose.onNodeWithText("被提醒的 TA 一定会来看看、留句话。").assertExists()
        compose.onNodeWithText("在睡觉 · 醒了再来看", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("正在和你见面 · 结束后来看", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("完成 1").assertExists()
        compose.onNodeWithText("阿澈", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("c2"), toggled)
        compose.onNodeWithText("完成 1").performClick()
        compose.waitForIdle()
        assertTrue(!page.showPicker)
    }

    /** h′（E11）：一个没选时完成钮 =「完成」。 */
    @Test fun h2_没选时完成() {
        page.showPicker = true
        show(ui(ComposeMomentState(content = "晚霞")))
        compose.onNodeWithText("完成").assertExists()
    }

    /** i（E12）：有改动点取消 → 保留对话框；保留 → keepDraft + 关。 */
    @Test fun i_有改动取消_保留() {
        every { vm.hasUnsavedChanges } returns true
        show(ui(ComposeMomentState(content = "晚霞")))
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("保留这次编辑吗？").assertExists()
        compose.onNodeWithText("保留的话，下次点「发动态」还在。").assertExists()
        compose.onNodeWithText("保留").performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.keepDraft() }
        assertEquals(1, closed)
    }

    /** i′（E12）：不保留 → discard + 关。 */
    @Test fun i2_有改动取消_不保留() {
        every { vm.hasUnsavedChanges } returns true
        show(ui(ComposeMomentState(content = "晚霞")))
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("不保留").performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.discard() }
        verify(exactly = 0) { vm.keepDraft() }
        assertEquals(1, closed)
    }

    /** j（E15）：录音中 → 工具栏上方出录音卡（「正在录音」「上滑取消」），卡在工具栏之上。 */
    @Test fun j_录音卡在工具栏上方() {
        show(ui(ComposeMomentState(), voice = ComposeMomentVoiceUi(recording = true, level = 0.5f, durationMs = 3_000L)))
        compose.onNodeWithText("正在录音", useUnmergedTree = true).assertExists()
        val hint = compose.onNodeWithText("上滑取消", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val tool = compose.onNodeWithContentDescription("添加图片").getUnclippedBoundsInRoot()
        assertTrue("录音卡底 ${hint.bottom} 应在工具栏顶 ${tool.top} 之上", hint.bottom <= tool.top)
    }

    /** k（E16）：转写中 → 纸面出「正在落笔…」。 */
    @Test fun k_落笔中() {
        show(ui(ComposeMomentState(), voice = ComposeMomentVoiceUi(transcribing = true)))
        compose.onNodeWithText("正在落笔…", useUnmergedTree = true).assertExists()
    }

    /** l（E17）：天上文字——大字时间 + 「九月二十七 · 周日 · 傍晚」+ 窄态一行。 */
    @Test fun l_天上文字() {
        show(ui(ComposeMomentState()))
        compose.onNodeWithText("17:42", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("九月二十七 · 周日 · 傍晚", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("17:42 · 周日傍晚", useUnmergedTree = true).assertExists()
    }

    /** m（E20）：没有角色 → 提醒谁看钮禁用。 */
    @Test fun m_没角色不能提醒() {
        show(ui(ComposeMomentState(), characters = emptyList()))
        compose.onNodeWithContentDescription("提醒谁看").assertIsNotEnabled()
    }

    /** n（E8）：按住第一张右拖一格 → 拖动中出提示行；松手换位 (0, 1)。 */
    @Test fun n_按住右拖一格换位() {
        show(ui(ComposeMomentState(content = "晚霞", images = photos)))
        val tile = compose.onNodeWithContentDescription("第 1 张照片")
        tile.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            val step = (width + 8.dp.toPx()) / 6f
            repeat(6) { moveBy(Offset(step, 0f)) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("松手放下 · 排第一的就是圈子里的第一张", useUnmergedTree = true).assertExists()
        tile.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(0 to 1), moves)
        verify(exactly = 1) { haptics.selection() }
        compose.onAllNodesWithText("松手放下", substring = true, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(null, page.viewerPath) // 长按拖动不误触看大图
    }

    /** n′（E9）：无障碍自定义动作「后移一位」→ (0, 1)；首张没有「前移一位」。 */
    @Test fun n2_无障碍后移一位() {
        show(ui(ComposeMomentState(content = "晚霞", images = photos)))
        val node = compose.onNodeWithContentDescription("第 1 张照片").fetchSemanticsNode()
        val custom = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf("后移一位"), custom.map { it.label })
        compose.runOnIdle { custom.single().action() }
        assertEquals(listOf(0 to 1), moves)
        val middle = compose.onNodeWithContentDescription("第 2 张照片").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("前移一位", "后移一位"), middle.map { it.label })
    }

    /**
     * n″（复核 R1）：被拖格 1:1 跟手——起拖后手指横移多少，格子就横移多少；拖动提示行出现把网格往下推一行，
     * 格子也不跟着掉下去（手指没竖着动，格子顶不变）。值从「跟手」反推：位移差 ≤ 0.5dp。
     */
    @Test fun n3_拖动一比一跟手() {
        show(ui(ComposeMomentState(content = "晚霞", images = photos)))
        val tile = compose.onNodeWithContentDescription("第 1 张照片")
        val rest = tile.getUnclippedBoundsInRoot()
        tile.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(1f, 0f))
        }
        compose.waitForIdle()
        compose.onNodeWithText("松手放下 · 排第一的就是圈子里的第一张", useUnmergedTree = true).assertExists()
        val lifted = tile.getUnclippedBoundsInRoot()
        var movedDp = 0f
        tile.performTouchInput {
            val step = width / 5f
            repeat(5) { moveBy(Offset(step, 0f)) }
            movedDp = (step * 5) / density
        }
        compose.waitForIdle()
        val dragged = tile.getUnclippedBoundsInRoot()
        assertEquals("横移 = 手指横移", movedDp, (dragged.left - lifted.left).value, 0.5f)
        assertEquals("提示行推网格，格子不掉", rest.top.value, dragged.top.value, 0.5f)
        tile.performTouchInput { up() }
        compose.waitForIdle()
    }

    private lateinit var hostView: View

    private fun showToggleable(value: ComposeMomentUi, dark: Boolean, shown: () -> Boolean) {
        stateFlow.value = value.state
        ui = value
        compose.setContent {
            hostView = LocalView.current
            AIPocketChatTheme(darkTheme = dark, skin = AppSkin.CLAY) {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    if (shown()) ComposeMomentScaffold(WarmComposeMomentFace, ui, page, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun lightStatusIcons(): Boolean =
        WindowCompat.getInsetsController((hostView.context as Activity).window, hostView).isAppearanceLightStatusBars

    /** o（复核 R1·§11 T-1）：浅色 App + 傍晚暗天 → 状态栏浅图标（压在天色上看得清）；离页 → 回到跟随 App（深图标）。 */
    @Test fun o_状态栏图标随暗天变浅_离页还原() {
        var shown by mutableStateOf(true)
        showToggleable(ui(ComposeMomentState()), dark = false) { shown }
        assertFalse("暗天上要浅图标", lightStatusIcons())
        shown = false
        compose.waitForIdle()
        assertTrue("离页还原跟随浅色 App", lightStatusIcons())
    }

    /** o′：深色 App + 白天亮天 → 深图标；离页 → 回到跟随深色 App（浅图标）。 */
    @Test fun o2_深色App白天亮天用深图标_离页还原() {
        var shown by mutableStateOf(true)
        val noon = ZonedDateTime.of(2026, 9, 27, 12, 0, 0, 0, shanghai).toInstant().toEpochMilli()
        showToggleable(ui(ComposeMomentState()).copy(nowMillis = noon), dark = true) { shown }
        assertTrue("亮天上要深图标", lightStatusIcons())
        shown = false
        compose.waitForIdle()
        assertFalse("离页还原跟随深色 App", lightStatusIcons())
    }
}
