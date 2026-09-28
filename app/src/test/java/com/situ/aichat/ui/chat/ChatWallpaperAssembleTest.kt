package com.situ.aichat.ui.chat

import android.graphics.Bitmap
import android.graphics.Color as AColor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T2-1（琉璃 2.0 卷四 §3.8 / §7）：[assembleChatWallpaper] 的中段 68% 条带亮度。100×100 位图：上 16 行、下 16 行一色，
 * 中 68 行另一色——中段亮度只看中间那 68 行，顶 / 底两条互不串。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChatWallpaperAssembleTest {

    private fun banded(edge: Int, middle: Int): Bitmap {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            val c = if (y < 16 || y >= 84) edge else middle
            bmp.setPixels(IntArray(100) { c }, 0, 100, 0, y, 100, 1)
        }
        return bmp
    }

    @Test fun midLuma_readsOnlyTheMiddleBand_blackMiddle() {
        val w = assembleChatWallpaper(banded(AColor.WHITE, AColor.BLACK), Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        assertTrue("中段应 < 0.02，实得 ${w.midLuma}", w.midLuma < 0.02f)
        assertTrue("顶条应 > 0.98，实得 ${w.topLuma}", w.topLuma > 0.98f)
        assertTrue("底条应 > 0.98，实得 ${w.bottomLuma}", w.bottomLuma > 0.98f)
    }

    @Test fun midLuma_readsOnlyTheMiddleBand_whiteMiddle() {
        val w = assembleChatWallpaper(banded(AColor.BLACK, AColor.WHITE), Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        assertTrue("中段应 > 0.98，实得 ${w.midLuma}", w.midLuma > 0.98f)
        assertTrue("顶条应 < 0.02，实得 ${w.topLuma}", w.topLuma < 0.02f)
    }
}
