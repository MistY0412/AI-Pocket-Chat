package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * T1-8 柔光氛围底落值（琉璃 2.0 卷一图纸 2026-09-25 §4.3 / §7）：四个光晕的浅 / 深字面量、四个中心、
 * 半径占比 0.78、`glows(dark)` 选对表。断言值在测试里重打字面量（不引用 `Palette`）。
 */
class LiuliAmbientTest {

    private fun argbs(colors: List<Color>): List<Int> = colors.map { it.toArgb() }

    @Test fun glows_matchLockedLiterals_bothModes() {
        assertEquals(
            argbs(listOf(Color(0xFFFFCDB8), Color(0xFFD6C3FF), Color(0xFFBDE0FF), Color(0xFFFFD6EE))),
            argbs(LiuliAmbientSpec.glowsLight),
        )
        assertEquals(
            argbs(listOf(Color(0xFF4A3590), Color(0xFF6B2D6A), Color(0xFF1E4A7A), Color(0xFF3F2C80))),
            argbs(LiuliAmbientSpec.glowsDark),
        )
    }

    @Test fun centers_areLeftTop_rightUpperMid_leftLowerMid_rightBottom() {
        val expected = listOf(Offset(0.12f, 0.10f), Offset(0.88f, 0.34f), Offset(0.08f, 0.66f), Offset(0.82f, 0.92f))
        assertEquals(expected.size, LiuliAmbientSpec.centers.size)
        expected.forEachIndexed { i, e ->
            assertEquals("第 $i 个中心 x", e.x, LiuliAmbientSpec.centers[i].x, 1e-6f)
            assertEquals("第 $i 个中心 y", e.y, LiuliAmbientSpec.centers[i].y, 1e-6f)
        }
    }

    @Test fun radiusFraction_is078OfWidth() {
        assertEquals(0.78f, LiuliAmbientSpec.RADIUS_FRACTION, 0f)
    }

    @Test fun glows_picksTableByDarkFlag() {
        assertSame(LiuliAmbientSpec.glowsLight, LiuliAmbientSpec.glows(dark = false))
        assertSame(LiuliAmbientSpec.glowsDark, LiuliAmbientSpec.glows(dark = true))
    }
}
