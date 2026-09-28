package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * T1：琉璃 2.0 零件材质落值（卷二图纸 2026-09-25 §3.1 / §7 T1-1）。
 *
 * 期望值是**在测试里重打的字面量**（设计稿 ③ P 表的色值 + 透明度），不引用 `Palette` 常量——常量被改一格、
 * 或浅深两档写反，这里都会红。比 `toArgb()`（alpha 字节级）。
 */
class LiuliMaterialsTest {

    private fun assertColor(expected: Color, actual: Color, label: String) =
        assertEquals(label, expected.toArgb(), actual.toArgb())

    private val white = Color(0xFFFFFFFF)
    private val black = Color(0xFF000000)
    private val dawnInk = Color(0xFF2A2440)

    @Test fun lightValues() {
        assertColor(white.copy(alpha = 0.52f), LiuliMaterials.cardFill(false), "cardFill 浅")
        assertColor(white.copy(alpha = 0.78f), LiuliMaterials.cardRim(false), "cardRim 浅")
        assertColor(white.copy(alpha = 0.95f), LiuliMaterials.highlight(false), "highlight 浅")
        assertColor(Color(0xFF5A4696).copy(alpha = 0.14f), LiuliMaterials.cardShadow(false), "cardShadow 浅")
        assertColor(dawnInk.copy(alpha = 0.08f), LiuliMaterials.divider(false), "divider 浅")
        assertColor(dawnInk.copy(alpha = 0.06f), LiuliMaterials.pressTint(false), "pressTint 浅")
        assertColor(white.copy(alpha = 0.55f), LiuliMaterials.chipFill(false), "chipFill 浅")
        assertColor(white.copy(alpha = 0.62f), LiuliMaterials.fieldFill(false), "fieldFill 浅")
        assertColor(dawnInk.copy(alpha = 0.06f), LiuliMaterials.segTrack(false), "segTrack 浅")
        assertColor(dawnInk.copy(alpha = 0.14f), LiuliMaterials.offTrack(false), "offTrack 浅")
        assertColor(Color(0xFF6B5BE8).copy(alpha = 0.16f), LiuliMaterials.focusRing(false), "focusRing 浅")
        assertColor(dawnInk.copy(alpha = 0.18f), LiuliMaterials.handle(false), "handle 浅")
        assertColor(Color(0xFFC8443A).copy(alpha = 0.10f), LiuliMaterials.dangerFill(Color(0xFFC8443A), false), "dangerFill 浅")
    }

    @Test fun darkValues() {
        assertColor(Color(0xFF241F3A).copy(alpha = 0.58f), LiuliMaterials.cardFill(true), "cardFill 深")
        assertColor(white.copy(alpha = 0.09f), LiuliMaterials.cardRim(true), "cardRim 深")
        assertColor(white.copy(alpha = 0.26f), LiuliMaterials.highlight(true), "highlight 深")
        assertColor(black.copy(alpha = 0.35f), LiuliMaterials.cardShadow(true), "cardShadow 深")
        assertColor(white.copy(alpha = 0.07f), LiuliMaterials.divider(true), "divider 深")
        assertColor(white.copy(alpha = 0.07f), LiuliMaterials.pressTint(true), "pressTint 深")
        assertColor(white.copy(alpha = 0.07f), LiuliMaterials.chipFill(true), "chipFill 深")
        assertColor(white.copy(alpha = 0.06f), LiuliMaterials.fieldFill(true), "fieldFill 深")
        assertColor(white.copy(alpha = 0.07f), LiuliMaterials.segTrack(true), "segTrack 深")
        assertColor(white.copy(alpha = 0.14f), LiuliMaterials.offTrack(true), "offTrack 深")
        assertColor(Color(0xFFB3A6FF).copy(alpha = 0.18f), LiuliMaterials.focusRing(true), "focusRing 深")
        assertColor(white.copy(alpha = 0.22f), LiuliMaterials.handle(true), "handle 深")
        assertColor(Color(0xFFC8443A).copy(alpha = 0.14f), LiuliMaterials.dangerFill(Color(0xFFC8443A), true), "dangerFill 深")
    }

    @Test fun accentValues() {
        assertColor(Color(0xFF6B5BE8).copy(alpha = 0.32f), LiuliMaterials.accentShadow, "accentShadow")
        assertNotNull(LiuliMaterials.accentBrush)
        assertNotNull(LiuliMaterials.accentHorizontalBrush)
    }

    @Test fun shadowGeometry() {
        assertEquals(8.dp, LiuliMaterials.cardShadowOffsetY)
        assertEquals(11.dp, LiuliMaterials.cardShadowBlur)
        assertEquals(2.dp, LiuliMaterials.thumbShadowOffsetY)
        assertEquals(4.dp, LiuliMaterials.thumbShadowBlur)
        assertEquals(4.dp, LiuliMaterials.chipShadowOffsetY)
        assertEquals(5.dp, LiuliMaterials.chipShadowBlur)
    }
}
