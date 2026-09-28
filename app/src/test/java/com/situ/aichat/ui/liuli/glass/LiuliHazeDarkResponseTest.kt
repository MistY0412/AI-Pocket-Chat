package com.situ.aichat.ui.liuli.glass

import androidx.compose.ui.graphics.Color
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.designsystem.ColorContrast
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import com.situ.aichat.ui.designsystem.assertContrast
import com.situ.aichat.ui.designsystem.over
import com.situ.aichat.ui.liuli.chat.liuliWallpaperIsDark
import com.situ.aichat.ui.liuli.designsystem.LiuliAmbientSpec
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassDark
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 卷三复核 R1 🔴-1：深色 Haze 玻璃的光学响应（[liuliHazeDarkResponse]）。
 *
 * - 值钉死：断言重打字面量（出处 = Haze main `273d6947` 的 `regularDark` / `clearDark`）。
 * - 「模型保守」：各对比度测试的玻璃例都按「染色覆在身后原色上」建模；Haze 实际在着色**之前**先按 whitePoint / contrast
 *   调身后（源码 `GlassShaders.kt` `applyColorGrading`，下方 [grade] 逐式移植·只用灰阶：chroma 调整在灰上不起作用）。
 *   深色响应把身后调得 ≤ 原色 → 浅字对比 ≥ 模型值；Haze 2.0.0 浅色内置响应调得 ≥ 原色 → 深字对比 ≥ 模型值。
 *   **升级 Haze 必须重核 [grade] 与本文件里两组浅色源码值**（`GlassDefaults.whitePoint` 0.55 / `contrast` 0、
 *   `GlassStyle.clear` 的 0.17 / 0.08）。
 * - 壁纸灰阶按真实渲染复算：加厚玻璃在 0.63 门槛两侧，玻璃主 / 次字全 ≥ 4.5；负向对照 = 深色仍用 2.0.0 内置浅色标定时，
 *   门槛下最亮一级灰次字 < 4.5（卷三施工装机实测 3.9:1 的来历）。
 */
class LiuliHazeDarkResponseTest {

    /** `applyColorGrading` 的灰阶移植：先向白（> 0）/ 黑（< 0）混 |whitePoint|，再绕 0.5 拉对比、钳到 0..1。 */
    private fun grade(b: Float, whitePoint: Float, contrast: Float): Float {
        val target = if (whitePoint > 0f) 1f else 0f
        val g = b + (target - b) * abs(whitePoint)
        return ((g - 0.5f) * (1f + contrast) + 0.5f).coerceIn(0f, 1f)
    }

    /** Haze 玻璃中部的实际颜色：身后灰 [gray] 调色后，再按染色透明度混上染色（`mix(graded, tint.rgb, tint.a)`）。 */
    private fun hazeOver(tint: Color, gray: Float, whitePoint: Float, contrast: Float): Color {
        val g = grade(gray, whitePoint, contrast)
        return over(tint, tint.alpha, Color(g, g, g))
    }

    private fun Color.rgb255() = listOf(red, green, blue).map { (it * 255).roundToInt() }

    /** 截图是 8 位取整：逐通道差 ≤ 1。 */
    private fun assertNear(expected: List<Int>, actual: List<Int>) =
        assertTrue("期望 $expected ±1，实得 $actual", expected.zip(actual).all { (e, a) -> abs(e - a) <= 1 })

    @Test fun darkResponse_valuesMatchUpstream() {
        assertEquals(LiuliHazeDarkResponse(0.38f, 0.32f, 0.08f, 0.08f, -0.22f, 1.1f), liuliHazeDarkResponse(clearStyle = false))
        assertEquals(LiuliHazeDarkResponse(0.42f, 0.25f, 0.22f, 0.12f, -0.18f, 1f), liuliHazeDarkResponse(clearStyle = true))
    }

    /** 移植式对得上装机实测（卷三施工 O-2：2.0.0 内置响应下加厚深玻璃压 #202020 / #A0A0A0 读 (71,70,77) / (93,92,99)）。 */
    @Test fun gradePort_reproducesMeasuredEmulatorPixels() {
        val tint = liuliHazeRecipe(LiuliGlassRole.Bar, GlassTier.SHEER, dark = true, thick = true).tint
        assertNear(listOf(71, 70, 77), hazeOver(tint, 0x20 / 255f, 0.55f, 0f).rgb255())
        assertNear(listOf(93, 92, 99), hazeOver(tint, 0xA0 / 255f, 0.55f, 0f).rgb255())
    }

