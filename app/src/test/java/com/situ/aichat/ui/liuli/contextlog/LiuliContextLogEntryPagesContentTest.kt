package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.contextlog.ContextLogEntryUiState
import com.situ.aichat.ui.contextlog.ContextLogReaderUiState
import com.situ.aichat.ui.contextlog.LogEntryContentCases
import com.situ.aichat.ui.contextlog.LogFailureContentCases
import com.situ.aichat.ui.contextlog.LogMapContentCases
import com.situ.aichat.ui.contextlog.LogReaderContentCases
import com.situ.aichat.ui.contextlog.LogReplyContentCases
import com.situ.aichat.ui.contextlog.model.LogReaderSearch
import com.situ.aichat.ui.contextlog.model.LogSentMessage
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * T2-10…T2-13 琉璃部分（四期·图纸四 §4.3–§4.6）：四个条目页跑与暖陶**同一批**用例（`LogEntryContentCases` 等），
 * 保证两张脸内容 / 顺序 / 文案完全相同。屏高 2400dp 让懒列表一次排完所有组。
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class LiuliContextLogEntryContentTest : LogEntryContentCases() {
    override val skin = AppSkin.LIULI

    /** Token 三格走 LiuliStatCard：整卡合成一句读屏文案。 */
    override fun assertTokenCells() {
        assertEquals(1, compose.onAllNodesWithContentDescription("输入 5,760，输出 120，缓存命中 69%").fetchSemanticsNodes().size)
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
    ) = LiuliContextLogEntryContent(state, {}, onOpenEntry, onOpenMap, onOpenSent, onOpenReply, onCopyAll, onExportReplay)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class LiuliContextLogMapContentTest : LogMapContentCases() {
    override val skin = AppSkin.LIULI

    @Composable
    override fun Content(state: ContextLogEntryUiState, onOpenSaver: () -> Unit) = LiuliContextLogMapContent(state, {}, onOpenSaver)
}

/** 图纸五 T2-4 琉璃部分：阅读器与暖陶跑同一批用例（取代原 LiuliContextLogSentContentTest）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class LiuliContextLogReaderContentTest : LogReaderContentCases() {
    override val skin = AppSkin.LIULI

    @Composable
    override fun Content(
        state: ContextLogReaderUiState, search: LogReaderSearch?, onOpenLogSettings: () -> Unit, onOpenMap: (Long) -> Unit,
        onQueryChange: (String) -> Unit, onCopyMessage: (LogSentMessage) -> Unit, onCopyAll: () -> Unit,
    ) = LiuliContextLogSentContent(state, search, {}, onOpenLogSettings, onOpenMap, onQueryChange, onCopyMessage, onCopyAll)
}

/** 图纸五 T2-5 琉璃部分：回复全文页与暖陶跑同一批用例。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class LiuliContextLogReplyContentTest : LogReplyContentCases() {
    override val skin = AppSkin.LIULI

    @Composable
    override fun Content(state: ContextLogEntryUiState, onCopy: (String) -> Unit) = LiuliContextLogReplyContent(state, {}, onCopy)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class LiuliContextLogFailureContentTest : LogFailureContentCases() {
    override val skin = AppSkin.LIULI

    @Composable
    override fun Content(state: ContextLogEntryUiState, onOpenApiSettings: () -> Unit) = LiuliContextLogFailureContent(state, {}, onOpenApiSettings)
}
