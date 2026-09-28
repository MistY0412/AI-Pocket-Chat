package com.situ.aichat.ui.liuli.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.situ.aichat.ui.liuli.designsystem.LiuliAmbientBackground
import com.situ.aichat.ui.liuli.glass.LiuliGlassHost
import com.situ.aichat.ui.liuli.page.LIULI_NAV_BAND_TAG
import com.situ.aichat.ui.liuli.page.LiuliCompactTopBar
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliTopScrollEdge

/**
 * 琉璃主页四页的共用骨架（图纸 2026-09-06 卷三 §4.2 · 契约 §6 Q-H1 甲）。
 *
 * 结构 = 一个**内层**玻璃宿主（[com.situ.aichat.ui.liuli.glass.LiuliGlassHost]）：`content`（列表 / 卡片流·柔光底）在下、`overlay`（收起后的玻璃顶栏 +
 * 「+」玻璃圆钮）在上。大标题带与搜索槽是内容的一部分（随内容滚走·Telegram 做法），不在 overlay 里
 * ——所以 [collapsed] 由调用方按**位置**判（大标题带是否滚出），与底栏缩丸的**方向**判各走各的。
 *
 * 外层还有一个宿主（[LiuliHomeHost]·给底栏那片玻璃用）；内外各取一次景，详情页时外层关门（A-5）。
 *
 * 「+」恒在 overlay、两态位置不变（A-17）：静止时它压在大标题带右端，收起后它在玻璃顶栏里，不跳。
 *
 * 收起态顶栏是悬浮胶囊（琉璃 2.0 卷二 §4.8-3）：状态栏那一截不再有玻璃挡着，overlay 最前面画一条顶部渐进模糊带
 * （琉璃 2.0 卷六·一：取代原状态栏柔光衬带·与二级页 / 聊天页同一个件），滚上去的内容不压在时钟下面。
 */
@Composable
fun LiuliHomeScaffold(
    title: String,
    collapsed: Boolean,
    plus: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LiuliGlassHost(
        modifier = modifier.fillMaxSize(),
        content = {
            // 柔光底**画在内容层里**（不在宿主 modifier 上）：宿主只录 content，底若只铺在宿主外面，overlay 里的玻璃
            // （「+」圆钮）切到的是透明底 → 合成后半透明 → 它自己的海拔影从片内透出来（Android 的 spot 影是八角形
            // 多边形·2026-09-06 圆钮甲装机发现「圆里一块八角亮斑」）。只画这一份，不在外层再铺一遍（独立复核 🔵-3）。
            LiuliAmbientBackground(Modifier.matchParentSize())
            content()
        },
        overlay = {
            // 收起态顶部渐进模糊带（琉璃 2.0 卷六·一：取代卷二 §4.8-3 的状态栏柔光衬带·与二级页 / 聊天页同一个件）；静止态照旧不画。
            if (collapsed) {
                LiuliTopScrollEdge(
                    statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    belowStatus = LiuliPageGeometry.compactBar + LiuliPageGeometry.edgeTail,
                    modifier = Modifier.testTag(LIULI_NAV_BAND_TAG),
                )
            }
            LiuliCompactTopBar(title = title, visible = collapsed)
            if (plus != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = LiuliHomeGeometry.titleTop, end = LiuliHomeGeometry.gutter)
                        // 版位 = 视觉 40；圆钮自带的 48dp 触达框超出约束时居中外溢（PITFALLS §1d）。
                        .size(LiuliHomeGeometry.plusButton),
                    contentAlignment = Alignment.Center,
                    content = { plus() },
                )
            }
        },
    )
}
