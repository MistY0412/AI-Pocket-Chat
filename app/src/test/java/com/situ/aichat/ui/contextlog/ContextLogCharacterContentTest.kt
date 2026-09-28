package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.contextlog.LogPageFixtures.at
import com.situ.aichat.ui.contextlog.model.LogBackgroundRow
import com.situ.aichat.ui.contextlog.model.LogConversationChip
import com.situ.aichat.ui.contextlog.model.LogDayKind
import com.situ.aichat.ui.contextlog.model.LogDaySection
import com.situ.aichat.ui.contextlog.model.LogMediaTag
import com.situ.aichat.ui.contextlog.model.LogTurnCard
import com.situ.aichat.ui.contextlog.model.LogTurnQuoteText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * T2-9（四期·图纸四 §4.2）：轮卡两行逐字、失败卡、子项点击、整卡点击、后台行、节头、会话芯片（≥ 2 才出）。
 * 用例写在这里，两张脸各一个子类（琉璃子类在 `ui/liuli/contextlog`）。
 */
internal abstract class LogCharacterContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(state: ContextLogCharacterUiState, onOpenEntry: (Long, Boolean) -> Unit, onSelectConversation: (String) -> Unit)

    private val today = LogDaySection(
        LogDayKind.TODAY, LocalDate.of(2026, 9, 27),
        listOf(
            LogTurnCard(
                "t:T1", at(27, 21, 52), 1, false, LogSource.CHAT, LogTurnQuoteText(listOf(LogMediaTag.IMAGE), "看我做的饭"), 2,
                5_880, false, 69, 6_800, 1, null, LogPageFixtures.kids(),
            ),
            LogTurnCard(
                "t:T2", at(27, 21, 33), 4, true, LogSource.CHAT, LogTurnQuoteText(emptyList(), "在干嘛呢"), null,
                0, true, null, null, 0, LlmFailureKind.INSUFFICIENT_BALANCE, emptyList(),
            ),
            LogBackgroundRow("b:5", at(27, 20), 5, LogSource.DIARY_GENERATION, 1_200, false, false),
        ),
    )
    private val yesterday = LogDaySection(
        LogDayKind.YESTERDAY, LocalDate.of(2026, 9, 26),
        listOf(LogTurnCard("r:6", at(26, 22), 6, false, LogSource.VOICE_CALL, null, null, 100, true, null, null, 0, null, emptyList())),
    )

    private fun show(conversations: List<LogConversationChip>) = host {
        Content(
            ContextLogCharacterUiState(loaded = true, name = "林晚", conversations = conversations, selectedConversation = "c1", sections = listOf(today, yesterday)),
            onOpenEntry = { id, failed -> events += "entry:$id:$failed" },
            onSelectConversation = { events += "conv:$it" },
        )
    }

    @Test
    fun turnCardLines_failureCard_background_headers() {
        show(listOf(LogConversationChip("c1", "日常"), LogConversationChip("c2", "")))
        assertEquals(1, count("今天 · 9月27日 周日"))
        assertEquals(1, count("昨天 · 9月26日 周六"))
        assertEquals(1, count("21:52"))
        assertEquals(1, count("你：[图片]「看我做的饭」"))
        assertEquals(1, count("回复 2 条 · 5.9k tk · 缓存 69% · 6.8 秒 · 重试 1 次"))
        assertEquals(1, count("你：「在干嘛呢」"))
        assertEquals(1, count("失败 · 余额不足（服务商说余额不够了，去充值或换 key）"))
        assertEquals(1, count("21:53 · 900 tk"))
        assertEquals(1, count("21:51 · ≈300 tk"))
        assertEquals("失败子项尾巴", 1, count(" · 失败"))
        assertEquals(1, count(LogSource.DIARY_GENERATION))
        assertEquals(1, count("20:00 · 1.2k tk"))
        assertEquals(1, count("后台"))
        assertEquals("没有引用 → 显示来源名", 1, count(LogSource.VOICE_CALL))
        assertEquals(1, count("≈100 tk"))
        assertEquals(1, count("对话：日常"))
        assertEquals(1, count("对话：未命名对话"))
    }

    @Test
    fun clicks_cardKidBackgroundChip() {
        show(listOf(LogConversationChip("c1", "日常"), LogConversationChip("c2", "")))
        compose.onNodeWithText("你：[图片]「看我做的饭」", useUnmergedTree = true).performClick()
        compose.onNodeWithText(LogSource.MEMORY_SUMMARY, useUnmergedTree = true).performClick()
        compose.onNodeWithText(LogSource.IMAGE_UNDERSTANDING, useUnmergedTree = true).performClick()
        compose.onNodeWithText("你：「在干嘛呢」", useUnmergedTree = true).performClick()
        compose.onNodeWithText(LogSource.DIARY_GENERATION, useUnmergedTree = true).performClick()
        compose.onNodeWithText("对话：未命名对话", useUnmergedTree = true).performClick()
        assertEquals(listOf("entry:1:false", "entry:2:false", "entry:3:true", "entry:4:true", "entry:5:false", "conv:c2"), events)
    }

    @Test
    fun singleConversation_noChips() {
        show(listOf(LogConversationChip("c1", "日常")))
        assertEquals(0, count("对话：日常"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
internal class ContextLogCharacterContentTest : LogCharacterContentCases() {
    override val skin = AppSkin.CLAY

    @Composable
    override fun Content(state: ContextLogCharacterUiState, onOpenEntry: (Long, Boolean) -> Unit, onSelectConversation: (String) -> Unit) =
        ContextLogCharacterContent(state, onBack = {}, onOpenEntry = onOpenEntry, onSelectConversation = onSelectConversation)
}
