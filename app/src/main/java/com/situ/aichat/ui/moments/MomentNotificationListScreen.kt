package com.situ.aichat.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppElevation
import com.situ.aichat.ui.designsystem.AppListDivider
import com.situ.aichat.ui.designsystem.AppSnackbarHost
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTopBar
import com.situ.aichat.ui.designsystem.grainSurface
import com.situ.aichat.util.DateFormatters
import com.situ.aichat.util.rememberTimeTick

/**
 * 朋友圈互动通知列表（M06 7.2.8，对齐 iOS `MomentNotificationListView`）：未读通知行（角色头像 + 描述 + 内容
 * 预览 + 相对时间）+ 滑动已读 + 全部已读 + 点进帖子详情（帖已删 → snackbar 提示）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentNotificationListScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: MomentNotificationViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val openNotification = rememberMomentNotificationOpener(viewModel, snackbarHostState, onOpenPost)

    val aiLabel = stringResource(R.string.moment_author_ai)
    val relStrings = momentRelativeTimeStrings()
    val nowMillis = rememberTimeTick() // moments-ui-10：通知列表相对时间每 60s 自动刷新（= iOS MomentNotificationListView 读 TimeTick）

    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.moment_notif_list_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
                actions = {
                    if (notifications.isNotEmpty()) {
                        AppButton(onClick = viewModel::markAllRead, style = AppButtonStyle.Text) {
                            Text(stringResource(R.string.moment_notif_mark_all_read))
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        if (notifications.isEmpty()) {
            val colors = AppTheme.colors
            MomentNotificationsEmptyState(Modifier.fillMaxSize().padding(padding).background(colors.surface.base).grainSurface().padding(32.dp))
        } else {
            // 页底 = surface.base + 纸感 grain（契约 §2.6·行自身画 base 保滑动揭示不透底）。
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.surface.base).grainSurface()) {
                items(notifications, key = { it.id }) { notification ->
                    val dismissState = rememberMomentMarkReadSwipeState { viewModel.markRead(notification.id) }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = { MomentMarkReadBackground() },
                    ) {
                        MomentNotificationRow(
                            notification = notification,
                            character = characters[notification.characterUuid],
                            aiLabel = aiLabel,
                            timeText = DateFormatters.relativeTimeString(notification.timestamp, nowMillis, relStrings),
                            onClick = { openNotification(notification) },
                        )
                    }
                    // 行间发丝分隔（契约 §2.6·D6 拍板：透明行不卡片化）：inset 68 = 行首距 16 + 头像 40 + 间距 12；
                    // 放在 SwipeToDismissBox 之外，滑动时分隔线不跟行位移。
                    AppListDivider(modifier = Modifier.padding(start = 68.dp), startInset = 0.dp)
                }
            }
        }
    }
}
