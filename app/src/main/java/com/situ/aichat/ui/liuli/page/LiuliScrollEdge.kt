package com.situ.aichat.ui.liuli.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.glass.LiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.currentLiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.liuliEdgeBlur
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.theme.LocalIsDarkTheme

// 屏边渐进模糊带（琉璃 2.0 卷六·一 §3.9：卷四聊天页顶带 `LiuliChatScrollEdge` 的实现只搬不改到页壳，
// 「状态栏以下伸多远」提成形参；新增屏底镜像版）。聊天页 / 二级页页壳 / 主页四 Tab 收起态共用这一个件。

/** 带子向左 / 右 / 上超出屏幕的量：玻璃边缘的高光与折射都落在屏外。 */
internal val SCROLL_EDGE_OVERSCAN: Dp = 32.dp
/** 渐隐段中点的遮罩浓度（线性两段近似缓出）。 */
internal const val SCROLL_EDGE_MID_ALPHA = 0.35f
/** Haze 路径的遮挡色最浓处（屏顶）：浅 = 白 40%、深 = DuskBase 50%（卷四复核 R1·用户附图的淡提白）。 */
internal const val SCROLL_EDGE_WASH_LIGHT = 0.40f
internal const val SCROLL_EDGE_WASH_DARK = 0.50f
/** Haze 路径的保险渐隐从渐隐段的这个比例处开始（此处模糊半径已不到 2.5dp，淡出看不出叠影）。 */
internal const val SCROLL_EDGE_SAFETY_FROM = 0.6f

/**
 * 遮罩四个停点（纯函数·T1）：[overscanPx] 为带子在屏外的上沿高度，[statusBarPx] 为状态栏高（底带时为导航栏），[heightPx] 为带子总高。
 * 返回 (位置 0..1, 不透明度) 四对：顶 → 状态栏底 全实；其下到带底线性两段渐隐到 0。毛玻璃档 / 只着色用。
 */
internal fun liuliScrollEdgeStops(overscanPx: Float, statusBarPx: Float, heightPx: Float): List<Pair<Float, Float>> {
    val solid = (overscanPx + statusBarPx) / heightPx
    val mid = solid + (1f - solid) * 0.5f
    return listOf(0f to 1f, solid to 1f, mid to SCROLL_EDGE_MID_ALPHA, 1f to 0f)
}

/**
 * Haze 路径叠在渐进模糊上的遮挡色停点（纯函数·T1）：屏顶 [top] → 状态栏底（底带时为导航栏顶）75% → 渐隐段中点 25% → 带底 0。
 * 只管「提白 / 压暗」多少，模糊由 [liuliEdgeBlur] 自己按行递减。
 */
internal fun liuliScrollEdgeWashStops(overscanPx: Float, statusBarPx: Float, heightPx: Float, top: Float): List<Pair<Float, Float>> {
    val solid = (overscanPx + statusBarPx) / heightPx
    val mid = solid + (1f - solid) * 0.5f
    return listOf(0f to top, solid to top * 0.75f, mid to top * 0.25f, 1f to 0f)
}

/**
 * Haze 路径的保险渐隐（纯函数·T1）：渐隐段前 [SCROLL_EDGE_SAFETY_FROM] 全实，其后到带底淡到 0。正常时模糊到带底已归零、
 * 这层看不出；万一 Haze 退回整片匀糊，带底也不会出现硬边。
 */
internal fun liuliScrollEdgeSafetyStops(overscanPx: Float, statusBarPx: Float, heightPx: Float): List<Pair<Float, Float>> {
    val solid = (overscanPx + statusBarPx) / heightPx
    return listOf(0f to 1f, (solid + (1f - solid) * SCROLL_EDGE_SAFETY_FROM) to 1f, 1f to 0f)
}

/** 屏顶带的停点翻成屏底带：位置 p → 1 − p 再倒序（纯函数·T1）。 */
internal fun List<Pair<Float, Float>>.mirroredEdgeStops(): List<Pair<Float, Float>> =
    map { (p, a) -> (1f - p) to a }.reversed()

