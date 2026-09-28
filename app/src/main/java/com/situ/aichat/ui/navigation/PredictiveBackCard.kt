package com.situ.aichat.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/*
 * 预测返回「原生卡片」的导航接线（图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §4.6）：
 * 导航库只拿零视觉的定时占位转场保两页存活，画面由每页的卡片包装按 [PredictiveBackMotion] 自画；
 * 卡片后面的舞台与当前页柔影 / 白边见 BackCardStage.kt（微图纸 2026-09-28-预测返回舞台底）。
 */

/** 入场占位：零视觉、实测零时长（初值 = 终值）；入场页本就是新的栈顶页、始终在场，不需要它留人——只为与出场成对。 */
internal val BackCardHoldEnter: EnterTransition =
    fadeIn(tween(BackCardSpec.HOLD_MS, easing = LinearEasing), initialAlpha = 1f)

/** 出场占位：真时长 HOLD_MS（终点 [BackCardSpec.HOLD_EXIT_ALPHA] ≠ 1 才装得进 tween），让导航库松手后再留当前页 (1 − p) × HOLD_MS；画面全由卡片包装自画。 */
private val BackCardHoldExit: ExitTransition =
    fadeOut(tween(BackCardSpec.HOLD_MS, easing = LinearEasing), targetAlpha = BackCardSpec.HOLD_EXIT_ALPHA)

/** NavHost `predictivePopExitTransition` 专用：导航库选中预测转场的同一刻建会话——占位与会话同生（图纸 §0.2-1）。 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.backCardHoldExit(
    motion: PredictiveBackMotion, swipeEdge: Int,
): ExitTransition {
    motion.onPredictiveSeek(closing = initialState, entering = targetState, swipeEdge = swipeEdge)
    return BackCardHoldExit
}

/** `composable(...)` + 预测返回卡片包装。NavHost 里的页面一律用它注册（裸 `composable` = 该页手势返回冻 450ms）。 */
internal fun NavGraphBuilder.backCardComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        PredictiveBackCard(entry.id) { content(entry) }
    }
}

@Composable
private fun PredictiveBackCard(entryId: String, content: @Composable () -> Unit) {
    val motion = LocalPredictiveBackMotion.current
    if (motion == null) {
        content()
        return
    }
    DisposableEffect(motion, entryId) { onDispose { motion.forget(entryId) } }
    val stage = rememberBackCardStageStyle()
    val cardBase = stage.base
    val density = LocalDensity.current
    val marginPx = with(density) { BackCardSpec.MARGIN_DP.dp.toPx() }
    val enteringOffsetPx = with(density) { BackCardSpec.ENTERING_START_OFFSET_DP.dp.toPx() }
    // 外层：满版、不缩（微图纸 2026-09-28-预测返回舞台底）。上一页 = 舞台（卡片之下）+ 遮罩（盖住卡片与空隙·原生口径·
    // 当前页 zIndex 更高所以在它之下）；当前页 = 柔影（卡片之下）。没有会话时一律不画。
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val shadowPaints by lazy { BackCardShadowPaints(this) }
                onDrawWithContent {
                    when (motion.roleFor(entryId)) {
                        BackCardRole.Entering -> drawBackCardStage(stage, motion.scrimFractionFor(entryId))
                        BackCardRole.Closing ->
                            motion.transformFor(entryId, size.width, size.height, marginPx, enteringOffsetPx)
                                ?.let { drawBackCardShadow(stage, it, motion.shadowCornerPx(), shadowPaints) }
                        null -> Unit
                    }
                    drawContent()
                    val a = stage.tone.scrim * motion.scrimFractionFor(entryId)
                    if (a > 0f) drawRect(stage.tint, alpha = a)
                }
            },
    ) {
        // 内层 = 卡片：先决定放不放（已弹出页不放置）→ 变换 + 圆角裁切 → 卡片底色（只在有角色时）→ 页面内容 → 琉璃白边（只当前页）。
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (!motion.isHidden(entryId)) placeable.place(0, 0)
                    }
                }
                .graphicsLayer {
                    val t = motion.transformFor(entryId, size.width, size.height, marginPx, enteringOffsetPx)
                    val shape = motion.shapeFor(entryId)
                    if (t == null || shape == null) {
                        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f; alpha = 1f
                        this.shape = RectangleShape; clip = false
                    } else {
                        scaleX = t.scale; scaleY = t.scale
                        translationX = t.translationX; translationY = t.translationY
                        alpha = t.alpha
                        this.shape = shape; clip = true
                    }
                }
                .drawBehind { if (motion.shapeFor(entryId) != null) drawRect(cardBase) }
                .drawWithCache {
                    // 读会话 = 换会话时重建外形；逐帧只读浮现度。
                    val shape = motion.shapeFor(entryId)
                    val rimOutline = if (stage.rim != null && shape != null) shape.createOutline(size, layoutDirection, this) else null
                    onDrawWithContent {
                        drawContent()
                        if (rimOutline != null && motion.roleFor(entryId) == BackCardRole.Closing) {
                            motion.transformFor(entryId, size.width, size.height, marginPx, enteringOffsetPx)
                                ?.let { drawBackCardRim(stage, rimOutline, cardEdgeEmergence(it.scale)) }
                        }
                    }
                },
        ) { content() }
    }
}
