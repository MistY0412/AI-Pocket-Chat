package com.situ.aichat.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * 记下「谁在哪个线程取 SharedPreferences」的 Context 包装（启动主线程读盘清零·图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）。
 *
 * 真读盘就发生在首次 `getSharedPreferences`（建目录 / 等加载）这一步，所以断「它不在调用方线程上发生」即可锁住
 * 「没把读盘放回主线程」。`applicationContext` 返回自己，让走 `context.applicationContext.getSharedPreferences` 的
 * store（`PendingDeliveryStore` / `MomentPendingInteractionStore`）也被记到；底下仍是 Robolectric 的真 SharedPreferences。
 */
class PrefsThreadRecordingContext(base: Context) : ContextWrapper(base) {

    private val threads = mutableListOf<Pair<String, Thread>>()

    /** 取过 [prefsName] 的全部线程（按取的先后）。 */
    fun threadsFor(prefsName: String): List<Thread> = synchronized(threads) {
        threads.filter { it.first == prefsName }.map { it.second }
    }

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        synchronized(threads) { threads += name.orEmpty() to Thread.currentThread() }
        return super.getSharedPreferences(name, mode)
    }
}
