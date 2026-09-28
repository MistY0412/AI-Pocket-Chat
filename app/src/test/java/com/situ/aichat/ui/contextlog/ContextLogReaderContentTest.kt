package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.remote.llm.ProviderMessageAdapter
import com.situ.aichat.diagnostics.CacheBreak
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.CacheComparison
import com.situ.aichat.diagnostics.LogContextFormat
import com.situ.aichat.diagnostics.LogRequestShape
import com.situ.aichat.diagnostics.LogSendAdaptation
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.LogPageFixtures.at
import com.situ.aichat.ui.contextlog.LogPageFixtures.m
import com.situ.aichat.ui.contextlog.LogPageFixtures.promptSeg
import com.situ.aichat.ui.contextlog.LogPageFixtures.readerMessages
import com.situ.aichat.ui.contextlog.LogPageFixtures.readerState
import com.situ.aichat.ui.contextlog.LogPageFixtures.stored
import com.situ.aichat.ui.contextlog.LogPageFixtures.turnEntry
import com.situ.aichat.ui.contextlog.model.LogReaderSearch
import com.situ.aichat.ui.contextlog.model.LogSentMessage
import com.situ.aichat.ui.contextlog.model.searchReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-4（四期·图纸五 §4·E1 / E3–E6 / E17–E19 / E21 / E24 / E26 / E27 / E31 / E32）：两张脸的阅读器内容层跑同一批用例。
 * 状态一律经 buildReaderUiState 从真请求造；搜索与 VM 同口径（关键词去首尾空白后非空才搜），由测试宿主在组合里现算。
 */
