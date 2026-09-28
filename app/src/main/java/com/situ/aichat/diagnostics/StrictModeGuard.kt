package com.situ.aichat.diagnostics

import android.os.Build
import android.os.StrictMode

/**
 * 稳定性防线 B（2026-09-28 用户拍板）：debug 包专用的 StrictMode 哨兵——**只记录、不打断**。
 *
 * 违规一律打进 logcat（tag = `StrictMode`，`adb logcat -s StrictMode` 可看；monkey / 体检脚本会顺带计数）：
 *  - 线程策略（只管主线程）：磁盘读 / 写、网络、自定义慢调用、资源类型不匹配、未缓冲 IO——主线程做这些会掉帧乃至 ANR。
 *  - 虚拟机策略（整个进程）：没关的 Closeable / SQLite 游标、Activity 实例泄漏、没注销的广播 / 服务连接、
 *    file:// URI 外泄、content:// 未授权外传；API 31+ 另查 Context 误用与不安全的 Intent 转发。
 *
 * 惩罚只用 `penaltyLog()`：绝不 penaltyDeath / penaltyDialog / penaltyFlashScreen——防线要「看得见」而不是「打断人」，
 * 也不改任何界面。刻意不开 detectCleartextNetwork：debug 清单为 T4 本机假 LLM 服务放行了明文 HTTP
 * （src/debug/AndroidManifest.xml），开了只是噪音。
 *
 * 只由 [com.situ.aichat.AIChatApplication.onCreate] 在 `BuildConfig.DEBUG` 为真时调用；release 包里这一分支是常量 false，
 * R8 连同本类一起删掉。Robolectric 单测里直接跳过（400+ 个 Robolectric 测试共用真 Application，开了只会往测试输出灌噪音）。
 * 关掉：删掉 AIChatApplication 里那一行调用。
 */
internal object StrictModeGuard {

    fun install() {
        if (Build.FINGERPRINT == ROBOLECTRIC_FINGERPRINT) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectIncorrectContextUse()
                        detectUnsafeIntentLaunch()
                    }
                }
                .penaltyLog()
                .build(),
        )
    }

    private const val ROBOLECTRIC_FINGERPRINT = "robolectric"
}
