package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogAnalysisReader
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.diagnostics.LogReplayRequest
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.ui.contextlog.model.LogAddedKind
import com.situ.aichat.ui.contextlog.model.LogOutlineSection
import com.situ.aichat.ui.contextlog.model.LogReaderCutAt
import com.situ.aichat.ui.contextlog.model.LogReaderHit
import com.situ.aichat.ui.contextlog.model.LogReaderSearch
import com.situ.aichat.ui.contextlog.model.LogReaderSource
import com.situ.aichat.ui.contextlog.model.LogSentPiece
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T2-1…T2-3（四期·图纸五 §3.1）：阅读器 VM 的来源选择（实际请求 / 兜底 / 详细关）、改写开头拆回、原样解读兜底（E40）、
 * 搜索流（也在 Default 上算）与纯装配（红框取自地图、20 万字级分块、截断页脚）。装配在 Dispatchers.Default → 等真的那一帧。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextLogReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true } // = NetworkModule.provideJson
    private val logDao = mockk<LogDao>()
    private val reader = mockk<LogAnalysisReader>()

    private val a = "核心规则：别说教。"
    private val b = "你是林晚。"
    private val c = "今天的日程：上班。"
    private val promptText = listOf(a, b, c).joinToString("\n\n")
    private fun seg(name: String, t: String) = ContextSegment(name, null, t.length, 0, ContextSegment.POSITION_PREFIX, ContextSegment.fingerprintOf(t))
    private val segments = listOf(seg("核心规则", a), seg("身份", b), seg("日程", c))

    private fun m(role: String, content: String) = ChatMessageDto(role = role, content = content)
    private val original = listOf(
        m("system", promptText),
        m("system", "【对话较长，前面的部分已省略】"),
        m("user", "早呀"),
        m("assistant", "早～"),
        m("system", "【时间 · 今天 21:52】"),
        m("user", "我在看朋友圈"),
        m("system", "回复规则：短一点。朋友圈别提。"),
    )
    private val shape = LogRequestShape.of(original)
    private fun stored(messages: List<ChatMessageDto>) = LogReplayRequest.encodeForStore(json, ChatRequestDto(model = "m", messages = messages, stream = false))
    private val noPrev = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())
    private val baseEntry = LogEntryEntity(id = 1, timestampMillis = 10_000, source = LogSource.CHAT, characterName = "林晚", conversationUuid = "c1", providerType = "deepseek")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { reader.compareWithPrevious(any()) } returns noPrev
        every { reader.shapeOf(any()) } returns shape
        every { reader.segmentsOf(any()) } returns segments
        every { reader.adaptationOf(any()) } returns LogSendAdaptation(asIs = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: String, e: LogEntryEntity?): ContextLogReaderViewModel {
        coEvery { logDao.getById(id.toLong()) } returns e
        return ContextLogReaderViewModel(SavedStateHandle(mapOf("id" to id)), logDao, reader, json)
    }

    private fun load(id: String, e: LogEntryEntity?): ContextLogReaderUiState {
        var out: ContextLogReaderUiState? = null
        runTest(dispatcher) {
            val v = vm(id, e)
            backgroundScope.launch { v.state.collect {} }
            out = v.state.first { it.loaded }
        }
        return out!!
    }

    // ── T2-1 ──

    @Test
    fun missingId_loadedWithoutEntry() {
        val s = load("99", null)
        assertTrue(s.loaded)
        assertNull(s.entry) // E1
        assertNull(s.view)
    }

    @Test
    fun asIsRequest_requestSource_withOutline() {
        val s = load("1", baseEntry.copy(requestJson = stored(original), fullContext = LogContextFormat.render(original)))
        val v = s.view!!
        assertEquals(LogReaderSource.REQUEST, v.source)
        assertEquals("DeepSeek", s.providerLabel)
        assertEquals(listOf("核心规则", "身份", "日程"), v.outline!!.sections.map { it.title })
        assertEquals(7, v.messages.size)
        assertEquals(shape.tokens.sum(), s.shapeTokens)
    }

    @Test
    fun adaptedWithLeadMerged_splitBack() {
        val adapted = ProviderMessageAdapter.adapt(original)
        every { reader.adaptationOf(any()) } returns LogSendAdaptation(false, adapted.leadingMerged, adapted.midMerged, adapted.tailMerged)
        val s = load("1", baseEntry.copy(requestJson = stored(adapted.messages)))
        val view = s.view!!
        val first = view.messages[0]
        assertEquals("前提：合并了开头两条", 2, adapted.leadingMerged)
        assertEquals(
            listOf(
                LogSentPiece.Text(promptText),
                LogSentPiece.Added(LogAddedKind.NOTE, "【对话较长，前面的部分已省略】"),
                LogSentPiece.Added(LogAddedKind.NOTE, ProviderMessageAdapter.EXPLAIN_NOTE),
            ),
            first.pieces,
        )
        assertEquals("系统提示词本体照常拆目录", 3, view.outline!!.sections.size)
    }

    @Test
    fun badRequestJson_fallsBackToRendered() {
        val s = load("1", baseEntry.copy(requestJson = "{坏的", fullContext = LogContextFormat.render(original)))
        val view = s.view!!
        assertEquals(LogReaderSource.RENDERED, view.source) // E4
        assertEquals(7, view.messages.size)
        val v51 = load("1", baseEntry.copy(requestJson = "", fullContext = LogContextFormat.render(original)))
        assertEquals(LogReaderSource.RENDERED, v51.view!!.source) // E5
    }

    @Test
    fun bothEmpty_noView_shapeTokens() {
        val s = load("1", baseEntry)
        assertNull(s.view) // E3
        assertEquals(shape.tokens.sum(), s.shapeTokens)
        every { reader.shapeOf(any()) } returns null
        assertNull("没有形状 → null", load("1", baseEntry).shapeTokens)
    }

    @Test
    fun requestWithoutAdaptationRecord_readAsIs() {
        every { reader.adaptationOf(any()) } returns null
        val s = load("1", baseEntry.copy(requestJson = stored(original)))
        assertEquals(
            "按原样解读：第 2 条 system 整条是 App 附加（E40）",
            listOf(LogSentPiece.Added(LogAddedKind.NOTE, "【对话较长，前面的部分已省略】")),
            s.view!!.messages[1].pieces,
        )
        assertNull(s.adaptation)
    }

    // ── T2-2 ──

    @Test
    fun search_trimmedQuery_hitsOnDefault_blankClears() = runTest(dispatcher) {
        val v = vm("1", baseEntry.copy(requestJson = stored(original)))
        backgroundScope.launch { v.state.collect {} }
        backgroundScope.launch { v.search.collect {} }
        v.state.first { it.loaded }
        v.setQuery("  朋友圈 ")
        val s: LogReaderSearch = v.search.first { it != null }!!
        assertEquals("朋友圈", s.query)
        assertEquals(listOf(LogReaderHit(6, 0, 3, 6), LogReaderHit(7, 0, 9, 12)), s.hits)
        v.setQuery("   ")
        assertNull(v.search.first { it == null })
    }

    @Test
    fun search_noView_staysNull() = runTest(dispatcher) {
        val v = vm("1", baseEntry)
        backgroundScope.launch { v.state.collect {} }
        backgroundScope.launch { v.search.collect {} }
        assertNull(v.state.first { it.loaded }.view)
        v.setQuery("朋友圈")
        val appeared = withContext(Dispatchers.Default) { withTimeoutOrNull(300) { v.search.first { it != null } } }
        assertNull(appeared)
    }

    // ── T2-3 ──

    @Test
    fun pure_frameFromMap_moduleMissingNamesNothing() {
        val cmp = CacheComparison(CacheBreak(CacheBreakKind.SYSTEM_PROMPT, messageIndex = 0, moduleName = "旧模块", cachedTokensEstimate = 0, totalTokensEstimate = 100), 5_000L, emptyList())
        val s = buildReaderUiState(baseEntry.copy(requestJson = stored(original)), cmp, shape, segments, LogSendAdaptation(asIs = true), json)
        val view = s.view!!
        val frame = view.frame!!
        assertEquals(CacheBreakKind.SYSTEM_PROMPT, frame.kind)
        assertNull("地图：模块本轮找不到 → 红框不写模块名", frame.moduleName)
        assertEquals(5_000L, frame.previousTimeMillis)
        assertEquals("目录末尾", LogReaderCutAt.InOutline(3), view.cutAt)
        assertNull("第一轮 / 旧记录：没有红框", buildReaderUiState(baseEntry.copy(requestJson = stored(original)), noPrev, shape, segments, null, json).view!!.frame)
    }

    @Test
    fun pure_twoHundredThousandChars_blocks() {
        val big = listOf(m("user", "字".repeat(200_000)))
        val requestJson = json.encodeToString(ChatRequestDto.serializer(), ChatRequestDto(model = "m", messages = big, stream = false))
        val s = buildReaderUiState(baseEntry.copy(requestJson = requestJson), noPrev, LogRequestShape.of(big), emptyList(), null, json)
        assertEquals("⌈200000 / 400⌉（E37）", 500, s.view!!.textBlocks.getValue(1 to 0).size)
    }

    @Test
    fun pure_renderedTruncatedFooterFlag_requestNeverTruncated() {
        val clipped = LogContextFormat.clip(LogContextFormat.render(original), 200) // 留到第 1 条表头之后，兜底解析得出
        val s = buildReaderUiState(baseEntry.copy(fullContext = clipped), noPrev, shape, segments, null, json)
        assertTrue("兜底 + 截断尾巴（E6）", s.view!!.truncated)
        val r = buildReaderUiState(baseEntry.copy(requestJson = stored(original), fullContext = clipped), noPrev, shape, segments, null, json)
        val rv = r.view!!
        assertFalse("实际请求视图不看 fullContext 的截断", rv.truncated)
        assertNotNull(rv.outline)
        assertEquals(LogOutlineSection("核心规则", 0, a.length, false), rv.outline!!.sections[0])
    }
}