internal abstract class LogReaderContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(
        state: ContextLogReaderUiState, search: LogReaderSearch?, onOpenLogSettings: () -> Unit, onOpenMap: (Long) -> Unit,
        onQueryChange: (String) -> Unit, onCopyMessage: (LogSentMessage) -> Unit, onCopyAll: () -> Unit,
    )

    /** [staleSearch] = 宿主像 VM 一样「晚几帧才清空」：退出搜索推来的空关键词不理，旧结果一直留着（复核 R1 🟡-1 的最坏情形）。 */
    private fun show(state: ContextLogReaderUiState, staleSearch: Boolean = false) = host {
        var q by remember { mutableStateOf("") }
        val search = remember(state, q) { state.view?.let { v -> q.trim().takeIf { it.isNotEmpty() }?.let { searchReader(v, it) } } }
        Content(
            state, search, onOpenLogSettings = { events += "settings" }, onOpenMap = { events += "map:$it" },
            onQueryChange = { if (!staleSearch || it.isNotBlank()) q = it },
            onCopyMessage = { events += "copy:#${it.index}" }, onCopyAll = { events += "copyAll" },
        )
    }

    private fun countSub(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun countCd(cd: String) = compose.onAllNodesWithContentDescription(cd).fetchSemanticsNodes().size
    private fun click(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).performClick().also { compose.waitForIdle() }
    private fun clickCd(cd: String) = compose.onNodeWithContentDescription(cd).performClick().also { compose.waitForIdle() }
    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextInput(text).also { compose.waitForIdle() }

    private val total = readerMessages.sumOf { it.content!!.length }

    /** 「身份」这一轮换了内容：系统提示词断点 + 「这段变了」。 */
    private fun cutState() = readerState(
        comparison = CacheComparison(
            CacheBreak(CacheBreakKind.SYSTEM_PROMPT, messageIndex = 0, moduleName = "身份", cachedTokensEstimate = 100, totalTokensEstimate = 300),
            at(27, 21, 31), listOf(promptSeg("身份", "你是旧的林晚。")),
        ),
    )

    @Test
    fun overview_caption_legend_cutRow_opensMap() {
        show(cutState())
        for (t in listOf("林晚 · 21:52 这一轮 · deepseek-chat（DeepSeek）", "7", "条消息", "约 5.8k tk · $total 字", "系统提示", "App 附加", "缓存从「身份」断开", "看地图")) {
            assertEquals("「$t」", 1, count(t))
        }
        assertEquals("图例 + 两条用户消息的徽标", 3, count("你"))
        assertEquals("图例 + 角色消息的徽标", 2, count("林晚"))
        click("缓存从「身份」断开")
        assertEquals(listOf("map:1"), events) // E32
    }

    @Test
    fun chips_countsAndFilter() {
        show(readerState())
        for (t in listOf("全部 7", "系统提示 1", "你 2", "林晚 1", "App 附加 3")) assertEquals("「$t」", 1, count(t))
        assertEquals("没有工具消息 → 不出「工具」芯片（E16）", 0, countSub("工具"))
        click("你 2")
        assertEquals(1, count("早呀"))
        assertEquals(1, count("我在看朋友圈"))
        assertEquals(0, count("早～"))
        assertEquals("系统提示词那条被筛掉", 0, count("核心规则"))
    }

    @Test
    fun outline_changedCut_expandAllAndCollapse() {
        show(cutState())
        for (t in listOf("核心规则", "身份", "日程", "这段变了", "缓存从这里断开", "你是林晚。", "展开全部原文")) assertEquals("「$t」", 1, count(t))
        assertEquals("默认只展开变了 / 断点那一节", 0, count(LogPageFixtures.PROMPT_A))
        click("展开全部原文")
        assertEquals(1, count(LogPageFixtures.PROMPT_A))
        assertEquals(1, count(LogPageFixtures.PROMPT_C))
        click("收起")
        assertEquals(0, count(LogPageFixtures.PROMPT_A))
        assertEquals(0, count("你是林晚。"))
        assertEquals(1, count("展开全部原文"))
    }

    @Test
    fun timePill_addedLabels_asIsNotice_footer() {
        show(readerState())
        for (t in listOf(
            "【时间 · 今天 21:52】", "App 附加 · 15 字", "App 附加 · 9 字", "这家服务商能直接读懂 App 的消息结构，原样发送，没有改写。", "共 7 条 · $total 字",
        )) assertEquals("「$t」", 1, count(t))
    }

    @Test
    fun adapted_frameInUserMessage_andIntro() {
        val adapted = ProviderMessageAdapter.adapt(readerMessages)
        show(
            readerState(
                entry = turnEntry.copy(requestJson = stored(adapted.messages)),
                adaptation = LogSendAdaptation(false, adapted.leadingMerged, adapted.midMerged, adapted.tailMerged),
            ),
        )
        assertEquals(1, count("这家服务商会把中途的系统消息挪走或丢掉，App 发送前改写了 3 处："))
        assertEquals("用户消息里的时间标记框", 1, count("App 附加 · 时间标记"))
        assertEquals("合并进开头的截断说明拆回成附加框", 1, count("【对话较长，前面的部分已省略】"))
    }

    @Test
    fun fallback_whenRequestMissing_andTruncatedFooter() {
        show(readerState(entry = turnEntry.copy(requestJson = "", fullContext = LogContextFormat.render(readerMessages))))
        assertEquals(1, count("这条没存下实际发出的请求（旧记录，或请求太长没存），下面是 App 发送前拼好的内容。")) // E4 / E5
        assertEquals(0, count("这家服务商能直接读懂 App 的消息结构，原样发送，没有改写。"))
        assertEquals("兜底也有概览", 1, count("条消息"))
    }

    @Test
    fun truncatedFooter() {
        show(readerState(entry = turnEntry.copy(requestJson = "", fullContext = LogContextFormat.clip(LogContextFormat.render(readerMessages), 200))))
        assertEquals(1, countSub(" 字 · 已截断")) // E6
    }

    @Test
    fun detailOff_threeParts_openSettings_noToolbarActions() {
        show(readerState(entry = turnEntry))
        val tk = LogTokenFormat.compact(LogRequestShape.of(readerMessages).tokens.sum())
        for (t in listOf("这家服务商能直接读懂 App 的消息结构，原样发送，没有改写。", "要看原文，得打开「记录完整详细内容」。关着的时候这里只显示条数和大小。", "共 12 条消息 · 约 $tk tk")) {
            assertEquals("「$t」", 1, count(t)) // K4：说明行 → 改写说明 → 详细关提示
        }
        assertEquals("没有概览 / 页脚（E3）", 0, count("条消息"))
        assertEquals(0, countCd("搜索"))
        assertEquals(0, countCd("复制全部"))
        click("去打开")
        assertEquals(listOf("settings"), events)
    }

    @Test
    fun copyMessage_andCopyAll() {
        show(readerState())
        compose.onAllNodesWithContentDescription("复制这条")[0].performClick()
        clickCd("复制全部")
        assertEquals(listOf("copy:#1", "copyAll"), events)
    }

    @Test
    fun shortContent_noJumpButtons_longContent_hasThem() {
        show(readerState())
        assertEquals("E31", 0, countCd("回到顶部"))
        assertEquals(0, countCd("跳到最后"))
    }

    private val longMessages = (1..30).map { k -> m("user", "紫${k}号" + "\n行".repeat(200)) }

    private fun longState() = readerState(messages = longMessages, segments = emptyList())

    @Test
    fun longContent_hasJumpButtons() {
        show(longState())
        assertEquals(1, countCd("回到顶部"))
        assertEquals(1, countCd("跳到最后"))
    }

    @Test
    fun search_countNext_scrollsTargetIntoView_prevWraps() {
        show(longState())
        clickCd("搜索")
        type("紫")
        assertEquals(1, count("1 / 30"))
        assertEquals("前提：第 2 处起初在屏外", 0, countSub("紫2号"))
        clickCd("下一个")
        assertEquals(1, count("2 / 30"))
        compose.onNode(hasText("紫2号", substring = true), useUnmergedTree = true).assertIsDisplayed() // E24
        assertEquals("搜索态没有浮钮", 0, countCd("回到顶部"))
        clickCd("上一个")
        clickCd("上一个")
        assertEquals("第一处按「上一个」→ 最后一处（E26）", 1, count("30 / 30"))
    }

    @Test
    fun search_onlyHitsToggle_changesRows() {
        show(readerState())
        clickCd("搜索")
        type("朋友圈")
        assertEquals(1, count("1 / 1"))
        assertEquals("只看有命中的消息（默认开）", 0, count("早呀"))
        click("只看有命中的消息")
        assertEquals(1, count("早呀"))
    }

    @Test
    fun search_noHits() {
        show(readerState())
        clickCd("搜索")
        type("不存在")
        assertEquals(1, count("无结果"))
        assertEquals(1, count("没有找到「不存在」")) // E21
        compose.onNodeWithContentDescription("下一个").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上一个").assertIsNotEnabled()
    }

    @Test
    fun search_exit_clearsQuery_keepsFilter() {
        show(readerState())
        click("你 2")
        clickCd("搜索")
        type("早")
        assertEquals(1, count("1 / 1"))
        clickCd("返回")
        assertEquals("退出搜索（E27）", 0, count("搜这一轮发出的内容"))
        assertEquals("筛选保留", 1, count("你 2"))
        assertEquals(0, count("早～"))
        clickCd("搜索")
        assertEquals("关键词已清空（占位字回来了）", 1, count("搜这一轮发出的内容"))
    }

    /** 带命中底色的文字节点数（§4.6：命中 / 当前处都是背景色 span）。 */
    private fun highlightedNodes() = compose.onAllNodes(hasText("紫", substring = true), useUnmergedTree = true).fetchSemanticsNodes().count { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.spanStyles.any { it.item.background != Color.Unspecified } }
    }

    /** 复核 R1 🟡-1：退出搜索后哪怕手里的结果还是旧的，也不许再高亮（§4.6「不在搜 → 原样文字」）——同一口径也挡住了按旧结果把列表拽去第一处。 */
    @Test
    fun search_exit_staleResults_noHighlightLeft() {
        show(longState(), staleSearch = true)
        clickCd("搜索")
        type("紫")
        clickCd("下一个")
        assertTrue("前提：搜索中有高亮", highlightedNodes() > 0)
        clickCd("返回")
        assertEquals("退出搜索后旧结果不再上色", 0, highlightedNodes())
    }

    @Test
    fun emptyName_toolChip_emptyBody() {
        val messages = listOf(m("user", "早"), m("assistant", ""), m("tool", "结果"))
        show(readerState(messages = messages, entry = turnEntry.copy(characterName = "", requestJson = stored(messages)), segments = emptyList()))
        for (t in listOf("角色 1", "工具 1", "角色", "（这条没有文字）")) assertEquals("「$t」", 1, count(t)) // E17 / E18 / E19
        assertEquals("图例 + 工具消息徽标", 2, count("工具"))
    }

    @Test
    fun missingEntry() {
        show(ContextLogReaderUiState(loaded = true))
        assertEquals(1, count("记录不存在或已被清除")) // E1
        assertEquals(0, countCd("搜索"))
        assertTrue(events.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class ContextLogReaderContentTest : LogReaderContentCases() {
    override val skin = AppSkin.CLAY

    @Composable
    override fun Content(
        state: ContextLogReaderUiState, search: LogReaderSearch?, onOpenLogSettings: () -> Unit, onOpenMap: (Long) -> Unit,
        onQueryChange: (String) -> Unit, onCopyMessage: (LogSentMessage) -> Unit, onCopyAll: () -> Unit,
    ) = ContextLogSentContent(state, search, {}, onOpenLogSettings, onOpenMap, onQueryChange, onCopyMessage, onCopyAll)
}
