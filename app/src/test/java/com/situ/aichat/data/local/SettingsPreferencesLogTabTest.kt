package com.situ.aichat.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * T2-6（时间感知四期·图纸四 §3.10）：日志首页分段记忆——真 [SettingsPreferences] + 临时文件 DataStore
 * （范式同 `SettingsRepositoryCacheSaverTest`）。
 */
class SettingsPreferencesLogTabTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: SettingsPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "p.preferences_pb") })
        prefs = SettingsPreferences(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun 没写过读空串() = runBlocking {
        assertEquals("", prefs.contextLogHomeTab.first())
    }

    @Test
    fun 写trend读回_键名context_log_home_tab() = runBlocking {
        prefs.setContextLogHomeTab("trend")
        assertEquals("trend", prefs.contextLogHomeTab.first())
        assertEquals("trend", store.data.first()[stringPreferencesKey("context_log_home_tab")])
        prefs.setContextLogHomeTab("flow")
        assertEquals("覆盖写", "flow", prefs.contextLogHomeTab.first())
    }
}
