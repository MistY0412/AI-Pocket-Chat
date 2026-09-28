package com.situ.aichat.ui.promptmodule

import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.ui.contextlog.cacheSummaryOf

/**
 * 省钱卡状态（时间感知四期·图纸二 §3.5·纯数据 + 纯函数）：两张脸共用，由 [PromptModuleSettingsViewModel.cacheSaver] 产出。
 */

/** 省钱卡的命中行（打开时才显示）：三态。 */
sealed interface RecentCacheLine {
    /** 最近没有成功的对话记录。 */
    data object NoRecords : RecentCacheLine

    /** [count] = 参与的记录条数（≤ [RECENT_CHAT_WINDOW]）；[percent] null = 服务商没报缓存数。 */
    data class Rate(val count: Int, val percent: Int?) : RecentCacheLine
}

data class CacheSaverCardState(
    val visible: Boolean = false,          // 全局页 = true；角色专属页 = false
    val enabled: Boolean = false,
    val providerWontCache: Boolean = false,
    val recent: RecentCacheLine = RecentCacheLine.NoRecords,
)

const val RECENT_CHAT_WINDOW = 20

/** rows = 最近 [RECENT_CHAT_WINDOW] 条成功「对话」记录（新 → 旧）。 */
internal fun recentCacheLineOf(rows: List<LogListRow>): RecentCacheLine =
    if (rows.isEmpty()) RecentCacheLine.NoRecords else RecentCacheLine.Rate(rows.size, cacheSummaryOf(rows)?.ratePercent)
