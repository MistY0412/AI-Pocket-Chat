package com.situ.aichat.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 预测返回「原生卡片」的全部参数与几何（纯函数·无 Android 依赖·T1 可测）。
 * 出处 = AOSP WMShell `CrossActivityBackAnimation.kt` / `DefaultCrossActivityBackAnimation.kt`（系统「Activity 之间
 * 返回」动画）+ `Shell/res/values/dimen.xml` + `com.android.app.animation.Interpolators`。
 * 图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §4。
 */
internal object BackCardSpec {
    const val MAX_SCALE = 0.9f
    const val MARGIN_DP = 8f
    const val ENTERING_START_OFFSET_DP = 96f
    const val COMMIT_MS = 450
    /** 导航占位转场时长 = 收尾时长：松手后导航库再留两页 (1 − p) × HOLD_MS（`SeekableTransitionState.animateTo` 线性走完剩余）。 */
    const val HOLD_MS = COMMIT_MS
    /**
     * 出场占位的终点透明度：必须 ≠ 1。初值 = 终值时 Transition 跳过更新（`updateTargetValue` 提前返回），tween 装不进去、
     * 时长为 0，导航库松手后约 64ms 就移除当前页（复核 R1 🔴-1）。0.999 = 0.1% 透明，肉眼不可辨。
     */
    const val HOLD_EXIT_ALPHA = 0.999f
    const val CLOSING_FADE_RATE = 5f
    const val FADE_SAFETY_MS = 34f
    const val SCRIM_ALPHA_LIGHT = 0.2f
    /** 深色模式遮罩：用户 2026-09-25 拍板调轻到 0.5（安卓原生 `MAX_SCRIM_ALPHA_DARK` = 0.8，后面那页压得太暗）。 */
    const val SCRIM_ALPHA_DARK = 0.5f
    const val DEFAULT_CORNER_RADIUS_DP = 34f

    /** `Interpolators.BACK_GESTURE` = PathInterpolator(0.1, 0.1, 0, 1)。 */
    val BackGesture: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    // EMPHASIZED 两段：各段按自身跨度归一成标准三次贝塞尔（逐轴仿射缩放不改曲线）。
    // 段 1 (0,0)→(0.166666,0.4)，控制点 (0.05,0)(0.133333,0.06) → 归一 (0.3,0)(0.8,0.15)
    // 段 2 (0.166666,0.4)→(1,1)，控制点 (0.208333,0.82)(0.25,1) → 归一 (0.05,0.7)(0.1,1)
    private const val EMPH_JOINT_X = 0.166666f
    private const val EMPH_JOINT_Y = 0.4f
    private val emphSeg1 = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val emphSeg2 = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** `Interpolators.EMPHASIZED`（= fast_out_extra_slow_in）。 */
    val Emphasized: Easing = Easing { x ->
        if (x <= EMPH_JOINT_X) {
            EMPH_JOINT_Y * emphSeg1.transform(x / EMPH_JOINT_X)
        } else {
            EMPH_JOINT_Y + (1f - EMPH_JOINT_Y) * emphSeg2.transform((x - EMPH_JOINT_X) / (1f - EMPH_JOINT_X))
        }
    }

    /** `android.view.animation.DecelerateInterpolator()`（factor 1）：1 − (1 − x)²。 */
    fun decelerate(x: Float): Float = 1f - (1f - x) * (1f - x)
}

/** 一张卡在某一帧的变换（graphicsLayer 口径：transformOrigin = 中心；平移单位 px）。 */
internal data class CardTransform(val scale: Float, val translationX: Float, val translationY: Float, val alpha: Float = 1f)

internal data class BackCardFrame(val closing: CardTransform, val entering: CardTransform)

internal data class CornerRadiiPx(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float)

internal enum class BackCardRole { Closing, Entering }

internal enum class BackCardPhase { Gesture, Committed, Cancelled }

/** 手势期（AOSP `onGestureProgress` + `getYOffset`）。[progress] 原始进度；[touchDeltaY] = 当前手指 Y − 起手 Y。 */
internal fun gestureFrame(
    progress: Float, touchDeltaY: Float, rightEdge: Boolean,
    width: Float, height: Float, marginPx: Float, enteringOffsetPx: Float,
): BackCardFrame {
    val g = BackCardSpec.BackGesture.transform(progress.coerceIn(0f, 1f))
    val scale = 1f - (1f - BackCardSpec.MAX_SCALE) * g
    val closingX = if (rightEdge) 0f else g * (width * (1f - BackCardSpec.MAX_SCALE) / 2f - marginPx)
    val half = height / 2f
    val ratio = if (half > 0f) min(half, abs(touchDeltaY)) / half else 0f
    val direction = if (touchDeltaY < 0f) -1f else 1f
    val y = max(0f, (height - scale * height) / 2f - marginPx) * BackCardSpec.decelerate(ratio) * direction
    return BackCardFrame(
        closing = CardTransform(scale, closingX, y),
        entering = CardTransform(scale, -enteringOffsetPx, y),
    )
}