    @Test fun darkResponse_neverBrightensBackdrop_lightBuiltInsNeverDarken() {
        for (i in 0..255) {
            val b = i / 255f
            listOf(false, true).forEach { clear ->
                val r = liuliHazeDarkResponse(clear)
                assertTrue("深色 clear=$clear 灰 $i", grade(b, r.whitePoint, r.contrast) <= b + 1e-6f)
            }
            assertTrue("浅色 regular 灰 $i", grade(b, 0.55f, 0f) >= b - 1e-6f)
            assertTrue("浅色 clear 灰 $i", grade(b, 0.17f, 0.08f) >= b - 1e-6f)
        }
    }

    @Test fun thickGlass_onWallpaperGrays_realRendering() {
        val dark = liuliHazeDarkResponse(clearStyle = false) // 加厚配方 = regular 底
        for (g in 0..255) {
            val isDark = liuliWallpaperIsDark(g / 255f)
            val tint = liuliHazeRecipe(LiuliGlassRole.Bar, GlassTier.SHEER, isDark, thick = true).tint
            val bg = if (isDark) hazeOver(tint, g / 255f, dark.whitePoint, dark.contrast) else hazeOver(tint, g / 255f, 0.55f, 0f)
            val onGlass = if (isDark) LiuliOnGlassDark else LiuliOnGlassLight
            assertContrast(onGlass.primary, bg, 4.5, "主字×灰 $g")
            assertContrast(onGlass.secondary, bg, 4.5, "次字×灰 $g")
        }
        // 负向对照：深色仍用 2.0.0 内置浅色标定（whitePoint 0.55·contrast 0）→ 门槛下最亮一级灰（160）次字不达标。
        val tint = liuliHazeRecipe(LiuliGlassRole.Bar, GlassTier.SHEER, dark = true, thick = true).tint
        val old = hazeOver(tint, 160 / 255f, 0.55f, 0f)
        assertTrue(ColorContrast.ratio(LiuliOnGlassDark.secondary, old) < 4.5)
    }

    /** 逐通道调色后混染色：彩色底的 Haze 近似（chroma 调整只改饱和度、不按亮度搬运，此处略去）。 */
    private fun hazeOverColor(tint: Color, under: Color, whitePoint: Float, contrast: Float): Color {
        val graded = Color(grade(under.red, whitePoint, contrast), grade(under.green, whitePoint, contrast), grade(under.blue, whitePoint, contrast))
        return over(tint, tint.alpha, graded)
    }

    /**
     * T1-4（卷四 §0.1-H / §7）：透镜 = clear 样式 + 透镜白，按**真实调色式**合成（浅 = 2.0.0 内置 clear 的 whitePoint 0.17 /
     * contrast 0.08；深 = [liuliHazeDarkResponse] clear）。透镜身后 = 页面底 + 四光晕，以及其上的半透明卡；
     * 透镜上的强调字 `accent.text` 与玻璃次字 ≥ 4.5（图纸预算：浅 6.20 / 深 5.42）。按「染色覆原色」旧口径深色只有 4.04，
     * 故透镜不进 [com.situ.aichat.ui.liuli.designsystem.LiuliColorContrastTest] 的染色集。
     */
    @Test fun lens_realRendering_labelsReadable() {
        listOf(
            Triple(LiuliLightAppColors, false, "浅"),
            Triple(LiuliDarkAppColors, true, "深"),
        ).forEach { (c, dark, name) ->
            val tint = liuliHazeRecipe(LiuliGlassRole.Lens, GlassTier.SHEER, dark).tint
            val (wp, ct) = if (dark) liuliHazeDarkResponse(clearStyle = true).let { it.whitePoint to it.contrast } else 0.17f to 0.08f
            val onGlass = if (dark) LiuliOnGlassDark else LiuliOnGlassLight
            val bare = listOf(c.surface.base) + LiuliAmbientSpec.glows(dark)
            val card = LiuliMaterials.cardFill(dark)
            val unders = bare + bare.map { over(card, card.alpha, it) }
            unders.forEachIndexed { i, u ->
                val bg = hazeOverColor(tint, u, wp, ct)
                assertContrast(c.accent.text, bg, 4.5, "$name 透镜强调字×底#$i")
                assertContrast(onGlass.secondary, bg, 4.5, "$name 透镜次字×底#$i")
            }
        }
    }

    /** 卷四 §0.2-2：只有「宿主在场 + Haze 引擎」走真透镜，其余三组合一律画出来的透镜。 */
    @Test fun lensUsesHaze() {
        assertTrue(liuliLensUsesHaze(hasHost = true, engine = LiuliGlassEngine.HAZE))
        assertFalse(liuliLensUsesHaze(hasHost = true, engine = LiuliGlassEngine.FROSTED_BLUR))
        assertFalse(liuliLensUsesHaze(hasHost = true, engine = LiuliGlassEngine.TINT_ONLY))
        assertFalse(liuliLensUsesHaze(hasHost = false, engine = LiuliGlassEngine.HAZE))
    }
}
