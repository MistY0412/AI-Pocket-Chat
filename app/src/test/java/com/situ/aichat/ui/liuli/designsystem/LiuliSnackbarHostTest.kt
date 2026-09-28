package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2：Snackbar 玻璃 pill（图纸 2026-09-06 卷五 A-4 ④·§8 C0）。
 *
 * 钉三件：`showSnackbar` 后正文真上屏 · 有动作时点动作回 [SnackbarResult.ActionPerformed] · 无队列时
 * 一个节点都不渲染（默认态对照·防「不管有没有都画一枚空 pill」的假绿）。
 *
 * 队列走 M3 [SnackbarHostState]（§9 ⑤ 允许它当纯数据结构），长相全自画。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliSnackbarHostTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var host: SnackbarHostState
    private lateinit var scope: CoroutineScope
    private var result: SnackbarResult? = null

    private fun mount() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    host = androidx.compose.runtime.remember { SnackbarHostState() }
                    scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize()) {
                        LiuliSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun 空队列时什么都不画() {
        mount()
        assertEquals(0, compose.onAllNodesWithText("已保存").fetchSemanticsNodes().size)
        assertNull(host.currentSnackbarData)
    }

    @Test fun 正文上屏() {
        mount()
        scope.launch { host.showSnackbar("已保存") }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("已保存").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已保存").assertExists()
    }

    @Test fun 点动作回ActionPerformed() {
        mount()
        scope.launch { result = host.showSnackbar(message = "已删除", actionLabel = "撤销") }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(TIMEOUT_MS) { result != null }
        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    /**
     * 动作字与正文同一条中线（朋友圈发布页·乙 复核 R1·§11 O-5）：48 触达撑的是点击盒，动作字本身不被撑高——被撑到 48 的
     * Text 会把字画在盒顶，比正文高出约 12dp。断言：两段字的版高相同、竖直中心相同。
     */
    @Test fun 动作字不被触达撑高_与正文同中线() {
        mount()
        scope.launch { host.showSnackbar(message = "已接着上次没发完的写", actionLabel = "清空", duration = SnackbarDuration.Indefinite) }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("清空").fetchSemanticsNodes().isNotEmpty() }
        val body = compose.onNodeWithText("已接着上次没发完的写", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val action = compose.onNodeWithText("清空", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("版高", (body.bottom - body.top).value, (action.bottom - action.top).value, 0.5f)
        assertEquals("中线", ((body.top + body.bottom) / 2).value, ((action.top + action.bottom) / 2).value, 0.5f)
    }

    // ── 自动收起（朋友圈发布页·乙 L-4·T2-3）：时长从图纸 §3.2 独立反推 = M3 1.4.0 的 4 000 / 10 000ms。 ──

    /** 暂停主时钟后入队一条，推帧直到它成为当前条（计时起点 = 它上屏那一帧的 LaunchedEffect）。 */
    private fun showPaused(show: suspend () -> Unit) {
        mount()
        compose.mainClock.autoAdvance = false
        scope.launch { show() }
        repeat(FRAME_BUDGET) {
            if (host.currentSnackbarData != null) return
            compose.mainClock.advanceTimeByFrame()
        }
        error("提示条没有上屏")
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
        compose.waitForIdle()
    }

    @Test fun Short四秒后自己收起() {
        showPaused { host.showSnackbar("已保存", duration = SnackbarDuration.Short) }
        advance(3_999L)
        assertNotNull("3 999ms 时还应在", host.currentSnackbarData)
        advance(1L)
        assertNull("4 000ms 到点应收起", host.currentSnackbarData)
    }

    @Test fun 带动作的Indefinite十秒后仍在() {
        showPaused { host.showSnackbar(message = "已删除", actionLabel = "撤销") }
        assertEquals(SnackbarDuration.Indefinite, host.currentSnackbarData?.visuals?.duration)
        advance(10_000L)
        advance(1_000L)
        assertNotNull("Indefinite 不自动收起", host.currentSnackbarData)
    }

    @Test fun Long十秒后收起() {
        showPaused { host.showSnackbar("已保存", duration = SnackbarDuration.Long) }
        advance(9_999L)
        assertNotNull("9 999ms 时还应在", host.currentSnackbarData)
        advance(1L)
        assertNull("10 000ms 到点应收起", host.currentSnackbarData)
    }

    private companion object {
        /** 等提示条上屏最多推这么多帧。 */
        const val FRAME_BUDGET = 20

        /** 内容经真协程到达，断言自带的 waitForIdle 吃不住（PITFALLS §1e）——一律显式 waitUntil。 */
        const val TIMEOUT_MS = 5_000L
    }
}
