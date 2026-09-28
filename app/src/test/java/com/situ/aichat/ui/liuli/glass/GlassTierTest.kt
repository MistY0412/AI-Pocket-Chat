package com.situ.aichat.ui.liuli.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.GlassTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-1：琉璃 2.0 玻璃质感三档的纯函数（卷一图纸 2026-09-25 §3.1 / §3.2 / §4.2 / §7 T1-1）。
 *
 * 断言值在测试里**重打字面量**（不引用 `LiuliGlassSpec`）：落值改一格 = 玻璃长相走样或字压不住，静帧截图看不出。
 * 覆盖 E1（旧值 / 空 / 乱码 → 通透）、E3（安卓 13 以下只有毛玻璃）、E4（安卓 12 以下只着色）、E9（卷四：大面板
 * 改标准底色、壁纸加厚独立配方、透镜配方）、E10（深色配方）。
 */
class GlassTierTest {

    private val white = Color.White
    private val duskBase = Color(0xFF14121E)
    private val duskFrost = Color(0xFF1E1A30)

    private fun assertColor(expected: Color, actual: Color, label: String) =
        assertEquals(label, expected.toArgb(), actual.toArgb())

    // ── fromRaw / raw / 顺序（E1） ──

    @Test fun fromRaw_roundTripsThreeTiers() {
        assertEquals(GlassTier.FROSTED, GlassTier.fromRaw("frosted"))
        assertEquals(GlassTier.STANDARD, GlassTier.fromRaw("standard"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("sheer"))
    }

    @Test fun fromRaw_oldEmptyOrGarbage_fallsBackToSheer() {
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("clear"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("tinted"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw(null))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("x"))
    }

    @Test fun rawStrings_areStable_andEntryOrderIsFrostedStandardSheer() {
        assertEquals("frosted", GlassTier.FROSTED.raw)
        assertEquals("standard", GlassTier.STANDARD.raw)
        assertEquals("sheer", GlassTier.SHEER.raw)
        assertEquals(listOf(GlassTier.FROSTED, GlassTier.STANDARD, GlassTier.SHEER), GlassTier.entries.toList())
    }

    // ── effective（E3） ──

    @Test fun effective_belowAndroid13_isAlwaysFrosted() {
        GlassTier.entries.forEach { assertEquals("$it @32", GlassTier.FROSTED, it.effective(sdk = 32)) }
    }

    @Test fun effective_onAndroid13Plus_keepsRequestedTier() {
        listOf(33, 36).forEach { sdk ->
            GlassTier.entries.forEach { assertEquals("$it @$sdk", it, it.effective(sdk = sdk)) }
        }
    }

    // ── resolveGlassEngine（E3 / E4） ──

    @Test fun resolveGlassEngine_table() {
        assertEquals(LiuliGlassEngine.FROSTED_BLUR, resolveGlassEngine(GlassTier.FROSTED, 36))
        assertEquals(LiuliGlassEngine.FROSTED_BLUR, resolveGlassEngine(GlassTier.FROSTED, 31))
        assertEquals(LiuliGlassEngine.TINT_ONLY, resolveGlassEngine(GlassTier.FROSTED, 30))
        assertEquals(LiuliGlassEngine.HAZE, resolveGlassEngine(GlassTier.STANDARD, 33))
        assertEquals(LiuliGlassEngine.HAZE, resolveGlassEngine(GlassTier.SHEER, 36))
        assertEquals(LiuliGlassEngine.FROSTED_BLUR, resolveGlassEngine(GlassTier.SHEER, 32))
        assertEquals(LiuliGlassEngine.TINT_ONLY, resolveGlassEngine(GlassTier.STANDARD, 29))
    }

    // ── liuliHazeRecipe：3 角色 × 2 档 × 2 深浅 = 12 格（E9 / E10） ──

    /** 卷四 §0.2-1：大面板变薄 = 两档都用标准底色（regular + 白 0.30 / DuskBase 0.50）。 */
    @Test fun hazeRecipe_panel_isRegularStandard_inEveryTier() {
        listOf(GlassTier.STANDARD, GlassTier.SHEER).forEach { tier ->
            val light = liuliHazeRecipe(LiuliGlassRole.Panel, tier, dark = false)
            assertEquals("Panel/$tier/浅 clear?", false, light.clearStyle)
            assertColor(white.copy(alpha = 0.30f), light.tint, "Panel/$tier/浅")
            val dark = liuliHazeRecipe(LiuliGlassRole.Panel, tier, dark = true)
            assertEquals("Panel/$tier/深 clear?", false, dark.clearStyle)
            assertColor(duskBase.copy(alpha = 0.50f), dark.tint, "Panel/$tier/深")
        }
    }

    /** 卷四 §0.2-1：壁纸加厚独立配方——Bar / Panel / Button × 两档 × 浅深，`thick = true` 恒 regular + 白 0.52 / DuskBase 0.62。 */
    @Test fun hazeRecipe_thick_isRegularThickened_forEveryNonLensRole() {
        listOf(LiuliGlassRole.Bar, LiuliGlassRole.Panel, LiuliGlassRole.Button).forEach { role ->
            listOf(GlassTier.STANDARD, GlassTier.SHEER).forEach { tier ->
                val light = liuliHazeRecipe(role, tier, dark = false, thick = true)
                assertEquals("$role/$tier/浅/加厚 clear?", false, light.clearStyle)
                assertColor(white.copy(alpha = 0.52f), light.tint, "$role/$tier/浅/加厚")
                val dark = liuliHazeRecipe(role, tier, dark = true, thick = true)
                assertEquals("$role/$tier/深/加厚 clear?", false, dark.clearStyle)
                assertColor(duskBase.copy(alpha = 0.62f), dark.tint, "$role/$tier/深/加厚")
            }
        }
    }

    /** 卷四 §0.2-2：透镜在两档 × 浅深 × 加厚真假下恒 clear + 白 0.30（浅）/ 白 0.10（深）。 */
    @Test fun hazeRecipe_lens_isClearLensTint_everywhere() {
        listOf(GlassTier.STANDARD, GlassTier.SHEER).forEach { tier ->
            listOf(false, true).forEach { thick ->
                val light = liuliHazeRecipe(LiuliGlassRole.Lens, tier, dark = false, thick = thick)
                assertEquals("Lens/$tier/浅/thick=$thick clear?", true, light.clearStyle)
                assertColor(white.copy(alpha = 0.30f), light.tint, "Lens/$tier/浅/thick=$thick")
                val dark = liuliHazeRecipe(LiuliGlassRole.Lens, tier, dark = true, thick = thick)
                assertEquals("Lens/$tier/深/thick=$thick clear?", true, dark.clearStyle)
                assertColor(white.copy(alpha = 0.10f), dark.tint, "Lens/$tier/深/thick=$thick")
            }
        }
    }

    @Test fun hazeRecipe_barAndButton_sheerIsClearThin_standardIsRegular() {
        listOf(LiuliGlassRole.Bar, LiuliGlassRole.Button).forEach { role ->
            val sheerLight = liuliHazeRecipe(role, GlassTier.SHEER, dark = false)
            assertEquals("$role/SHEER/浅 clear?", true, sheerLight.clearStyle)
            assertColor(white.copy(alpha = 0.14f), sheerLight.tint, "$role/SHEER/浅")
            val sheerDark = liuliHazeRecipe(role, GlassTier.SHEER, dark = true)
            assertEquals("$role/SHEER/深 clear?", true, sheerDark.clearStyle)
            assertColor(duskBase.copy(alpha = 0.28f), sheerDark.tint, "$role/SHEER/深")
            val stdLight = liuliHazeRecipe(role, GlassTier.STANDARD, dark = false)
            assertEquals("$role/STANDARD/浅 clear?", false, stdLight.clearStyle)
            assertColor(white.copy(alpha = 0.30f), stdLight.tint, "$role/STANDARD/浅")
            val stdDark = liuliHazeRecipe(role, GlassTier.STANDARD, dark = true)
            assertEquals("$role/STANDARD/深 clear?", false, stdDark.clearStyle)
            assertColor(duskBase.copy(alpha = 0.50f), stdDark.tint, "$role/STANDARD/深")
        }
    }

    // ── liuliFrostedTint ──

    @Test fun frostedTint_fourCells() {
        assertColor(white.copy(alpha = 0.62f), liuliFrostedTint(dark = false, blurred = true), "浅·模糊")
        assertColor(duskFrost.copy(alpha = 0.72f), liuliFrostedTint(dark = true, blurred = true), "深·模糊")
        assertColor(white.copy(alpha = 0.88f), liuliFrostedTint(dark = false, blurred = false), "浅·兜底")
        assertColor(duskFrost.copy(alpha = 0.86f), liuliFrostedTint(dark = true, blurred = false), "深·兜底")
    }

    // ── liuliFallbackUnderlay（卷一复核 R1 🔴-1）──

    @Test fun fallbackUnderlay_isOpaqueRaisedPaper() {
        assertColor(white, liuliFallbackUnderlay(dark = false), "浅·垫底")
        assertColor(Color(0xFF1D1A2E), liuliFallbackUnderlay(dark = true), "深·垫底")
        assertEquals(1f, liuliFallbackUnderlay(dark = false).alpha)
        assertEquals(1f, liuliFallbackUnderlay(dark = true).alpha)
    }

    // ── liuliGlassElevation ──

    @Test fun elevation_buttonAndLensAre2dp_barAndPanelAre8dp() {
        assertEquals(2.dp, liuliGlassElevation(LiuliGlassRole.Button))
        assertEquals(2.dp, liuliGlassElevation(LiuliGlassRole.Lens))
        assertEquals(8.dp, liuliGlassElevation(LiuliGlassRole.Bar))
        assertEquals(8.dp, liuliGlassElevation(LiuliGlassRole.Panel))
    }
}
