package com.situ.aichat.ui.designsystem

import androidx.compose.ui.graphics.Brush
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1-1（琉璃 2.0 卷三 §7）：用户泡渐变刷的唯一出口 [userFill]。暖陶（`userMid == null`）必须与改前**同一个表达式**
 * `Brush.linearGradient(listOf(userStart, userEnd))`（逐像素不变的前提）；琉璃必须与主色三段刷 [LiuliMaterials.accentBrush] 相等。
 */
class AppBubbleFillTest {

    @Test fun warmSchemes_keepTwoStopGradient() {
        listOf(LightAppColors, DarkAppColors).forEach { c ->
            assertNull(c.bubble.userMid)
            assertEquals(Brush.linearGradient(listOf(c.bubble.userStart, c.bubble.userEnd)), c.bubble.userFill())
        }
    }

    @Test fun liuliSchemes_equalAccentBrush() {
        assertEquals(LiuliMaterials.accentBrush, LiuliLightAppColors.bubble.userFill())
        assertEquals(LiuliMaterials.accentBrush, LiuliDarkAppColors.bubble.userFill())
    }

    @Test fun midStop_is055() {
        assertEquals(0.55f, USER_GRADIENT_MID_STOP)
    }
}
