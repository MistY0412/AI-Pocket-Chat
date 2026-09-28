package com.situ.aichat.moments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 朋友圈前台一轮（乙 L-1）：先处理待互动队列（醒了 / 见面完的来兑现），再补丢失的互动。回前台与前台每 4 分钟循环共用。
 * 整轮在 IO 线程跑：两个调用点都是主线程协程（viewModelScope），而 drain 与恢复场景 D 第一步就同步读待互动队列的
 * SharedPreferences（稳定性防线 B 冷启实测的主线程读盘·图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md ④）。
 */
internal suspend fun runMomentForegroundPass(interaction: MomentInteractionService, recovery: MomentRecoveryService) =
    withContext(Dispatchers.IO) {
        interaction.processPendingInteractions()
        recovery.recoverIfNeeded()
    }
