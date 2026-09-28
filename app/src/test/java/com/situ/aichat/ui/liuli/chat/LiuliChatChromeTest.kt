package com.situ.aichat.ui.liuli.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.situ.aichat.ui.chat.ChatWallpaper
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassDark
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassLight
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmentPosition
import com.situ.aichat.ui.liuli.designsystem.liuliSegmentPosition
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1-2（琉璃 2.0 卷三 §7）：壁纸深浅门槛 0.63、聊天玻璃件深浅 / 加厚六例、分段卡段位四例（纯函数）。 */
class LiuliChatChromeTest {

    private fun wallpaper(topLuma: Float, bottomLuma: Float, midLuma: Float = 0.5f) = ChatWallpaper(
        sharp = mockk<ImageBitmap>(relaxed = true),
        frosted = mockk<ImageBitmap>(relaxed = true),
        // 暖陶布尔按暖陶 0.55 口径（与本卷判定无关·故意与琉璃门槛不同，证明琉璃只读亮度原值）。
        topDark = topLuma < 0.55f,
        bottomDark = bottomLuma < 0.55f,
        topLuma = topLuma,
        bottomLuma = bottomLuma,
        midLuma = midLuma,
    )

    /** T2-2（卷四 §3.7）：琉璃泡下时间戳色调——无壁纸 null；中段 0.2 → 琉璃玻璃浅主字 + 黑 0.55；0.8 → 墨主字 + 白 0.80。 */
    @Test fun liuliBubbleStampTone_byMidLuma() {
        assertEquals(null, liuliBubbleStampTone(null))
        val darkWp = liuliBubbleStampTone(wallpaper(0.9f, 0.9f, midLuma = 0.2f))
        assertEquals(LiuliOnGlassDark.primary.toArgb(), darkWp?.color?.toArgb())
        assertEquals(Color.Black.copy(alpha = 0.55f).toArgb(), darkWp?.halo?.toArgb())
        val lightWp = liuliBubbleStampTone(wallpaper(0.1f, 0.1f, midLuma = 0.8f))
        assertEquals(LiuliOnGlassLight.primary.toArgb(), lightWp?.color?.toArgb())
        assertEquals(Color.White.copy(alpha = 0.80f).toArgb(), lightWp?.halo?.toArgb())
    }

    @Test fun wallpaperThreshold_is063() {
        assertTrue(liuliWallpaperIsDark(0.62f))
        assertFalse(liuliWallpaperIsDark(0.63f))
    }

    @Test fun offlineWithoutWallpaper_bothDark_notThick() {
        assertEquals(LiuliChatChromeTones(topDark = true, bottomDark = true, thick = false), liuliChatChromeTones(false, null, offlineChrome = true))
    }

    @Test fun offlineWithWallpaper_bothDark_thick() {
        assertEquals(
            LiuliChatChromeTones(topDark = true, bottomDark = true, thick = true),
            liuliChatChromeTones(false, wallpaper(0.9f, 0.9f), offlineChrome = true),
        )
    }

    @Test fun wallpaperTopLightBottomDark() {
        assertEquals(
            LiuliChatChromeTones(topDark = false, bottomDark = true, thick = true),
            liuliChatChromeTones(true, wallpaper(0.8f, 0.2f), offlineChrome = false),
        )
    }

    @Test fun wallpaperTopDarkBottomLight() {
        // 0.6 落在暖陶判亮（≥ 0.55）、琉璃判暗（< 0.63）之间：证明判定读的是亮度原值 + 0.63。
        assertEquals(
            LiuliChatChromeTones(topDark = true, bottomDark = false, thick = true),
            liuliChatChromeTones(false, wallpaper(0.6f, 0.7f), offlineChrome = false),
        )
    }

    @Test fun noWallpaper_followsApp_notThick() {
        assertEquals(LiuliChatChromeTones(topDark = false, bottomDark = false, thick = false), liuliChatChromeTones(false, null, offlineChrome = false))
        assertEquals(LiuliChatChromeTones(topDark = true, bottomDark = true, thick = false), liuliChatChromeTones(true, null, offlineChrome = false))
    }

    @Test fun segmentPosition_fourCases() {
        assertEquals(LiuliSegmentPosition.Single, liuliSegmentPosition(0, 1))
        assertEquals(LiuliSegmentPosition.Top, liuliSegmentPosition(0, 3))
        assertEquals(LiuliSegmentPosition.Bottom, liuliSegmentPosition(2, 3))
        assertEquals(LiuliSegmentPosition.Middle, liuliSegmentPosition(1, 3))
    }
}
