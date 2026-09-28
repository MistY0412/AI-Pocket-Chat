package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmentPosition
import com.situ.aichat.ui.liuli.designsystem.liuliAccentFill
import com.situ.aichat.ui.liuli.designsystem.liuliCardSegment
import com.situ.aichat.ui.liuli.page.LiuliFloatingBar
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.moments.MomentCommentRowContent
import com.situ.aichat.ui.moments.MomentCommentRowModel
import com.situ.aichat.ui.moments.MomentReplyChip
import com.situ.aichat.ui.moments.momentCommentIndent
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 评论行上下内距（= 暖陶行上下 4 + 行间 12 + 4 的一半·分段卡里行与行的节奏同暖陶 20）。 */
private val COMMENT_ROW_PAD_V = 10.dp

/** 详情正文卡头像（= 暖陶 44·光环外径 48）。 */
internal val DETAIL_AVATAR = 44.dp

/** 发送钮：36 圆 · 纸飞机 18（= 暖陶）；禁用透明度 = `LiuliButton` 禁用档。 */
private val SEND_DISC = 36.dp
private val SEND_ICON = 18.dp
private const val SEND_DISABLED_ALPHA = 0.38f

/**
 * 评论楼层的一行（琉璃 2.0 卷六·二 §4.5）：自己画分段卡的一截，一组行拼成一张半透明卡；点行 = 回复、长按 = 玻璃菜单
 * （回复 / 删除评论）→ 琉璃确认。点击面排在分段卡之后：分段卡把内容画在本段形状的裁切里 → ripple 不出段；行间不画发丝（同暖陶评论无分隔）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiuliMomentCommentRow(
    row: MomentCommentRowModel,
    timeText: String,
    position: LiuliSegmentPosition,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val replyText = stringResource(R.string.moment_comment_reply)
    val deleteText = stringResource(R.string.moment_comment_delete)
    Box(Modifier.fillMaxWidth().liuliCardSegment(position, dark)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onReply, onLongClick = { menuExpanded = true })
                .padding(
                    start = LiuliPageGeometry.groupPadH + momentCommentIndent(row.level),
                    end = LiuliPageGeometry.groupPadH,
                    top = COMMENT_ROW_PAD_V,
                    bottom = COMMENT_ROW_PAD_V,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            MomentCommentRowContent(row.comment, row.authorName, row.authorAvatarPath, timeText)
        }
        LiuliPopupMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = listOfNotNull(
                LiuliMenuEntry(replyText, onClick = onReply),
                if (row.canDelete) LiuliMenuEntry(deleteText, danger = true, onClick = { showDeleteConfirm = true }) else null,
            ),
        )
    }
    if (showDeleteConfirm) {
        LiuliDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.moment_comment_delete_title),
            body = stringResource(R.string.moment_comment_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { showDeleteConfirm = false; onDelete() },
            confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** 底部悬浮玻璃评论条（跟键盘·对齐照暖陶居中）：回复 chip + 多行输入（≤ 4 行）+ 发送钮。 */
@Composable
internal fun LiuliMomentCommentBar(
    text: String,
    replyName: String?,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
) {
    LiuliFloatingBar {
        if (replyName != null) MomentReplyChip(replyName, onCancelReply)
        LiuliField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(if (replyName != null) R.string.moment_detail_reply_hint else R.string.moment_detail_comment_hint),
            singleLine = false,
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        LiuliMomentSendButton(enabled = canSend, onClick = onSend)
    }
}

/** 发送钮：48 触达里 36 主色渐变圆 + 白纸飞机；空白时淡到 38% 且点不动。 */
@Composable
internal fun LiuliMomentSendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(LiuliPageGeometry.touchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // alpha 必须排在画底之前（PITFALLS §1d·LiuliButton 同法），否则禁用只淡图标、渐变底满色。
            modifier = Modifier.size(SEND_DISC).alpha(if (enabled) 1f else SEND_DISABLED_ALPHA).liuliAccentFill(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppMomentIcons.PaperPlane, contentDescription = stringResource(R.string.moment_detail_send), tint = Palette.White, modifier = Modifier.size(SEND_ICON))
        }
    }
}
