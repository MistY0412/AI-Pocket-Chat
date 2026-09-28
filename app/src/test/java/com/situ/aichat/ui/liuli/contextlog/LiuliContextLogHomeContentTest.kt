package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogCategory
import com.situ.aichat.ui.contextlog.ContextLogCharacterUiState
import com.situ.aichat.ui.contextlog.ContextLogHomeUiState
import com.situ.aichat.ui.contextlog.LogCharacterContentCases
import com.situ.aichat.ui.contextlog.LogHomeContentCases
import com.situ.aichat.ui.contextlog.model.LogHomeTab
import com.situ.aichat.ui.contextlog.model.TrendRange
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-8（四期·图纸四 §4.1·琉璃内容层）：跑与暖陶 T2-7 **同一批**用例（[LogHomeContentCases]）。
 * 唯一脸差：今天四格是 `LiuliStatCard`，整卡合成一句读屏文案（逐格文字不进语义树）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
internal class LiuliContextLogHomeContentTest : LogHomeContentCases() {
    override val skin = AppSkin.LIULI

    override fun assertTodayStats() {
        val spoken = "调用 46，失败 2，缓存命中 18%，token 38.2万"
        assertEquals(1, compose.onAllNodesWithContentDescription(spoken).fetchSemanticsNodes().size)
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
    ) = LiuliContextLogHomeContent(state, {}, onOpenCharacter, onOpenEntry, onOpenSettings, onSelectTab, onCategory, onReason, onRange, onShowFailures, onClearAll)
}

/** T2-9 琉璃部分：跑与暖陶同一批角色页用例（[LogCharacterContentCases]）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
internal class LiuliContextLogCharacterContentTest : LogCharacterContentCases() {
    override val skin = AppSkin.LIULI

    @Composable
    override fun Content(state: ContextLogCharacterUiState, onOpenEntry: (Long, Boolean) -> Unit, onSelectConversation: (String) -> Unit) =
        LiuliContextLogCharacterContent(state, onBack = {}, onOpenEntry = onOpenEntry, onSelectConversation = onSelectConversation)
}
