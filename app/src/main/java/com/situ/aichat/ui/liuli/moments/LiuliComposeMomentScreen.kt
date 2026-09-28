package com.situ.aichat.ui.liuli.moments

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.situ.aichat.ui.moments.ComposeMomentRoute
import com.situ.aichat.ui.moments.ComposeMomentViewModel

/**
 * 琉璃发动态入口（朋友圈发布页·乙 §3.3.3）：D「此刻」版式——布局 / 键盘联动 / 天色 / 弹层时机全在共用骨架
 * [com.situ.aichat.ui.moments.ComposeMomentScaffold]，本入口只选琉璃脸 [LiuliComposeMomentFace]。签名不变（反射测试钉 = 1 + VM）。
 */
@Composable
internal fun LiuliComposeMomentScreen(
    onClose: () -> Unit,
    viewModel: ComposeMomentViewModel = hiltViewModel(),
) {
    ComposeMomentRoute(onClose, LiuliComposeMomentFace, viewModel)
}
