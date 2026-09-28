package com.situ.aichat.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/*
 * NavHost 的四个常规转场（push / 非手势 pop）。2026-09-24 自 AIChatApp.kt 原样搬出（只搬不改·给 ⛔ 大户减行·
 * 图纸 docs/handoff/2026-09-24-预测性返回原生卡片.md §4.7）；边缘手势返回不走这里，见 PredictiveBackCard.kt。
 *
 * 13.3 / nav-shell-1：详情页 push/pop 从右滑入 / 滑出（带视差，≈ iOS NavigationStack）；底部 tab 之间切换不横滑、
 * 只交叉淡入（≈ iOS TabView）。
 * 聊天页转场（2026-07-06 拍板·取代旧「壁纸沉浸重构①整页 fade」）：回归 iOS 式 push——聊天页从右整页
 * 滑入、底页 1/4 视差左推、返回镜像。与其他详情页唯一差别是**不掺 fade**：聊天页全屏不透明（壁纸或底色），
 * 掺 fade 会让壁纸半透明透出底页（=旧「横滑割裂」真凶）；壁纸铺满含状态栏后、是页面一部分，随整页同步滑。
 * 冷加载晚到的壁纸淡入兜底在 ChatScreen 壁纸层。
 */

private val navSlide = 300
private val navFade = 160

private fun isChatRoute(route: String?) = route?.startsWith("chat/") == true

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnterTransition(topLevelRoutes: Set<String>): EnterTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val tab = (from ?: "") in topLevelRoutes && (to ?: "") in topLevelRoutes
    return when {
        isChatRoute(from) || isChatRoute(to) -> slideInHorizontally(tween(navSlide)) { it }
        tab -> fadeIn(tween(navFade))
        else -> slideInHorizontally(tween(navSlide)) { it } + fadeIn(tween(navSlide))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition(topLevelRoutes: Set<String>): ExitTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val tab = (from ?: "") in topLevelRoutes && (to ?: "") in topLevelRoutes
    return when {
        isChatRoute(from) || isChatRoute(to) -> slideOutHorizontally(tween(navSlide)) { -it / 4 }
        tab -> fadeOut(tween(navFade))
        else -> slideOutHorizontally(tween(navSlide)) { -it / 4 } + fadeOut(tween(navSlide))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnterTransition(topLevelRoutes: Set<String>): EnterTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val tab = (from ?: "") in topLevelRoutes && (to ?: "") in topLevelRoutes
    return when {
        isChatRoute(from) || isChatRoute(to) -> slideInHorizontally(tween(navSlide)) { -it / 4 }
        tab -> fadeIn(tween(navFade))
        else -> slideInHorizontally(tween(navSlide)) { -it / 4 } + fadeIn(tween(navSlide))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExitTransition(topLevelRoutes: Set<String>): ExitTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val tab = (from ?: "") in topLevelRoutes && (to ?: "") in topLevelRoutes
    return when {
        isChatRoute(from) || isChatRoute(to) -> slideOutHorizontally(tween(navSlide)) { it }
        tab -> fadeOut(tween(navFade))
        else -> slideOutHorizontally(tween(navSlide)) { it } + fadeOut(tween(navSlide))
    }
}
