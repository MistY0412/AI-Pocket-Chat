package com.situ.aichat.ui.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.situ.aichat.ui.designsystem.LightAppColors
import com.situ.aichat.ui.theme.AIPocketChatTheme
import com.situ.aichat.util.DateFormatters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * T2-3（琉璃 2.0 卷四 §3.7 / §7）：[BubbleInlineTimestamp] 读 [LocalBubbleStampTone]。黑底上画一条用户已读时间戳，
 * 只在**时间字**的边界里找最亮像素（勾是另一种色，排除在外）：
 * ① 不提供色调 → 最亮 ≈ `text.secondary`（±8·暖陶浅色）；② 提供（白，黑 55%）→ 最亮 R/G/B 都 ≥ 240；
 * ③ 负向对照：提供色调但字色设成 `text.secondary` → ② 的断言不成立。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class BubbleInlineTimestampToneTest {

    @get:Rule
    val compose = createComposeRule()

    private val ts = 1_790_345_400_000L
    private val text = DateFormatters.hourMinute(ts)

    /** 时间字边界内最亮（三通道和最大）的像素 RGB。 */
    private fun brightestTimeStroke(tone: BubbleStampTone?): Triple<Int, Int, Int> {
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.align(Alignment.Center)) {
                        CompositionLocalProvider(LocalBubbleStampTone provides tone) {
                            BubbleInlineTimestamp(timestampMs = ts, isUser = true, read = true)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val d = Density(v.resources.displayMetrics.density)
        val b = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        val (l, t, r, btm) = with(d) { listOf(b.left.toPx(), b.top.toPx(), b.right.toPx(), b.bottom.toPx()).map { it.toInt() } }
        var best = Triple(0, 0, 0)
        for (y in t until btm) for (x in l until r) {
            val c = bmp.getPixel(x, y)
            val px = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            if (px.first + px.second + px.third > best.first + best.second + best.third) best = px
        }
        return best
    }

    private fun allAtLeast240(p: Triple<Int, Int, Int>) = p.first >= 240 && p.second >= 240 && p.third >= 240

    @Test fun noTone_timeStaysTextSecondary() {
        val p = brightestTimeStroke(null)
        val s = LightAppColors.text.secondary
        val e = Triple((s.red * 255).toInt(), (s.green * 255).toInt(), (s.blue * 255).toInt())
        assertTrue("无色调：最亮 $p 应 ≈ text.secondary $e ±8", abs(p.first - e.first) <= 8 && abs(p.second - e.second) <= 8 && abs(p.third - e.third) <= 8)
    }

    @Test fun whiteTone_timeIsWhite() {
        val p = brightestTimeStroke(BubbleStampTone(Color.White, Color.Black.copy(alpha = 0.55f)))
        assertTrue("白色调：最亮 $p 应 R/G/B ≥ 240", allAtLeast240(p))
    }

    @Test fun toneWithSecondaryColor_failsWhiteCheck_negativeControl() {
        val p = brightestTimeStroke(BubbleStampTone(LightAppColors.text.secondary, Color.Black.copy(alpha = 0.55f)))
        assertFalse("负向对照：字色 = text.secondary 时不应过「≥ 240」（实测 $p）", allAtLeast240(p))
    }
}
