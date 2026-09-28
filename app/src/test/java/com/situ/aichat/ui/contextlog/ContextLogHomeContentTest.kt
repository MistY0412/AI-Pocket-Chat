package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.FailureRateAlert
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.model.LogCharacterRowModel
import com.situ.aichat.ui.contextlog.model.LogConversationTabState
import com.situ.aichat.ui.contextlog.model.LogDayKind
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.LogTodayStats
import com.situ.aichat.ui.contextlog.model.LogTrendState
import com.situ.aichat.ui.contextlog.model.SYSTEM_KEY
import com.situ.aichat.ui.contextlog.model.TrendRange
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-7 / T2-8（四期·图纸四 §4.1·内容层）：按对话四格 / 角色行元信息三种日格式逐字 / 失败药丸 / 系统任务行 / 告警类名后缀；
 * 分段点选；全部流水失败行「类名 · 短句」与原因芯片；趋势范围芯片与空态；「⋯」菜单两项。用例写在这里，两张脸各一个子类
 * （琉璃子类在 `ui/liuli/contextlog`）；唯一的脸差 = 今天四格的读法（[assertTodayStats]）。
 */
internal abstract class LogHomeContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(
        state: ContextLogHomeUiState,
        onOpenCharacter: (String) -> Unit,
        onOpenEntry: (Long, Boolean) -> Unit,
        onOpenSettings: () -> Unit,
        onSelectTab: (LogHomeTab) -> Unit,
        onCategory: (LogCategory) -> Unit,
        onReason: (LlmFailureKind?) -> Unit,
        onRange: (TrendRange) -> Unit,
        onShowFailures: () -> Unit,
        onClearAll: () -> Unit,
    )

    /** 今天四格：暖陶逐格是字，琉璃统计卡整卡一句读屏文案。 */
    abstract fun assertTodayStats()

    private fun at(d: Int, h: Int, m: Int = 0) = LogPageFixtures.at(d, h, m)

    private fun row(key: String, name: String, last: Long, day: LogDayKind, turns: Int = 0, voice: Int = 0, bg: Int = 0, rate: Int? = null, failures: Int = 0, top: List<String> = emptyList(), calls: Int = 0) =
        LogCharacterRowModel(key, name, null, last, day, turns, voice, bg, rate, failures, top, calls)

    private val conversation = LogConversationTabState(
        empty = false,
        today = LogTodayStats(calls = 46, failures = 2, cacheRatePercent = 18, totalTokens = 382_000),
        characters = listOf(
            row("a", "林晚", at(27, 21, 52), LogDayKind.TODAY, turns = 12, voice = 1, bg = 3, rate = 41, failures = 2),
            row("b", "阿来", at(26, 21, 5), LogDayKind.YESTERDAY, turns = 3),
            row("name:旧人", "旧人", at(20, 10), LogDayKind.EARLIER, bg = 2),
        ),
        system = row(SYSTEM_KEY, "", at(27, 5), LogDayKind.TODAY, bg = 5, top = listOf(LogSource.DIARY_GENERATION, LogSource.MOMENT_POST), calls = 5),
    )

    private fun show(state: ContextLogHomeUiState) = host {
        Content(
            state = state,
            onOpenCharacter = { events += "char:$it" },
            onOpenEntry = { id, failed -> events += "entry:$id:$failed" },
            onOpenSettings = { events += "settings" },
            onSelectTab = { events += "tab:${it.raw}" },
            onCategory = { events += "cat:${it.name}" },
            onReason = { events += "reason:${it?.raw}" },
            onRange = { events += "range:${it.name}" },
            onShowFailures = { events += "failures" },
            onClearAll = { events += "clear" },
        )
    }

    @Test
    fun conversation_statsRowsMetaPillSystemAlert() {
        show(
            ContextLogHomeUiState(
                loaded = true, conversation = conversation,
                // 按对话读自己的告警（复核 R1：恒对全量算，不随全部流水的「失败」筛选隐去）；flow 里的故意留空
                conversationAlerts = listOf(FailureRateAlert(LogSource.STORY_GENERATION, 3, 4, 75)),
                flow = ContextLogUiState(loaded = true),
                alertKinds = mapOf(LogSource.STORY_GENERATION to LlmFailureKind.TIMEOUT),
            ),
        )
        for (t in listOf("今天", "角色", "不属于具体角色", "系统任务")) {
            assertEquals("「$t」", 1, count(t))
        }
        assertTodayStats()
        assertEquals(1, count("今天 12 轮 · 语音通话 1 次 · 后台 3 次"))
        assertEquals(1, count("昨天 21:05 · 3 轮"))
        assertEquals(1, count("9月20日 · 后台 2 次"))
        assertEquals(1, count("日记生成、朋友圈动态等 · 今天 5 次"))
        assertEquals(1, count("2 失败"))
        assertEquals(1, count("41%"))
        assertEquals("阿来 / 旧人 / 系统任务都没报缓存", 3, count("—"))
        assertEquals(3, count("缓存"))
        assertEquals("告警行接「 · 类名」", 1, count("「故事生成」失败 3/4（75%） · 超时"))
        compose.onNodeWithText("林晚").performClick()
        compose.onNodeWithText("系统任务").performClick()
        compose.onNodeWithText("查看失败 ›", useUnmergedTree = true).performClick()
        assertEquals(listOf("char:a", "char:$SYSTEM_KEY", "failures"), events)
    }

    @Test
    fun notLoaded_nothingUnderTabs_andTabClick() {
        show(ContextLogHomeUiState(loaded = false, conversation = LogConversationTabState(empty = true)))
        assertEquals(0, count("还没有任何调用日志"))
        compose.onNodeWithText("趋势").performClick()
        assertEquals(listOf("tab:trend"), events)
    }

    @Test
    fun emptyConversation() {
        show(ContextLogHomeUiState(loaded = true, conversation = LogConversationTabState(empty = true)))
        assertEquals(1, count("还没有任何调用日志"))
    }

    @Test
    fun flow_failureLineAndReasonChips() {
        val failed = LogListRow(id = 7, timestampMillis = at(27, 21, 33), characterName = "小满", modelName = "deepseek-v4-flash", isSuccess = false, failureKind = "insufficient_balance", errorMessage = "HTTP 402")
        show(
            ContextLogHomeUiState(
                loaded = true, tab = LogHomeTab.FLOW,
                flow = ContextLogUiState(entries = listOf(failed), category = LogCategory.FAILED, loaded = true, detailEnabled = true),
                reasonChips = listOf(LlmFailureKind.INSUFFICIENT_BALANCE to 1),
                flowEntries = listOf(failed),
            ),
        )
        assertEquals(1, count("余额不足 · 服务商说余额不够了，去充值或换 key"))
        assertEquals("原始报错不再上卡", 0, count("HTTP 402"))
        assertEquals(1, count("全部原因 1"))
        assertEquals(1, count("余额不足 1"))
        compose.onNodeWithText("余额不足 1").performClick()
        compose.onNodeWithText("小满").performClick()
        assertEquals(listOf("reason:insufficient_balance", "entry:7:true"), events)
    }

    @Test
    fun trend_rangeChipsAndEmpty() {
        show(ContextLogHomeUiState(loaded = true, tab = LogHomeTab.TREND, trend = LogTrendState(range = TrendRange.WEEK, empty = true)))
        assertEquals(1, count("近 7 天"))
        assertEquals(1, count("这段时间还没有调用记录"))
        compose.onNodeWithText("近 30 天").performClick()
        assertEquals(listOf("range:MONTH"), events)
    }

    @Test
    fun moreMenu_settingsAndClearConfirm() {
        show(ContextLogHomeUiState(loaded = true, conversation = conversation))
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("保留设置").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("清空全部").performClick()
        compose.onNodeWithText("清空全部日志？").assertExists()
        compose.onNodeWithText("清空").performClick()
        assertEquals(listOf("settings", "clear"), events)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
internal class ContextLogHomeContentTest : LogHomeContentCases() {
    override val skin = AppSkin.CLAY

    override fun assertTodayStats() {
        for (t in listOf("46", "2", "18%", "38.2万", "调用", "失败", "缓存命中", "token")) assertEquals("「$t」", 1, count(t))
    }

    @Composable
    override fun Content(
        state: ContextLogHomeUiState,
        onOpenCharacter: (String) -> Unit,
        onOpenEntry: (Long, Boolean) -> Unit,
        onOpenSettings: () -> Unit,
        onSelectTab: (LogHomeTab) -> Unit,
        onCategory: (LogCategory) -> Unit,
        onReason: (LlmFailureKind?) -> Unit,
        onRange: (TrendRange) -> Unit,
        onShowFailures: () -> Unit,
        onClearAll: () -> Unit,
    ) = ContextLogHomeContent(state, {}, onOpenCharacter, onOpenEntry, onOpenSettings, onSelectTab, onCategory, onReason, onRange, onShowFailures, onClearAll)
}
