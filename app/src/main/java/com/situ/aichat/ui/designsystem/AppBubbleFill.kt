package com.situ.aichat.ui.designsystem

import androidx.compose.ui.graphics.Brush

/** 用户泡三段渐变的中段位置（= 琉璃 `LiuliMaterials.accentBrush` 的 0.55·卷三 §0.2-3）。 */
const val USER_GRADIENT_MID_STOP = 0.55f

/**
 * 用户泡渐变刷的唯一出口（琉璃 2.0 卷三 §3.1）：[AppBubbleColors.userMid] 为 null（暖陶）→ 与改前逐字相同的两段
 * `userStart → userEnd`；非 null（琉璃）→ 三段。135° 端点随绘制区域解算。调用方照旧包在 `remember(colors) { }` 里。
 */
fun AppBubbleColors.userFill(): Brush {
    val mid = userMid
    return if (mid == null) {
        Brush.linearGradient(listOf(userStart, userEnd))
    } else {
        Brush.linearGradient(0f to userStart, USER_GRADIENT_MID_STOP to mid, 1f to userEnd)
    }
}
