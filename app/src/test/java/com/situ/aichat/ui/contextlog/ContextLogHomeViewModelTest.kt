package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import com.situ.aichat.data.local.SettingsPreferences
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.dao.LogStatsDao
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.diagnostics.ContextLogService
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** T2-2（四期·图纸四 §3.2·E29）：路由带 tab 时覆盖并记住一次、selectTab 持久化、showFailures 三件事。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextLogHomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val remembered = MutableStateFlow("")
    private val prefs = mockk<SettingsPreferences>()
    private val logDao = mockk<LogDao>()
    private val statsDao = mockk<LogStatsDao>()
    private val characters = mockk<CharacterRepository>()
    private val settings = mockk<SettingsRepository>()
    private val service = mockk<ContextLogService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { prefs.contextLogHomeTab } returns remembered
        coEvery { prefs.setContextLogHomeTab(any()) } answers { remembered.value = firstArg() }
        every { logDao.recent(any()) } returns flowOf(emptyList())
        every { statsDao.since(any()) } returns flowOf(emptyList())
        every { characters.observeAll() } returns flowOf(emptyList())
        every { settings.appSettings } returns flowOf(AppSettings())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(tab: String? = null, handle: SavedStateHandle = SavedStateHandle(if (tab == null) emptyMap() else mapOf("tab" to tab))) =
        ContextLogHomeViewModel(handle, logDao, statsDao, characters, settings, prefs, service)

    @Test
    fun routeTabTrend_overridesAndRemembersOnce() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("tab" to "trend"))
        val vm = vm(handle = handle)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        coVerify(exactly = 1) { prefs.setContextLogHomeTab("trend") }
        assertEquals(LogHomeTab.TREND, vm.state.value.tab)
        assertEquals("下次从设置进（无参数）也停在趋势", "trend", remembered.value)
        assertNull("路由参数用完即清（复核 R1）：进程被杀恢复这一页时不再覆盖用户后来选的分段", handle.get<String>("tab"))
    }

    @Test
    fun noRouteTab_neverWrites_readsRemembered() = runTest(dispatcher) {
        remembered.value = "flow"
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        coVerify(exactly = 0) { prefs.setContextLogHomeTab(any()) }
        assertEquals(LogHomeTab.FLOW, vm.state.value.tab)
    }

    @Test
    fun selectTab_persists() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        vm.selectTab(LogHomeTab.TREND)
        advanceUntilIdle()
        coVerify(exactly = 1) { prefs.setContextLogHomeTab("trend") }
        assertEquals(LogHomeTab.TREND, vm.state.value.tab)
    }

    @Test
    fun showFailures_flowFailedReasonCleared() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        vm.setCategory(LogCategory.FAILED)
        vm.setReason(LlmFailureKind.TIMEOUT)
        advanceUntilIdle()
        assertEquals(LlmFailureKind.TIMEOUT, vm.state.value.reason)
        vm.selectTab(LogHomeTab.CONVERSATION)
        vm.showFailures()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(LogHomeTab.FLOW, s.tab)
        assertEquals(LogCategory.FAILED, s.flow.category)
        assertNull(s.reason)
        coVerify { prefs.setContextLogHomeTab("flow") }
    }

    @Test
    fun leavingFailed_clearsReason() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        vm.setCategory(LogCategory.FAILED)
        vm.setReason(LlmFailureKind.NETWORK)
        vm.setCategory(LogCategory.CHAT)
        advanceUntilIdle()
        assertNull(vm.state.value.reason)
        vm.clearAll()
        advanceUntilIdle()
        coVerify(exactly = 1) { service.clearAll() }
    }
}