/**
 * 页壳顶带在状态栏以下伸多远（琉璃 2.0 卷六·一 §0.2-3·纯函数·T1）：未收起 = 导航行 + 大标题带顶距（恰到大标题顶，
 * 静止时一个像素都不碰标题）；收起 = 悬浮胶囊（+ subBar）+ 尾巴 [LiuliPageGeometry.edgeTail]。
 */
internal fun liuliPageEdgeBelowStatus(collapsed: Boolean, hasSubBar: Boolean): Dp = if (!collapsed) {
    LiuliPageGeometry.navRow + LiuliPageGeometry.titleTop
} else {
    LiuliPageGeometry.compactBar + (if (hasSubBar) LiuliPageGeometry.subBar else 0.dp) + LiuliPageGeometry.edgeTail
}

/**
 * 屏顶渐进模糊带（卷四 §0.2-5·设计稿 ② 乙；卷四复核 R1 按用户 09-26 附图改成真渐进；卷六·一起是页壳共用件）：
 * - Haze（通透 / 标准档）：[liuliEdgeBlur] 真渐进模糊（屏顶最糊、往下逐行变清、到带底归零）+ 一层同样渐淡的遮挡色 +
 *   保险渐隐；带子左 / 右 / 上超出屏幕 [SCROLL_EDGE_OVERSCAN]（Haze 在屏外照样取得到景）；
 * - 自研毛玻璃档：匀糊 + 遮挡色，DstIn 竖向遮罩渐隐；带子**不出屏**（屏外无景，出屏会让边上糊不透）；
 * - 只着色（安卓 12 以下 / 宿主关门）：大面板玻璃兜底 + DstIn 遮罩（自然退化成颜色渐隐）。
 * [belowStatus] = 带子在状态栏以下伸多远（聊天页 62·页壳见 [liuliPageEdgeBelowStatus]）。
 * 放在 overlay 里、顶栏之前声明（顶栏压在它上面）。不收点击。
 */
@Composable
internal fun BoxScope.LiuliTopScrollEdge(
    statusBarTop: Dp,
    belowStatus: Dp,
    modifier: Modifier = Modifier,
    /** 遮挡色（琉璃 2.0 卷六·三·下甲·阅读器传纸色；null = 原值 白 / DuskBase）。 */
    wash: Color? = null,
) {
    val dark = LocalIsDarkTheme.current
    val engine = currentLiuliGlassEngine()
    val overscan = if (engine == LiuliGlassEngine.FROSTED_BLUR) 0.dp else SCROLL_EDGE_OVERSCAN
    val density = LocalDensity.current
    val overscanPx = with(density) { overscan.toPx() }
    val statusBarPx = with(density) { statusBarTop.toPx() }
    val heightPx = overscanPx + with(density) { (statusBarTop + belowStatus).toPx() }
    val washColor = wash ?: if (dark) Palette.DuskBase else Color.White
    val washTop = if (dark) SCROLL_EDGE_WASH_DARK else SCROLL_EDGE_WASH_LIGHT
    val band = modifier
        .align(Alignment.TopCenter)
        .layout { measurable, constraints ->
            val over = overscan.roundToPx()
            val height = (statusBarTop + belowStatus).roundToPx() + over
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + over * 2, height))
            layout(constraints.maxWidth, height - over) { placeable.place(-over, -over) }
        }
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    val washLayer = Modifier.drawBehind {
        val stops = liuliScrollEdgeWashStops(overscanPx, statusBarPx, size.height, washTop)
        drawRect(Brush.verticalGradient(*stops.map { (p, a) -> p to washColor.copy(alpha = a) }.toTypedArray()))
    }
    when (engine) {
        LiuliGlassEngine.HAZE -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeSafetyStops(overscanPx, statusBarPx, size.height))
                }
                .liuliEdgeBlur(fadeFromPx = overscanPx, fadeToPx = heightPx)
                .then(washLayer),
        )
        LiuliGlassEngine.FROSTED_BLUR -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeStops(overscanPx, statusBarPx, size.height))
                }
                .liuliEdgeBlur(fadeFromPx = overscanPx, fadeToPx = heightPx)
                .then(washLayer),
        )
        LiuliGlassEngine.TINT_ONLY -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeStops(overscanPx, statusBarPx, size.height))
                }
                .liuliGlass(LiuliShapes.rect, dark = dark, role = LiuliGlassRole.Panel),
        )
    }
}

