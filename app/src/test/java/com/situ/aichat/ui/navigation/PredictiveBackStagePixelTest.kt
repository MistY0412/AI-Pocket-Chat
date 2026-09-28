package com.situ.aichat.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.designsystem.AppElevation
import com.situ.aichat.ui.designsystem.LightAppColors
import com.situ.aichat.ui.theme.AIPocketChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 预测返回「舞台底」像素 T2（微图纸 docs/handoff/2026-09-28-预测返回舞台底.md §5）。
 *
 * 真 NavHost + 生产同一组占位转场，两页都是纯色（上一页 a 深蓝、当前页 b 中灰），手势拖满（进度 1：两卡缩到 0.9、
 * 当前页右缘离屏 8dp、上一页左移 96dp）后对根视图软件光栅化取像素。期望值由本测试按 token 与对比稿浓度**另算**，
 * 不调被测的取色 / 浓度代码；取点都在卡片之外的缝里（或卡片内缘），远离柔影可及范围的点才做精确比对。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PredictiveBackStagePixelTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private var view: View? = null
    private val input = DirectNavigationEventInput()
    private var frameMs = 0L

    private val pageA = Color(0xFF3D5A80)
    private val pageB = Color(0xFF808080)

    private fun launch(skin: AppSkin) {
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = false, skin = skin) {
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
                        popExitTransition = { ExitTransition.None },
                        predictivePopEnterTransition = { BackCardHoldEnter },
                        predictivePopExitTransition = { e -> backCardHoldExit(motion, e) },
                    ) {
                        backCardComposable("a") { Box(Modifier.fillMaxSize().background(pageA)) }
                        backCardComposable("b") { Box(Modifier.fillMaxSize().background(pageB)) }
                    }
                }
            }
        }
        compose.runOnUiThread { compose.activity.navigationEventDispatcher.addInput(input) }
        compose.runOnUiThread { nav.navigate("b") }
        compose.waitForIdle()
    }

    /** 左缘拖满（进度 1）并停住。 */
    private fun dragFully() {
        val y = checkNotNull(view).height / 2f
        compose.runOnUiThread { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 2f, y, frameMs++)) }
        compose.waitForIdle()
        compose.runOnUiThread { input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, 1f, 2f, y, frameMs++)) }
        compose.waitForIdle()
    }

    private fun snapshot(): Bitmap {
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLACK)
        compose.runOnUiThread { v.draw(Canvas(bmp)) }
        return bmp
    }

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    private fun Bitmap.rgb(x: Float, y: Float): Rgb {
        val c = getPixel(x.toInt(), y.toInt())
        return Rgb(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    }

    private fun Color.rgb(): Rgb {
        val c = toArgb()
        return Rgb(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    }

    /** 不透明色 [dst] 上叠 [src]×[alpha]（src-over）。 */
    private fun over(dst: Rgb, src: Rgb, alpha: Float) = Rgb(
        (src.r * alpha + dst.r * (1 - alpha)).toInt(),
        (src.g * alpha + dst.g * (1 - alpha)).toInt(),
        (src.b * alpha + dst.b * (1 - alpha)).toInt(),
    )

    private fun assertRgb(msg: String, expected: Rgb, actual: Rgb, tol: Int = 3) {
        assertEquals("$msg R（期望 $expected 实得 $actual）", expected.r.toFloat(), actual.r.toFloat(), tol.toFloat())
        assertEquals("$msg G（期望 $expected 实得 $actual）", expected.g.toFloat(), actual.g.toFloat(), tol.toFloat())
        assertEquals("$msg B（期望 $expected 实得 $actual）", expected.b.toFloat(), actual.b.toFloat(), tol.toFloat())
    }

    private val d get() = compose.density.density

    // 拖满时（进度 1）的卡片几何：两卡 0.9 倍；当前页左缘 = 0.1W − 8dp、右缘 = W − 8dp；上下各留 0.05H。
    private fun closingLeft(w: Int) = 0.1f * w - 8f * d
    private fun closingRight(w: Int) = w - 8f * d

    /** 暖陶浅：缝 = 瓷白底 → 压 5% 暖陶投影墨（舞台加深）→ 压 15% 同色（遮罩）。 */
    private fun clayGap(): Rgb {
        val ink = AppElevation.shadowInk.rgb()
        return over(over(LightAppColors.surface.base.rgb(), ink, 0.05f), ink, 0.15f)
    }

    @Test
    fun 静止时零舞台零白边() {
        launch(AppSkin.LIULI)
        val bmp = snapshot()
        val w = bmp.width
        val h = bmp.height
        assertRgb("左上角", pageB.rgb(), bmp.rgb(0.02f * w, 0.005f * h))
        assertRgb("右缘缝位", pageB.rgb(), bmp.rgb(closingRight(w) + 2f * d, 0.5f * h))
        assertRgb("卡片内缘位", pageB.rgb(), bmp.rgb(closingLeft(w) + 1.7f, 0.5f * h))
    }

    @Test
    fun 暖陶_缝是瓷白底压两层暖墨() {
        launch(AppSkin.CLAY)
        dragFully()
        val bmp = snapshot()
        // 左上角远离当前页柔影（横 25dp、纵 56dp 之外）→ 只有舞台 + 加深 + 遮罩。
        assertRgb("左上缝", clayGap(), bmp.rgb(0.02f * bmp.width, 0.005f * bmp.height))
        // 暖陶没有柔光：左右两处缝同色。
        assertRgb("右上缝同色", bmp.rgb(0.12f * bmp.width, 0.005f * bmp.height), bmp.rgb(0.88f * bmp.width, 0.005f * bmp.height))
    }

    @Test
    fun 琉璃_缝里是柔光_左上偏桃_右上偏紫() {
        launch(AppSkin.LIULI)
        dragFully()
        val bmp = snapshot()
        val left = bmp.rgb(0.12f * bmp.width, 0.005f * bmp.height)
        val right = bmp.rgb(0.88f * bmp.width, 0.005f * bmp.height)
        // 左上靠桃色光晕（中心 0.12W / 0.10H）、右上只沾一点丁香紫：蓝 / 绿通道右高左低，红通道左不低于右。
        assertTrue("右上比左上更偏蓝：$left vs $right", right.b - left.b >= 20)
        assertTrue("右上比左上更偏绿：$left vs $right", right.g - left.g >= 10)
        assertTrue("左上红不低于右上：$left vs $right", left.r >= right.r)
        // 不是灰：右上带紫（蓝明显高于红）。
        assertTrue("右上应偏紫而非中性灰：$right", right.b - right.r >= 10)
    }

    @Test
    fun 当前页右缘外有柔影() {
        launch(AppSkin.CLAY)
        dragFully()
        val bmp = snapshot()
        val near = bmp.rgb(closingRight(bmp.width) + 2f * d, 0.5f * bmp.height)
        val flat = clayGap()
        assertTrue("卡缘外 2dp 应比平缝暗 ≥ 10：平缝 $flat 实得 $near", flat.r - near.r >= 10 && flat.g - near.g >= 10)
    }

    @Test
    fun 琉璃当前页卡片内缘有白边_暖陶没有() {
        launch(AppSkin.LIULI)
        dragFully()
        val bmp = snapshot()
        val y = 0.5f * bmp.height
        val edge = bmp.rgb(closingLeft(bmp.width) + 1.7f, y)
        val inner = bmp.rgb(closingLeft(bmp.width) + 10f * d, y)
        assertRgb("卡内（离边 10dp）= 页面本色", pageB.rgb(), inner)
        assertTrue("内缘应被白边提亮 ≥ 40：内 $inner 缘 $edge", edge.r - inner.r >= 40)
    }

    @Test
    fun 暖陶当前页卡片内缘无白边() {
        launch(AppSkin.CLAY)
        dragFully()
        val bmp = snapshot()
        val y = 0.5f * bmp.height
        assertRgb("内缘 = 页面本色", pageB.rgb(), bmp.rgb(closingLeft(bmp.width) + 1.7f, y))
    }

    @Test
    fun 松手收尾完零残留() {
        launch(AppSkin.LIULI)
        dragFully()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { input.backCompleted() }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        val bmp = snapshot()
        val w = bmp.width
        val h = bmp.height
        for ((x, y) in listOf(2f to 2f, 0.02f * w to 0.005f * h, closingRight(w) + 2f * d to 0.5f * h, w - 3f to h - 3f)) {
            assertRgb("($x, $y)", pageA.rgb(), bmp.rgb(x, y))
        }
    }
}
