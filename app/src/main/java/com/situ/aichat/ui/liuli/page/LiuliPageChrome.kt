package com.situ.aichat.ui.liuli.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 页 chrome 三件（大标题带 / 小节标题 / 收起后的玻璃顶栏）。
 *
 * 卷四 A-2 从 `ui/liuli/home/LiuliHomeScaffold.kt` **只搬不改**搬来并公有化，另按 §2.1 给玻璃顶栏加
 * [LiuliCompactTopBar] 的 `leading` / `trailing` / `subBar` 三个槽（主页与本卷四屏一律传 null / subBar
 * 只有资料页传·**行为对既有调用方零变**）。几何改读 [LiuliPageGeometry]（与 `LiuliHomeGeometry` 同值·
 * 由 `LiuliPageGeometryTest` 钉住）。
 */

/** 收起顶栏的进出时长（卷三 §4.2·180ms fade + 6dp slide）。 */
private const val COMPACT_BAR_MS = 180
private val COMPACT_BAR_SLIDE = 6.dp

/** 悬浮胶囊顶栏两侧内缩（琉璃 2.0 卷二 §4.8-1）：= gutter 20 − 4——40dp 返回圆钮在 x = 20 时落在胶囊内 4dp、上下各 2dp。 */
private val COMPACT_PILL_INSET = 16.dp

/**
 * 大标题带（列表的第 0 个 item / 卡片流的第一项）：顶 + 2 起、40 高、左 20，
 * 右侧留 52（钮 40 + 缝 12）给恒在 overlay 的尾随件。
 *
 * 主页里它落在「状态栏底 + 2」；二级页里列表另带 `contentPadding.top = 状态栏 + 导航行`，
 * 于是同一个件落在「导航行底 + 2」（A-3）。
 */
@Composable
fun LiuliLargeTitle(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = LiuliPageGeometry.titleTop,
                start = LiuliPageGeometry.gutter,
                end = LiuliPageGeometry.gutter + LiuliPageGeometry.titleEndReserve,
            )
            .height(LiuliPageGeometry.titleHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            style = AppTypography.titleLarge.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.W700),
            color = AppTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * 列表小节标题（「置顶」/「对话」）：12/500 字距 .06em `text.secondary`（卷三：压裸柔光底 ≥ 4.5），上 14 下 6；
 * 左内距 = gutter + 卡内行距（= 36·与卡内行字起点及设置组标题同列·卷三 §4.7）。
 */
@Composable
fun LiuliSectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = LiuliPageGeometry.gutter + LiuliPageGeometry.groupPadH, end = LiuliPageGeometry.gutter, top = 14.dp, bottom = 6.dp),
    ) {
        Text(
            text,
            style = AppTypography.caption.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.W500,
                letterSpacing = 0.06.em,
            ),
            color = AppTheme.colors.text.secondary,
            maxLines = 1,
        )
    }
}

/**
 * 收起后的**悬浮玻璃胶囊顶栏**（琉璃 2.0 卷二 §4.8-1）：状态栏下方、两侧各内缩 [COMPACT_PILL_INSET] 的一片
 * [LiuliShapes].compactPill 22 圆角玻璃，高 44、小标题居中。[subBar] 非空时同一片胶囊再往下延 56
 * （8 + 40 玻璃 pill + 8·A-11），胶囊高 100；覆盖区总高仍 = 状态栏 + 44（+ 56）。状态栏那一截不再有玻璃挡着，
 * 由页壳在 overlay 里画一条顶部渐进模糊带（琉璃 2.0 卷六·一·`LiuliTopScrollEdge`·`LiuliPage` / `LiuliHomeScaffold`）。
 *
 * **不挂任何 pointerInput**（卷三 A-17）：它是纯玻璃条，列表在它下面照常滚。
 * [leading] / [trailing] 是给「返回钮住进顶栏」那类页型留的槽；本卷四屏与主页四 Tab 都不传——它们的
 * 圆钮**恒在 overlay 同一位置两态不跳**（A-3），不随顶栏进出。
 */
@Composable
fun BoxScope.LiuliCompactTopBar(
    title: String,
    visible: Boolean,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subBar: (@Composable () -> Unit)? = null,
    /** 书名后的小字（琉璃 2.0 卷六·三·下甲·阅读器「· 第 N 章」·加法零回归：null = 原渲染）。 */
    titleSuffix: String? = null,
) {
    val dark = LocalIsDarkTheme.current
    val reduceMotion = rememberReduceMotion()
    val slidePx = with(LocalDensity.current) { COMPACT_BAR_SLIDE.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = if (reduceMotion) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(COMPACT_BAR_MS)) + slideInVertically(tween(COMPACT_BAR_MS)) { -slidePx }
        },
        exit = if (reduceMotion) {
            fadeOut(tween(0))
        } else {
            fadeOut(tween(COMPACT_BAR_MS)) + slideOutVertically(tween(COMPACT_BAR_MS)) { -slidePx }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
            Box(
                Modifier
                    .padding(horizontal = COMPACT_PILL_INSET)
                    .fillMaxWidth()
                    .liuliGlass(LiuliShapes.compactPill, dark = dark),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(LiuliPageGeometry.compactBar), contentAlignment = Alignment.Center) {
                        if (leading != null) {
                            Box(
                                Modifier.align(Alignment.CenterStart).padding(start = LiuliPageGeometry.gutter - COMPACT_PILL_INSET),
                                content = { leading() },
                            )
                        }
                        // 标题两侧让位（卷六·三·下甲·加法零回归）：只有传了 leading / trailing 才让（现有调用方都不传 → 走原 Text 分支逐字节同渲染）。
                        val titleSide = if (leading != null || trailing != null) {
                            LiuliPageGeometry.gutter - COMPACT_PILL_INSET + LiuliPageGeometry.backButton + LiuliPageGeometry.titleGap
                        } else {
                            0.dp
                        }
                        if (titleSuffix == null && titleSide == 0.dp) {
                            Text(
                                title,
                                style = AppTypography.caption.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
                                color = LiuliTheme.onGlass.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Row(Modifier.padding(horizontal = titleSide), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    title,
                                    style = AppTypography.caption.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
                                    color = LiuliTheme.onGlass.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (titleSuffix != null) {
                                    Text(
                                        titleSuffix,
                                        style = AppTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.W500),
                                        color = LiuliTheme.onGlass.secondary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                        if (trailing != null) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = LiuliPageGeometry.gutter - COMPACT_PILL_INSET),
                                horizontalArrangement = Arrangement.spacedBy(LiuliPageGeometry.actionButtonGap),
                                verticalAlignment = Alignment.CenterVertically,
                                content = trailing,
                            )
                        }
                    }
                    if (subBar != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(LiuliPageGeometry.subBar)
                                .padding(
                                    horizontal = LiuliPageGeometry.gutter - COMPACT_PILL_INSET,
                                    vertical = (LiuliPageGeometry.subBar - LiuliPageGeometry.stripGlass) / 2,
                                ),
                            content = { subBar() },
                        )
                    }
                }
            }
        }
    }
}
