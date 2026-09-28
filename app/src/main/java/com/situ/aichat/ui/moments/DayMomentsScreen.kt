package com.situ.aichat.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.grainSurface

/**
 * 「{日期} · 朋友圈」（图纸 2026-09-03 §4）：「我们的日子」日页事实层「看动态 ›」的落点——只装这一天涉及的
 * 动态，分「这一天发的」/「更早发的 · 这一天有来往」两组。骨架照 [MomentAuthorScreen] 范式；卡片复用
 * [MomentPostCard]（零改）。
 *
 * 本页有意不做（§0.3-5）：下拉刷新、发布 FAB、长按菜单、`onCharacterTap`、`animateItem()`、顶栏 actions。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMomentsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: DayMomentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val date = state.date
    val title = momentDayTitle(date)
    val cardUserName = userProfile?.nickname.orEmpty()
    val cardUserAvatar = userProfile?.avatarPath

    val listState = rememberLazyListState()
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
            // 页底 = surface.base + 纸感 grain（同范式页 MomentAuthorScreen）。
            modifier = Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 组为空 ⇒ 标签与该组整体都不发射（不留空壳·E5 / E6）。
            if (state.postedThatDay.isNotEmpty()) {
                item(key = "label-posted") { DayMomentsGroupLabel(stringResource(R.string.moment_day_group_posted)) }
                postCards(state.postedThatDay, characters, cardUserName, cardUserAvatar, viewModel, onOpenPost)
            }
            if (state.earlier.isNotEmpty()) {
                item(key = "label-earlier") { DayMomentsGroupLabel(stringResource(R.string.moment_day_group_earlier)) }
                postCards(state.earlier, characters, cardUserName, cardUserAvatar, viewModel, onOpenPost)
            }
            // 首帧未从 DB 返回前留白，不画空态（J6）。
            if (state.loaded && state.postedThatDay.isEmpty() && state.earlier.isEmpty()) {
                item(key = "empty") { DayMomentsEmptyState() }
            }
        }
    }
}

/** 帖子卡列表（§4.4·复用 [MomentPostCard] 零改：不传 onCharacterTap、不加 animateItem / 长按分支）。 */
private fun LazyListScope.postCards(
    list: List<MomentPostWithRelations>,
    characters: Map<String, CharacterEntity>,
    cardUserName: String,
    cardUserAvatar: String?,
    viewModel: DayMomentsViewModel,
    onOpenPost: (String) -> Unit,
) {
    items(list, key = { it.post.uuid }) { post ->
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
