package com.situ.aichat.data.model

/**
 * 深浅外观模式（1:1 iOS `Models/AppSettings.swift` 的 `AppearanceMode`，raw 串对齐 "system"/"light"/"dark"）。
 *
 * iOS 把 `AppearanceMode.colorScheme` 映射为 SwiftUI `preferredColorScheme`（nil = 跟随系统）；
 * 安卓这里映射为 Compose 的 `darkTheme: Boolean`——跟随系统时由 `isSystemInDarkTheme()` 决定。
 *
 * 纯枚举 + 纯函数，无 Compose 依赖，便于单测反推 iOS 语义。
 */
enum class AppearanceMode(val raw: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /**
     * 解析为是否走深色（纯函数，反推 iOS `colorScheme` 语义）：
     * 跟随系统 → 交给系统 `systemInDark`、浅色 → false、深色 → true。
     */
    fun resolveDarkTheme(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** 解析持久化 raw 串，未知/空回退跟随系统（1:1 iOS `AppearanceMode(rawValue:) ?? .system`）。 */
        fun fromRaw(raw: String?): AppearanceMode =
            entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

/**
 * 界面「脸」（琉璃第二张脸·见 FABLE5_THEME_LIULI_PROPOSAL.md §7.1）。与 [AppearanceMode] 深浅**正交**：
 * 脸管配色 + 器型家族（暖陶 / 琉璃），深浅管明暗，两两组合成各自的 AppColors。
 *
 * raw 串持久化（DataStore key 仍是历史遗留的 `"theme_palette"`，值域 `"clay"` / `"liuli"`）；未知/空回退 [DEFAULT]
 * ——老用户存的 `"qinghua"`（青花已推翻）也由此静默回退，**不做迁移**。
 * 默认脸 = 琉璃（用户 2026-09-28 拍板）：从没在外观里选过的人（含新装）进来就是琉璃；自己选过暖陶的照旧暖陶。
 */
enum class AppSkin(val raw: String) {
    CLAY("clay"),   // 暖陶（设计语言主强调 #BE8A76·第一张脸）
    LIULI("liuli"); // 琉璃（液态玻璃·冷灰瓷白 + 钴蓝·第二张脸·默认）

    companion object {
        /** 产品默认脸（单源：读偏好回退、根部外观初值、设置页初值都用它）。 */
        val DEFAULT: AppSkin = LIULI

        fun fromRaw(raw: String?): AppSkin =
            entries.firstOrNull { it.raw == raw } ?: DEFAULT
    }
}

/**
 * 琉璃玻璃「质感」三档（琉璃 2.0·用户 2026-09-24 / 25 拍板）。住 data 层是因为要经 DataStore 持久化
 * （数据层绝不依赖 UI 包）；版本兜底与引擎判定在 `ui/liuli/glass/LiuliGlassEngine.kt`。
 * 未知 / 空 / 旧版的 "clear"、"tinted" 一律回退默认「通透」（旧值不做一一对应·总规划 §4-3）。
 */
enum class GlassTier(val raw: String) {
    FROSTED("frosted"),   // 毛玻璃：自研模糊 + 雾面底色（安卓 13 以下唯一可用档）
    STANDARD("standard"), // 标准：Haze regular
    SHEER("sheer");       // 通透：Haze clear（默认·聊天壁纸上的玻璃自动加厚·卷四起大面板不再加厚）

    companion object {
        fun fromRaw(raw: String?): GlassTier =
            entries.firstOrNull { it.raw == raw } ?: SHEER
    }
}

/**
 * 根部主题读取的外观快照：脸 + 深浅模式 + Material You 动态取色开关 + 玻璃质感档。
 *
 * iOS 这一项是「多主题 currentThemeID」。安卓 2026-06-30 起开放多主题配色，2026-09-04 起升级为
 * 「两张脸一个大脑」（[skin]·见 FABLE5_THEME_LIULI_PROPOSAL.md §1）；[skin] 与 [mode] 深浅正交。
 * 动态取色（[useDynamicColor]）仍为安卓特有 opt-in；[glassTier] 只影响琉璃的玻璃片。
 */
data class AppearanceState(
    val mode: AppearanceMode = AppearanceMode.SYSTEM,
    // Fable-5 Phase 0：默认关动态取色=品牌调色板，Monet 降 opt-in（设计语言 §1.5）。
    val useDynamicColor: Boolean = false,
    // 界面「脸」（默认琉璃 = [AppSkin.DEFAULT]·与深浅正交·见 FABLE5_THEME_LIULI_PROPOSAL.md §7.1）。
    val skin: AppSkin = AppSkin.DEFAULT,
    // 琉璃玻璃质感档（默认通透·只影响琉璃玻璃片）。
    val glassTier: GlassTier = GlassTier.SHEER,
) {
    companion object {
        /** 加载完成前的默认值（跟随系统 + 默认琉璃 + 通透）；启动屏等偏好读完才放首帧，不会先闪一下别的脸。 */
        val DEFAULT = AppearanceState()
    }
}
