package com.situ.aichat.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 预测返回原生卡片的几何与曲线 T1（图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §7 T1-1…T1-12；t1_10 / 10b / 10c
 * 随微图纸 2026-09-28-预测返回舞台底 §5 改写：遮罩拆成时间系数 + 按脸浓度表，另加卡边浮现度）。
 *
 * 期望值一律由本测试**自己**按 AOSP 口径另算，绝不调被测函数求期望：
 * - 曲线：自带三次贝塞尔二分求解器（按 x 反求参数 t 再取 y）；EMPHASIZED 用**原始坐标**两段路径，不用实现里的归一控制点。
 * - 几何：按 AOSP `DefaultCrossActivityBackAnimation` 的**矩形**口径（全屏矩形 → 目标矩形按插值进度 lerp，
 *   目标 = 0.9 倍大小、右边贴到离屏幕右缘 margin），再换算成 graphicsLayer 的中心缩放 + 平移，与实现的平移公式不同路。
 */
class BackCardGeometryTest {

    private val w = 1080f
    private val h = 2400f
    private val m = 21f
    private val o = 252f
    private val eps = 1e-3f

    // ---------- 测试自带的曲线求解 ----------

    /** 一般三次贝塞尔（P0..P3）在 x 处的 y：对参数 t 二分（x(t) 单调）。 */
    private fun pathY(x: Double, x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Double {
        fun b(t: Double, a0: Double, a1: Double, a2: Double, a3: Double): Double {
            val u = 1 - t
            return u * u * u * a0 + 3 * u * u * t * a1 + 3 * u * t * t * a2 + t * t * t * a3
        }
        var lo = 0.0
        var hi = 1.0
        repeat(80) {
            val mid = (lo + hi) / 2
            if (b(mid, x0, x1, x2, x3) < x) lo = mid else hi = mid
        }
        return b((lo + hi) / 2, y0, y1, y2, y3)
    }

    /** `PathInterpolator(0.1, 0.1, 0, 1)`。 */
    private fun backGesture(x: Double) = pathY(x, 0.0, 0.0, 0.1, 0.1, 0.0, 1.0, 1.0, 1.0)

    /** `Interpolators.EMPHASIZED` 原始路径：两段 cubicTo，接点 (0.166666, 0.4)。 */
    private fun emphasized(x: Double): Double = if (x <= 0.166666) {
        pathY(x, 0.0, 0.0, 0.05, 0.0, 0.133333, 0.06, 0.166666, 0.4)
    } else {
        pathY(x, 0.166666, 0.4, 0.208333, 0.82, 0.25, 1.0, 1.0, 1.0)
    }

    // ---------- 测试自带的 AOSP 矩形口径 ----------

    /** 手势期期望：矩形 lerp → 中心缩放 + 平移。 */
    private fun expectedGesture(p: Float, dy: Float, rightEdge: Boolean): BackCardFrame {
        val g = backGesture(p.toDouble()).toFloat()
        // 当前页：全屏 (0,0,W) → 目标宽 0.9W；左缘起手右边贴 W − m，右缘起手居中。
        val targetLeft = if (rightEdge) (w - 0.9f * w) / 2f else w - m - 0.9f * w
        val targetRight = targetLeft + 0.9f * w
        val left = 0f + (targetLeft - 0f) * g
        val right = w + (targetRight - w) * g
        val s = (right - left) / w
        val closingTx = (left + right) / 2f - w / 2f
        // getYOffset：纵向位移按半屏归一 → DecelerateInterpolator → 最多移到离边 m。
        val ratio = min(h / 2f, abs(dy)) / (h / 2f)
        val dec = 1f - (1f - ratio) * (1f - ratio)
        val y = max(0f, (h - s * h) / 2f - m) * dec * (if (dy < 0f) -1f else 1f)
        // 上一页：同大小，中心起步左移 o。
        return BackCardFrame(CardTransform(s, closingTx, y), CardTransform(s, -o, y))
    }

    private fun assertCard(msg: String, expected: CardTransform, actual: CardTransform, tol: Float = eps) {
        assertEquals("$msg scale", expected.scale, actual.scale, tol)
        assertEquals("$msg tx", expected.translationX, actual.translationX, tol * 1000f)
        assertEquals("$msg ty", expected.translationY, actual.translationY, tol * 1000f)
        assertEquals("$msg alpha", expected.alpha, actual.alpha, tol)
    }

    // ---------- T1-1 / T1-2 / T1-3 曲线 ----------

    @Test
    fun t1_1_backGesture与自带贝塞尔求解一致() {
        for (x in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            assertEquals("x=$x", backGesture(x).toFloat(), BackCardSpec.BackGesture.transform(x.toFloat()), 1e-3f)
        }
    }

    @Test
    fun t1_2_emphasized与原始坐标两段路径一致() {
        for (x in listOf(0.05, 0.1, 0.166666, 0.3, 0.5, 0.8)) {
            assertEquals("x=$x", emphasized(x).toFloat(), BackCardSpec.Emphasized.transform(x.toFloat()), 2e-3f)
        }
        assertEquals(0f, BackCardSpec.Emphasized.transform(0f), 1e-6f)
        assertEquals(1f, BackCardSpec.Emphasized.transform(1f), 1e-6f)
    }

    @Test
    fun t1_3_decelerate() {
        assertEquals(0f, BackCardSpec.decelerate(0f), 1e-6f)
        assertEquals(0.75f, BackCardSpec.decelerate(0.5f), 1e-6f)
        assertEquals(1f, BackCardSpec.decelerate(1f), 1e-6f)
    }

    // ---------- T1-4 … T1-7 手势期 ----------

    @Test
    fun t1_4_起手帧为恒等() {
        val f = gestureFrame(0f, 0f, false, w, h, m, o)
        assertCard("closing", CardTransform(1f, 0f, 0f, 1f), f.closing)
        assertCard("entering", CardTransform(1f, -o, 0f), f.entering)
    }

    @Test
    fun t1_5_左缘拖满() {
        val f = gestureFrame(1f, 0f, false, w, h, m, o)
        assertCard("closing", CardTransform(0.9f, 33f, 0f), f.closing)
        assertCard("entering", CardTransform(0.9f, -252f, 0f), f.entering)
        // 与矩形口径互证（半程也对）。
        assertCard("closing p=1 rect", expectedGesture(1f, 0f, false).closing, f.closing)
        val half = gestureFrame(0.5f, 0f, false, w, h, m, o)
        assertCard("closing p=0.5 rect", expectedGesture(0.5f, 0f, false).closing, half.closing)
        assertCard("entering p=0.5 rect", expectedGesture(0.5f, 0f, false).entering, half.entering)
    }

    @Test
    fun t1_6_右缘起手只居中缩不横移() {
        val f = gestureFrame(1f, 0f, true, w, h, m, o)
        assertEquals(0.9f, f.closing.scale, eps)
        assertEquals(0f, f.closing.translationX, eps)
        assertEquals(-252f, f.entering.translationX, eps)
        assertCard("closing p=0.5 R rect", expectedGesture(0.5f, 0f, true).closing, gestureFrame(0.5f, 0f, true, w, h, m, o).closing)
    }

    @Test
    fun t1_7_纵向跟手() {
        assertEquals(99f, gestureFrame(1f, 1200f, false, w, h, m, o).closing.translationY, eps)
        assertEquals(99f, gestureFrame(1f, 1200f, false, w, h, m, o).entering.translationY, eps)
        assertEquals(-74.25f, gestureFrame(1f, -600f, false, w, h, m, o).closing.translationY, eps)
        assertEquals(99f, gestureFrame(1f, 5000f, false, w, h, m, o).closing.translationY, eps)
        for (dy in listOf(-5000f, -600f, 0f, 600f, 5000f)) {
            assertEquals("p=0 dy=$dy", 0f, gestureFrame(0f, dy, false, w, h, m, o).closing.translationY, eps)
        }
        assertCard("p=0.4 dy=-300", expectedGesture(0.4f, -300f, false).closing, gestureFrame(0.4f, -300f, false, w, h, m, o).closing)
    }

    // ---------- T1-8 / T1-9 松手确认 ----------

    @Test
    fun t1_8_commitFrame起点等于松手帧_终点全屏右移() {
        val p = 0.5f
        val release = gestureFrame(p, 300f, false, w, h, m, o)
        val expectedRelease = expectedGesture(p, 300f, false)
        val start = commitFrame(release, p, 0f, w, o)
        assertCard("t=0 closing", expectedRelease.closing, start.closing)
        assertCard("t=0 entering", expectedRelease.entering, start.entering)

        val end = commitFrame(release, p, 1f, w, o)
        val s0 = expectedRelease.closing.scale
        val tx0 = expectedRelease.closing.translationX
        assertEquals(1f, end.closing.scale, eps)
        assertEquals(tx0 + (1f - s0) * w / 2f + o, end.closing.translationX, 0.5f)
        assertEquals(0f, end.closing.translationY, 0.5f)
        assertCard("t=1 entering", CardTransform(1f, 0f, 0f), end.entering)
    }

    @Test
    fun t1_8b_中途按EMPHASIZED插值() {
        val p = 0.5f
        val release = gestureFrame(p, 0f, false, w, h, m, o)
        val t = 0.3f
        val e = emphasized(t.toDouble()).toFloat()
        val r = expectedGesture(p, 0f, false)
        val mid = commitFrame(release, p, t, w, o)
        assertEquals(r.entering.scale + (1f - r.entering.scale) * e, mid.entering.scale, 2e-3f)
        assertEquals(-o + o * e, mid.entering.translationX, 1f)
    }

    @Test
    fun t1_9_当前页淡出按余量压缩() {
        // p=0.3：余量 0.7×450−34 = 281ms ≥ 90ms → 原生 1 − 5t。
        assertEquals(0.5f, closingCommitAlpha(0.3f, 0.1f), 1e-4f)
        assertEquals(0.05f, closingCommitAlpha(0.3f, 0.19f), 1e-4f)
        assertEquals(0f, closingCommitAlpha(0.3f, 0.2f), 1e-6f)
        // p=0.9：余量 45 − 34 = 11ms → 5.5ms 时半透、11ms 时全透。
        assertEquals(0.5f, closingCommitAlpha(0.9f, 5.5f / 450f), 1e-4f)
        assertEquals(0f, closingCommitAlpha(0.9f, 11f / 450f), 1e-4f)
        // p=1：余量为负 → 恒 0。
        for (t in listOf(0f, 0.1f, 1f)) assertEquals(0f, closingCommitAlpha(1f, t), 1e-6f)
    }

    // ---------- T1-10 / T1-11 / T1-12 ----------

    @Test
    fun t1_10_遮罩时间系数() {
        // AOSP：手势期 / 取消期恒满值；确认后按线性收尾时间退到 0。
        assertEquals(1f, scrimFraction(BackCardPhase.Gesture, 0f), 1e-6f)
        assertEquals(1f, scrimFraction(BackCardPhase.Gesture, 0.9f), 1e-6f)
        assertEquals(1f, scrimFraction(BackCardPhase.Cancelled, 0.5f), 1e-6f)
        assertEquals(0.5f, scrimFraction(BackCardPhase.Committed, 0.5f), 1e-6f)
        assertEquals(0f, scrimFraction(BackCardPhase.Committed, 1f), 1e-6f)
        assertEquals(0f, scrimFraction(BackCardPhase.Committed, 1.3f), 1e-6f)
    }

    @Test
    fun t1_10b_浓度表逐值对齐对比稿() {
        // 微图纸 2026-09-28-预测返回舞台底 §4（对比稿 predictive_back_stage_mockup.html 用户 09-28 过审）：遮罩 / 舞台加深 / 柔影。
        fun check(t: BackCardTone, scrim: Float, deepen: Float, shadow: Float) {
            assertEquals("$t scrim", scrim, t.scrim, 1e-6f)
            assertEquals("$t deepen", deepen, t.stageDeepen, 1e-6f)
            assertEquals("$t shadow", shadow, t.shadow, 1e-6f)
        }
        check(BackCardTone.LiuliLight, 0.16f, 0.06f, 0.38f)
        check(BackCardTone.LiuliDark, 0.5f, 0.10f, 0.55f) // 深色遮罩 0.5 = 用户 09-25 拍板值，不变
        check(BackCardTone.ClayLight, 0.15f, 0.05f, 0.24f)
        check(BackCardTone.ClayDark, 0.5f, 0.10f, 0.60f)
        assertEquals(BackCardTone.LiuliLight, BackCardTone.of(liuli = true, dark = false))
        assertEquals(BackCardTone.LiuliDark, BackCardTone.of(liuli = true, dark = true))
        assertEquals(BackCardTone.ClayLight, BackCardTone.of(liuli = false, dark = false))
        assertEquals(BackCardTone.ClayDark, BackCardTone.of(liuli = false, dark = true))
    }

    @Test
    fun t1_10c_卡边浮现度() {
        // 浮现度 = 缩小进度（0 = 全屏、1 = 缩到 0.9）× 1.6，封顶 1；静止必须为 0（静止像素零变化的前提）。
        assertEquals(0f, cardEdgeEmergence(1f), 1e-6f)
        assertEquals(0.48f, cardEdgeEmergence(0.97f), 1e-4f)
        assertEquals(0.8f, cardEdgeEmergence(0.95f), 1e-4f)
        assertEquals(1f, cardEdgeEmergence(0.9f), 1e-6f)
        assertEquals(1f, cardEdgeEmergence(0.8f), 1e-6f)
        assertEquals(0f, cardEdgeEmergence(1.02f), 1e-6f)
    }

    @Test
    fun t1_11_圆角逐角取值() {
        assertEquals(CornerRadiiPx(89.25f, 89.25f, 89.25f, 120f), resolveCornerRadii(null, 0, -3, 120, 89.25f))
    }

    @Test
    fun t1_12_backCardTransform按阶段取帧() {
        // Cancelled：进度按 (1 − clock) 退回 → p=0.6、clock=0.5 等于手势 0.3。
        val cancelExpected = expectedGesture(0.3f, 0f, false)
        fun at(role: BackCardRole, phase: BackCardPhase, p: Float, clock: Float) =
            backCardTransform(role, phase, p, clock, 0f, false, w, h, m, o)
        assertCard("cancel closing", cancelExpected.closing, at(BackCardRole.Closing, BackCardPhase.Cancelled, 0.6f, 0.5f))
        assertCard("cancel entering", cancelExpected.entering, at(BackCardRole.Entering, BackCardPhase.Cancelled, 0.6f, 0.5f))
        // Committed clock=0 等于松手帧。
        val release = expectedGesture(0.6f, 0f, false)
        assertCard("commit closing", release.closing, at(BackCardRole.Closing, BackCardPhase.Committed, 0.6f, 0f))
        assertCard("commit entering", release.entering, at(BackCardRole.Entering, BackCardPhase.Committed, 0.6f, 0f))
        // Gesture 期不读 clock。
        assertCard("gesture ignores clock", expectedGesture(0.6f, 0f, false).closing, at(BackCardRole.Closing, BackCardPhase.Gesture, 0.6f, 0.9f))
    }
}
