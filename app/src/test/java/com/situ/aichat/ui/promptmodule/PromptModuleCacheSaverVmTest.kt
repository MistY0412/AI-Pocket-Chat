package com.situ.aichat.ui.promptmodule

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.model.ApiFunction
import com.situ.aichat.data.model.ApiProviderType
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.remote.llm.ApiConfigValues
import com.situ.aichat.data.repository.ApiConfigRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.diagnostics.LogListRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * T2-3（时间感知四期·图纸二 §3.5 · E22–E24 / E26）：[PromptModuleSettingsViewModel.cacheSaver] 真路径——
 * 设置流 / 日志行流 / 配置解析都用 MockK 假掉，VM 的 combine + stateIn 真跑（Robolectric 主线程）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptModuleCacheSaverVmTest {

    private val settingsFlow = MutableStateFlow(AppSettings())
    private val rowsFlow = MutableStateFlow<List<LogListRow>>(emptyList())
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true).also {
        every { it.appSettings } returns settingsFlow
        coEvery { it.getAppSettings() } returns AppSettings()
    }
    private val logDao = mockk<LogDao>().also {
        every { it.recentSuccessBySource(any(), any()) } returns rowsFlow
    }
    private val apiConfigRepo = mockk<ApiConfigRepository>()

    private val jobs = mutableListOf<Job>()
    private val vms = mutableListOf<PromptModuleSettingsViewModel>()
    private var latest: CacheSaverCardState? = null

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
        vms.forEach { it.viewModelScope.cancel() }
        repeat(10) { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun await(message: String, condition: () -> Boolean) {
        repeat(400) {
            idle()
            if (condition()) return
            Thread.sleep(5)
        }
        error("等待超时：$message（latest = $latest）")
    }

    private fun cfg(type: ApiProviderType, model: String) =
        ApiConfigValues(providerType = type, apiKey = "k", baseUrl = "https://example.test/v1", modelName = model)

    private fun vm(characterUuid: String? = null, config: ApiConfigValues? = cfg(ApiProviderType.DEEPSEEK, "deepseek-v4-flash")): PromptModuleSettingsViewModel {
        coEvery { apiConfigRepo.resolveConfigValues(any()) } returns config
        val handle = SavedStateHandle(if (characterUuid != null) mapOf("characterUuid" to characterUuid) else emptyMap())
        val vm = PromptModuleSettingsViewModel(settingsRepo, handle, logDao, apiConfigRepo)
        vms += vm
        jobs += CoroutineScope(Dispatchers.Main).launch { vm.cacheSaver.collect { latest = it } }
        await("首帧") { latest != null }
        return vm
    }

    private fun row(id: Long, hit: Int, miss: Int) =
        LogListRow(id = id, timestampMillis = id, cacheHitTokens = hit, cacheMissTokens = miss)

    @Test
    fun `E24_全局页可见_角色专属页不可见`() {
        assertTrue(vm().cacheSaver.value.visible)
        latest = null
        val charVm = vm(characterUuid = "c1")
        assertFalse(charVm.cacheSaver.value.visible)
        // 订阅前的初值（stateIn 的 initialValue）也要按页面区分：不订阅，直接读 value。
        coEvery { apiConfigRepo.resolveConfigValues(any()) } returns null
        val unsubscribedChar = PromptModuleSettingsViewModel(settingsRepo, SavedStateHandle(mapOf("characterUuid" to "c2")), logDao, apiConfigRepo)
        val unsubscribedGlobal = PromptModuleSettingsViewModel(settingsRepo, SavedStateHandle(), logDao, apiConfigRepo)
        vms += unsubscribedChar
        vms += unsubscribedGlobal
        assertFalse("角色页订阅前的初值不可见", unsubscribedChar.cacheSaver.value.visible)
        assertTrue("总页订阅前的初值可见", unsubscribedGlobal.cacheSaver.value.visible)
    }

    @Test
    fun `E24_角色专属页_开关状态照常反映_供角色记忆行提示用`() {
        settingsFlow.value = AppSettings(cacheSaverEnabled = true)
        val v = vm(characterUuid = "c1")
        await("开") { latest?.enabled == true }
        assertFalse(v.cacheSaver.value.visible)
    }

    @Test
    fun `E23_Claude配置_灰字出现`() {
        vm(config = cfg(ApiProviderType.ANTHROPIC, "claude-sonnet-4-5"))
        await("灰字") { latest?.providerWontCache == true }
        coVerify(exactly = 1) { apiConfigRepo.resolveConfigValues(ApiFunction.CHAT) }
    }

    @Test
    fun `E23_经OpenRouter的Claude_灰字出现`() {
        vm(config = cfg(ApiProviderType.OPENROUTER, "anthropic/claude-sonnet-4-5"))
        await("灰字") { latest?.providerWontCache == true }
    }

    @Test
    fun `DeepSeek配置_无灰字`() {
        vm(config = cfg(ApiProviderType.DEEPSEEK, "deepseek-v4-flash"))
        idle()
        assertFalse(latest!!.providerWontCache)
        coVerify(exactly = 1) { apiConfigRepo.resolveConfigValues(ApiFunction.CHAT) }
    }

    @Test
    fun `E26_首装无配置_无灰字_关_还没有记录`() {
        vm(config = null)
        idle()
        assertEquals(CacheSaverCardState(visible = true, enabled = false, providerWontCache = false, recent = RecentCacheLine.NoRecords), latest)
    }

    @Test
    fun `开关跟设置流_setter写仓库`() {
        val v = vm()
        assertFalse(latest!!.enabled)
        v.setCacheSaverEnabled(true)
        idle()
        coVerify(exactly = 1) { settingsRepo.setCacheSaverEnabled(true) }
        settingsFlow.value = AppSettings(cacheSaverEnabled = true)
        await("开") { latest?.enabled == true }
    }

    @Test
    fun `E22_命中行三态_查的是对话来源最近20条`() {
        vm()
        verify { logDao.recentSuccessBySource("对话", 20) }
        assertEquals(RecentCacheLine.NoRecords, latest!!.recent)
        rowsFlow.value = listOf(row(1, 0, 0), row(2, 0, 0))
        await("有记录无缓存数") { latest?.recent == RecentCacheLine.Rate(2, null) }
        rowsFlow.value = listOf(row(1, 680, 320))
        await("有数") { latest?.recent == RecentCacheLine.Rate(1, 68) }
    }
}
