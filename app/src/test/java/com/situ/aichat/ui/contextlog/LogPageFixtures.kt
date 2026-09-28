package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.data.remote.llm.ChatRequestDto
import com.situ.aichat.diagnostics.LogReplayRequest
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.diagnostics.LogTimeMarker
import com.situ.aichat.prompt.ContextSegment
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.contextlog.model.LogKid
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import com.situ.aichat.ui.contextlog.model.buildContextMap
import com.situ.aichat.ui.contextlog.model.buildMiniMap
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 日志页内容层测试的公共底座（四期·图纸四 T2-9…T2-13）：两张脸的内容层测试继承同一组用例（[skin] 选脸），
 * 保证「内容 / 顺序 / 文案完全相同」由同一批断言钉住。时区钉 Asia/Shanghai（时刻 / 日期文字可逐字断言）。
 */
internal abstract class LogPageTestBase {

    @get:Rule val compose = createComposeRule()

    abstract val skin: AppSkin

    val events = mutableListOf<String>()
    private var savedTz: TimeZone? = null

    @Before
    fun pinZone() {
        savedTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(LogPageFixtures.zone))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedTz)

    fun host(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = skin) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) { content() }
            }
        }
        compose.waitForIdle()
    }

    fun count(text: String): Int = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
}

internal object LogPageFixtures {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun at(d: Int, h: Int, m: Int = 0, s: Int = 0): Long = LocalDateTime.of(2026, 9, d, h, m, s).atZone(zone).toInstant().toEpochMilli()

    /** 一轮成功的对话：21:52，输入 5760 / 输出 120，命中 4000 / 未命中 1760（69%），改写三处。 */
    val turnEntry = LogEntryEntity(
        id = 1, timestampMillis = at(27, 21, 52, 7), characterName = "林晚", modelName = "deepseek-chat", isSuccess = true,
        source = LogSource.CHAT, messageCount = 12, durationMillis = 6_800, responseContent = "嗯嗯", fullContext = "全文",
        promptTokens = 5_760, completionTokens = 120, reasoningTokens = 30, cacheHitTokens = 4_000, cacheMissTokens = 1_760,
        isTokenEstimated = false, conversationUuid = "c1", turnId = "T1", providerType = "deepseek",
    )

    val shape = LogRequestShape(
        "ssuasu", listOf("S", "L", "U1", "A1", "M", "U2"), listOf(300, 20, 100, 100, 10, 100),
        listOf(LogTimeMarker(4, "【时间 · 今天 21:52 · 距离上条消息过去了约 12 分钟】")),
    )

    fun comparison(kind: CacheBreakKind, module: String? = null, cached: Int = 0, total: Int = 630, prev: Long? = at(27, 21, 31)) =
        CacheComparison(CacheBreak(kind, moduleName = module, cachedTokensEstimate = cached, totalTokensEstimate = total), prev, emptyList())

    fun segments(): List<ContextSegment> = listOf(
        ContextSegment("核心规则", null, 400, 200, ContextSegment.POSITION_PREFIX, "k"),
        ContextSegment("人设", null, 200, 100, ContextSegment.POSITION_PREFIX, "p"),
    )

    fun kids() = listOf(LogKid(2, LogSource.MEMORY_SUMMARY, at(27, 21, 53), 900, false, false), LogKid(3, LogSource.IMAGE_UNDERSTANDING, at(27, 21, 51), 300, true, true))

    /** 「这一轮」页的完整状态（各页共用；按需 copy 改字段）。 */
    fun turnState(
        entry: LogEntryEntity = turnEntry,
        comparison: CacheComparison = comparison(CacheBreakKind.TAIL, cached = 580),
        purged: Boolean = false,
    ): ContextLogEntryUiState {
        val map = buildContextMap(shape, if (purged) emptyList() else segments(), comparison, purged)
        return ContextLogEntryUiState(
            loaded = true, entry = entry, isTurn = true, conversationTitle = "日常",
            quote = LogTurnQuoteText(emptyList(), "在干嘛呢"), providerLabel = "DeepSeek", cacheRatePercent = 69,
            comparison = comparison, map = map, miniMap = buildMiniMap(map),
            adaptation = LogSendAdaptation(asIs = false, leadingMerged = 2, midMerged = 1, tailMerged = 1),
            kids = kids(), detailEnabled = true, canExportReplay = true,
        )
    }

    // ── 上下文阅读器（四期·图纸五 T2-4）：状态一律用真请求经 buildReaderUiState 造 ──

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true } // = NetworkModule.provideJson
    const val PROMPT_A = "核心规则：别说教。"
    const val PROMPT_B = "你是林晚。"
    const val PROMPT_C = "今天的日程：上班。"
    val promptText = listOf(PROMPT_A, PROMPT_B, PROMPT_C).joinToString("\n\n")

    fun promptSeg(name: String, text: String, fp: String = ContextSegment.fingerprintOf(text)) =
        ContextSegment(name, null, text.length, 0, ContextSegment.POSITION_PREFIX, fp)

    val readerSegments = listOf(promptSeg("核心规则", PROMPT_A), promptSeg("身份", PROMPT_B), promptSeg("日程", PROMPT_C))

    fun m(role: String, content: String) = ChatMessageDto(role = role, content = content)

    /** 7 条：系统提示词 / 截断说明 / 早呀 / 早～ / 时间标记 / 我在看朋友圈 / 末尾规则。 */
    val readerMessages = listOf(
        m("system", promptText), m("system", "【对话较长，前面的部分已省略】"), m("user", "早呀"), m("assistant", "早～"),
        m("system", "【时间 · 今天 21:52】"), m("user", "我在看朋友圈"), m("system", "回复规则：短一点。"),
    )

    fun stored(messages: List<ChatMessageDto>): String =
        LogReplayRequest.encodeForStore(json, ChatRequestDto(model = "m", messages = messages, stream = false))

    val noPrevious = CacheComparison(CacheBreak(CacheBreakKind.NO_PREVIOUS), null, emptyList())

    /** 阅读器状态：默认 = 原样发送的真请求（DeepSeek·林晚·21:52）。 */
    fun readerState(
        messages: List<ChatMessageDto> = readerMessages,
        entry: LogEntryEntity = turnEntry.copy(requestJson = stored(messages)),
        comparison: CacheComparison = noPrevious,
        adaptation: LogSendAdaptation? = LogSendAdaptation(asIs = true),
        segments: List<ContextSegment> = readerSegments,
        shape: LogRequestShape? = LogRequestShape.of(messages),
    ): ContextLogReaderUiState = buildReaderUiState(entry, comparison, shape, segments, adaptation, json)

    /** v50 老记录：没有服务商 / 形状 / 改写记录 / 请求体，详细记录关着。 */
    fun oldState(): ContextLogEntryUiState = ContextLogEntryUiState(
        loaded = true,
        entry = LogEntryEntity(id = 9, timestampMillis = at(20, 9), characterName = "", modelName = "m", source = LogSource.DIARY_GENERATION, messageCount = 2),
        isTurn = false,
    )
}
