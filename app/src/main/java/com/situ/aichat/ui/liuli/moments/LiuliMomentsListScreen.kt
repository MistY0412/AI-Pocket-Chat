package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliCircleButton
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.designsystem.LiuliSnackbarHost
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.moments.MomentsEmptyState
import com.situ.aichat.ui.moments.MomentsRefreshResultEffect
import com.situ.aichat.ui.moments.MomentsViewModel
import com.situ.aichat.ui.moments.MomentsWindowPagingEffects
import com.situ.aichat.ui.moments.NotificationBanner
import com.situ.aichat.ui.moments.momentUserLiked
import com.situ.aichat.ui.theme.LocalIsDarkTheme

private val FEED_BOTTOM_RESERVE = LiuliPageGeometry.fabBottom + LiuliPageGeometry.fab + LiuliPageGeometry.pageBottom

/** 圈子要画的数据（无 VM 页的入参·测试直接造）。 */
@Immutable
internal data class LiuliMomentsFeedData(
    val feed: List<MomentPostWithRelations>,
    val characters: Map<String, CharacterEntity>,
    val userName: String,
    val userAvatarPath: String?,
    val unreadCount: Int,
    val refreshing: Boolean,
)

/** 圈子的全部回调（与暖陶同一批 VM 方法、同一批实参）。 */
internal class LiuliMomentsFeedCallbacks(
    val onBack: () -> Unit,
    val onCompose: () -> Unit,
    val onOpenPost: (String) -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenCharacterMoments: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleLike: (MomentPostWithRelations) -> Unit,
    val onRequestDelete: (String) -> Unit,
)

/**
 * 琉璃圈子（琉璃 2.0 卷六·二 §4.2·设计稿 M1）：与暖陶 [com.situ.aichat.ui.moments.MomentsListScreen] 共用同一个 VM、
 * 刷新提示 / 窗口分页 / 横幅 / 空态 / 动态卡内容件，只换外壳与材质：琉璃页壳（大标题 + 收起胶囊）、半透明动态卡 + 头像光环、
 * 玻璃圆钮发动态 + 屏底渐进带、琉璃下拉转圈 / 菜单 / 对话框 / snackbar。
 */
@Composable
internal fun LiuliMomentsListScreen(
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCharacterMoments: (String) -> Unit,
    viewModel: MomentsViewModel = hiltViewModel(),
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshResult by viewModel.refreshResult.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlderPosts.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    MomentsRefreshResultEffect(refreshResult, snackbarHostState, onConsumed = viewModel::consumeRefreshResult)
    MomentsWindowPagingEffects(listState, hasMoreOlder, onLoadOlder = viewModel::loadOlderPosts, onShrinkWindow = viewModel::shrinkWindow)

    LiuliMomentsListPage(
        data = LiuliMomentsFeedData(feed, characters, userProfile?.nickname.orEmpty(), userProfile?.avatarPath, unreadCount, refreshing),
        callbacks = LiuliMomentsFeedCallbacks(
            onBack, onCompose, onOpenPost, onOpenNotifications, onOpenCharacterMoments,
            onRefresh = viewModel::refresh,
            onToggleLike = { post -> viewModel.toggleLike(post.post.uuid, momentUserLiked(post)) },
            onRequestDelete = { deleteTarget = it },
        ),
        listState = listState,
        snackbarHostState = snackbarHostState,
    )

    deleteTarget?.let { uuid ->
        LiuliDialog(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.moment_delete_title),
            body = stringResource(R.string.moment_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(uuid); deleteTarget = null },
            confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 无 VM 的圈子页（测试直接驱动它）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliMomentsListPage(
    data: LiuliMomentsFeedData,
    callbacks: LiuliMomentsFeedCallbacks,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
) {
    val title = stringResource(R.string.moment_nav_title)
    val dark = LocalIsDarkTheme.current
    val scrolledPast = rememberLargeTitleCollapsed(listState)
    val collapsed = scrolledPast && data.feed.isNotEmpty()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LiuliPage(
        title, callbacks.onBack, collapsed,
        fab = {
            LiuliCircleButton(callbacks.onCompose, stringResource(R.string.moment_publish), size = LiuliPageGeometry.fab) {
                Icon(AppMomentIcons.QuillBold, contentDescription = null, modifier = Modifier.size(LiuliPageGeometry.fabIcon))
            }
        },
        bottomEdge = MOMENT_FEED_BOTTOM_EDGE,
    ) {
        // 机制照借 M3（同暖陶·只按 refreshing 显隐），指示器换琉璃转圈。
        PullToRefreshBox(data.refreshing, callbacks.onRefresh, Modifier.fillMaxSize(), indicator = { LiuliMomentsRefreshIndicator(data.refreshing) }) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentMaxWidth(),
                // 底留白 = 导航栏 + 发动态钮距底 24 + 钮高 56 + 页底 24（末卡永远在圆钮之上 24）。
                contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = navBarBottom + FEED_BOTTOM_RESERVE),
                verticalArrangement = Arrangement.spacedBy(MOMENT_CARD_GAP),
            ) {
                item(key = "large-title") { LiuliLargeTitle(title) }
                if (data.feed.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            MomentsEmptyState {
                                LiuliButton(onClick = callbacks.onCompose, style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.moment_empty_action)) }
                            }
                        }
                    }
                } else {
                    if (data.unreadCount > 0) {
                        item(key = "notif-banner") {
                            val bannerModifier = Modifier.animateItem().padding(horizontal = LiuliPageGeometry.gutter)
                            NotificationBanner(data.unreadCount, callbacks.onOpenNotifications, bannerModifier, surface = Modifier.liuliMomentCard(dark))
                        }
                    }
                    items(data.feed, key = { it.post.uuid }) { post ->
                        LiuliMomentFeedItem(post, data, callbacks, Modifier.animateItem().padding(horizontal = LiuliPageGeometry.gutter))
                    }
                }
            }
        }
        // 悬在发动态钮之上（占位清单 §4.8）。
        LiuliSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = LiuliPageGeometry.fabBottom + LiuliPageGeometry.fab))
    }
}

/** 一条动态：琉璃动态卡（点开进详情 / 长按出菜单）+ 玻璃菜单（点赞 / 取消点赞 · 删除动态）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiuliMomentFeedItem(
    post: MomentPostWithRelations,
    data: LiuliMomentsFeedData,
    callbacks: LiuliMomentsFeedCallbacks,
    modifier: Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val liked = momentUserLiked(post)
    Box(modifier) {
        LiuliMomentCard(
            post, data.characters, data.userName, data.userAvatarPath,
            onToggleLike = { callbacks.onToggleLike(post) },
            interaction = Modifier.combinedClickable(
                onClickLabel = stringResource(R.string.a11y_moment_open_post),
                onClick = { callbacks.onOpenPost(post.post.uuid) },
                onLongClickLabel = stringResource(R.string.a11y_moment_post_actions),
                onLongClick = { menuExpanded = true },
            ),
            onCharacterTap = callbacks.onOpenCharacterMoments,
        )
        LiuliPopupMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = listOf(
                LiuliMenuEntry(stringResource(if (liked) R.string.moment_unlike else R.string.moment_like), onClick = { callbacks.onToggleLike(post) }),
                LiuliMenuEntry(stringResource(R.string.moment_menu_delete), danger = true, onClick = { callbacks.onRequestDelete(post.post.uuid) }),
            ),
        )
    }
}
