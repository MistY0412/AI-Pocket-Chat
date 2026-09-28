package com.situ.aichat.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.situ.aichat.ui.designsystem.ColorContrast
import com.situ.aichat.ui.designsystem.over
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassDark
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassLight
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-2（琉璃 2.0 卷四 §3.7 / §7）：泡下时间戳在壁纸上的色调（两张脸共用）。
 *
 * - 门槛 0.494（白字 / 墨字对比度交点 = 灰 126 / 255·卷四复核 R1）：0.49 → 深色色调（字 = onDark、描边 黑 0.55），
 *   0.494 → 浅色色调（字 = onLight、描边 白 0.80）；
 * - 暖陶取暖陶玻璃主字（#F3EEE8 / #2E2925·重打字面量）；
 * - **描边模型**：灰阶 g = 0..255 的均匀壁纸（中段亮度 = g / 255），按门槛选字色后，底 = 描边色以 **0.30** 覆在灰上——
 *   0.30 是「4dp 模糊描边在字形周围等效一层反色垫」的建模假设，真机批 ① 复看；两套主字全 ≥ 4.5（实算最坏 5.6 上下）。
 *   无描边（字直接压灰）的原始值只记录下限、防门槛被改坏：≥ 3.5（交点处实算 暖陶 3.54 / 琉璃 3.63；门槛偏到 0.53 就只剩 3.11）。
 */
class BubbleStampToneTest {

    private val onDark = Color(0xFF111111)
    private val onLight = Color(0xFF222222)

    private fun assertColor(expected: Color, actual: Color, label: String) = assertEquals(label, expected.toArgb(), actual.toArgb())

    @Test fun threshold_0494_picksDarkOrLightTone() {
        val below = bubbleStampTone(0.49f, onDark, onLight)
        assertColor(onDark, below.color, "0.49 字色")
        assertColor(Color.Black.copy(alpha = 0.55f), below.halo, "0.49 描边")
        val at = bubbleStampTone(0.494f, onDark, onLight)
        assertColor(onLight, at.color, "0.494 字色")
        assertColor(Color.White.copy(alpha = 0.80f), at.halo, "0.494 描边")
        // 旧门槛 0.53 下灰 126–135 走白字；现走墨字（灰 130 上墨字对比比白字高）。
        assertColor(onLight, bubbleStampTone(130 / 255f, onDark, onLight).color, "灰 130 字色")
    }

    @Test fun warmTone_usesWarmGlassPrimaryInk() {
        fun wp(mid: Float) = ChatWallpaper(mockk(relaxed = true), mockk(relaxed = true), false, false, 0.5f, 0.5f, mid)
        assertColor(Color(0xFFF3EEE8), warmBubbleStampTone(wp(0.2f)).color, "暖陶 暗壁纸 字色")
        assertColor(Color(0xFF2E2925), warmBubbleStampTone(wp(0.8f)).color, "暖陶 亮壁纸 字色")
    }

    @Test fun haloModel_everyGray_bothFaces_readable() {
        val faces = listOf(
            "暖陶" to (Color(0xFFF3EEE8) to Color(0xFF2E2925)),
            "琉璃" to (LiuliOnGlassDark.primary to LiuliOnGlassLight.primary),
        )
        faces.forEach { (face, inks) ->
            var rawMin = Double.MAX_VALUE
            for (g in 0..255) {
                val gray = Color(g, g, g)
                val tone = bubbleStampTone(g / 255f, inks.first, inks.second)
                val haloOpaque = tone.halo.copy(alpha = 1f)
                val bg = over(haloOpaque, 0.30f, gray)
                val r = ColorContrast.ratio(tone.color, bg)
                assertTrue("$face 灰 $g 描边模型 ${"%.2f".format(r)} < 4.5", r >= 4.5)
                rawMin = minOf(rawMin, ColorContrast.ratio(tone.color, gray))
            }
            assertTrue("$face 无描边最坏 ${"%.2f".format(rawMin)} < 3.5", rawMin >= 3.5)
        }
    }
}
