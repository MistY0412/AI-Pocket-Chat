package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.ui.contextlog.LogPageFixtures.at
import com.situ.aichat.ui.contextlog.model.LogDayKind
import com.situ.aichat.ui.contextlog.model.LogFailureView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-10（四期·图纸四 §4.3·E5 / E17 / E18 / E38）：一轮详情各卡文字、各导航行回调、子项导航、导出置灰 + 两种提示、老记录三处说明、
 * 失败兜底卡。屏高给到 2400dp，让懒列表一次排完所有卡（逐字断言不受可视区影响）。
 */
internal abstract class LogEntryContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(
        state: ContextLogEntryUiState,
        onOpenEntry: (Long, Boolean) -> Unit,
        onOpenMap: (Long) -> Unit,
        onOpenSent: (Long) -> Unit,
        onOpenReply: (Long) -> Unit,
        onCopyAll: (LogEntryEntity) -> Unit,
        onExportReplay: (LogEntryEntity) -> Unit,
    )

    /** Token 三格：暖陶逐格是字，琉璃统计卡整卡一句读屏文案（唯一的脸差）。 */
    abstract fun assertTokenCells()

    private fun show(state: ContextLogEntryUiState) = host {
        Content(
            state,
            onOpenEntry = { id, failed -> events += "entry:$id:$failed" },
            onOpenMap = { events += "map:$it" },
            onOpenSent = { events += "sent:$it" },
            onOpenReply = { events += "reply:$it" },
            onCopyAll = { events += "copy:${it.id}" },
            onExportReplay = { events += "export:${it.id}" },
        )
    }

    @Test
    fun turn_allCardsVerbatim() {
        show(LogPageFixtures.turnState())
        for (t in listOf(
            "这一轮", "林晚 · 对话「日常」 · 你：「在干嘛呢」", "成功", "2026-09-27 21:52:07",
            "来源", "对话", "模型", "deepseek-chat", "服务商", "DeepSeek", "耗时", "6.8 秒", "消息数", "12 条",
            "Token 用量", "思考 30 · 命中 4,000 · 未命中 1,760",
            "上下文地图", "前置区", "每轮会变", "聊天记录", "末尾块", "前面全部命中，只重算末尾（正常）", "点开看完整地图",
            "实际发出去的样子", "改写了 3 处", "回复全文",
            "这一轮带出的后台调用", "21:53 · 900 tk ›", "21:51 · ≈300 tk · 失败 ›",
            "导出的是这次发给模型的原样请求，不含 API key，可以拿去用真模型重放对比。",
        )) {
            assertEquals("「$t」", 1, count(t))
        }
        assertTokenCells()
        assertEquals("图纸五：「完整上下文」一行已去掉", 0, count("完整上下文"))
    }

    @Test
    fun turn_navigationCallbacks() {
        show(LogPageFixtures.turnState())
        compose.onNodeWithText("前面全部命中，只重算末尾（正常）", useUnmergedTree = true).performClick()
        compose.onNodeWithText("实际发出去的样子", useUnmergedTree = true).performClick()
        compose.onNodeWithText("回复全文", useUnmergedTree = true).performClick()
        compose.onNodeWithText("21:53 · 900 tk ›", useUnmergedTree = true).performClick()
        compose.onNodeWithText("21:51 · ≈300 tk · 失败 ›", useUnmergedTree = true).performClick()
        compose.onNodeWithText("复制全文", useUnmergedTree = true).performClick()
        compose.onNodeWithText("导出可重放请求", useUnmergedTree = true).performClick()
        assertEquals(
            listOf("map:1", "sent:1", "reply:1", "entry:2:false", "entry:3:true", "copy:1", "export:1"),
            events,
        )
    }

    @Test
    fun oldRecord_threeNotes_exportNeedsDetail() {
        show(LogPageFixtures.oldState())
        for (t in listOf("调用详情", "旧记录没有这项", "旧记录没有记下请求的形状，画不出地图", "没有记下发送的内容", "要看原文，得打开「记录完整详细内容」。")) {
            assertEquals("「$t」", 1, count(t))
        }
        assertEquals("说明行（角色名空 → 来源名）+ 来源键值行", 2, count("日记生成"))
        assertEquals(0, count("这一轮带出的后台调用"))
        compose.onNodeWithText("导出可重放请求").assertIsNotEnabled()
    }

    @Test
    fun detailOnButNoRequest_exportNone() {
        show(LogPageFixtures.oldState().copy(detailEnabled = true))
        assertEquals(1, count("这条没有可导出的请求。"))
        compose.onNodeWithText("导出可重放请求").assertIsNotEnabled()
    }

    @Test
    fun failedEntry_fallbackCard_goesToFailurePage() {
        val failed = LogPageFixtures.turnEntry.copy(isSuccess = false, failureKind = "insufficient_balance")
        show(
            LogPageFixtures.turnState(entry = failed).copy(
                failure = LogFailureView(LlmFailureKind.INSUFFICIENT_BALANCE, 402, "HTTP 402 · x", 1, listOf(at(27, 21, 52)), LogDayKind.TODAY),
            ),
        )
        assertEquals("失败", 1, count("失败"))
        assertEquals(0, count("Token 用量"))
        assertEquals(1, count("余额不足"))
        assertEquals(1, count("服务商说余额不够了，去充值或换 key"))
        compose.onNodeWithText("看失败详情", useUnmergedTree = true).performClick()
        assertEquals(listOf("entry:1:true"), events)
    }

    @Test
    fun missingEntry() {
        show(ContextLogEntryUiState(loaded = true))
        assertEquals(1, count("记录不存在或已被清除"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class ContextLogEntryContentTest : LogEntryContentCases() {
    override val skin = AppSkin.CLAY

    override fun assertTokenCells() {
        for (t in listOf("5,760", "120", "69%")) assertEquals("「$t」", 1, count(t))
    }

    @Composable
    override fun Content(
        state: ContextLogEntryUiState,
        onOpenEntry: (Long, Boolean) -> Unit,
        onOpenMap: (Long) -> Unit,
        onOpenSent: (Long) -> Unit,
        onOpenReply: (Long) -> Unit,
        onCopyAll: (LogEntryEntity) -> Unit,
        onExportReplay: (LogEntryEntity) -> Unit,
    ) = ContextLogEntryContent(state, {}, onOpenEntry, onOpenMap, onOpenSent, onOpenReply, onCopyAll, onExportReplay)
}
