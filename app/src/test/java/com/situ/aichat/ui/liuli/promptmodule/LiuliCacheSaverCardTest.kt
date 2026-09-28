package com.situ.aichat.ui.liuli.promptmodule

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.promptmodule.CacheSaverCardState
import com.situ.aichat.ui.promptmodule.RecentCacheLine
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-4（时间感知四期·图纸二 §4.2 / §4.4 · E22 / E23）：琉璃省钱卡——真组件（琉璃皮）渲染 + 点击回调。
 * 断言与暖陶 `CacheSaverCardTest` 同一套（两张脸同内容同顺序），文案逐字重新打字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliCacheSaverCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val toggles = mutableListOf<Boolean>()
    private var logOpens = 0

    private fun show(state: CacheSaverCardState, dark: Boolean = false) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = dark, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliCacheSaverCard(state = state, onToggle = { toggles += it }, onOpenLog = { logOpens++ })
                }
            }
        }
    }

    private val note = "打开后，每轮都会变的记忆内容（相关的旧聊天、提到的日期、世界书条目）会挪到你最新一条消息前面，前面不变的部分就能被服务商缓存。\n" +
        "代价：测试中回复质量没有变化，但个别场景下角色更新心情的频率会略有不同。\n" +
        "只对会自动缓存的服务商有效，如 DeepSeek、GLM、Grok；Claude 等无效。"

    @Test
    fun `关着_无命中行_有标题副标题与说明全文`() {
        show(CacheSaverCardState(visible = true, enabled = false, recent = RecentCacheLine.Rate(20, 68)))
        compose.onNodeWithText("省钱模式", substring = true).assertIsDisplayed()
        compose.onNodeWithText("提高缓存命中，重复的内容按缓存价计费", substring = true).assertIsDisplayed()
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText("最近", substring = true).assertDoesNotExist()
        compose.onNodeWithText("看日志 ›", substring = true).assertDoesNotExist()
    }

    @Test
    fun `开着_命中行文字_点命中行打开日志一次`() {
        show(CacheSaverCardState(visible = true, enabled = true, recent = RecentCacheLine.Rate(20, 68)))
        compose.onNodeWithText("最近 20 次对话 · 缓存命中 68%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("看日志 ›", substring = true)
            .assert(SemanticsMatcher("读屏点击标签 = 打开上下文日志") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "打开上下文日志" })
            .performClick()
        compose.waitForIdle()
        assertEquals(1, logOpens)
        assertEquals("点命中行不切开关", emptyList<Boolean>(), toggles)
    }

    @Test
    fun `开着_服务商没报缓存数_显示横杠`() {
        show(CacheSaverCardState(visible = true, enabled = true, recent = RecentCacheLine.Rate(7, null)))
        compose.onNodeWithText("最近 7 次对话 · 缓存命中 —", substring = true).assertIsDisplayed()
    }

    @Test
    fun `开着_没有记录_还没有对话记录`() {
        show(CacheSaverCardState(visible = true, enabled = true, recent = RecentCacheLine.NoRecords))
        compose.onNodeWithText("还没有对话记录", substring = true).assertIsDisplayed()
        compose.onNodeWithText("最近", substring = true).assertDoesNotExist()
    }

    @Test
    fun `点开关行_onToggle收到true`() {
        show(CacheSaverCardState(visible = true, enabled = false))
        compose.onNodeWithText("省钱模式", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), toggles)
        assertEquals(0, logOpens)
    }

    @Test
    fun `E23_服务商不会缓存_灰字开关开关都在_深色也在`() {
        show(CacheSaverCardState(visible = true, enabled = false, providerWontCache = true), dark = true)
        compose.onNodeWithText("当前服务商不会因此省钱").assertIsDisplayed()
    }

    @Test
    fun `E23_服务商不会缓存_开着也有灰字`() {
        show(CacheSaverCardState(visible = true, enabled = true, providerWontCache = true))
        compose.onNodeWithText("当前服务商不会因此省钱").assertIsDisplayed()
    }

    @Test
    fun `服务商会缓存_无灰字`() {
        show(CacheSaverCardState(visible = true, enabled = true, providerWontCache = false))
        compose.onNodeWithText("当前服务商不会因此省钱").assertDoesNotExist()
    }
}
