package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk

/** 卷五测试共用宿主：琉璃脸浅色 + 假触觉（琉璃开关 / 钮都读 [LocalAppHaptics]）。 */
@Composable
internal fun LiuliEditTestHost(dark: Boolean = false, content: @Composable () -> Unit) {
    AIPocketChatTheme(darkTheme = dark, skin = AppSkin.LIULI) {
        CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true), content = content)
    }
}
