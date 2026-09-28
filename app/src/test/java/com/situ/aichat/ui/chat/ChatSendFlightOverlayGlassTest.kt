package com.situ.aichat.ui.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.ui.designsystem.LightAppColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * T2-5（琉璃 2.0 卷三 §7·由琉璃旧 `LiuliSendFlightOverlayTest` 改写）：卷三起琉璃复用暖陶飞行渲染层，
 * 起点是玻璃输入胶囊（`glassSourceTextColor` 非 null）。
 *
 * 位置断言从**规格**反推：飞行泡帧矩形由 `flightFrame(起点, 终点, …)` 给，文字画在帧的内边距处——起点 16 / 10
 * （输入胶囊）、终点 12 / 8（暖陶泡）。进度 0 时源文字左上 = 起点 + (16, 10)、落地帧 = 终点 + (12, 8)。
 * 新例（NATIVE 量像素）：玻璃起点第 1 帧不画 sunken 底（胶囊区 = 身后底色）；`glassSourceTextColor = null` 的
 * 同设置下该处 = `surface.sunken`（负向对照即该例本身）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class ChatSendFlightOverlayGlassTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = ChatSendFlightState(nowMs = { NOW })
    private val startRect = Rect(60f, 800f, 380f, 844f)
    private val targetRect = Rect(160f, 300f, 380f, 340f)

    private fun message(text: String) = MessageEntity(
        messageUUID = "m1",
        conversationUuid = "c",
        roleRaw = "user",
        content = text,
        timestamp = NOW,
    )

    /** 起飞先于组合 → 首帧即进度 0。[glass] = 玻璃起点的源文字色（null = 暖陶原路径）。 */
    private fun setFlying(
        text: String = "在吗",
        glass: Color? = Color.Black,
        tone: BubbleStampTone? = null,
        onView: (View) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        state.inputBounds = startRect
        state.beginFlight(message(text), targetRect)
        compose.setContent {
            onView(LocalView.current)
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                CompositionLocalProvider(LocalBubbleStampTone provides tone) {
                    ChatSendFlightOverlay(state, wallpaper = null, glassSourceTextColor = glass)
                }
            }
        }
        compose.mainClock.advanceTimeBy(1)
    }

    private fun copyTopLeft() =
        compose.onAllNodesWithText("在吗")[0].getUnclippedBoundsInRoot().let { it.left.value to it.top.value }

    @Test fun flightCopy_startsOnInputCapsule_landsOnBubble_thenHandsBackToRealRow() {
        setFlying()
        val (x0, y0) = copyTopLeft()
        assertEquals("进度 0：文字左缘 = 输入胶囊左 + 16dp 内边距", startRect.left + 16f, x0, 1f)
        assertEquals("进度 0：文字上缘 = 输入胶囊顶 + 10dp 内边距", startRect.top + 10f, y0, 1f)

        compose.mainClock.advanceTimeBy(FLIGHT_MS - 2L)
        val (x1, y1) = copyTopLeft()
        assertEquals("落地：文字左缘 = 目标气泡左 + 12dp 内边距", targetRect.left + 12f, x1, 1f)
        assertEquals("落地：文字上缘 = 目标气泡顶 + 8dp 内边距", targetRect.top + 8f, y1, 1f)
        assertNull("飞完必须交还真行（finally endFlight），否则真行永远 alpha 0", state.flight)

        compose.mainClock.advanceTimeBy(FRAME_MS)
        compose.onAllNodesWithText("在吗").assertCountEquals(0)
    }

    @Test fun flightCopy_leavesTheCapsuleButIsNotThereYetMidFlight() {
        setFlying()
        val (_, y0) = copyTopLeft()
        compose.mainClock.advanceTimeBy(FRAME_MS * 4)
        val (_, yMid) = copyTopLeft()
        assertTrue("飞行中应已离开输入胶囊（y0=$y0 mid=$yMid）", yMid < y0)
        assertTrue("但中途还没落到目标（mid=$yMid target=${targetRect.top}）", yMid > targetRect.top)
    }

    @Test fun launchGate_letsTenLinesFly_butNotEleven() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) { ChatSendFlightOverlay(state, wallpaper = null, glassSourceTextColor = Color.Black) }
        }
        compose.waitForIdle()
        state.inputBounds = startRect
        val launch = requireNotNull(state.onLaunch) { "覆盖层必须装上 onLaunch（DisposableEffect）" }
        val pending = PendingSendFlight("x", NOW) {}
        compose.runOnUiThread { launch(message(lines(10)), targetRect, pending) }
        assertNotNull("10 行 = 闸上限，应当起飞", state.flight)
        compose.runOnUiThread { state.endFlight() }
        compose.runOnUiThread { launch(message(lines(11)), targetRect, pending) }
        assertNull("11 行越闸 → 不起飞，静默走普通入场", state.flight)
    }

    /** 第 1 帧胶囊右段（远离文字与圆角）的像素。 */
    private fun firstFramePixel(glass: Color?): Int {
        var view: View? = null
        setFlying(glass = glass) { view = it }
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp.getPixel((startRect.right - 40f).toInt(), startRect.center.y.toInt())
    }

    private fun assertRgb(expected: Int, actual: Int, label: String) {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        val ok = listOf(16, 8, 0).all { abs(ch(expected, it) - ch(actual, it)) <= 2 }
        assertTrue("$label：实测 #${Integer.toHexString(actual)}，期望 ≈ #${Integer.toHexString(expected)}", ok)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun glassStart_doesNotPaintSunkenFill() {
        assertRgb(Color.Red.toArgb(), firstFramePixel(glass = Color.Black), "玻璃起点第 1 帧：胶囊区 = 身后底色")
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun warmStart_paintsSunkenFill_negativeControl() {
        assertRgb(LightAppColors.surface.sunken.toArgb(), firstFramePixel(glass = null), "暖陶起点第 1 帧：胶囊区 = sunken")
    }

    /**
     * T2-4（卷四 §3.7）：飞入时间戳读 [LocalBubbleStampTone]。时间戳在飞行前 40% 由透明渐显（`flightAlphaRamp`），
     * 故取 200ms（t = 0.8·已全显）那一帧，数整屏 R/G/B 都 ≥ 240 的近白像素：提供白色调 → 有；不提供（原路径）→ 无。
     */
    private fun whitePixelsAt200ms(tone: BubbleStampTone?): Int {
        var view: View? = null
        setFlying(tone = tone) { view = it }
        compose.mainClock.advanceTimeBy(200)
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            if (((c shr 16) and 0xFF) >= 240 && ((c shr 8) and 0xFF) >= 240 && (c and 0xFF) >= 240) n++
        }
        return n
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun stampTone_paintsFlightTimestampInToneColor() {
        val n = whitePixelsAt200ms(BubbleStampTone(Color.White, Color.Black.copy(alpha = 0.55f)))
        assertTrue("提供白色调：飞入时间戳应有近白笔画（实得 $n 个像素）", n > 0)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun noStampTone_timestampStaysSecondary_negativeControl() {
        assertEquals("不提供色调 = 原路径：整屏不应有近白像素", 0, whitePixelsAt200ms(null))
    }

    private fun lines(n: Int) = (1..n).joinToString("\n") { "一" }

    private companion object {
        const val NOW = 1_000L
        const val FRAME_MS = 16L
    }
}
