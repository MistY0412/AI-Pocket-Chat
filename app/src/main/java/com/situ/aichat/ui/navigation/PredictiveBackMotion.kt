package com.situ.aichat.ui.navigation

import android.os.Build
import android.util.Log
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.situ.aichat.ui.components.rememberReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * 预测返回「原生卡片」会话状态机（图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §3.1 / §4.6）。
 * 两个写口：会话只在导航库选中预测转场时经 [PredictiveBackMotion.onPredictiveSeek] 建（由 backCardHoldExit 调）；
 * 手势数据只取自 `NavigationEventDispatcher.transitionState`（只读旁听·不抢事件）。本类不做任何导航动作。
 */

private const val TAG = "BackCard"

/** 一次「导航库真的在做预测返回」的会话——只在导航库选中预测转场时由 [PredictiveBackMotion.onPredictiveSeek] 建。 */
@Stable
internal class BackCardSession(
    val closingId: String,
    val enteringId: String,
    val rightEdge: Boolean,
    private val startTouchY: Float,
    val corners: CornerRadiiPx,
) {
    val shape: Shape = AbsoluteRoundedCornerShape(
        topLeft = corners.topLeft, topRight = corners.topRight,
        bottomRight = corners.bottomRight, bottomLeft = corners.bottomLeft,
    )
    var phase by mutableStateOf(BackCardPhase.Gesture)
    /** 原始手势进度 0..1；松手后冻结 = 松手进度。 */
    var progress by mutableFloatStateOf(0f)
    var touchDeltaY by mutableFloatStateOf(0f)
    /** 收尾时钟（每会话一只·图纸 §0.2-3）：确认 0→1 线性 450ms；取消 0→1 over 进度×450ms FastOutSlowIn。 */
    val clock = Animatable(0f)

    fun update(event: NavigationEvent) {
        progress = event.progress.coerceIn(0f, 1f)
        touchDeltaY = event.touchY - startTouchY
    }

    fun roleOf(entryId: String): BackCardRole? = when (entryId) {
        closingId -> BackCardRole.Closing
        enteringId -> BackCardRole.Entering
        else -> null
    }
}

