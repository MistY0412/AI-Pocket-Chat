package com.situ.aichat.ui.liuli.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * T2-8（琉璃 2.0 卷四 §3.5 / §7）：[liuliPressLight] 跟手按压光。纯红底上 80×80dp 的块（mdpi：1dp = 1px），
 * 逐像素量：按下处变亮、光斑跟手、松手淡回纯红、禁用不亮、外层 clickable 恰收一次点击（不消费）、减弱动画一帧即满亮。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class LiuliPressLightTest {

    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null
    private var origin = Offset.Zero
    private var clicks = 0

    @After fun restoreAnimatorScale() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun host(enabled: Boolean = true) {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .onGloballyPositioned { origin = it.positionInRoot() }
                        .testTag("light")
                        // 无指示（涟漪按下会压暗红底、干扰像素判读）。
                        .clickable(interactionSource = null, indication = null) { clicks++ }
                        .background(Color.Red)
                        .liuliPressLight(dark = false, enabled = enabled),
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun shot(): Bitmap {
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp
    }

    private fun Bitmap.at(xDp: Float, yDp: Float): Triple<Int, Int, Int> {
        val p = getPixel((origin.x + xDp).toInt(), (origin.y + yDp).toInt())
        return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
    }

    private fun Triple<Int, Int, Int>.sum() = first + second + third

    private fun assertPureRed(px: Triple<Int, Int, Int>, label: String) =
        assertTrue("$label 应为纯红 ±3（实测 $px）", abs(px.first - 255) <= 3 && px.second <= 3 && px.third <= 3)

    /** 暂停时钟下注入指针后先推一帧（重组读到按下 / 抬起），动画从下一帧起算。 */
    private fun input(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit, thenMs: Long) {
        compose.onNodeWithTag("light").performTouchInput(block)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(thenMs)
    }

    @Test fun pressLight_followsFinger_fadesOut_andDoesNotConsume() {
        host()
        input({ down(Offset(20f, 20f)) }, thenMs = 200)
        val pressed = shot()
        val near = pressed.at(20f, 20f)
        val far = pressed.at(70f, 70f)
        assertTrue("按下处 $near 应比远处 $far 亮 ≥ 40", near.sum() - far.sum() >= 40)
        assertPureRed(far, "按住时光斑外 (70,70)")

        input({ moveTo(Offset(60f, 60f)) }, thenMs = 50)
        val moved = shot()
        assertTrue("光斑跟手：(60,60) ${moved.at(60f, 60f)} 应比 (20,20) ${moved.at(20f, 20f)} 亮", moved.at(60f, 60f).sum() > moved.at(20f, 20f).sum())

        input({ moveTo(Offset(40f, 40f)) }, thenMs = 0)
        input({ up() }, thenMs = 400)
        val released = shot()
        assertPureRed(released.at(20f, 20f), "松手后 (20,20)")
        assertPureRed(released.at(60f, 60f), "松手后 (60,60)")
        assertPureRed(released.at(40f, 40f), "松手后 (40,40)")
        assertEquals("外层 clickable 恰收一次点击（按压光只观察不消费）", 1, clicks)
    }

    @Test fun disabled_neverLightsUp() {
        host(enabled = false)
        input({ down(Offset(20f, 20f)) }, thenMs = 200)
        assertPureRed(shot().at(20f, 20f), "禁用按下处")
    }

    /** 按下后推两帧（一帧重组 + 一帧动画）时的光斑中心 G 通道。 */
    private fun greenAfterTwoFrames(): Int {
        host()
        compose.onNodeWithTag("light").performTouchInput { down(Offset(20f, 20f)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        return shot().at(20f, 20f).second
    }

    @Test fun normalMotion_fadesIn_notFullAfterTwoFrames() {
        val g = greenAfterTwoFrames()
        // 对照：淡入 90ms，两帧时远不到满亮（满亮 ≈ 128）。
        assertTrue("常规动效两帧时应未满亮（实测 G=$g）", g < 100)
    }

    @Test fun reduceMotion_fullyLitAfterTwoFrames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val g = greenAfterTwoFrames()
        // 满亮 = 白 50% 覆红 ≈ (255, 128, 128)：减弱动画不淡入、直接亮。
        assertTrue("减弱动画两帧即满亮（实测 G=$g）", g >= 118)
    }
}
