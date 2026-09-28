package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.situ.aichat.ui.components.rememberReduceMotion

/** 光斑半径 = 形状短边 × 本值（同 Haze `interactionLightRadiusFraction` 默认 0.7）。 */
internal const val PRESS_LIGHT_RADIUS_FRACTION = 0.7f
/** 光斑中心不透明度（浅 / 深）。 */
internal const val PRESS_LIGHT_ALPHA_LIGHT = 0.50f
internal const val PRESS_LIGHT_ALPHA_DARK = 0.20f
private const val PRESS_LIGHT_IN_MS = 90
private const val PRESS_LIGHT_OUT_MS = 220

/**
 * 玻璃按压光（卷四 §0.2-4）：手指按住时，一团白色径向光跟着手指走，松手淡出。指针在 Initial 相位**只观察、不消费**，
 * 点击 / 长按照常。须排在玻璃之后（画在玻璃的裁切里）。减弱动画：不做淡入淡出，直接亮 / 灭。
 */
fun Modifier.liuliPressLight(dark: Boolean, enabled: Boolean = true): Modifier = composed {
    val reduceMotion = rememberReduceMotion()
    var position by remember { mutableStateOf(Offset.Unspecified) }
    var down by remember { mutableStateOf(false) }
    val strength by animateFloatAsState(
        targetValue = if (down && enabled) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(if (down) PRESS_LIGHT_IN_MS else PRESS_LIGHT_OUT_MS),
        label = "liuliPressLight",
    )
    val peak = if (dark) PRESS_LIGHT_ALPHA_DARK else PRESS_LIGHT_ALPHA_LIGHT
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                position = first.position
                down = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull { it.id == first.id }?.let { position = it.position }
                } while (event.changes.any { it.pressed })
                down = false
            }
        }
        .drawWithContent {
            drawContent()
            if (strength > 0f && position.isSpecified) {
                val radius = size.minDimension * PRESS_LIGHT_RADIUS_FRACTION
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = peak * strength), Color.White.copy(alpha = 0f)),
                        center = position,
                        radius = radius,
                    ),
                    radius = radius,
                    center = position,
                )
            }
        }
}
