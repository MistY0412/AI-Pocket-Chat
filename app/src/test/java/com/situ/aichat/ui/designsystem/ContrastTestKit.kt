package com.situ.aichat.ui.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue

// 对比度测试的共用辅助（琉璃 2.0 卷三 §2.1·自 ColorContrastTest 私有成员逐字搬出，函数体不改）：
// ColorContrastTest 与 LiuliColorContrastTest 共用。

/** WCAG 1.4.11 非文字对比门槛（纯装饰图形 / 图标压实色底）。 */
internal const val NON_TEXT = 3.0

internal fun assertContrast(fg: Color, bg: Color, min: Double, label: String) {
    val r = ColorContrast.ratio(fg, bg)
    assertTrue("$label: 实测 ${"%.2f".format(r)}:1 < 要求 ${"%.1f".format(min)}:1", r >= min)
}

/** alpha 合成：fg 以 [alpha] 覆于实底 bg 上的等效实色（对齐 Compose `copy(alpha=)` over 实底的渲染）。 */
internal fun over(fg: Color, alpha: Float, bg: Color): Color = Color(
    red = fg.red * alpha + bg.red * (1 - alpha),
    green = fg.green * alpha + bg.green * (1 - alpha),
    blue = fg.blue * alpha + bg.blue * (1 - alpha),
)
