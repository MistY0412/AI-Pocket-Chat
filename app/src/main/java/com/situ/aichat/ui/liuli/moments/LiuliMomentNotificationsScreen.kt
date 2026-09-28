package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentNotificationEntity
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliSegmentPosition
import com.situ.aichat.ui.liuli.designsystem.LiuliSnackbarHost
import com.situ.aichat.ui.liuli.designsystem.liuliCardSegment
import com.situ.aichat.ui.liuli.designsystem.liuliSegmentPosition
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.moments.MomentMarkReadBackground
import com.situ.aichat.ui.moments.MomentNotificationRow
import com.situ.aichat.ui.moments.MomentNotificationViewModel
import com.situ.aichat.ui.moments.MomentNotificationsEmptyState
import com.situ.aichat.ui.moments.momentRelativeTimeStrings
import com.situ.aichat.ui.moments.rememberMomentMarkReadSwipeState
import com.situ.aichat.ui.moments.rememberMomentNotificationOpener
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import com.situ.aichat.util.DateFormatters
import com.situ.aichat.util.rememberTimeTick

/** 行间发丝缩进（= 暖陶行首 16 + 头像 40 + 缝 12）· 粗 0.5（= 聊天列表段内发丝）· 空态内距 32（= 暖陶）。 */
private val NOTIF_DIVIDER_INSET = 68.dp
private val NOTIF_DIVIDER = 0.5.dp
private val NOTIF_EMPTY_PAD = 32.dp

/**
 * 琉璃消息（琉璃 2.0 卷六·二 §4.7·设计稿 M4）：与暖陶 [com.situ.aichat.ui.moments.MomentNotificationListScreen] 共用同一个 VM、
 * 打开 / 左滑态 / 通知行 / 揭示底 / 空态；外壳换琉璃页壳 + 玻璃「全部已读」，一组通知拼成一张分段卡，揭示底只画行让出来的那一截。
 */
@Composable
internal fun LiuliMomentNotificationsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: MomentNotificationViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val open = rememberMomentNotificationOpener(viewModel, snackbarHostState, onOpenPost)
    LiuliMomentNotificationsPage(notifications, characters, onBack, open, viewModel::markRead, viewModel::markAllRead, snackbarHostState)
}

/** 无 VM 的消息页（测试直接驱动它）。 */
@Composable
internal fun LiuliMomentNotificationsPage(
    notifications: List<MomentNotificationEntity>,
    characters: Map<String, CharacterEntity>,
    onBack: () -> Unit,
    onOpen: (MomentNotificationEntity) -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val title = stringResource(R.string.moment_notif_list_title)
    val aiLabel = stringResource(R.string.moment_author_ai)
    val relStrings = momentRelativeTimeStrings()
    val nowMillis = rememberTimeTick() // 相对时间每 60s 自动刷新（同暖陶）
    val listState = rememberLazyListState()
    val scrolledPast = rememberLargeTitleCollapsed(listState)
    val collapsed = scrolledPast && notifications.isNotEmpty()
    LiuliPage(
        title = title,
        onBack = onBack,
        collapsed = collapsed,
        actions = if (notifications.isNotEmpty()) {
            { LiuliButton(onClick = onMarkAllRead, style = LiuliButtonStyle.Glass) { Text(stringResource(R.string.moment_notif_mark_all_read)) } }
        } else {
            null
        },
    ) {
        if (notifications.isEmpty()) {
            Column(Modifier.fillMaxSize().contentMaxWidth().padding(top = LiuliPageGeometry.navRow)) {
                LiuliLargeTitle(title)
                MomentNotificationsEmptyState(Modifier.weight(1f).fillMaxWidth().padding(NOTIF_EMPTY_PAD))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().contentMaxWidth(),
                contentPadding = PaddingValues(
                    top = LiuliPageGeometry.navRow,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LiuliPageGeometry.pageBottom,
                ),
            ) {
                item(key = "large-title") { LiuliLargeTitle(title) }
                item(key = "title-gap") { Spacer(Modifier.height(LiuliPageGeometry.titleGap)) }
                itemsIndexed(notifications, key = { _, n -> n.id }) { index, n ->
                    LiuliMomentNotificationItem(
                        notification = n,
                        character = characters[n.characterUuid],
                        aiLabel = aiLabel,
                        timeText = DateFormatters.relativeTimeString(n.timestamp, nowMillis, relStrings),
                        position = liuliSegmentPosition(index, notifications.size),
                        showDivider = index > 0,
                        onOpen = { onOpen(n) },
                        onMarkRead = { onMarkRead(n.id) },
                    )
                }
            }
        }
        LiuliSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** 一条通知：分段卡的一截 + 顶端发丝（画在滑动盒外·不随行位移）+ 左滑标记已读（行透明，底由分段卡画）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiuliMomentNotificationItem(
    notification: MomentNotificationEntity,
    character: CharacterEntity?,
    aiLabel: String,
    timeText: String,
    position: LiuliSegmentPosition,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val dismissState = rememberMomentMarkReadSwipeState(onMarkRead)
    Column(Modifier.padding(horizontal = LiuliPageGeometry.gutter).liuliCardSegment(position, dark)) {
        if (showDivider) {
            // 画在滑动盒外：不随行位移。
            Box(Modifier.fillMaxWidth().padding(start = NOTIF_DIVIDER_INSET).height(NOTIF_DIVIDER).background(LiuliMaterials.divider(dark)))
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = { LiuliMarkReadReveal(dismissState) },
        ) {
            MomentNotificationRow(notification, character, aiLabel, timeText, onClick = onOpen, surface = Modifier) // 行透明：底由分段卡画
        }
    }
}

/** 左滑揭示底：只画被行让出来的那一截（琉璃行透明，整块揭示底会从行身透出来·§0.2-9）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiuliMarkReadReveal(state: SwipeToDismissBoxState) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                // 只画被行让出来的那一截：琉璃行透明，整块揭示底会从行身透出来（§0.2-9）。
                // requireOffset 在绘制阶段已由 SwipeToDismissBox 的布局初始化。
                val revealed = (-state.requireOffset()).coerceIn(0f, size.width)
                clipRect(left = size.width - revealed) { this@drawWithContent.drawContent() }
            },
    ) { MomentMarkReadBackground() }
}
