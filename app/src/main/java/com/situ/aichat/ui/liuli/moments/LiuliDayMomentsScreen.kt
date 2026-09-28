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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.moments.DayMomentsEmptyState
import com.situ.aichat.ui.moments.DayMomentsGroupLabel
import com.situ.aichat.ui.moments.DayMomentsViewModel
import com.situ.aichat.ui.moments.momentDayTitle
import com.situ.aichat.ui.moments.momentUserLiked

/**
 * 琉璃「那天的动态」（琉璃 2.0 卷六·二 §4.4）：与暖陶 [com.situ.aichat.ui.moments.DayMomentsScreen] 共用同一个 VM、
 * 标题 / 分节标签 / 空态内容件；外壳换琉璃页壳，动态卡换半透明卡。同暖陶：无长按、无头像点击、无 `animateItem`。
 */
@Composable
internal fun LiuliDayMomentsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: DayMomentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val title = momentDayTitle(state.date)
    val userName = userProfile?.nickname.orEmpty()
    val userAvatarPath = userProfile?.avatarPath
    val onToggleLike: (MomentPostWithRelations) -> Unit = { post -> viewModel.toggleLike(post.post.uuid, momentUserLiked(post)) }
    val listState = rememberLazyListState()

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
            // 组为空 ⇒ 标签与该组整体都不发射（同暖陶）。
            if (state.postedThatDay.isNotEmpty()) {
                item(key = "label-posted") { DayMomentsGroupLabel(stringResource(R.string.moment_day_group_posted)) }
                liuliDayPostCards(state.postedThatDay, characters, userName, userAvatarPath, onToggleLike, onOpenPost)
            }
            if (state.earlier.isNotEmpty()) {
                item(key = "label-earlier") { DayMomentsGroupLabel(stringResource(R.string.moment_day_group_earlier)) }
                liuliDayPostCards(state.earlier, characters, userName, userAvatarPath, onToggleLike, onOpenPost)
            }
            // 首帧未从 DB 返回前留白，不画空态（同暖陶）。
            if (state.loaded && state.postedThatDay.isEmpty() && state.earlier.isEmpty()) {
                item(key = "empty") { DayMomentsEmptyState() }
            }
        }
    }
}

/** 一组动态卡（琉璃动态卡·点开进详情）。 */
private fun LazyListScope.liuliDayPostCards(
    list: List<MomentPostWithRelations>,
    characters: Map<String, CharacterEntity>,
    userName: String,
    userAvatarPath: String?,
    onToggleLike: (MomentPostWithRelations) -> Unit,
    onOpenPost: (String) -> Unit,
) {
    items(list, key = { it.post.uuid }) { post ->
        LiuliMomentCard(
            post = post,
            characters = characters,
            userName = userName,
            userAvatarPath = userAvatarPath,
            onToggleLike = { onToggleLike(post) },
            interaction = Modifier.clickable(onClickLabel = stringResource(R.string.a11y_moment_open_post)) { onOpenPost(post.post.uuid) },
            modifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter),
        )
    }
}
