package com.situ.aichat.ui.liuli.chat

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.situ.aichat.ui.chat.MessageRowActions
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue

/** 琉璃消息行测试共用夹具（卷三：`LiuliMessageRowTest` 与 `LiuliReactionBurstTest` 共用）。全部回调空实现。 */
internal val liuliNoopRowActions = MessageRowActions(
    onVoiceToggle = {},
    onOpenImage = {},
    onSaveImage = {},
    onQuote = {},
    onDelete = {},
    onOpenMenu = { _, _, _ -> },
    onFlightBubblePositioned = { _, _ -> },
    onRegenerate = {},
    loadDiyImage = { null },
    onOpenDiyDetail = {},
    observeRedPacket = { flowOf(null) },
    onRedPacketClick = {},
    onAcceptInvite = {},
    onDeclineInvite = {},
    onEndMeeting = {},
    onContinueMeeting = {},
    onReviewOffline = {},
    observeAppointment = { flowOf(null) },
    onAppointmentAccept = {},
    onAppointmentDecline = {},
    onAppointmentReschedule = {},
    onAppointmentChangeApply = {},
    onAppointmentChangeKeep = {},
    onVoiceCascadePlayed = {},
    onOpenVoiceSetup = {},
)

/**
 * 泡下时间戳的边界（卷三 T2-1）：有合并读屏句时时间戳被 `clearAndSetSemantics {}` 隐去文字（F4 口径），但节点
 * 仍在未合并树里（空配置）——所以取「未合并树里整个落在泡底之下的节点」的并集当时间戳的边界（行里泡下只有它）。
 */
internal fun ComposeContentTestRule.stampBoundsBelow(bubble: Rect): Rect {
    val below = onAllNodes(SemanticsMatcher("任意节点") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }
        .filter { it.top >= bubble.bottom - 0.5f && it.height > 0f }
    assertTrue("泡下应有时间戳节点（泡 $bubble）", below.isNotEmpty())
    return Rect(below.minOf { it.left }, below.minOf { it.top }, below.maxOf { it.right }, below.maxOf { it.bottom })
}
