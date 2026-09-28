package com.situ.aichat.ui.moments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.MomentCommentEntity
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppElevation
import com.situ.aichat.ui.designsystem.AppListDivider
import com.situ.aichat.ui.designsystem.AppMenu
import com.situ.aichat.ui.designsystem.AppMenuItem
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.AppTextArea
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.grainSurface
import com.situ.aichat.util.DateFormatters

/**
 * 朋友圈详情（M06 7.2.8，对齐 iOS `MomentDetailView`）：正文区 + 点赞名单 + 展平评论树 + 底部评论输入栏
 * （含回复@目标）。提交评论 → VM 落库 + 排 AI 延迟回复。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MomentDetailScreen(
    onBack: () -> Unit,
    viewModel: MomentDetailViewModel = hiltViewModel(),
) {
    val post by viewModel.post.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val composer = rememberMomentCommentComposerState()

    val meLabel = stringResource(R.string.moment_author_me)
    val aiLabel = stringResource(R.string.moment_author_ai)
    val relStrings = momentRelativeTimeStrings()
    val nowMillis = System.currentTimeMillis()
    val userName = momentUserName(userProfile, meLabel)
    val userAvatarPath = userProfile?.avatarPath

    // 门楣升起态要读滚动位置，故 listState 从内容 lambda 提到屏级（顶栏在 Scaffold 参数里，看不见 lambda 内的局部量）。
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.moment_detail_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )
        },
        bottomBar = {
            CommentInputBar(
                text = composer.text,
                replyName = composer.replyTarget?.name,
                onTextChange = { composer.text = it },
                onCancelReply = { composer.replyTarget = null },
                onSend = { composer.send(viewModel::submitComment) },
                modifier = Modifier.imePadding(),
            )
        },
    ) { padding ->
        val p = post
        if (p == null) {
            Box(
                Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.moment_detail_deleted), color = AppTheme.colors.text.secondary)
            }
            return@Scaffold
        }
        val rows = remember(p, characters, userAvatarPath, meLabel, aiLabel) { momentCommentRows(p, characters, userAvatarPath, meLabel, aiLabel) }
        MomentCommentsAutoScrollEffect(listState, p.comments.size, rows.isNotEmpty())
        LazyColumn(
            state = listState,
            // 页底 = surface.base + 纸感 grain；gutter 20（契约 §2.4·v2 军规）。
            modifier = Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "content") {
                MomentPostContentSection(p, characters, userName, userAvatarPath, relStrings, nowMillis, meLabel, aiLabel)
            }
            if (p.likes.isNotEmpty()) {
                item(key = "likes") { MomentLikesSection(p, characters, meLabel, aiLabel) }
            }
            item(key = "comment-header") { CommentHeader(p.comments.size) }
            items(rows, key = { it.comment.uuid }) { row ->
                MomentCommentRow(
                    comment = row.comment,
                    level = row.level,
                    authorName = row.authorName,
                    authorAvatarPath = row.authorAvatarPath,
                    timeText = DateFormatters.relativeTimeString(row.comment.timestamp, nowMillis, relStrings),
                    canDelete = row.canDelete,
                    onReply = { composer.replyTarget = MomentReplyTarget(row.comment.uuid, row.authorName) },
                    onDelete = { viewModel.deleteComment(row.comment.uuid) },
                )
            }
        }
    }
}

@Composable
private fun CommentHeader(count: Int) {
    Text(
        momentCommentHeaderText(count),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = AppTheme.colors.text.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentCommentRow(
    comment: MomentCommentEntity,
    level: Int,
    authorName: String,
    authorAvatarPath: String?,
    timeText: String,
    canDelete: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val indent = momentCommentIndent(level)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent, top = 4.dp, bottom = 4.dp)
                .combinedClickable(onClick = onReply, onLongClick = { menuExpanded = true }),
            verticalAlignment = Alignment.Top,
        ) {
            MomentCommentRowContent(comment, authorName, authorAvatarPath, timeText)
        }
        AppMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
            AppMenuItem(
                text = stringResource(R.string.moment_comment_reply),
                onClick = { menuExpanded = false; onReply() },
            )
            if (canDelete) {
                AppMenuItem(
                    text = stringResource(R.string.moment_comment_delete),
                    onClick = { menuExpanded = false; showDeleteConfirm = true },
                    danger = true,
                )
            }
        }
    }
    if (showDeleteConfirm) {
        AppDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.moment_comment_delete_title),
            body = stringResource(R.string.moment_comment_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { showDeleteConfirm = false; onDelete() },
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun CommentInputBar(
    text: String,
    replyName: String?,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 契约 §2.4：raised 平底 + 顶边发丝线（v2 海拔口径：分层靠明度+发丝，不靠 M3 tonal）。
    val colors = AppTheme.colors
    Surface(modifier = modifier, color = colors.surface.raised) {
        Column {
            AppListDivider(startInset = 0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (replyName != null) MomentReplyChip(replyName, onCancelReply)
                AppTextArea(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = stringResource(if (replyName != null) R.string.moment_detail_reply_hint else R.string.moment_detail_comment_hint),
                    minHeight = 52.dp, // 评论栏单行起步，随内容长到 maxLines=4
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                // 发送钮（契约 §2.4）：36dp 浅陶双 stop 圆钮 + 深墨纸飞机（「浅底深字」与用户气泡同口径·
                // 对比度走既有 onAccent×gradient 断言）；禁用 = sunken 底 + tertiary 图标。点击域 48dp（a11y 红线）。
                val canSend = text.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    Brush.linearGradient(listOf(colors.accent.gradientStart, colors.accent.gradientEnd))
                                } else {
                                    SolidColor(colors.surface.sunken)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppMomentIcons.PaperPlane,
                            contentDescription = stringResource(R.string.moment_detail_send),
                            tint = if (canSend) colors.text.onAccent else colors.text.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
