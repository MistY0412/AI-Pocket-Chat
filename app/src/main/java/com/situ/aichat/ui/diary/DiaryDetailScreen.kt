package com.situ.aichat.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.DiaryEntryEntity
import com.situ.aichat.data.model.imagePaths
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppMenu
import com.situ.aichat.ui.designsystem.AppMenuItem
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.AppTopBarIcons

/**
 * 日记详情（日记重设计 R1·契约 §1.1 S3）：心情洇染头（大数字日期 + M月·周几 + 心情 chip）+ 元信息行 +
 * 正文（可选中·AI 代写走楷体 [com.situ.aichat.ui.designsystem.AppTypography.kaiBody]）+ 图片画廊 +
 * 「记于 HH:mm」印章小字 + 评论区（票据虚线分隔）。Menu：编辑 / 删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: DiaryDetailViewModel = hiltViewModel(),
) {
    val entryWC by viewModel.entry.collectAsStateWithLifecycle()
    val chars by viewModel.charactersByUuid.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.diary_nav_title),
                onBack = onBack,
                lifted = scrollState.value > 0,
                actions = {
                    val ewc = entryWC
                    if (ewc != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(AppTopBarIcons.More, contentDescription = stringResource(R.string.action_more))
                        }
                        AppMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
                            AppMenuItem(
                                text = stringResource(R.string.action_edit),
                                leadingIcon = Icons.Filled.Edit,
                                onClick = { menuExpanded = false; onEdit(ewc.entry.uuid) },
                            )
                            AppMenuItem(
                                text = stringResource(R.string.action_delete),
                                leadingIcon = Icons.Filled.Delete,
                                danger = true,
                                onClick = { menuExpanded = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val ewc = entryWC
        if (ewc == null) {
            // 已删除 / 尚未加载：留空（删除后由调用方 onBack 返回）。
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val entry = ewc.entry
        val colors = AppTheme.colors
        // U3：作者显示名——活名 / 已删角色的快照名（§6.3 O1）。留言入口(O4) 仍只认活角色（diaryExchangeNoteAuthor）。
        val authorDisplay = diaryAuthorDisplayOf(entry, chars)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailHeaderWash(entry)
            if (authorDisplay?.isOrphan == true) {
                // U3 故人来信淡标（§6.3 O2·日期头下·text.secondary·无警示色·尊重语气）。
                Text(
                    stringResource(R.string.diary_exchange_orphan_label),
                    style = AppTheme.typography.caption,
                    color = colors.text.secondary,
                )
            }
            DetailMetaRow(entry, ewc.comments.size)

            // 正文：③ 段落呼吸（段间距 + 首行缩进·两方通用·U2③）；⑤ TA 的信垫暖信笺（U2⑤·authorCharacterUuid 非空）。
            //（可选中·楷体 = AI 代写 / TA 的信，手写 = 正文黑体·契约 §1 手法4）
            DiaryDetailBody(
                content = entry.content.ifEmpty { stringResource(R.string.diary_no_content) },
                style = diaryDetailBodyStyle(entry),
                isLetter = entry.authorCharacterUuid != null,
            )

            // 图片画廊（每行 3 张方图）
            val images = entry.imagePaths
            if (images.isNotEmpty()) DiaryDetailImageGrid(images)

            // 印章小字（右对齐）：TA 的信 = 楷体署名「—— 名 · 写于 HH:mm」；用户日记 = 「记于 HH:mm」。
            DiaryDetailSignature(authorDisplay, entry.timestamp)

            // 互动区（R3 评论区活化）：角色点赞行 + 评论线程（共用一道票据虚线）。
            // R6-1：TA 的信可给作者留言（作者仍在 + 尚无顶层留言 → 入口露出，无评论时也显示）。
            val noteAuthor = diaryExchangeNoteAuthor(entry, ewc.comments, chars)
            if (ewc.reactions.isNotEmpty() || ewc.comments.isNotEmpty() || noteAuthor != null) {
                DiaryDashedDivider()
            }
            if (ewc.reactions.isNotEmpty()) {
                DiaryReactionRow(ewc.reactions, chars)
            }
            if (ewc.comments.isNotEmpty()) {
                Text(
                    stringResource(R.string.diary_comments_header, ewc.comments.size),
                    style = AppTheme.typography.label,
                    color = colors.text.primary,
                    modifier = Modifier.semantics { heading() },
                )
                DiaryCommentThreadSection(
                    comments = ewc.comments,
                    charactersByUuid = chars,
                    onDeleteComment = viewModel::deleteComment,
                    onReply = viewModel::replyToComment,
                )
            }
            if (noteAuthor != null) {
                ExchangeNoteAffordance(
                    authorName = noteAuthor.name,
                    onSend = viewModel::commentOnEntry,
                )
            }
        }
    }

    if (showDelete) {
        AppDialog(
            onDismissRequest = { showDelete = false },
            title = stringResource(R.string.diary_delete_title),
            body = stringResource(R.string.diary_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { showDelete = false; viewModel.delete(onDone = onBack) },
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showDelete = false },
        )
    }
}

/** 洇染头：心情 tint（无心情=sunken）+ 大数字日期（tnum）+ M月·周几 + yyyy年 + 心情 chip。tint 上文字只用 primary。 */
@Composable
private fun DetailHeaderWash(entry: DiaryEntryEntity) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.medium)
            .background(diaryMoodTint(entry.moodEmoji) ?: colors.surface.sunken)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDiaryDate(entry.timestamp, stringResource(R.string.diary_fmt_day_number)),
                style = AppTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = colors.text.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDiaryDate(entry.timestamp, stringResource(R.string.diary_fmt_detail_sub)),
                    style = AppTheme.typography.label,
                    color = colors.text.primary,
                )
                Text(
                    formatDiaryDate(entry.timestamp, stringResource(R.string.diary_fmt_month_section_year)),
                    style = AppTheme.typography.caption,
                    color = colors.text.primary,
                )
            }
            entry.moodEmoji?.takeIf { it.isNotEmpty() }?.let { emoji ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(AppTheme.shapes.full)
                        .background(colors.surface.raised.copy(alpha = 0.8f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(emoji, style = AppTheme.typography.secondary)
                    entry.moodText?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = AppTheme.typography.secondary, color = colors.text.primary)
                    }
                }
            }
        }
    }
}