/**
 * 屏底渐进模糊带（琉璃 2.0 卷六·一：顶带的镜像）：屏底最糊、往上逐行变清，到带顶归零；实心段 = 导航栏。
 * 有浮动钮的页由 `LiuliPage(bottomEdge = …)` 画。不收点击。
 */
@Composable
internal fun BoxScope.LiuliBottomScrollEdge(
    navBarBottom: Dp,
    aboveNav: Dp,
    modifier: Modifier = Modifier,
    /** 遮挡色（琉璃 2.0 卷六·三·下甲·阅读器传纸色；null = 原值 白 / DuskBase）。 */
    wash: Color? = null,
) {
    val dark = LocalIsDarkTheme.current
    val engine = currentLiuliGlassEngine()
    val overscan = if (engine == LiuliGlassEngine.FROSTED_BLUR) 0.dp else SCROLL_EDGE_OVERSCAN
    val density = LocalDensity.current
    val overscanPx = with(density) { overscan.toPx() }
    val navPx = with(density) { navBarBottom.toPx() }
    val visiblePx = with(density) { (navBarBottom + aboveNav).toPx() }
    val washColor = wash ?: if (dark) Palette.DuskBase else Color.White
    val washTop = if (dark) SCROLL_EDGE_WASH_DARK else SCROLL_EDGE_WASH_LIGHT
    val band = modifier
        .align(Alignment.BottomCenter)
        .layout { measurable, constraints ->
            val over = overscan.roundToPx()
            val height = (navBarBottom + aboveNav).roundToPx() + over
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + over * 2, height))
            layout(constraints.maxWidth, height - over) { placeable.place(-over, 0) }
        }
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    val washLayer = Modifier.drawBehind {
        val stops = liuliScrollEdgeWashStops(overscanPx, navPx, size.height, washTop).mirroredEdgeStops()
        drawRect(Brush.verticalGradient(*stops.map { (p, a) -> p to washColor.copy(alpha = a) }.toTypedArray()))
    }
    when (engine) {
        LiuliGlassEngine.HAZE -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeSafetyStops(overscanPx, navPx, size.height).mirroredEdgeStops())
                }
                .liuliEdgeBlur(fadeFromPx = 0f, fadeToPx = visiblePx, blurAtEnd = true)
                .then(washLayer),
        )
        LiuliGlassEngine.FROSTED_BLUR -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeStops(overscanPx, navPx, size.height).mirroredEdgeStops())
                }
                .liuliEdgeBlur(fadeFromPx = 0f, fadeToPx = visiblePx, blurAtEnd = true)
                .then(washLayer),
        )
        LiuliGlassEngine.TINT_ONLY -> Box(
            band
                .drawWithContent {
                    drawContent()
                    drawEdgeMask(liuliScrollEdgeStops(overscanPx, navPx, size.height).mirroredEdgeStops())
                }
                .liuliGlass(LiuliShapes.rect, dark = dark, role = LiuliGlassRole.Panel),
        )
    }
}

/** 按停点把已画内容的不透明度乘上一道竖向渐变（DstIn）。 */
private fun DrawScope.drawEdgeMask(stops: List<Pair<Float, Float>>) {
    drawRect(
        brush = Brush.verticalGradient(*stops.map { (p, a) -> p to Color.Black.copy(alpha = a) }.toTypedArray()),
        blendMode = BlendMode.DstIn,
    )
}
