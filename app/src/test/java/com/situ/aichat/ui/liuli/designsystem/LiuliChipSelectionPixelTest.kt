package com.situ.aichat.ui.liuli.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
 * E12（卷二图纸 2026-09-25 §4.5-1 / §5）：标签选中 = 渐变底按「选中度」淡入、柔影同步浓起来；减弱动画时瞬切。
 *
 * 量法：浅色白底上放一枚标签，取两处像素——胶囊左内距里（离左缘 6dp·无字无边）与胶囊底边正中下 3dp（柔影区）。
 * 未选时两处都是纯白（未选底白 55% 覆白 = 白、无影）。切到选中后：
 * - 减弱动画：一变就等于落定值（瞬切·无中间帧）；
 * - 常规：刚开始变的那一帧与落定值明显不同、之后还有多帧中间态（正在淡入），且柔影与渐变同步浓起来。
 * 「刚开始变」按像素找（状态改后有两三帧固定的组合 → 动画起跑延迟，不按帧号写死）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LiuliChipSelectionPixelTest {

    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null
    private var selected by mutableStateOf(false)

    private fun show() {
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppHaptics provides mockk(relaxed = true)) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        LiuliChip(
                            selected = selected,
                            onClick = {},
                            label = "手作",
                            modifier = Modifier.align(Alignment.Center).testTag(TAG),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** （胶囊左内距像素, 胶囊底下 3dp 像素）。 */
    private fun sample(): Pair<Int, Int> {
        val v = checkNotNull(view)
        val density = v.resources.displayMetrics.density
        val b = compose.onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val x = ((b.left.value + 6f) * density).toInt()
        val cx = ((b.left.value + b.right.value) / 2f * density).toInt()
        val cy = ((b.top.value + b.bottom.value) / 2f * density).toInt()
        val below = cy + ((16f + 3f) * density).toInt()
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp.getPixel(x, cy) to bmp.getPixel(cx, below)
    }

    private fun channels(p: Int) = intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    private fun maxDiff(a: Int, b: Int): Int = channels(a).zip(channels(b)).maxOf { (x, y) -> abs(x - y) }

    /** 切到选中后逐帧采样（最多 [MAX_FRAMES] 帧·覆盖弹簧落定），返回每帧的（胶囊内, 胶囊下）。 */
    private fun selectAndTrace(): List<Pair<Int, Int>> {
        show()
        val before = sample()
        assertTrue("未选胶囊内应为白（实测 ${channels(before.first).toList()}）", maxDiff(before.first, WHITE) <= 3)
        assertTrue("未选时胶囊下无影（实测 ${channels(before.second).toList()}）", maxDiff(before.second, WHITE) <= 3)
        compose.mainClock.autoAdvance = false
        selected = true
        return (1..MAX_FRAMES).map {
            compose.mainClock.advanceTimeByFrame()
            sample()
        }
    }

    /** 首个「胶囊内已离开白」的帧（组合 → 动画起跑有两三帧固定管线延迟，与动画档无关）。 */
    private fun firstChanged(frames: List<Pair<Int, Int>>): Int =
        frames.indexOfFirst { maxDiff(it.first, WHITE) > 3 }.also { assertTrue("选中后始终没有变化", it >= 0) }

    @Test fun reducedMotion_selectionSnapsWithoutIntermediateFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val frames = selectAndTrace()
        val settled = frames.last()
        assertTrue("选中落定后胶囊内应为渐变、不是白（实测 ${channels(settled.first).toList()}）", maxDiff(settled.first, WHITE) >= 60)
        assertTrue("选中落定后胶囊下应有柔影（实测 ${channels(settled.second).toList()}）", maxDiff(settled.second, WHITE) >= 4)
        val first = frames[firstChanged(frames)]
        assertTrue(
            "减弱动画：一变就是落定值、无中间帧（实测 ${channels(first.first).toList()} vs ${channels(settled.first).toList()}）",
            maxDiff(first.first, settled.first) <= 3 && maxDiff(first.second, settled.second) <= 3,
        )
    }

    @Test fun normalMotion_gradientAndShadowFadeInTogether() {
        val frames = selectAndTrace()
        val settled = frames.last()
        assertTrue("选中落定后胶囊内应为渐变（实测 ${channels(settled.first).toList()}）", maxDiff(settled.first, WHITE) >= 60)
        val i = firstChanged(frames)
        val first = frames[i]
        assertTrue(
            "常规动画：刚开始变时渐变还在淡入（实测 ${channels(first.first).toList()} vs 落定 ${channels(settled.first).toList()}）",
            maxDiff(first.first, settled.first) >= 20,
        )
        assertTrue(
            "常规动画：柔影随选中度同步浓起来（同帧 ${channels(first.second).toList()} vs 落定 ${channels(settled.second).toList()}）",
            maxDiff(first.second, WHITE) < maxDiff(settled.second, WHITE),
        )
        val intermediate = frames.drop(i).count { maxDiff(it.first, settled.first) > 3 }
        assertTrue("常规动画应有多帧中间态（实测 $intermediate 帧）", intermediate >= 3)
    }

    private companion object {
        const val TAG = "chip"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MAX_FRAMES = 40
    }
}
