package com.situ.aichat.ui.designsystem

import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.model.AppearanceMode
import com.situ.aichat.data.model.AppearanceState
import com.situ.aichat.data.model.GlassTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选脸契约回归（[FABLE5_THEME_LIULI_PROPOSAL.md] §7.1 · 图纸 2026-09-04-琉璃第二张脸-卷一 §3.2）：
 * ① [AppSkin.fromRaw] 往返 + 未知/空/老「青花」值回退暖陶 + raw 串稳定（改了破坏老用户持久化偏好）；
 * ② [GlassTier.fromRaw] 三档往返 + 旧 clear / tinted、未知、空一律回退通透（琉璃 2.0 卷一图纸 §7 T1-2）；
 * ③ 琉璃**只换** text/surface/accent/bubble，economy/status/emotion/pet 与暖陶**同一引用**（复用不复制·缩范围降风险）；
 * ④ isDark 档位正确；⑤ [AppearanceState.DEFAULT] 四字段 = 跟随系统 / 关动态取色 / 琉璃 / 通透。
 * 对比红线另由 [ColorContrastTest] 的 liuli 两档看门。
 */
class AppSkinTest {

    @Test fun fromRaw_roundTripsAndDefaultsToLiuli() {
        // 默认脸 = 琉璃（用户 2026-09-28 拍板）；自己选过暖陶的（存了 "clay"）照旧暖陶。
        assertEquals(AppSkin.LIULI, AppSkin.DEFAULT)
        assertEquals(AppSkin.CLAY, AppSkin.fromRaw("clay"))
        assertEquals(AppSkin.LIULI, AppSkin.fromRaw("liuli"))
        assertEquals(AppSkin.LIULI, AppSkin.fromRaw(null))
        assertEquals(AppSkin.LIULI, AppSkin.fromRaw("unknown"))
        // 老用户存的青花值静默回退默认脸（青花已推翻·有意不迁移·图纸 §0 ② 1）。
        assertEquals(AppSkin.LIULI, AppSkin.fromRaw("qinghua"))
        // raw 串是持久化键，必须稳定。
        assertEquals("clay", AppSkin.CLAY.raw)
        assertEquals("liuli", AppSkin.LIULI.raw)
    }

    @Test fun glassTier_fromRaw_roundTripsAndDefaultsToSheer() {
        assertEquals(GlassTier.FROSTED, GlassTier.fromRaw("frosted"))
        assertEquals(GlassTier.STANDARD, GlassTier.fromRaw("standard"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("sheer"))
        // 旧版两档的存值、空、乱码 → 通透（不写回·E1）。
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("clear"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("tinted"))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw(null))
        assertEquals(GlassTier.SHEER, GlassTier.fromRaw("x"))
        assertEquals("frosted", GlassTier.FROSTED.raw)
        assertEquals("standard", GlassTier.STANDARD.raw)
        assertEquals("sheer", GlassTier.SHEER.raw)
    }

    @Test fun appearanceStateDefault_isSystemLiuliSheer() {
        val d = AppearanceState.DEFAULT
        assertEquals(AppearanceMode.SYSTEM, d.mode)
        assertFalse(d.useDynamicColor)
        assertEquals(AppSkin.LIULI, d.skin)
        assertEquals(GlassTier.SHEER, d.glassTier)
    }

    @Test fun liuli_swapsOnlyFourFamilies_light() {
        // 换了的四族：与暖陶不同。
        assertNotEquals(LightAppColors.surface.base, LiuliLightAppColors.surface.base)
        assertNotEquals(LightAppColors.text.primary, LiuliLightAppColors.text.primary)
        assertNotEquals(LightAppColors.accent.primary, LiuliLightAppColors.accent.primary)
        assertNotEquals(LightAppColors.bubble.userStart, LiuliLightAppColors.bubble.userStart)
        // 沿用的四族：与暖陶**同一引用**（复用，不复制·D-12）。
        assertSame(LightAppColors.economy, LiuliLightAppColors.economy)
        assertSame(LightAppColors.status, LiuliLightAppColors.status)
        assertSame(LightAppColors.emotion, LiuliLightAppColors.emotion)
        assertSame(LightAppColors.pet, LiuliLightAppColors.pet)
    }

    @Test fun liuli_swapsOnlyFourFamilies_dark() {
        assertNotEquals(DarkAppColors.surface.base, LiuliDarkAppColors.surface.base)
        assertNotEquals(DarkAppColors.text.primary, LiuliDarkAppColors.text.primary)
        assertNotEquals(DarkAppColors.bubble.userStart, LiuliDarkAppColors.bubble.userStart)
        assertSame(DarkAppColors.economy, LiuliDarkAppColors.economy)
        assertSame(DarkAppColors.status, LiuliDarkAppColors.status)
        assertSame(DarkAppColors.emotion, LiuliDarkAppColors.emotion)
        assertSame(DarkAppColors.pet, LiuliDarkAppColors.pet)
    }

    @Test fun liuli_isDarkFlagsCorrect() {
        assertFalse(LiuliLightAppColors.isDark)
        assertTrue(LiuliDarkAppColors.isDark)
    }
}
