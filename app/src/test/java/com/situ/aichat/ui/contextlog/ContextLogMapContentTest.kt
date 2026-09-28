package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.LogPageFixtures.comparison
import com.situ.aichat.ui.contextlog.LogPageFixtures.turnState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-11（四期·图纸四 §4.4）：三种断点的红框文字逐字（含「和上一轮（HH:mm）相比，」与「约 N tk」）、省钱入口显示规则
 * （命中 < 40 且省钱关且来源对话）、分段已清提示、首条可比说明、服务商报告卡、老记录。
 */
internal abstract class LogMapContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(state: ContextLogEntryUiState, onOpenSaver: () -> Unit)

    private fun show(state: ContextLogEntryUiState) = host { Content(state, onOpenSaver = { events += "saver" }) }

    private val saverNav = "想提高命中？打开「省钱模式」"

    @Test
    fun cut_systemPromptModule() {
        show(turnState(comparison = comparison(CacheBreakKind.SYSTEM_PROMPT, module = "人设", cached = 200)))
        assertEquals(1, count("缓存从这里断开"))
        assertEquals(1, count("和上一轮（21:31）相比，下面这段「人设」换了内容。从这里往后约 430 tk 按原价计费。"))
    }

    @Test
    fun cut_tail() {
        show(turnState(comparison = comparison(CacheBreakKind.TAIL, cached = 580)))
        assertEquals(1, count("和上一轮（21:31）相比，前面全部命中，只有末尾新加的部分重新计算——这是正常的。从这里往后约 50 tk 按原价计费。"))
    }

    @Test
    fun cut_windowSlid_compactThousands() {
        show(turnState(comparison = comparison(CacheBreakKind.HISTORY_WINDOW_SLID, cached = 320, total = 1_630)))
        assertEquals(1, count("和上一轮（21:31）相比，最早的一段聊天被挤出了窗口，后面的聊天记录整段都要重新计算。从这里往后约 1.3k tk 按原价计费。"))
    }

    @Test
    fun captionProviderCard_highHit_noSaverNav() {
        show(turnState())
        assertEquals(1, count("林晚 · 21:52 这一轮 · 输入 630 tk"))
        assertEquals(1, count("服务商报告"))
        assertEquals(1, count("4,000 tk（69%）"))
        assertEquals(1, count("1,760 tk"))
        assertEquals("命中 69% ≥ 40 → 不出省钱入口", 0, count(saverNav))
    }

    @Test
    fun saverNav_lowHitSaverOffChat_clicks() {
        show(turnState().copy(cacheRatePercent = 30))
        compose.onNodeWithText(saverNav, useUnmergedTree = true).performClick()
        assertEquals(listOf("saver"), events)
    }

    @Test
    fun saverNav_rule() {
        val base = turnState().copy(cacheRatePercent = 30)
        assertEquals(true, showSaverNav(base))
        assertEquals("服务商没报也出", true, showSaverNav(base.copy(cacheRatePercent = null)))
        assertEquals("恰 40 不出", false, showSaverNav(base.copy(cacheRatePercent = 40)))
        assertEquals("省钱已开不出", false, showSaverNav(base.copy(cacheSaverEnabled = true)))
        assertEquals("来源不是对话不出", false, showSaverNav(base.copy(entry = base.entry!!.copy(source = LogSource.VOICE_CALL))))
    }

    @Test
    fun purged_andNoPreviousNote() {
        show(turnState(comparison = comparison(CacheBreakKind.NO_PREVIOUS, prev = null), purged = true))
        assertEquals(1, count("分段记录已随「清除既有日志全文」清掉，只能看到消息级的断点。"))
        assertEquals(1, count("这是这段对话里第一条可比的记录，没有上一轮可以比。"))
        assertEquals(0, count("缓存从这里断开"))
    }

    @Test
    fun oldRecord_noMap() {
        show(LogPageFixtures.oldState())
        assertEquals(1, count("旧记录没有记下请求的形状，画不出地图"))
        assertEquals(0, count("服务商报告"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class ContextLogMapContentTest : LogMapContentCases() {
    override val skin = AppSkin.CLAY

    @Composable
    override fun Content(state: ContextLogEntryUiState, onOpenSaver: () -> Unit) = ContextLogMapContent(state, {}, onOpenSaver)
}
