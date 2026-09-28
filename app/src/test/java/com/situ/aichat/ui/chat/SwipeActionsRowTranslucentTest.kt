package com.situ.aichat.ui.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.LightAppColors
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
 * T2-9（琉璃 2.0 卷三 §3.11·NATIVE 量像素）：`SwipeActionsRow(translucentContent = true)` 压在纯红底上——
 * 关着时行尾动作面区域 = 红（动作面一像素没画，免得透过半透明卡看见）；左滑吸附全开（露出 76dp）后该区出现动作面
 * （蓝）；`translucentContent = false`（暖陶原样）关着时该区 = `surface.base`（不透明内容层盖住动作面）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class SwipeActionsRowTranslucentTest {

    @get:Rule
    val compose = createComposeRule()

    private var view: View? = null

    private fun show(translucent: Boolean) {
        val delete = SwipeAction("删除", Icons.Filled.Delete, Color.Transparent, Color.White) {}
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                Column(Modifier.fillMaxSize().background(Color.Red)) {
                    SwipeActionsRow(
                        onRowClick = {},
                        leadingActions = emptyList(),
                        trailingActions = listOf(delete),
                        modifier = Modifier.testTag(ROW),
                        actionFace = { _, faceModifier, _ -> Box(faceModifier.background(Color.Blue)) },
                        translucentContent = translucent,
                    ) {
                        Box(Modifier.fillMaxWidth().height(78.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** 行尾动作面区域正中（离右缘 38dp·行高一半）的像素。 */
    private fun actionAreaPixel(): Int {
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val row = compose.onNodeWithTag(ROW).fetchSemanticsNode().boundsInRoot
        return bmp.getPixel((row.right - 38f * density).toInt(), (row.top + row.height / 2f).toInt())
    }

    private fun assertRgb(expected: Int, actual: Int, label: String) {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        val ok = listOf(16, 8, 0).all { abs(ch(expected, it) - ch(actual, it)) <= 2 }
        assertTrue("$label：实测 #${Integer.toHexString(actual)}，期望 ≈ #${Integer.toHexString(expected)}", ok)
    }

    @Test fun translucent_closed_drawsNoActionFace() {
        show(translucent = true)
        assertRgb(Color.Red.toArgb(), actionAreaPixel(), "透明内容层·关着：动作面区域 = 身后的红")
    }

    @Test fun translucent_opened_revealsActionFace() {
        show(translucent = true)
        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft(startX = right - 10f, endX = right - 200f) }
        compose.waitForIdle()
        assertRgb(Color.Blue.toArgb(), actionAreaPixel(), "透明内容层·左滑全开：露出的那一截画动作面")
    }

    @Test fun opaque_closed_coversWithSurfaceBase() {
        show(translucent = false)
        assertRgb(LightAppColors.surface.base.toArgb(), actionAreaPixel(), "暖陶原样·关着：不透明内容层盖住动作面")
    }

    private companion object {
        const val ROW = "row"
    }
}
