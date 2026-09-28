package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.contextlog.LogPageFixtures.turnEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-5（四期·图纸五 §4.8·E19 / E33）：回复全文页——徽标角色名（空名 →「角色」）、「 #1 · N 字」、正文、思考用量 0 不出 / > 0 出、
 * 回复空 →「（无内容）」且无复制钮、截断尾巴 →「已截断」脚注、两枚复制钮都交出全文。两张脸同一批用例。
 */
internal abstract class LogReplyContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(state: ContextLogEntryUiState, onCopy: (String) -> Unit)

    private fun show(entry: LogEntryEntity?) =
        host { Content(ContextLogEntryUiState(loaded = true, entry = entry, providerLabel = "DeepSeek")) { events += "copy:$it" } }

    private fun countSub(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun countCd(cd: String) = compose.onAllNodesWithContentDescription(cd).fetchSemanticsNodes().size

    @Test
    fun badge_tail_body_footer_noReasoning_copyBoth() {
        show(turnEntry.copy(responseContent = "嗯嗯，在呢", reasoningTokens = 0))
        for (t in listOf("林晚 · 21:52 这一轮 · deepseek-chat（DeepSeek）", "林晚", " #1 · 5 字", "嗯嗯，在呢", "共 5 字 · 完整记录")) {
            assertEquals("「$t」", 1, count(t))
        }
        assertEquals("思考用量 0 → 不出提示框", 0, countSub("这一轮模型思考了"))
        compose.onNodeWithContentDescription("复制全部").performClick()
        compose.onNodeWithContentDescription("复制这条").performClick()
        assertEquals(listOf("copy:嗯嗯，在呢", "copy:嗯嗯，在呢"), events)
    }

    @Test
    fun reasoningTokens_notice() {
        show(turnEntry.copy(responseContent = "嗯", reasoningTokens = 1_024))
        assertEquals(1, count("这一轮模型思考了 1,024 tk（只记了用量，没记思考原文）。"))
    }

    @Test
    fun emptyName_roleWord() {
        show(turnEntry.copy(characterName = "", responseContent = "嗯"))
        assertEquals("E19", 1, count("角色"))
    }

    @Test
    fun emptyReply_noCopyNoFooter() {
        show(turnEntry.copy(responseContent = null, reasoningTokens = 30))
        assertEquals(1, count("（无内容）"))
        assertEquals(0, countCd("复制全部"))
        assertEquals(0, countCd("复制这条"))
        assertEquals(0, countSub("完整记录"))
        assertEquals("空回复也照样报思考用量", 1, count("这一轮模型思考了 30 tk（只记了用量，没记思考原文）。"))
    }

    @Test
    fun truncatedReply_footer() {
        val clipped = "字".repeat(20) + "\n\n[日志内容已截断，共 300000 字]"
        show(turnEntry.copy(responseContent = clipped))
        assertEquals(1, count("共 ${clipped.length} 字 · 已截断")) // K6
    }

    @Test
    fun missingEntry() {
        show(null)
        assertEquals(1, count("记录不存在或已被清除"))
        assertEquals(0, countCd("复制全部"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class ContextLogReplyContentTest : LogReplyContentCases() {
    override val skin = AppSkin.CLAY

    @Composable
    override fun Content(state: ContextLogEntryUiState, onCopy: (String) -> Unit) = ContextLogReplyContent(state, {}, onCopy)
}
