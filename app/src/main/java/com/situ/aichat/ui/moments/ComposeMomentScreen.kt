package com.situ.aichat.ui.moments

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 朋友圈发布页·暖陶入口（朋友圈发布页·乙 §3.3.3）：D「此刻」版式——布局 / 键盘联动 / 天色 / 弹层时机全在共用骨架
 * [ComposeMomentScaffold]，本入口只选暖陶脸 [WarmComposeMomentFace]。签名不变（反射测试钉 onClose + VM = 2）。
 */
@Composable
fun ComposeMomentScreen(
    onClose: () -> Unit,
    viewModel: ComposeMomentViewModel = hiltViewModel(),
) {
    ComposeMomentRoute(onClose, WarmComposeMomentFace, viewModel)
}