@Stable
internal class PredictiveBackMotion(
    private val navController: NavController,
    private val dispatcher: NavigationEventDispatcher,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
    private val readCorners: () -> CornerRadiiPx,
) {
    var session: BackCardSession? by mutableStateOf(null)
        private set

    /** 已确认弹出的页：永不再显示（图纸 §0.2-4·连按两次返回防旧页闪回）。 */
    private val poppedIds = mutableStateMapOf<String, Unit>()

    /** 唯一建会话口：导航库选中预测转场的同一刻（经 [backCardHoldExit]）。 */
    fun onPredictiveSeek(closing: NavBackStackEntry, entering: NavBackStackEntry, swipeEdge: Int) {
        if (reduceMotion) return
        val current = session
        if (current != null && current.phase == BackCardPhase.Gesture && current.closingId == closing.id) return
        val event = (dispatcher.transitionState.value as? NavigationEventTransitionState.InProgress)?.latestEvent
        val corners = readCorners()
        session = BackCardSession(
            closingId = closing.id,
            enteringId = entering.id,
            rightEdge = swipeEdge == NavigationEvent.EDGE_RIGHT,
            startTouchY = event?.touchY ?: 0f,
            corners = corners,
        ).also { s -> event?.let(s::update) }
        Log.d(TAG, "start ${closing.destination.route} -> ${entering.destination.route} edge=${if (swipeEdge == NavigationEvent.EDGE_RIGHT) "R" else "L"} corners=$corners")
    }

    /** 旁听返回事件（只读·不抢事件）。 */
    fun onTransitionState(state: NavigationEventTransitionState) {
        val s = session?.takeIf { it.phase == BackCardPhase.Gesture } ?: return
        when (state) {
            is NavigationEventTransitionState.InProgress ->
                if (state.direction == NavigationEventTransitionState.TRANSITIONING_BACK) s.update(state.latestEvent)
            NavigationEventTransitionState.Idle -> finish(s)
        }
    }

    private fun finish(s: BackCardSession) {
        // 导航库先 pop 后置 Idle（NavigationEventProcessor.dispatchOnCompleted）→ 栈顶变了 = 确认。
        if (navController.currentBackStackEntry?.id != s.closingId) {
            s.phase = BackCardPhase.Committed
            poppedIds[s.closingId] = Unit
            Log.d(TAG, "commit p=${s.progress}")
            scope.launch {
                s.clock.animateTo(1f, tween(BackCardSpec.COMMIT_MS, easing = LinearEasing))
                if (session === s) session = null
            }
        } else {
            s.phase = BackCardPhase.Cancelled
            Log.d(TAG, "cancel p=${s.progress}")
            scope.launch {
                // 与 NavHost 取消回退同口径：tween((fraction × 总时长).toInt())·默认 FastOutSlowIn（NavHost.kt:950）。
                s.clock.animateTo(1f, tween((s.progress * BackCardSpec.HOLD_MS).toInt(), easing = FastOutSlowInEasing))
                if (session === s) session = null
            }
        }
    }

    fun forget(entryId: String) { poppedIds.remove(entryId) }

    fun transformFor(entryId: String, width: Float, height: Float, marginPx: Float, enteringOffsetPx: Float): CardTransform? {
        val s = session ?: return null
        val role = s.roleOf(entryId) ?: return null
        return backCardTransform(role, s.phase, s.progress, s.clock.value, s.touchDeltaY, s.rightEdge, width, height, marginPx, enteringOffsetPx)
    }

    fun shapeFor(entryId: String): Shape? = session?.takeIf { it.roleOf(entryId) != null }?.shape

    /** 本页在当前会话里的角色；没有会话或与本页无关 = null（舞台 / 柔影 / 白边一律挂在它上面，静止零绘制）。 */
    fun roleFor(entryId: String): BackCardRole? = session?.roleOf(entryId)

    /** 遮罩与舞台加深的时间系数：只有上一页（Entering）有，其余 0。 */
    fun scrimFractionFor(entryId: String): Float {
        val s = session ?: return 0f
        return if (s.roleOf(entryId) == BackCardRole.Entering) scrimFraction(s.phase, s.clock.value) else 0f
    }

    /** 柔影圆角：四角平均 × 最小缩放，整场会话取常数（Skia 圆角矩形模糊按圆角 + 模糊缓存，逐帧变圆角会反复重算）。 */
    fun shadowCornerPx(): Float {
        val c = session?.corners ?: return 0f
        return (c.topLeft + c.topRight + c.bottomRight + c.bottomLeft) / 4f * BackCardSpec.MAX_SCALE
    }

    /** 已弹出的页：除「本会话正在淡出的那张」外一律不放置（不可见也不可点·图纸 §0.2-4/5）。 */
    fun isHidden(entryId: String): Boolean {
        if (!poppedIds.containsKey(entryId)) return false
        val s = session ?: return true
        if (s.closingId != entryId || s.phase != BackCardPhase.Committed) return true
        return closingCommitAlpha(s.progress, s.clock.value) <= 0f
    }
}

@Composable
internal fun rememberPredictiveBackMotion(navController: NavController): PredictiveBackMotion {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "PredictiveBackMotion requires a NavigationEventDispatcherOwner"
    }.navigationEventDispatcher
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val motion = remember(navController, dispatcher, view, scope, reduceMotion) {
        PredictiveBackMotion(navController, dispatcher, scope, reduceMotion, readCorners = { readScreenCornerRadii(view) })
    }
    LaunchedEffect(motion, dispatcher) { dispatcher.transitionState.collect { motion.onTransitionState(it) } }
    return motion
}

internal val LocalPredictiveBackMotion = staticCompositionLocalOf<PredictiveBackMotion?> { null }

/** 屏幕真实圆角（API 31+ `WindowInsets.getRoundedCorner`·窗口坐标·逐角）；读不到用默认 34dp。 */
internal fun readScreenCornerRadii(view: View): CornerRadiiPx {
    val defaultPx = BackCardSpec.DEFAULT_CORNER_RADIUS_DP * view.resources.displayMetrics.density
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return resolveCornerRadii(null, null, null, null, defaultPx)
    return readRoundedCornersApi31(view, defaultPx)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun readRoundedCornersApi31(view: View, defaultPx: Float): CornerRadiiPx {
    val insets = view.rootWindowInsets
    fun r(position: Int): Int? = insets?.getRoundedCorner(position)?.radius
    return resolveCornerRadii(
        r(RoundedCorner.POSITION_TOP_LEFT), r(RoundedCorner.POSITION_TOP_RIGHT),
        r(RoundedCorner.POSITION_BOTTOM_RIGHT), r(RoundedCorner.POSITION_BOTTOM_LEFT), defaultPx,
    )
}
