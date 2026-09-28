package com.situ.aichat.ui.liuli.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.data.model.AppSkin
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
 * 复核 R1 🟡-1：锁定章整屏糊层不许在屏幕四周画出一圈细框（装机实测通透档深色一圈亮线 / 浅色一圈暗线 = 玻璃描边落在屏内）。
 *
 * 量法：深色暖灰纸底上铺锁定层，取屏幕最外一列 / 一行的像素与往里 16dp 处比——四边都应与内侧同色（描边已伸到屏外）。
 * 取样行 / 列避开居中的锁卡（上 / 下取屏宽正中，左 / 右取屏高 15% 处）。负向对照：把伸出量改成 0 本例当场红。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryLockLayerEdgePixelTest {

    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null

    private fun channels(p: Int) = intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    private fun maxDiff(a: Int, b: Int): Int = channels(a).zip(channels(b)).maxOf { (x, y) -> abs(x - y) }

    @Test
    fun dark_lockLayer_noFrameAlongScreenEdges() {
        val now = 1_790_000_000_000L
        val ch = StoryChapterEntity(id = "c2", storyId = "s1", chapterNumber = 2, title = "七楼的灯", unlockAt = now + 2 * 3_600_000L)
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = true, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF211E18))) {
                        LiuliStoryLockLayer(ch, mutableLongStateOf(now))
                    }
                }
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        val inset = (16f * v.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val w = bmp.width
        val h = bmp.height
        val row = (h * 0.15f).toInt()
        val edges = mapOf(
            "左" to (bmp.getPixel(0, row) to bmp.getPixel(inset, row)),
            "右" to (bmp.getPixel(w - 1, row) to bmp.getPixel(w - 1 - inset, row)),
            "上" to (bmp.getPixel(w / 2, 0) to bmp.getPixel(w / 2, inset)),
            "下" to (bmp.getPixel(w / 2, h - 1) to bmp.getPixel(w / 2, h - 1 - inset)),
        )
        edges.forEach { (side, pair) ->
            val (edge, inner) = pair
            assertTrue(
                "${side}边最外像素 ${channels(edge).toList()} 应与内侧 ${channels(inner).toList()} 同色（无细框）",
                maxDiff(edge, inner) <= 3,
            )
        }
    }
}
