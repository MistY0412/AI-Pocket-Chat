package com.situ.aichat.ui.moments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.clickableScale
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppDialogTone
import com.situ.aichat.ui.designsystem.AppLoadingRing
import com.situ.aichat.ui.designsystem.AppLoadingRingSize
import com.situ.aichat.ui.designsystem.AppMenu
import com.situ.aichat.ui.designsystem.AppMenuItem
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.AppSnackbarHost
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.appCardSurface
import com.situ.aichat.ui.designsystem.grainSurface

/**
 * 朋友圈信息流（M06 7.2.7，对齐 iOS `FriendCircleView`）：下拉刷新触发 AI 发帖检查、未读通知 banner、发布 FAB、
 * 帖子卡列表（点开详情 7.2.8 / 长按点赞·删除）、空状态。
 *
 * [onOpenPost]→帖子详情（7.2.8 接线，现路由占位）。[onOpenNotifications]→通知列表（7.2.8）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MomentsListScreen(
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

    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    MomentsRefreshResultEffect(refreshResult, snackbarHostState, onConsumed = viewModel::consumeRefreshResult)

    val hasMoreOlder by viewModel.hasMoreOlderPosts.collectAsStateWithLifecycle()
    MomentsWindowPagingEffects(listState, hasMoreOlder, onLoadOlder = viewModel::loadOlderPosts, onShrinkWindow = viewModel::shrinkWindow)

    val userName = userProfile?.nickname.orEmpty()
    val userAvatarPath = userProfile?.avatarPath

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.moment_nav_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )
        },
        floatingActionButton = {
            // 深陶羽毛笔 FAB（契约 §2.2·D2 拍板）：56dp 圆 + deepStart→deepEnd 135° 双 stop（同旧 Hero/深档气泡族）
            // + raised 双层软影 + 按压 clickableScale（calm）+ 轻触觉；56dp ≥ 48dp a11y 触达。
            val colors = AppTheme.colors
            val haptics = LocalAppHaptics.current
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .appCardSurface(
                        raised = true,
                        cornerRadius = 28.dp,
                        background = Brush.linearGradient(listOf(colors.accent.deepStart, colors.accent.deepEnd)),
                    )
                    .clickableScale { haptics.light(); onCompose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppMomentIcons.QuillBold,
                    contentDescription = stringResource(R.string.moment_publish),
                    tint = colors.accent.onDeep,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            // 页底 = surface.base + 纸感 grain（契约 §2.2·v2 质感层）。
            modifier = Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface(),
            // 只换指示器长相：陶环取代 M3 默认转圈。**不做下拉进度联动**（恒转·§4.15 明文），
            // 只按 isRefreshing 出现/消失（E-C7）——M3 默认指示器自己管显隐，换成自绘件就得自己判。
            indicator = {
                if (refreshing) {
                    AppLoadingRing(
                        modifier = Modifier.align(Alignment.TopCenter),
                        size = AppLoadingRingSize.Medium,
                    )
                }
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (feed.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            MomentsEmptyState { AppButton(onClick = onCompose, style = AppButtonStyle.Primary) { Text(stringResource(R.string.moment_empty_action)) } }
                        }
                    }
                } else {
                    if (unreadCount > 0) {
                        item(key = "notif-banner") {
                            // moments-ui-6：通知 banner 插入/消失带滑入淡入（= iOS .move(.top)+.opacity，spring）
                            NotificationBanner(
                                count = unreadCount,
                                onClick = onOpenNotifications,
                                modifier = Modifier.animateItem().padding(horizontal = 20.dp), // v2 军规：屏 gutter 恒 20
                            )
                        }
                    }
                    items(feed, key = { it.post.uuid }) { post ->
                        // moments-ui-6：帖子卡出现/重排带淡入+弹性位移（= iOS 滚入淡入的安卓地道等价；
                        // animateItem 不复刻 iOS 连续滚动驱动的 scale，按 LazyColumn 习惯只做出现/位移动画）
                        MomentFeedItem(
                            post = post,
                            characterDict = characters,
                            userName = userName,
                            userAvatarPath = userAvatarPath,
                            onOpenPost = { onOpenPost(post.post.uuid) },
                            onToggleLike = {
                                val hasUserLike = momentUserLiked(post)
                                viewModel.toggleLike(post.post.uuid, hasUserLike)
                            },
                            onRequestDelete = { deleteTarget = post.post.uuid },
                            onCharacterTap = onOpenCharacterMoments,
                            modifier = Modifier.animateItem().padding(horizontal = 20.dp), // v2 军规：屏 gutter 恒 20
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { uuid ->
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.moment_delete_title),
            body = stringResource(R.string.moment_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(uuid); deleteTarget = null },
            confirmTone = AppDialogTone.Danger,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 一条 feed 项：卡片 + 长按菜单（点赞/取消赞、删除）。点开卡片进详情。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentFeedItem(
    post: MomentPostWithRelations,
    characterDict: Map<String, CharacterEntity>,
    userName: String,
    userAvatarPath: String?,
    onOpenPost: () -> Unit,
    onToggleLike: () -> Unit,
    onRequestDelete: () -> Unit,
    onCharacterTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasUserLike = momentUserLiked(post)
    Box(
        // P1-21：动作标签（combinedClickable 自带 mergeDescendants，整卡已是单焦点节点=iOS 消费方 Button 包卡；
        // 卡内绝不再加 semantics(mergeDescendants)——嵌套双合并劣化 TalkBack）。
        modifier = modifier.combinedClickable(
            onClickLabel = stringResource(R.string.a11y_moment_open_post),
            onClick = onOpenPost,
            onLongClickLabel = stringResource(R.string.a11y_moment_post_actions),
            onLongClick = { menuExpanded = true },
        ),
    ) {
        MomentPostCard(
            post = post,
            characterDict = characterDict,
            userName = userName,
            userAvatarPath = userAvatarPath,
            onToggleLike = onToggleLike,
            onCharacterTap = onCharacterTap,
        )
        AppMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
            AppMenuItem(
                text = stringResource(if (hasUserLike) R.string.moment_unlike else R.string.moment_like),
                onClick = { onToggleLike(); menuExpanded = false },
            )
            AppMenuItem(
                text = stringResource(R.string.moment_menu_delete),
                onClick = { onRequestDelete(); menuExpanded = false },
                danger = true,
            )
        }
    }
}