/** 松手确认后第 [t]（0..1·线性时间）帧（AOSP `onGestureCommitted` + `onPostCommitProgress`·不含甩动回弹）。 */
internal fun commitFrame(
    release: BackCardFrame, releaseProgress: Float, t: Float, width: Float, enteringOffsetPx: Float,
): BackCardFrame {
    val lt = t.coerceIn(0f, 1f)
    val e = BackCardSpec.Emphasized.transform(lt)
    val c = release.closing
    // 目标 = 全屏大小、左边 = 松手时左边 + 96dp → 平移 = 松手左边 + 96dp
    val closingTargetX = c.translationX + (1f - c.scale) * width / 2f + enteringOffsetPx
    val n = release.entering
    return BackCardFrame(
        closing = CardTransform(
            lerp(c.scale, 1f, e), lerp(c.translationX, closingTargetX, e), lerp(c.translationY, 0f, e),
            alpha = closingCommitAlpha(releaseProgress, lt),
        ),
        entering = CardTransform(lerp(n.scale, 1f, e), lerp(n.translationX, 0f, e), lerp(n.translationY, 0f, e)),
    )
}

/**
 * 当前页收尾淡出：原生 1 − 5t（90ms 全透明）。导航库只再留旧页 (1 − p) × HOLD_MS，扣 [BackCardSpec.FADE_SAFETY_MS]
 * 后不足 90ms 则按余量压缩——保证被移除前已全透明（图纸 §0.2-7）。
 */
internal fun closingCommitAlpha(releaseProgress: Float, t: Float): Float {
    val nativeFadeMs = BackCardSpec.COMMIT_MS / BackCardSpec.CLOSING_FADE_RATE
    val lifetimeMs = (1f - releaseProgress.coerceIn(0f, 1f)) * BackCardSpec.HOLD_MS - BackCardSpec.FADE_SAFETY_MS
    val fadeMs = min(nativeFadeMs, lifetimeMs)
    if (fadeMs <= 0f) return 0f
    return max(0f, 1f - t.coerceIn(0f, 1f) * BackCardSpec.COMMIT_MS / fadeMs)
}

/** 遮罩（AOSP：手势期恒为满值；确认后 满值 × (1 − 线性进度)）。 */
internal fun scrimAlpha(phase: BackCardPhase, t: Float, isDark: Boolean): Float {
    val full = if (isDark) BackCardSpec.SCRIM_ALPHA_DARK else BackCardSpec.SCRIM_ALPHA_LIGHT
    return if (phase == BackCardPhase.Committed) full * (1f - t.coerceIn(0f, 1f)) else full
}

/** 某页在某阶段的变换（图纸 §3.2 表）。[clock] = 会话收尾时钟 0..1（Gesture 期不读）。 */
internal fun backCardTransform(
    role: BackCardRole, phase: BackCardPhase, progress: Float, clock: Float, touchDeltaY: Float, rightEdge: Boolean,
    width: Float, height: Float, marginPx: Float, enteringOffsetPx: Float,
): CardTransform {
    val frame = when (phase) {
        BackCardPhase.Gesture ->
            gestureFrame(progress, touchDeltaY, rightEdge, width, height, marginPx, enteringOffsetPx)
        BackCardPhase.Cancelled ->
            gestureFrame(progress * (1f - clock), touchDeltaY, rightEdge, width, height, marginPx, enteringOffsetPx)
        BackCardPhase.Committed -> commitFrame(
            gestureFrame(progress, touchDeltaY, rightEdge, width, height, marginPx, enteringOffsetPx),
            progress, clock, width, enteringOffsetPx,
        )
    }
    return if (role == BackCardRole.Closing) frame.closing else frame.entering
}

/** 屏幕圆角逐角取值：读不到（null）或 ≤ 0 用默认值（用户拍板：识别不到就用默认圆角）。 */
internal fun resolveCornerRadii(topLeft: Int?, topRight: Int?, bottomRight: Int?, bottomLeft: Int?, defaultPx: Float): CornerRadiiPx {
    fun pick(r: Int?): Float = if (r != null && r > 0) r.toFloat() else defaultPx
    return CornerRadiiPx(pick(topLeft), pick(topRight), pick(bottomRight), pick(bottomLeft))
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
