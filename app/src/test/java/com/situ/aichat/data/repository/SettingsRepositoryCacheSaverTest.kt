package com.situ.aichat.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.situ.aichat.data.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * T2-2（时间感知四期·图纸二 §3.1 · E25）：省钱模式开关的设置四处链——真 [SettingsRepository] + 临时文件 DataStore。
 */
class SettingsRepositoryCacheSaverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: SettingsRepository
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "s.preferences_pb") })
        repo = SettingsRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun 默认关() = runBlocking {
        assertFalse(repo.appSettings.first().cacheSaverEnabled)
        assertFalse(AppSettings().cacheSaverEnabled)
    }

    @Test
    fun setter持久化_键名cache_saver_enabled() = runBlocking {
        repo.setCacheSaverEnabled(true)
        assertTrue(repo.appSettings.first().cacheSaverEnabled)
        assertEquals(true, store.data.first()[SettingsRepository.KEY_CACHE_SAVER])
        assertEquals("cache_saver_enabled", SettingsRepository.KEY_CACHE_SAVER.name)
        repo.setCacheSaverEnabled(false)
        assertFalse(repo.appSettings.first().cacheSaverEnabled)
    }

    @Test
    fun 备份恢复_开着的备份恢复为开() = runBlocking {
        repo.applyBackupSettings(AppSettings(cacheSaverEnabled = true))
        assertTrue(repo.appSettings.first().cacheSaverEnabled)
    }

    @Test
    fun 老备份无字段_恢复后为关_E25() = runBlocking {
        repo.setCacheSaverEnabled(true)
        // 老备份 JSON 里没有该字段 → 照备份导入的宽松解码得默认 false（BackupModels KDoc 同配置）。
        val legacy = lenientJson.decodeFromString(AppSettings.serializer(), """{"textingToneEnabled":false}""")
        assertFalse(legacy.cacheSaverEnabled)
        repo.applyBackupSettings(legacy)
        assertFalse(repo.appSettings.first().cacheSaverEnabled)
        repo.setCacheSaverEnabled(true)
        repo.applyBackupSettings(AppSettings())
        assertFalse(repo.appSettings.first().cacheSaverEnabled)
    }
}
