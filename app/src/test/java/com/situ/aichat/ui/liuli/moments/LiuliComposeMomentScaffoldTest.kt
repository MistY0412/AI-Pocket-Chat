package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.glass.LocalLiuliGlassHost
import com.situ.aichat.ui.liuli.glass.LocalLiuliWindowGlassSource
import com.situ.aichat.ui.moments.ComposeMomentActions
import com.situ.aichat.ui.moments.ComposeMomentFace
import com.situ.aichat.ui.moments.ComposeMomentPageState
import com.situ.aichat.ui.moments.ComposeMomentScaffold
import com.situ.aichat.ui.moments.ComposeMomentState
import com.situ.aichat.ui.moments.ComposeMomentUi
import com.situ.aichat.ui.moments.ComposeMomentViewModel
import com.situ.aichat.ui.moments.ComposeMomentVoiceUi
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * T2-8（朋友圈发布页·乙 §7·E21）：琉璃脸驱动共用骨架。「探针脸」逐个方法委托 [LiuliComposeMomentFace]，在纸面 / 顶栏 /
 * 工具栏里记「同窗口玻璃宿主」是否在场（= 住 overlay·真玻璃），在选人弹层 / 保留对话框里记「跨窗口取景源在场、同窗口宿主
 * 不在场」（= 住 content·PITFALLS §2 #25）。另跑琉璃版 E1 / E2 / E5 / E10 / E12 五个行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliComposeMomentScaffoldTest {

    @get:Rule
    val compose = createComposeRule()

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 9, 27, 17, 42, 0, 0, shanghai).toInstant().toEpochMilli()
    private val characters = listOf(
        CharacterEntity(uuid = "c1", name = "小满", creationDate = 1),
        CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 1),
    )

    private val stateFlow = MutableStateFlow(ComposeMomentState())
    private val vm = mockk<ComposeMomentViewModel>(relaxed = true).also {
        every { it.state } returns stateFlow
        every { it.hasUnsavedChanges } returns false
    }
    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var closed = 0
    private var adds = 0
    private var cleared = 0
    private val page = ComposeMomentPageState(vm, onClose = { closed++ }, launchImagePicker = { adds++ }, SnackbarHostState())
    private val actions = ComposeMomentActions(
        onContentChange = {}, onRemoveImage = {}, onMoveImage = { _, _ -> }, onToggleMention = {},
        onClearMentions = { cleared++ }, onStartVoice = {}, onVoiceDrag = {}, onFinishVoice = {},
    )

    /** 每个方法照原样转给琉璃脸，并记下调用那一刻所在的层。 */
    private class ProbeFace : ComposeMomentFace {
        val overlayHost = mutableMapOf<String, Boolean>()
        val windowSource = mutableMapOf<String, Boolean>()
        private val real = LiuliComposeMomentFace

        @Composable private fun record(slot: String) {
            overlayHost[slot] = LocalLiuliGlassHost.current != null
            windowSource[slot] = LocalLiuliWindowGlassSource.current != null
        }

        override val skyFadesOut get() = real.skyFadesOut
        override val tileCorner get() = real.tileCorner

        @Composable override fun Host(modifier: Modifier, content: @Composable BoxScope.() -> Unit, overlay: @Composable BoxScope.() -> Unit) =
            real.Host(modifier, content, overlay)

        @Composable override fun TopBar(lightSky: Boolean, canPublish: Boolean, onCancel: () -> Unit, onPublish: () -> Unit) {
            record("TopBar")
            real.TopBar(lightSky, canPublish, onCancel, onPublish)
        }

        @Composable override fun Sheet(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
            record("Sheet")
            real.Sheet(modifier, content)
        }

        @Composable override fun Toolbar(content: @Composable RowScope.() -> Unit) {
            record("Toolbar")
            real.Toolbar(content)
        }

        @Composable override fun RecordingCard(level: Float, durationMs: Long, cancelling: Boolean) =
            real.RecordingCard(level, durationMs, cancelling)

        @Composable override fun Snackbar(state: SnackbarHostState, modifier: Modifier) = real.Snackbar(state, modifier)

        @Composable override fun tileRim(): Modifier = real.tileRim()

        @Composable override fun MentionPicker(
            characters: List<CharacterEntity>,
            selected: List<String>,
            availability: Map<String, MentionAvailability>,
            onToggle: (String) -> Unit,
            onDismiss: () -> Unit,
        ) {
            record("MentionPicker")
            real.MentionPicker(characters, selected, availability, onToggle, onDismiss)
        }

        @Composable override fun KeepDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onDismissRequest: () -> Unit) {
            record("KeepDialog")
            real.KeepDialog(onKeep, onDiscard, onDismissRequest)
        }
    }

    private fun show(state: ComposeMomentState, face: ComposeMomentFace = LiuliComposeMomentFace) {
        stateFlow.value = state
        val ui = ComposeMomentUi(state, characters, emptyMap(), ComposeMomentVoiceUi(), now, shanghai)
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    ComposeMomentScaffold(face, ui, page, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    /** E21：纸面 / 顶栏 / 工具栏住 overlay（同窗口宿主在场）；选人弹层 / 保留对话框住 content（取景源在场、同窗口宿主不在场）。 */
    @Test fun E21_琉璃层位() {
        page.showPicker = true
        page.showKeepDialog = true
        val probe = ProbeFace()
        show(ComposeMomentState(content = "晚霞"), probe)
        compose.runOnIdle {
            for (slot in listOf("Sheet", "TopBar", "Toolbar")) {
                assertEquals("$slot 被组合了", true, probe.overlayHost.containsKey(slot))
                assertEquals("$slot 要住 overlay（真玻璃）", true, probe.overlayHost[slot])
            }
            for (slot in listOf("MentionPicker", "KeepDialog")) {
                assertEquals("$slot 被组合了", true, probe.windowSource.containsKey(slot))
                assertEquals("$slot 要拿到跨窗口取景源", true, probe.windowSource[slot])
                assertEquals("$slot 不许住 overlay", false, probe.overlayHost[slot])
            }
        }
    }

    /** E1：空白进页——提示语、不聚焦、发布灰。 */
    @Test fun E1_空白进页() {
        show(ComposeMomentState())
        compose.onNodeWithText("此刻，想和 TA 们说点什么…", useUnmergedTree = true).assertExists()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onNodeWithText("发布").assertIsNotEnabled()
    }

    /** E2：有字 → 发布可点 → VM 发布 + success 轻震。 */
    @Test fun E2_有字可发布() {
        show(ComposeMomentState(content = "今天晴"))
        compose.onNodeWithText("发布").assertIsEnabled().performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.publish(any()) }
        verify(exactly = 1) { haptics.success() }
    }

    /** E5：3 张图 → 三格 +「3/9」格；点「+」格 = 去选图。 */
    @Test fun E5_三张图出加号格() {
        show(ComposeMomentState(content = "晚霞", images = listOf("/nope/a.jpg", "/nope/b.jpg", "/nope/c.jpg")))
        compose.onAllNodesWithContentDescription("移除图片").assertCountEquals(3)
        compose.onNodeWithText("3/9").performClick()
        compose.waitForIdle()
        assertEquals(1, adds)
    }

    /** E10：提醒签——签文、点叉全清、点签开弹层并先刷新可用性。 */
    @Test fun E10_提醒签() {
        show(ComposeMomentState(content = "晚霞", mentions = listOf("c1", "c2")))
        compose.onNodeWithText("提醒 小满、阿澈 看", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("取消提醒").performClick()
        compose.waitForIdle()
        assertEquals(1, cleared)
        compose.onNodeWithText("提醒 小满、阿澈 看").performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.refreshMentionAvailability() }
        assertTrue(page.showPicker)
    }

    /** E12：有改动点取消 → 琉璃保留对话框；保留 → keepDraft + 关。 */
    @Test fun E12_有改动取消_保留() {
        every { vm.hasUnsavedChanges } returns true
        show(ComposeMomentState(content = "晚霞"))
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("保留这次编辑吗？").assertExists()
        compose.onNodeWithText("保留").performClick()
        compose.waitForIdle()
        verify(exactly = 1) { vm.keepDraft() }
        assertEquals(1, closed)
    }
}
