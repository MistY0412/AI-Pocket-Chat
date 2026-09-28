package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.liuliSegmentPosition
import com.situ.aichat.ui.liuli.page.LiuliGroupHeader
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.moments.MomentCommentComposerState
import com.situ.aichat.ui.moments.MomentCommentsAutoScrollEffect
import com.situ.aichat.ui.moments.MomentDetailViewModel
import com.situ.aichat.ui.moments.MomentLikesSection
import com.situ.aichat.ui.moments.MomentPostContentSection
import com.situ.aichat.ui.moments.MomentReplyTarget
import com.situ.aichat.ui.moments.momentCommentHeaderText
import com.situ.aichat.ui.moments.momentCommentRows
import com.situ.aichat.ui.moments.momentRelativeTimeStrings
import com.situ.aichat.ui.moments.momentUserName
import com.situ.aichat.ui.moments.rememberMomentCommentComposerState
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import com.situ.aichat.util.DateFormatters

/**
 * 琉璃动态详情（琉璃 2.0 卷六·二 §4.5·设计稿 M2）：与暖陶 [com.situ.aichat.ui.moments.MomentDetailScreen] 共用同一个 VM、
 * 草稿 / 评论行模型 / 自动滚 / 正文卡 / 点赞名单 / 回复 chip；页壳常驻收起态（顶部就是「← 动态详情」胶囊），正文卡换半透明卡 +
 * 光环、点赞名单换 `segTrack` 小笺、评论楼层装进分段卡、底部悬浮玻璃评论条。
 */
@Composable
internal fun LiuliMomentDetailScreen(
    onBack: () -> Unit,
    viewModel: MomentDetailViewModel = hiltViewModel(),
) {
    val post by viewModel.post.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val composer = rememberMomentCommentComposerState()
    LiuliMomentDetailPage(
        post = post,
        characters = characters,
        userProfile = userProfile,
        composer = composer,
        onBack = onBack,
        onSend = { composer.send(viewModel::submitComment) },
        onDeleteComment = viewModel::deleteComment,
    )
}

/** 无 VM 的详情页（测试直接驱动它）。[post] 为 null = 帖已删 → 居中一句、评论条照旧在（同暖陶）。 */
@Composable
internal fun LiuliMomentDetailPage(
    post: MomentPostWithRelations?,
    characters: Map<String, CharacterEntity>,
    userProfile: UserProfileEntity?,
    composer: MomentCommentComposerState,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    val meLabel = stringResource(R.string.moment_author_me)
    val aiLabel = stringResource(R.string.moment_author_ai)
    val relStrings = momentRelativeTimeStrings()
    val nowMillis = System.currentTimeMillis()
    val userName = momentUserName(userProfile, meLabel)
    val userAvatarPath = userProfile?.avatarPath
    val listState = rememberLazyListState()
    val dark = LocalIsDarkTheme.current
    val colors = AppTheme.colors

    LiuliPage(
        title = stringResource(R.string.moment_detail_title),
        onBack = onBack,
        collapsed = true,
        bottomBar = {
            LiuliMomentCommentBar(
                text = composer.text,
                replyName = composer.replyTarget?.name,
                canSend = composer.canSend,
                onTextChange = { composer.text = it },
                onCancelReply = { composer.replyTarget = null },
                onSend = onSend,
            )
        },
        bottomEdge = MOMENT_DETAIL_BOTTOM_EDGE,
    ) {
        val p = post
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.moment_detail_deleted), style = AppTypography.body, color = colors.text.secondary)
            }
            return@LiuliPage
        }
        val rows = remember(p, characters, userAvatarPath, meLabel, aiLabel) { momentCommentRows(p, characters, userAvatarPath, meLabel, aiLabel) }
        MomentCommentsAutoScrollEffect(listState, p.comments.size, rows.isNotEmpty())
        // 底留白 = 导航栏与键盘取大 + 评论条让位 68 + 页底 24（键盘升起时末条评论仍能滚到条上方）。
        val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            // 不用 spacedBy：分段卡段间不许留缝（卡与卡之间的缝由各项自带底距给）。
            contentPadding = PaddingValues(
                start = LiuliPageGeometry.gutter,
                end = LiuliPageGeometry.gutter,
                top = LiuliPageGeometry.navRow + LiuliPageGeometry.titleGap,
                bottom = bottomInset + LiuliPageGeometry.floatingBarReserve + LiuliPageGeometry.pageBottom,
            ),
        ) {
            item(key = "content") {
                MomentPostContentSection(
                    p, characters, userName, userAvatarPath, relStrings, nowMillis, meLabel, aiLabel,
                    modifier = Modifier.padding(bottom = MOMENT_CARD_GAP),
                    surface = Modifier.liuliMomentCard(dark),
                    avatarFrame = { avatar -> LiuliMomentAvatarRing(DETAIL_AVATAR, avatar) },
                )
            }
            if (p.likes.isNotEmpty()) {
                item(key = "likes") {
                    MomentLikesSection(
                        p, characters, meLabel, aiLabel,
                        modifier = Modifier.padding(bottom = MOMENT_CARD_GAP),
                        surface = Modifier.liuliMomentNote(dark),
                    )
                }
            }
            item(key = "comment-header") { LiuliGroupHeader(momentCommentHeaderText(p.comments.size)) }
            itemsIndexed(rows, key = { _, row -> row.comment.uuid }) { index, row ->
                LiuliMomentCommentRow(
                    row = row,
                    timeText = DateFormatters.relativeTimeString(row.comment.timestamp, nowMillis, relStrings),
                    position = liuliSegmentPosition(index, rows.size),
                    onReply = { composer.replyTarget = MomentReplyTarget(row.comment.uuid, row.authorName) },
                    onDelete = { onDeleteComment(row.comment.uuid) },
                )
            }
        }
    }
}
