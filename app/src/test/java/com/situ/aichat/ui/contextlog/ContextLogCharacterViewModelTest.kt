package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.ConversationEntity
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.ConversationRepository
import com.situ.aichat.data.repository.MessageRepository
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.model.LogConversationChip
import com.situ.aichat.ui.contextlog.model.LogTurnCard
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** T2-3（四期·图纸四 §3.3·E36）：两步轻投影的参数、无锚点两步都不调、切会话重读。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextLogCharacterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val logDao = mockk<LogDao>()
    private val characters = mockk<CharacterRepository>()
    private val conversations = mockk<ConversationRepository>()
    private val messages = mockk<MessageRepository>()
    private val lin = CharacterEntity(uuid = "a", name = "林晚", creationDate = 0L)

    private fun row(id: Long, ts: Long, conv: String?, turn: String? = null, anchor: String? = null, source: String = LogSource.CHAT) =
        LogListRow(id = id, timestampMillis = ts, characterName = "林晚", characterUuid = "a", conversationUuid = conv, turnId = turn, anchorMessageUuid = anchor, source = source)

    private fun brief(uuid: String, ts: Long, role: String = "user", conv: String = "c1") = LogMessageBrief(uuid, conv, role, "话$uuid", ts, null, false, "plain_text")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { characters.observeAll() } returns flowOf(listOf(lin))
        coEvery { conversations.get("c1") } returns ConversationEntity(uuid = "c1", title = "日常", characterUuid = "a", creationDate = 0L)
        coEvery { conversations.get("c2") } returns ConversationEntity(uuid = "c2", title = "", characterUuid = "a", creationDate = 0L)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(key: String = "a") = ContextLogCharacterViewModel(SavedStateHandle(mapOf("key" to key)), logDao, characters, conversations, messages)

    @Test
    fun twoStepBriefs_fromEarliestAnchor_toMaxRowPlusMinute() = runTest(dispatcher) {
        every { logDao.recent(any()) } returns flowOf(
            listOf(
                row(1, 5_000, "c1", "T1", "u1"),
                row(2, 9_000, "c1", "T2", "u2"),
                row(3, 7_000, null, source = LogSource.GROWTH_ANALYSIS), // 无会话行：不影响终点
                row(4, 1_000, "c2", "T3", "u9"),
            ),
        )
        coEvery { messages.logBriefsByUuids(any()) } returns listOf(brief("u1", 4_000), brief("u2", 8_500))
        coEvery { messages.logBriefsInRange(any(), any(), any()) } returns listOf(brief("u1", 4_000), brief("r1", 4_500, "assistant"), brief("u2", 8_500))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        coVerify(exactly = 1) { messages.logBriefsByUuids(match { it.toSet() == setOf("u1", "u2") }) }
        coVerify(exactly = 1) { messages.logBriefsInRange("c1", 4_000, 9_000 + 60_000) }
        val s = vm.state.value
        assertEquals("林晚", s.name)
        assertEquals(listOf(LogConversationChip("c1", "日常"), LogConversationChip("c2", "")), s.conversations)
        assertEquals("c1", s.selectedConversation)
        val t1 = s.sections.single().items.filterIsInstance<LogTurnCard>().single { it.key == "t:T1" }
        assertEquals(LogTurnQuoteText(emptyList(), "话u1"), t1.quote)
        assertEquals(1, t1.replies)
    }

    @Test
    fun noAnchors_bothStepsSkipped() = runTest(dispatcher) {
        every { logDao.recent(any()) } returns flowOf(listOf(row(1, 5_000, "c1", "T1"), row(2, 6_000, "c1")))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        coVerify(exactly = 0) { messages.logBriefsByUuids(any()) }
        coVerify(exactly = 0) { messages.logBriefsInRange(any(), any(), any()) }
        assertEquals(2, vm.state.value.sections.single().items.size)
    }

    @Test
    fun switchConversation_rereadsForIt() = runTest(dispatcher) {
        every { logDao.recent(any()) } returns flowOf(listOf(row(1, 50_000, "c1", "T1", "u1"), row(2, 20_000, "c2", "T2", "u2")))
        coEvery { messages.logBriefsByUuids(listOf("u1")) } returns listOf(brief("u1", 49_000))
        coEvery { messages.logBriefsByUuids(listOf("u2")) } returns listOf(brief("u2", 19_000, conv = "c2"))
        coEvery { messages.logBriefsInRange(any(), any(), any()) } returns emptyList()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        coVerify(exactly = 1) { messages.logBriefsInRange("c1", 49_000, 110_000) }
        vm.selectConversation("c2")
        advanceUntilIdle()
        coVerify(exactly = 1) { messages.logBriefsInRange("c2", 19_000, 80_000) }
        assertEquals("c2", vm.state.value.selectedConversation)
        assertEquals(listOf("t:T2"), vm.state.value.sections.single().items.map { it.key })
    }
}
