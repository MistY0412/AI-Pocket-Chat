package com.situ.aichat.util

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.LocaleList
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.LocaleManager as SystemLocaleManager

/**
 * 启动主线程读盘清零 ⑤（图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）：首启语言有意留在主线程同步做，
 * 只对 StrictMode 声明「有意为之」。锁两件事：
 *  1. 语言规则一字不变（13.10d）：首启简体中文 / 老用户选择迁进系统 / 「跟随系统」按系统语言归一 / 已设显式语言不动；
 *     Android 12 及以下（这里用 sdk 30）把残留的「跟随系统」迁成显式标签。
 *  2. 放行只限这一段：读写盘期间 StrictMode 不查磁盘，结束后调用方的线程策略原样还原。
 * 期望值从 LocaleManager 的 KDoc 规格独立写出。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleManagerEnsureDefaultTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun legacyPrefs(): SharedPreferences = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    private fun systemLocales(): SystemLocaleManager = app.getSystemService(SystemLocaleManager::class.java)

    private fun appLanguage(): String = systemLocales().applicationLocales.toLanguageTags()

    /** 模拟「刚装好」：系统里还没有本 App 的语言、旧 prefs 为空（Application.onCreate 已替测试环境设过一次，先清掉）。 */
    @Before
    fun freshInstall() {
        legacyPrefs().edit().clear().commit()
        if (android.os.Build.VERSION.SDK_INT >= 33) systemLocales().applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun 首启无旧选择_落简体中文() {
        LocaleManager.ensureDefaultLocale(app)

        assertEquals("zh-CN", appLanguage())
    }

    @Test
    fun 老用户曾选英文_迁进系统不丢() {
        legacyPrefs().edit().putString(LEGACY_KEY, "en").commit()

        LocaleManager.ensureDefaultLocale(app)

        assertEquals("en", appLanguage())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun 曾选跟随系统_英文系统归一为英文() {
        legacyPrefs().edit().putString(LEGACY_KEY, "").commit()

        LocaleManager.ensureDefaultLocale(app)

        assertEquals("en", appLanguage())
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun 曾选跟随系统_不支持的系统语言回落简体中文() {
        legacyPrefs().edit().putString(LEGACY_KEY, "").commit()

        LocaleManager.ensureDefaultLocale(app)

        assertEquals("zh-CN", appLanguage())
    }

    @Test
    fun 已设显式语言_不动() {
        systemLocales().applicationLocales = LocaleList.forLanguageTags("en")
        legacyPrefs().edit().putString(LEGACY_KEY, "zh-CN").commit()

        LocaleManager.ensureDefaultLocale(app)

        assertEquals("en", appLanguage())
    }

    @Test
    fun 读写盘期间放行StrictMode_结束后原样还原() {
        val strict = StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build()
        val permitted = StrictMode.ThreadPolicy.Builder(strict).permitDiskReads().permitDiskWrites().build()
        var policyWhileReading: String? = null
        val probe = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                policyWhileReading = StrictMode.getThreadPolicy().toString()
                return super.getSharedPreferences(name, mode)
            }
        }
        StrictMode.setThreadPolicy(strict)
        try {
            LocaleManager.ensureDefaultLocale(probe)

            assertEquals("读旧 prefs 时磁盘检查应已放行（其余检查照旧）", permitted.toString(), policyWhileReading)
            assertEquals("结束后调用方的线程策略原样还原", strict.toString(), StrictMode.getThreadPolicy().toString())
            assertEquals("zh-CN", appLanguage())
        } finally {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
    }

    @Test
    @Config(sdk = [30], qualifiers = "en-rUS")
    fun 安卓12及以下_残留跟随系统迁成显式标签() {
        legacyPrefs().edit().putString(LEGACY_KEY, "").commit()

        LocaleManager.ensureDefaultLocale(app)

        assertEquals("en", legacyPrefs().getString(LEGACY_KEY, null))
    }

    @Test
    @Config(sdk = [30])
    fun 安卓12及以下_没选过或已选显式语言_一字不写() {
        LocaleManager.ensureDefaultLocale(app)
        assertFalse("没选过 → 不写（首启默认由 wrap() 读缺省值给出）", legacyPrefs().contains(LEGACY_KEY))

        legacyPrefs().edit().putString(LEGACY_KEY, "en").commit()
        LocaleManager.ensureDefaultLocale(app)
        assertEquals("en", legacyPrefs().getString(LEGACY_KEY, null))
    }

    private companion object {
        /** 与 [LocaleManager] 的 prefs 名 / 键同值（它们是 private，这里照抄）。 */
        const val LEGACY_PREFS = "app_locale"
        const val LEGACY_KEY = "lang_tag"
    }
}
