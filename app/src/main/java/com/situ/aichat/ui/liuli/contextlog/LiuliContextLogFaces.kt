package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.runtime.Composable
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.contextlog.ContextLogCharacterScreen
import com.situ.aichat.ui.contextlog.ContextLogEntryScreen
import com.situ.aichat.ui.contextlog.ContextLogFailureScreen
import com.situ.aichat.ui.contextlog.ContextLogHomeScreen
import com.situ.aichat.ui.contextlog.ContextLogMapScreen
import com.situ.aichat.ui.contextlog.ContextLogSentScreen
import com.situ.aichat.ui.contextlog.ContextLogSettingsScreen
import com.situ.aichat.ui.contextlog.ContextLogReplyScreen
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin

/*
 * 上下文日志各页的选脸包装：与暖陶屏同签名、VM 默认形参不进包装（惯例同 `LiuliSettingsFacesB.kt`）。
 * 「保留设置」页 = 卷五 A-1；其余七页 = 四期·图纸四 §3.1（全文页已由回复全文页取代·图纸五）。
 */

private val liuli: Boolean @Composable get() = LocalAppSkin.current == AppSkin.LIULI

@Composable
fun SkinnedContextLogSettingsScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliContextLogSettingsScreen(onBack = onBack)
        return
    }
    ContextLogSettingsScreen(onBack = onBack)
}

@Composable
fun SkinnedContextLogHomeScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (liuli) LiuliContextLogHomeScreen(onBack, onOpenCharacter, onOpenEntry, onOpenSettings)
    else ContextLogHomeScreen(onBack, onOpenCharacter, onOpenEntry, onOpenSettings)
}

@Composable
fun SkinnedContextLogCharacterScreen(onBack: () -> Unit, onOpenEntry: (Long, Boolean) -> Unit) {
    if (liuli) LiuliContextLogCharacterScreen(onBack, onOpenEntry) else ContextLogCharacterScreen(onBack, onOpenEntry)
}

@Composable
fun SkinnedContextLogEntryScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long, Boolean) -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenSent: (Long) -> Unit,
    onOpenReply: (Long) -> Unit,
) {
    if (liuli) LiuliContextLogEntryScreen(onBack, onOpenEntry, onOpenMap, onOpenSent, onOpenReply)
    else ContextLogEntryScreen(onBack, onOpenEntry, onOpenMap, onOpenSent, onOpenReply)
}

@Composable
fun SkinnedContextLogMapScreen(onBack: () -> Unit, onOpenSaver: () -> Unit) {
    if (liuli) LiuliContextLogMapScreen(onBack, onOpenSaver) else ContextLogMapScreen(onBack, onOpenSaver)
}

@Composable
fun SkinnedContextLogSentScreen(onBack: () -> Unit, onOpenLogSettings: () -> Unit, onOpenMap: (Long) -> Unit) {
    if (liuli) LiuliContextLogSentScreen(onBack, onOpenLogSettings, onOpenMap) else ContextLogSentScreen(onBack, onOpenLogSettings, onOpenMap)
}

@Composable
fun SkinnedContextLogFailureScreen(onBack: () -> Unit, onOpenApiSettings: () -> Unit) {
    if (liuli) LiuliContextLogFailureScreen(onBack, onOpenApiSettings) else ContextLogFailureScreen(onBack, onOpenApiSettings)
}

@Composable
fun SkinnedContextLogReplyScreen(onBack: () -> Unit) {
    if (liuli) LiuliContextLogReplyScreen(onBack) else ContextLogReplyScreen(onBack)
}
