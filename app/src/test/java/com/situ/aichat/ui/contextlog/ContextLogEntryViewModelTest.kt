package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.ConversationEntity
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.ConversationRepository
import com.situ.aichat.data.repository.MessageRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogAnalysisReader
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** T2-4（四期·图纸四 §3.4）：id 不存在、同轮子项（含挂进来的图片理解）、导出三态、设置透传。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextLogEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }
    private val logDao = mockk<LogDao>()
    private val reader = mockk<LogAnalysisReader>()
    private val messages = mockk<MessageRepository>()
    private val conversations = mockk<ConversationRepository>()
    private val settings = mockk<SettingsRepository>()

    private val entry = LogEntryEntity(
        id = 1, timestampMillis = 10_000, source = LogSource.CHAT, characterName = "林晚", conversationUuid = "c1",
        turnId = "T1", anchorMessageUuid = "u1", requestJson = """{"model":"m","messages":[],"stream":false}""",
    )
    private fun row(id: Long, ts: Long, source: String, turn: String?, anchor: String? = null, ok: Boolean = true) =
        LogListRow(id = id, timestampMillis = ts, source = source, turnId = turn, anchorMessageUuid = anchor, conversationUuid = "c1", isSuccess = ok, promptTokens = 10)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { reader.compareWithPrevious(any()) } returns CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())
        every { reader.shapeOf(any()) } returns null
        every { reader.segmentsOf(any()) } returns emptyList()
        every { reader.adaptationOf(any()) } returns null
        coEvery { conversations.get("c1") } returns ConversationEntity(uuid = "c1", title = "日常", characterUuid = "a", creationDate = 0L)
        every { settings.appSettings } returns flowOf(AppSettings(logDetailEnabled = true, cacheSaverEnabled = false))
        every { logDao.recent(any()) } returns flowOf(
            listOf(
                row(1, 10_000, LogSource.CHAT, "T1", "u1"),
                row(2, 12_000, LogSource.MEMORY_SUMMARY, "T1", ok = false),
                row(3, 9_000, LogSource.IMAGE_UNDERSTANDING, null, "u2"), // 锚点 u2 在本轮用户消息里 → 挂进来
                row(4, 9_500, LogSource.IMAGE_UNDERSTANDING, null, "u9"), // 不在 → 不挂
            ),
        )
        coEvery { messages.logBriefsByUuids(any()) } returns listOf(LogMessageBrief("u1", "c1", "user", "早呀", 8_000, null, false, "plain_text"))
        coEvery { messages.logBriefsInRange("c1", 8_000, 72_000) } returns listOf(
            LogMessageBrief("u1", "c1", "user", "早呀", 8_000, null, false, "plain_text"),
            LogMessageBrief("u2", "c1", "user", "[图片]", 8_500, "img/1.jpg", false, "plain_text"),
            LogMessageBrief("a1", "c1", "assistant", "早", 11_000, null, false, "plain_text"),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun load(id: String, e: LogEntryEntity?) = runTest(dispatcher) {
        coEvery { logDao.getById(id.toLong()) } returns e
        val vm = ContextLogEntryViewModel(SavedStateHandle(mapOf("id" to id)), logDao, reader, messages, conversations, settings, json)
        backgroundScope.launch { vm.state.collect {} }
        // 装配在 Dispatchers.Default 上跑（复核 R1·不占主线程）→ 不能只推虚拟时钟，等真的装好那一帧。
        result = vm.state.first { it.loaded }
    }

    private var result: ContextLogEntryUiState? = null

    @Test
    fun missingId_loadedWithoutEntry() {
        load("99", null)
        assertTrue(result!!.loaded)
        assertNull(result!!.entry)
    }

    @Test
    fun turn_kidsQuoteTitleAndPassthrough() {
        load("1", entry)
        val s = result!!
        assertTrue(s.isTurn)
        assertEquals("日常", s.conversationTitle)
        assertEquals(LogTurnQuoteText(listOf(com.situ.aichat.ui.contextlog.model.LogMediaTag.IMAGE), "早呀"), s.quote)
        assertEquals("同轮后台 + 挂进来的图片理解，按时刻升序", listOf(3L, 2L), s.kids.map { it.id })
        assertTrue(s.kids.single { it.id == 2L }.failed)
        assertTrue(s.detailEnabled)
        assertFalse("两个开关各自透传（复核 R1：原来两个都是 true，对调也测不出）", s.cacheSaverEnabled)
        assertNull("旧记录没有服务商", s.providerLabel)
        assertTrue(s.canExportReplay)
        assertNull("成功 → 没有失败视图", s.failure)
    }

    @Test
    fun canExportReplay_emptyAndBadJson_false() {
        load("1", entry.copy(requestJson = ""))
        assertFalse(result!!.canExportReplay)
        load("1", entry.copy(requestJson = "{坏的"))
        assertFalse("坏 JSON 导不出（E19）", result!!.canExportReplay)
    }

    @Test
    fun backgroundEntry_titledCallDetails_noKids() {
        load("2", LogEntryEntity(id = 2, timestampMillis = 12_000, source = LogSource.MEMORY_SUMMARY, conversationUuid = "c1", turnId = "T1", isSuccess = false, failureKind = "timeout"))
        val s = result!!
        assertFalse(s.isTurn)
        assertEquals(emptyList<Any>(), s.kids)
        assertEquals(com.situ.aichat.diagnostics.LlmFailureKind.TIMEOUT, s.failure!!.kind)
    }

    /**
     * 复核 R1：连着失败的两轮共用锚点 u1（u1 = 图片，第一轮 20s 失败；u2 = 60s 才发的文字，第二轮 80s 成功）——
     * 第一轮的引用不含后发的 u2；图片理解（锚点 u1）只挂在最早带上这张图的第一轮。
     */
    @Test
    fun sharedAnchor_quoteBoundedByTurnStart_imageKidOnEarliestTurn() {
        every { logDao.recent(any()) } returns flowOf(
            listOf(
                row(11, 80_000, LogSource.CHAT, "T2", "u1"),
                row(10, 20_000, LogSource.CHAT, "T1", "u1", ok = false),
                row(12, 16_000, LogSource.IMAGE_UNDERSTANDING, null, "u1"),
            ),
        )
        val u1 = LogMessageBrief("u1", "c1", "user", "[图片]", 15_000, "img/1.jpg", false, "plain_text")
        val u2 = LogMessageBrief("u2", "c1", "user", "在吗", 60_000, null, false, "plain_text")
        coEvery { messages.logBriefsByUuids(any()) } returns listOf(u1)
        coEvery { messages.logBriefsInRange("c1", any(), any()) } returns listOf(u1, u2)
        val t1 = LogEntryEntity(id = 10, timestampMillis = 20_000, source = LogSource.CHAT, conversationUuid = "c1", turnId = "T1", anchorMessageUuid = "u1", isSuccess = false)
        val t2 = LogEntryEntity(id = 11, timestampMillis = 80_000, source = LogSource.CHAT, conversationUuid = "c1", turnId = "T2", anchorMessageUuid = "u1")

        load("10", t1)
        assertEquals("第一轮只看到 20s 之前发的图", LogTurnQuoteText(listOf(com.situ.aichat.ui.contextlog.model.LogMediaTag.IMAGE), null), result!!.quote)
        assertEquals(listOf(12L), result!!.kids.map { it.id })

        load("11", t2)
        assertEquals("第二轮把没回复的两条都带上了", LogTurnQuoteText(listOf(com.situ.aichat.ui.contextlog.model.LogMediaTag.IMAGE), "在吗"), result!!.quote)
        assertEquals("图片理解已归第一轮", emptyList<Long>(), result!!.kids.map { it.id })
    }
}
