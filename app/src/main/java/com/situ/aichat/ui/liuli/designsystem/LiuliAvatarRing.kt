package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 光环宽（过审稿 `.ring{padding:2px}`）：头像 = 外径 − 2 × 本值。 */
val LiuliAvatarRingWidth: Dp = 2.dp

/**
 * 主页头像的渐变光环（卷三 §3.4·过审稿 `ringG`·纯装饰）：外径 [outer] 的渐变圆盘，头像居中压在上面、四周露 2dp。
 * [content] 负责画头像本身（尺寸 = [outer] − 4dp）。聊天顶栏的心情环不用本件（那是另一件事）。
 */
@Composable
fun LiuliAvatarRing(outer: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val (a, b, c) = LiuliPalette.avatarRing(LocalIsDarkTheme.current)
    Box(
        modifier = modifier
            .size(outer)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b, c)))
            .padding(LiuliAvatarRingWidth),
        contentAlignment = Alignment.Center,
    ) { content() }
}
