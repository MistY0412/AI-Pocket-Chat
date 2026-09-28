package com.situ.aichat.ui.liuli.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import com.situ.aichat.ui.chat.BubbleStampTone
import com.situ.aichat.ui.chat.ChatWallpaper
import com.situ.aichat.ui.chat.bubbleStampTone
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import com.situ.aichat.ui.designsystem.LocalAppColors
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassDark
import com.situ.aichat.ui.liuli.designsystem.LiuliOnGlassLight
import com.situ.aichat.ui.liuli.glass.LocalLiuliGlassThick
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 壁纸亮度切深浅的门槛（卷三 §0.1-D：Rec.601 亮度·加厚配方下 0.525–0.73 之间都让主 / 次字 ≥ 4.5，取 0.63 两侧留余量）。
 * 与暖陶 `ChatWallpaperState` 的 0.55 不同——那条管系统栏图标与暖陶玻璃，本条只管琉璃聊天屏的玻璃件。
 */
internal const val LIULI_WALLPAPER_DARK_LUMA = 0.63f

/** 这块壁纸条带算不算暗（纯函数·T1·测试用它扫灰阶）。 */
internal fun liuliWallpaperIsDark(luma: Float): Boolean = luma < LIULI_WALLPAPER_DARK_LUMA

/** 聊天屏上组（顶栏 + 世界胶囊）/ 下组（加号面板 + 输入区）玻璃件的深浅与是否加厚（卷三 §0.2-8）。 */
@Immutable
internal data class LiuliChatChromeTones(val topDark: Boolean, val bottomDark: Boolean, val thick: Boolean)

/** 见面态 = 两组都深；有壁纸 = 按壁纸顶 / 底条带亮度、并加厚；否则跟 App（纯函数·T1）。 */
internal fun liuliChatChromeTones(appIsDark: Boolean, wallpaper: ChatWallpaper?, offlineChrome: Boolean): LiuliChatChromeTones =
    when {
        offlineChrome -> LiuliChatChromeTones(topDark = true, bottomDark = true, thick = wallpaper != null)
        wallpaper != null -> LiuliChatChromeTones(
            topDark = liuliWallpaperIsDark(wallpaper.topLuma),
            bottomDark = liuliWallpaperIsDark(wallpaper.bottomLuma),
            thick = true,
        )
        else -> LiuliChatChromeTones(topDark = appIsDark, bottomDark = appIsDark, thick = false)
    }

/**
 * 在 [content] 这一小片里把琉璃换成 [dark] 那一档（`LocalIsDarkTheme` 与 `LocalAppColors` 一起换——只换其一会出现浅色
 * `accent.text` 压深玻璃），并按 [thick] 打开玻璃加厚。不建布局节点：`content` 里仍可用外层 `BoxScope` 的 `align`。
 */
@Composable
internal fun LiuliChromeTone(dark: Boolean, thick: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides dark,
        LocalAppColors provides if (dark) LiuliDarkAppColors else LiuliLightAppColors,
        LocalLiuliGlassThick provides thick,
        content = content,
    )
}

/** 琉璃的泡下时间戳色调（卷四 §0.2-7）：有壁纸 → 按中段亮度取琉璃玻璃主字色；无壁纸 → null（原样）。 */
internal fun liuliBubbleStampTone(wallpaper: ChatWallpaper?): BubbleStampTone? =
    wallpaper?.let { bubbleStampTone(it.midLuma, LiuliOnGlassDark.primary, LiuliOnGlassLight.primary) }
