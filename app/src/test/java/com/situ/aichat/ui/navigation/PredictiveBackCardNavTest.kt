package com.situ.aichat.ui.navigation

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 预测返回原生卡片 T2（图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §7 T2-1…T2-10）。
 *
 * 真 NavHost + 与生产**同一组**占位转场（predictivePop* = BackCardHoldEnter / backCardHoldExit），页面经
 * backCardComposable 注册；手势经 [DirectNavigationEventInput] 直注 Activity 的返回事件分发器。期望矩形由本测试
 * 按 AOSP 矩形口径 + 自带贝塞尔求解**另算**（不调被测几何函数），误差 ≤ 1px。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PredictiveBackCardNavTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private val input = DirectNavigationEventInput()
    private var frameMs = 0L
    private var handled = 0
    private val coords = mutableMapOf<String, LayoutCoordinates>()

    /** 栈顶页静止矩形（px）：W / H 与坐标原点都取它。 */
    private lateinit var rest: Rect
    private val w get() = rest.width
    private val h get() = rest.height
    private val m get() = with(compose.density) { 8.dp.toPx() }
    private val o get() = with(compose.density) { 96.dp.toPx() }

    private fun launch(stack: List<String> = listOf("b"), bExtra: @Composable () -> Unit = {}) {
        compose.setContent {
            val navController = rememberNavController()
            nav = navController
            val motion = rememberPredictiveBackMotion(navController)
            CompositionLocalProvider(LocalPredictiveBackMotion provides motion) {
                NavHost(
                    navController = navController,
                    startDestination = "a",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } },
                    predictivePopEnterTransition = { BackCardHoldEnter },
                    predictivePopExitTransition = { e -> backCardHoldExit(motion, e) },
                ) {
                    backCardComposable("a") { Page("a") }
                    backCardComposable("b") { Page("b") { bExtra() } }
                    backCardComposable("c") { Page("c") }
                }
            }
        }
        compose.runOnUiThread { compose.activity.navigationEventDispatcher.addInput(input) }
        stack.forEach { route -> compose.runOnUiThread { nav.navigate(route) }; compose.waitForIdle() }
        rest = rect(stack.last())
    }

    // ---------- 手势注入 ----------

    private fun event(p: Float, y: Float, edge: Int) =
        NavigationEvent(edge, p, if (edge == NavigationEvent.EDGE_RIGHT) w - 2f else 2f, y, frameMs++)

    /** 推进到画面稳定：自动推钟时 waitForIdle；手动推钟时推 3 帧（重组 → seek → 再重组）。 */
    private fun settle() {
        if (compose.mainClock.autoAdvance) compose.waitForIdle() else repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun gesture(p: Float, edge: Int = NavigationEvent.EDGE_LEFT, dy: Float = 0f) {
        val y0 = rest.center.y
        compose.runOnUiThread { input.backStarted(event(0f, y0, edge)) }
        settle()
        compose.runOnUiThread { input.backProgressed(event(p, y0 + dy, edge)) }
        settle()
    }

    /** 松手：之后一律手动推钟，按毫秒量收尾。 */
    private fun release(complete: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { if (complete) input.backCompleted() else input.backCancelled() }
    }

    // ---------- 量与算 ----------

    @Composable
    private fun Page(tag: String, extra: @Composable () -> Unit = {}) {
        Box(Modifier.fillMaxSize().testTag(tag).onGloballyPositioned { coords[tag] = it }) { extra() }
    }

    /**
     * 页面在根坐标里的**实画**矩形：经祖先 graphicsLayer 的缩放 / 平移映射、不裁切（GlassSliceOffsetTest 范式）。
     * 注：`getUnclippedBoundsInRoot()` 是「变换后位置 + 未缩放尺寸」，量不出卡片宽高，不能用。
     */
    private fun rect(tag: String): Rect {
        compose.onNodeWithTag(tag).assertExists()
        var r = Rect.Zero
        compose.runOnUiThread { val c = coords.getValue(tag); r = c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false) }
        return r
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** 「不显示或不存在」：节点在树里则必须未显示（已弹出页不放置）。 */
    private fun assertNotShown(tag: String) {
        if (exists(tag)) compose.onNodeWithTag(tag).assertIsNotDisplayed()
    }

    /** 自带三次贝塞尔 (0,0)(x1,y1)(x2,y2)(1,1) 按 x 二分求 y。 */
    private fun bezier(x: Float, x1: Double, y1: Double, x2: Double, y2: Double): Float {
        fun b(t: Double, a1: Double, a2: Double) = 3 * (1 - t) * (1 - t) * t * a1 + 3 * (1 - t) * t * t * a2 + t * t * t
        var lo = 0.0
        var hi = 1.0
        repeat(80) { val mid = (lo + hi) / 2; if (b(mid, x1, x2) < x) lo = mid else hi = mid }
        return b((lo + hi) / 2, y1, y2).toFloat()
    }

    /** AOSP 矩形口径另算手势期卡片矩形（closing = 当前页；否则上一页）。 */
    private fun expectedRect(closing: Boolean, p: Float, dy: Float = 0f, rightEdge: Boolean = false): Rect {
        val g = bezier(p, 0.1, 0.1, 0.0, 1.0)
        val targetLeft = if (rightEdge) (w - 0.9f * w) / 2f else w - m - 0.9f * w
        val left = targetLeft * g
        val right = w + (targetLeft + 0.9f * w - w) * g
        val s = (right - left) / w
        val ratio = min(h / 2f, abs(dy)) / (h / 2f)
        val y = max(0f, (h - s * h) / 2f - m) * (1f - (1f - ratio) * (1f - ratio)) * (if (dy < 0f) -1f else 1f)
        val cx = if (closing) (left + right) / 2f else w / 2f - o
        val cy = h / 2f + y
        return Rect(rest.left + cx - s * w / 2f, rest.top + cy - s * h / 2f, rest.left + cx + s * w / 2f, rest.top + cy + s * h / 2f)
    }

    private fun assertRect(msg: String, expected: Rect, actual: Rect) {
        assertEquals("$msg left", expected.left, actual.left, 1f)
        assertEquals("$msg top", expected.top, actual.top, 1f)
        assertEquals("$msg right", expected.right, actual.right, 1f)
        assertEquals("$msg bottom", expected.bottom, actual.bottom, 1f)
    }

    // ---------- 用例 ----------

    @Test
    fun t2_1_左缘拖到半程_两页成卡() {
        launch()
        gesture(0.5f)
        assertRect("b 当前页", expectedRect(closing = true, p = 0.5f), rect("b"))
        assertRect("a 上一页", expectedRect(closing = false, p = 0.5f), rect("a"))
    }

    @Test
    fun t2_2_松手确认_当前页淡完不放置_上一页回全屏() {
        launch()
        gesture(0.5f)
        release(complete = true)
        compose.mainClock.advanceTimeBy(120)
        // 占位真留人（复核 R1 🔴-1）：导航库要到 (1 − 0.5) × 450 = 225ms 后才移除 b，此刻 b 必须还在树上、但已淡完不放置。
        compose.onNodeWithTag("b").assertExists()
        compose.onNodeWithTag("b").assertIsNotDisplayed()
        val a = rect("a")
        assertTrue("a 宽 ${a.width} 应介于 0.9W 与 W 之间", a.width > 0.9f * w + 0.5f && a.width < w - 0.5f)
        compose.mainClock.advanceTimeBy(400)
        assertRect("a 全屏", rest, rect("a"))
        compose.onNodeWithTag("b").assertDoesNotExist()
    }

    @Test
    fun t2_3_松手取消_两页原路退回() {
        launch()
        gesture(0.5f)
        val card = expectedRect(closing = true, p = 0.5f)
        release(complete = false)
        compose.mainClock.advanceTimeBy(120)
        // 取消回退与导航库同步（0.5 × 450 = 225ms）：此刻上一页必须还垫在下面。
        compose.onNodeWithTag("a").assertExists()
        val b = rect("b")
        assertTrue("b 宽 ${b.width} 应介于卡宽 ${card.width} 与 W 之间", b.width > card.width + 0.5f && b.width < w - 0.5f)
        compose.mainClock.advanceTimeBy(200)
        assertRect("b 全屏", rest, rect("b"))
        compose.onNodeWithTag("a").assertDoesNotExist()
    }

    @Test
    fun t2_4_右缘起手_居中缩不横移() {
        launch()
        gesture(1f, edge = NavigationEvent.EDGE_RIGHT)
        val b = rect("b")
        assertEquals(rest.left + (w - 0.9f * w) / 2f, b.left, 1f)
        assertRect("b 右缘卡", expectedRect(closing = true, p = 1f, rightEdge = true), b)
    }

    @Test
    fun t2_5_纵向跟手_最多到离底边8dp() {
        launch()
        gesture(1f, dy = h / 2f)
        val b = rect("b")
        assertEquals(rest.top + h - m, b.bottom, 1f)
        assertRect("b 下移", expectedRect(closing = true, p = 1f, dy = h / 2f), b)
    }

    @Test
    fun t2_6_页面自拦返回_不建会话不缩() {
        launch { BackHandler { handled++ } }
        gesture(0.5f)
        assertRect("b 全屏", rest, rect("b"))
        compose.onNodeWithTag("a").assertDoesNotExist()
        compose.runOnUiThread { input.backCompleted() }
        compose.waitForIdle()
        assertEquals(1, handled)
        assertRect("b 仍全屏", rest, rect("b"))
    }

    @Test
    fun t2_7_页面自拦后自己pop_走普通横滑不叠卡片() {
        launch { BackHandler { nav.popBackStack(); handled++ } }
        gesture(0.5f)
        assertRect("b 手势期全屏", rest, rect("b"))
        release(complete = true)
        compose.mainClock.advanceTimeBy(150)
        assertEquals(1, handled)
        val b = rect("b")
        assertEquals("b 未缩", w, b.width, 1f)
        assertTrue("b 正在横滑（left=${b.left}）", b.left > rest.left + 1f)
    }

    @Test
    fun t2_8_减弱动态效果_不建会话() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        launch()
        gesture(0.5f)
        // 正向证据：手势确已进入导航库的预测返回（上一页已被 seek 进来），只是不画卡片。
        assertTrue("a 应已被预测返回带进来", exists("a"))
        assertRect("b 全屏", rest, rect("b"))
    }

    @Test
    fun t2_9_连续两次快速返回_旧页永不再显示() {
        launch(stack = listOf("b", "c"))
        gesture(0.3f)
        release(complete = true)
        compose.mainClock.advanceTimeBy(50)
        gesture(0.3f)
        assertNotShown("c")
        assertRect("b = 进度 0.3 的卡片", expectedRect(closing = true, p = 0.3f), rect("b"))
    }

    @Test
    fun t2_11_松手后导航库按剩余进度留住当前页() {
        launch()
        gesture(0.3f)
        release(complete = true)
        // (1 − 0.3) × 450 = 315ms 之前 b 必须还在（零时长占位会在约 64ms 就移除它）；之后移除。
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("b").assertExists()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("b").assertDoesNotExist()
    }

    @Test
    fun t2_10_程序化pop_走普通横滑() {
        launch()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.popBackStack() }
        compose.mainClock.advanceTimeBy(150)
        val b = rect("b")
        assertEquals("b 未缩", w, b.width, 1f)
        assertTrue("b 正在横滑（left=${b.left}）", b.left > rest.left + 1f)
    }
}
