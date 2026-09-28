package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.liuli.page.LiuliTopScrollEdge

/** 带子在状态栏以下还要伸多远 = 顶栏顶距 6 + 顶栏 44 + 12。 */
internal val SCROLL_EDGE_BELOW_STATUS: Dp = 62.dp

/** 琉璃聊天页顶部渐进模糊带（卷四 §0.2-5 / 卷四复核 R1）：实现自卷六·一起是页壳共用件 [LiuliTopScrollEdge]，这里只给聊天页的伸出量。 */
@Composable
internal fun BoxScope.LiuliChatScrollEdge(statusBarTop: Dp) = LiuliTopScrollEdge(statusBarTop, SCROLL_EDGE_BELOW_STATUS)
