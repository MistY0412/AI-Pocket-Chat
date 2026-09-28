package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.liuli.designsystem.LiuliAvatarRing
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.moments.MomentAuthorEmptyState
import com.situ.aichat.ui.moments.MomentAuthorHeader
import com.situ.aichat.ui.moments.MomentAuthorViewModel
import com.situ.aichat.ui.moments.MomentsWindowPagingEffects
import com.situ.aichat.ui.moments.momentAuthorHeading
import com.situ.aichat.ui.moments.momentUserLiked

/** 作者头光环（= 暖陶白瓷圈 68·内头像 64 = 68 − 2 × 2）。 */
private val AUTHOR_RING = 68.dp

/** 作者头下距（= 暖陶）。 */
private val AUTHOR_HEADER_BOTTOM = 8.dp

/**
 * 琉璃「某人的动态 / 我的动态」（琉璃 2.0 卷六·二 §4.3）：与暖陶 [com.situ.aichat.ui.moments.MomentAuthorScreen] 共用
 * 同一个 VM、窗口分页、作者头 / 空态内容件；外壳换琉璃页壳（大标题 + 收起胶囊），头像换光环，动态卡换半透明卡。
 * 同暖陶：无发动态钮、无长按菜单、无头像点击。
 */
@Composable
internal fun LiuliMomentAuthorScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: MomentAuthorViewModel = hiltViewModel(),
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlderPosts.collectAsStateWithLifecycle()

    val meLabel = stringResource(R.string.moment_author_me)
    val aiLabel = stringResource(R.string.moment_author_ai)
    val isUser = viewModel.isUserMode
    val heading = momentAuthorHeading(isUser, if (!isUser) characters[viewModel.characterUuid] else null, userProfile, meLabel, aiLabel)
    val title = if (isUser) stringResource(R.string.moment_user_moments_title) else heading.name
    val listState = rememberLazyListState()
    MomentsWindowPagingEffects(listState, hasMoreOlder, viewModel::loadOlderPosts, viewModel::shrinkWindow)

    LiuliPage(title = title, onBack = onBack, collapsed = rememberLargeTitleCollapsed(listState)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            contentPadding = PaddingValues(
                top = LiuliPageGeometry.navRow,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LiuliPageGeometry.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(MOMENT_CARD_GAP),
        ) {
            item(key = "large-title") { LiuliLargeTitle(title) }
            item(key = "header") {
                MomentAuthorHeader(
                    name = heading.name,
                    avatarPath = heading.avatarPath,
                    postCount = totalCount,
                    contentPadding = PaddingValues(start = LiuliPageGeometry.gutter, end = LiuliPageGeometry.gutter, bottom = AUTHOR_HEADER_BOTTOM),
                    avatarFrame = { avatar -> LiuliAvatarRing(AUTHOR_RING) { avatar() } },
                )
            }
            if (loaded && posts.isEmpty()) {
                item(key = "empty") { MomentAuthorEmptyState(isUser) }
            } else {
                items(posts, key = { it.post.uuid }) { post ->
                    LiuliMomentCard(
                        post = post,
                        characters = characters,
                        userName = userProfile?.nickname.orEmpty(),
                        userAvatarPath = userProfile?.avatarPath,
                        onToggleLike = { viewModel.toggleLike(post.post.uuid, momentUserLiked(post)) },
                        interaction = Modifier.clickable(onClickLabel = stringResource(R.string.a11y_moment_open_post)) { onOpenPost(post.post.uuid) },
                        modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter),
                    )
                }
            }
        }
    }
}
