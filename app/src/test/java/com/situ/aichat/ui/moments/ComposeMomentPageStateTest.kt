package com.situ.aichat.ui.moments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppSnackbarHost
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-6（朋友圈发布页·乙 §7·E12 / E13 / E14）+ 卷六·二遗留 T1（三个派生量 / 能否加图）：页级状态件真组合跑（VM 用 MockK 假掉）——
 * 关页守卫两态、保留 / 不保留、开选人先刷新可用性、发布可发时轻震、草稿恢复提示（点清空 / 4 秒自己收）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class ComposeMomentPageStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val stateFlow = MutableStateFlow(ComposeMomentState())
    private val vm = mockk<ComposeMomentViewModel>(relaxed = true).also {
        every { it.hasUnsavedChanges } returns false
        every { it.state } returns stateFlow
        every { it.voiceMessage } returns emptyFlow()
    }
    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var closed = 0
    private lateinit var page: ComposeMomentPageState

    private fun show(pauseClock: Boolean = false) {
        // 计时类用例先停钟：M3 提示条的入场动画会在 waitForIdle 里吃掉几百毫秒虚拟时间，窗口要从它上屏那一帧量起。
        if (pauseClock) compose.mainClock.autoAdvance = false
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.CLAY) {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    page = rememberComposeMomentPageState(vm, onClose = { closed++ })
                    Box(Modifier.fillMaxSize()) { AppSnackbarHost(page.snackbarHostState, Modifier.align(Alignment.BottomCenter)) }
                }
            }
        }
        if (!pauseClock) compose.waitForIdle()
    }

    // ── 纯派生量（卷六·二 T1 保留） ──

    @Test fun 字数上限500是闭区间() {
        assertFalse(ComposeMomentState(content = "字".repeat(500)).overLimit)
        assertTrue(ComposeMomentState(content = "字".repeat(501)).overLimit)
        assertEquals(501, ComposeMomentState(content = "字".repeat(501)).charCount)
    }

    @Test fun 能否发布() {
        assertFalse(ComposeMomentState(content = "  ").canPublish)
        assertTrue(ComposeMomentState(content = "今天晴").canPublish)
        assertFalse(ComposeMomentState(content = "今天晴", publishing = true).canPublish)
        assertFalse(ComposeMomentState(content = "字".repeat(501)).canPublish)
    }

    @Test fun 九张封顶() {
        assertTrue(composeMomentCanAddImage(List(8) { "/$it.jpg" }))
        assertFalse(composeMomentCanAddImage(List(9) { "/$it.jpg" }))
    }

    // ── 关页守卫（E12 / E13） ──

    @Test fun 没改动时关闭直接走() {
        show()
        compose.runOnIdle { page.attemptClose() }
        assertEquals(1, closed)
        assertFalse(page.showKeepDialog)
    }

    @Test fun 有改动时关闭先问保留() {
        every { vm.hasUnsavedChanges } returns true
        show()
        compose.runOnIdle { page.attemptClose() }
        assertEquals(0, closed)
        assertTrue(page.showKeepDialog)
    }

    @Test fun 保留并关闭() {
        show()
        compose.runOnIdle {
            page.showKeepDialog = true
            page.keepAndClose()
        }
        verify(exactly = 1) { vm.keepDraft() }
        verify(exactly = 0) { vm.discard() }
        assertEquals(1, closed)
        assertFalse(page.showKeepDialog)
    }

    @Test fun 不保留并关闭() {
        show()
        compose.runOnIdle {
            page.showKeepDialog = true
            page.discardAndClose()
        }
        verify(exactly = 1) { vm.discard() }
        verify(exactly = 0) { vm.keepDraft() }
        assertEquals(1, closed)
        assertFalse(page.showKeepDialog)
    }

    // ── 弹层 / 查看器 ──

    @Test fun 开选人先刷新可用性再开_关了收起() {
        show()
        compose.runOnIdle { page.openPicker() }
        verifyOrder { vm.refreshMentionAvailability() }
        assertTrue(page.showPicker)
        compose.runOnIdle { page.closePicker() }
        assertFalse(page.showPicker)
    }

    @Test fun 看大图开关() {
        show()
        compose.runOnIdle { page.openViewer("/a.jpg") }
        assertEquals("/a.jpg", page.viewerPath)
        compose.runOnIdle { page.closeViewer() }
        assertNull(page.viewerPath)
    }

    // ── 发布（Y-5） ──

    @Test fun 可发时轻震并交给VM() {
        stateFlow.value = ComposeMomentState(content = "今天晴")
        show()
        compose.runOnIdle { page.publish(haptics) }
        verify(exactly = 1) { haptics.success() }
        verify(exactly = 1) { vm.publish(any()) }
        assertEquals(0, closed) // 关页由 VM 发布成功后回调
    }

    @Test fun 不可发时不震_仍交给VM守门() {
        stateFlow.value = ComposeMomentState(content = "  ")
        show()
        compose.runOnIdle { page.publish(haptics) }
        verify(exactly = 0) { haptics.success() }
        verify(exactly = 1) { vm.publish(any()) }
    }

    // ── 草稿恢复提示（E14） ──

    @Test fun 草稿恢复_提示出现_点清空() {
        stateFlow.value = ComposeMomentState(content = "昨天没写完", restoredFromDraft = true)
        show()
        compose.waitUntil(TIMEOUT_MS) { page.snackbarHostState.currentSnackbarData != null }
        verify(exactly = 1) { vm.consumeDraftRestored() }
        val data = page.snackbarHostState.currentSnackbarData!!
        assertEquals("已接着上次没发完的写", data.visuals.message)
        assertEquals("清空", data.visuals.actionLabel)
        compose.runOnIdle { data.performAction() }
        compose.waitForIdle()
        verify(exactly = 1) { vm.clearAll() }
    }

    @Test fun 草稿恢复_不点则四秒自己收() {
        stateFlow.value = ComposeMomentState(content = "昨天没写完", restoredFromDraft = true)
        show(pauseClock = true)
        var frames = 0
        while (!::page.isInitialized || page.snackbarHostState.currentSnackbarData == null) {
            check(frames++ < FRAME_BUDGET) { "提示条没有上屏" }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(3_900L, ignoreFrameDuration = true)
        compose.waitForIdle()
        assertNotNull("4 秒前还在", page.snackbarHostState.currentSnackbarData)
        compose.mainClock.advanceTimeBy(300L, ignoreFrameDuration = true)
        compose.waitForIdle()
        assertNull("4 秒后自己收", page.snackbarHostState.currentSnackbarData)
        verify(exactly = 0) { vm.clearAll() }
    }

    @Test fun 没草稿不弹提示() {
        show()
        assertNull(page.snackbarHostState.currentSnackbarData)
        verify(exactly = 0) { vm.consumeDraftRestored() }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val FRAME_BUDGET = 30
    }
}
