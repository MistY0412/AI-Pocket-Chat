package com.situ.aichat.ui.liuli.diary

import androidx.compose.runtime.Composable
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin
import com.situ.aichat.ui.diary.ComposeDiaryScreen
import com.situ.aichat.ui.diary.DiaryDetailScreen
import com.situ.aichat.ui.diary.DiaryListScreen
import com.situ.aichat.ui.diary.DiaryPromptPreviewScreen
import com.situ.aichat.ui.diary.DiaryPromptSettingsScreen
import com.situ.aichat.ui.diary.DiarySettingsScreen

/**
 * 日记族三屏的选脸包装（图纸 2026-09-06 卷五 A-1）。三屏的琉璃版排在 C3，本文件先与另外二十七个包装
 * 一起建齐，好让 `AIChatApp.kt` 只动一次（§10 ⑥）。
 * 琉璃 2.0 卷六·一起再加日记本 / 日记详情 / 写日记三屏（签名 = 暖陶去掉 VM 默认形参）。
 */

@Composable
fun SkinnedDiarySettingsScreen(onBack: () -> Unit, onOpenWritingRules: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDiarySettingsScreen(onBack = onBack, onOpenWritingRules = onOpenWritingRules)
        return
    }
    DiarySettingsScreen(onBack = onBack, onOpenWritingRules = onOpenWritingRules)
}

@Composable
fun SkinnedDiaryPromptSettingsScreen(
    onBack: () -> Unit,
    onOpenPreviewMine: () -> Unit,
    onOpenPreviewExchange: () -> Unit,
) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDiaryPromptSettingsScreen(
            onBack = onBack,
            onOpenPreviewMine = onOpenPreviewMine,
            onOpenPreviewExchange = onOpenPreviewExchange,
        )
        return
    }
    DiaryPromptSettingsScreen(
        onBack = onBack,
        onOpenPreviewMine = onOpenPreviewMine,
        onOpenPreviewExchange = onOpenPreviewExchange,
    )
}

@Composable
fun SkinnedDiaryPromptPreviewScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDiaryPromptPreviewScreen(onBack = onBack)
        return
    }
    DiaryPromptPreviewScreen(onBack = onBack)
}

@Composable
fun SkinnedDiaryListScreen(onBack: () -> Unit, onCompose: () -> Unit, onOpenEntry: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDiaryListScreen(onBack = onBack, onCompose = onCompose, onOpenEntry = onOpenEntry)
        return
    }
    DiaryListScreen(onBack = onBack, onCompose = onCompose, onOpenEntry = onOpenEntry)
}

@Composable
fun SkinnedDiaryDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliDiaryDetailScreen(onBack = onBack, onEdit = onEdit)
        return
    }
    DiaryDetailScreen(onBack = onBack, onEdit = onEdit)
}

@Composable
fun SkinnedComposeDiaryScreen(onClose: () -> Unit, onNavigateToApiConfig: () -> Unit = {}) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliComposeDiaryScreen(onClose = onClose, onNavigateToApiConfig = onNavigateToApiConfig)
        return
    }
    ComposeDiaryScreen(onClose = onClose, onNavigateToApiConfig = onNavigateToApiConfig)
}
