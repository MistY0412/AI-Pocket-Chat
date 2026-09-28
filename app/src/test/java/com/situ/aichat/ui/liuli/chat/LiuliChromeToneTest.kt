package com.situ.aichat.ui.liuli.chat

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.ui.designsystem.AppColors
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassColors
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassDark
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassLight
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.liuli.glass.LocalLiuliGlassThick
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-6（琉璃 2.0 卷三 §7）：[LiuliChromeTone] 在一小片里**一起**换 `LocalIsDarkTheme` 与 `LocalAppColors`（只换其一
 * 会出现浅色 `accent.text` 压深玻璃），并按 thick 打开玻璃加厚；外层是反向档，证明不是继承来的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliChromeToneTest {

    @get:Rule
    val compose = createComposeRule()

    private class Seen(val dark: Boolean, val colors: AppColors, val onGlass: LiuliOnGlassColors, val thick: Boolean)

    private fun seenInside(outerDark: Boolean, dark: Boolean, thick: Boolean): Seen {
        var seen: Seen? = null
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalIsDarkTheme provides outerDark,
                com.situ.aichat.ui.designsystem.LocalAppColors provides if (outerDark) LiuliDarkAppColors else LiuliLightAppColors,
                LocalLiuliGlassThick provides !thick,
            ) {
                LiuliChromeTone(dark = dark, thick = thick) {
                    seen = Seen(LocalIsDarkTheme.current, AppTheme.colors, LiuliTheme.onGlass, LocalLiuliGlassThick.current)
                }
            }
        }
        compose.waitForIdle()
        return checkNotNull(seen)
    }

    @Test fun darkThick_insideLightApp() {
        val s = seenInside(outerDark = false, dark = true, thick = true)
        assertEquals(true, s.dark)
        assertSame(LiuliDarkAppColors, s.colors)
        assertSame(LiuliOnGlassDark, s.onGlass)
        assertEquals(true, s.thick)
    }

    @Test fun lightThin_insideDarkApp() {
        val s = seenInside(outerDark = true, dark = false, thick = false)
        assertEquals(false, s.dark)
        assertSame(LiuliLightAppColors, s.colors)
        assertSame(LiuliOnGlassLight, s.onGlass)
        assertEquals(false, s.thick)
    }
}
