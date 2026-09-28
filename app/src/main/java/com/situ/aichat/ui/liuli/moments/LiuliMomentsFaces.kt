package com.situ.aichat.ui.liuli.moments

import androidx.compose.runtime.Composable
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin
import com.situ.aichat.ui.moments.ComposeMomentScreen
import com.situ.aichat.ui.moments.DayMomentsScreen
import com.situ.aichat.ui.moments.MomentAuthorScreen
import com.situ.aichat.ui.moments.MomentDetailScreen
import com.situ.aichat.ui.moments.MomentNotificationListScreen
import com.situ.aichat.ui.moments.MomentSettingsScreen
import com.situ.aichat.ui.moments.MomentsListScreen

// 朋友圈选脸包装：设置页（卷五 A-1）；卷六·二：朋友圈六屏（圈子 / 某人与我的动态 / 那天的动态 / 动态详情 / 发动态 / 消息）。
// 签名 = 暖陶去掉 VM 默认形参，由反射测试钉住（LiuliMomentsPageFacesTest）。

/** 朋友圈设置页的选脸包装（图纸 2026-09-06 卷五 A-1）。琉璃版排在 C3。 */
@Composable
fun SkinnedMomentSettingsScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliMomentSettingsScreen(onBack = onBack)
        return
    }
    MomentSettingsScreen(onBack = onBack)
}

@Composable
fun SkinnedMomentsListScreen(
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCharacterMoments: (String) -> Unit,
) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliMomentsListScreen(onBack, onCompose, onOpenPost, onOpenNotifications, onOpenCharacterMoments)
        return
    }
    MomentsListScreen(onBack, onCompose, onOpenPost, onOpenNotifications, onOpenCharacterMoments)
}

@Composable
fun SkinnedMomentAuthorScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliMomentAuthorScreen(onBack = onBack, onOpenPost = onOpenPost)
        return
    }
    MomentAuthorScreen(onBack = onBack, onOpenPost = onOpenPost)
}

@Composable
fun SkinnedDayMomentsScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDayMomentsScreen(onBack = onBack, onOpenPost = onOpenPost)
        return
    }
    DayMomentsScreen(onBack = onBack, onOpenPost = onOpenPost)
}

@Composable
fun SkinnedMomentDetailScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliMomentDetailScreen(onBack = onBack)
        return
    }
    MomentDetailScreen(onBack = onBack)
}

@Composable
fun SkinnedComposeMomentScreen(onClose: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliComposeMomentScreen(onClose = onClose)
        return
    }
    ComposeMomentScreen(onClose = onClose)
}

@Composable
fun SkinnedMomentNotificationListScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliMomentNotificationsScreen(onBack = onBack, onOpenPost = onOpenPost)
        return
    }
    MomentNotificationListScreen(onBack = onBack, onOpenPost = onOpenPost)
}
