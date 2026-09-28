package com.situ.aichat.ui.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.grainSurface

/**
 * 角色 / 用户动态页（M06 7.2.8，对齐 iOS `CharacterMomentsView` / `UserMomentsView`）：封面头（头像 + 名 +
 * 动态数）+ 该作者的帖子列表（复用 [MomentPostCard]，点开进详情）。模式由 [MomentAuthorViewModel] 路由参数决定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentAuthorScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: MomentAuthorViewModel = hiltViewModel(),
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    val meLabel = stringResource(R.string.moment_author_me)
    val aiLabel = stringResource(R.string.moment_author_ai)
    val isUser = viewModel.isUserMode
    val heading = momentAuthorHeading(isUser, if (!isUser) characters[viewModel.characterUuid] else null, userProfile, meLabel, aiLabel)
    val title = if (isUser) stringResource(R.string.moment_user_moments_title) else heading.name
    val cardUserName = userProfile?.nickname.orEmpty()
    val cardUserAvatar = userProfile?.avatarPath

    val listState = rememberLazyListState()

    val hasMoreOlder by viewModel.hasMoreOlderPosts.collectAsStateWithLifecycle()
    MomentsWindowPagingEffects(listState, hasMoreOlder, viewModel::loadOlderPosts, viewModel::shrinkWindow)

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            // 页底 = surface.base + 纸感 grain（契约 §2.3）。
            modifier = Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                MomentAuthorHeader(name = heading.name, avatarPath = heading.avatarPath, postCount = totalCount)
            }
            // 复核 R1 🟡-1：首帧未从 DB 返回前留白——否则计数流先到会出现「N 条动态」压着「还没有动态」。
            if (loaded && posts.isEmpty()) {
                item(key = "empty") { MomentAuthorEmptyState(isUser) }
            } else {
                items(posts, key = { it.post.uuid }) { post ->
                    MomentPostCard(
                        post = post,
                        characterDict = characters,
                        userName = cardUserName,
                        userAvatarPath = cardUserAvatar,
                        onToggleLike = {
                            val hasUserLike = momentUserLiked(post)
                            viewModel.toggleLike(post.post.uuid, hasUserLike)
                        },
                        modifier = Modifier.padding(horizontal = 20.dp) // v2 军规：屏 gutter 恒 20
                            .clickable(onClickLabel = stringResource(R.string.a11y_moment_open_post)) { onOpenPost(post.post.uuid) },
                    )
                }
            }
        }
    }
}
