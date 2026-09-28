package com.situ.aichat.ui.liuli.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 卷四复核 R1：按压提亮只亮形状内（[liuliPressBrighten] 挂在形状裁切之后）。原先提亮画满整个 48dp 触达框，深色下按住圆钮 /
 * 胶囊钮，形状外亮出一块方角（装机 `t4_6d_presslight_dark_ab`）。深色、mdpi（1dp = 1px）、深灰底上量：按住时形状外的角落
 * 与松开时同色（±4·缩 0.96 会让 2dp 小影挪一两级），形状内变亮。旧写法下角落会亮 ~18 级。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
class LiuliPressBrightenTest {

    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null
    private var bounds = Rect.Zero

    private fun host(content: @Composable (Modifier) -> Unit) {
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
                        content(Modifier.testTag("btn").onGloballyPositioned { bounds = it.boundsInRoot() })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun shot(): Bitmap {
        val v = checkNotNull(view)
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }

    private fun Bitmap.sum(x: Float, y: Float): Int {
        val p = getPixel(x.toInt(), y.toInt())
        return ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
    }

    /** 松开 / 按住 300ms 两张。 */
    private fun releasedAndPressed(): Pair<Bitmap, Bitmap> {
        val released = shot()
        compose.onNodeWithTag("btn").performTouchInput { down(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(300)
        return released to shot()
    }

    @Test fun circleButton_pressed_cornerOutsideCircleStaysDark() {
        host { m -> LiuliCircleButton(onClick = {}, contentDescription = "加", modifier = m) { Text("+") } }
        val (released, pressed) = releasedAndPressed()
        // 48dp 触达框的左上角内 5px：离圆心 ≈ 26.9 > 圆半径 20 → 在圆外。
        val cx = bounds.left + 5f
        val cy = bounds.top + 5f
        val delta = pressed.sum(cx, cy) - released.sum(cx, cy)
        assertTrue("圆外角落按下后不该变亮（Δ=$delta）", abs(delta) <= 12)
        val mid = bounds.center
        assertTrue("圆内按下应变亮", pressed.sum(mid.x - 10f, mid.y) - released.sum(mid.x - 10f, mid.y) >= 12)
    }

    @Test fun prominentButton_pressed_touchMarginStaysDark() {
        host { m -> LiuliButton(onClick = {}, style = LiuliButtonStyle.Prominent, modifier = m) { Text("保存") } }
        val (released, pressed) = releasedAndPressed()
        // 视觉 40 高、触达 48：胶囊上沿外 2px 属触达留白。
        val x = bounds.center.x
        val y = bounds.top + 2f
        val delta = pressed.sum(x, y) - released.sum(x, y)
        assertTrue("胶囊外的触达留白按下后不该变亮（Δ=$delta）", abs(delta) <= 12)
    }
}
