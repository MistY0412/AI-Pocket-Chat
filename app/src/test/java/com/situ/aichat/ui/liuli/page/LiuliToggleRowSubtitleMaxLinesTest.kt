package com.situ.aichat.ui.liuli.page

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-P1（琉璃 2.0 卷六·三·上 §3.14·页壳加法零回归）：[LiuliToggleRow] 的 `subtitleMaxLines` 限两行时截断，不传时逐行全出。
 * 200 字副标题每 20 字一个硬换行（= 10 行）：Robolectric 假字宽恒偏窄、长句永不自动折行（PITFALLS §1e），用硬换行保证行数可测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliToggleRowSubtitleMaxLinesTest {

    @get:Rule
    val compose = createComposeRule()

    private val subtitle = (0 until 10).joinToString("\n") { "温柔体贴的邻家姐姐总会在雨夜替你留一盏灯" }

    private fun layout(maxLines: Int?): TextLayoutResult {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    if (maxLines == null) {
                        LiuliToggleRow(title = "林夏", checked = false, onCheckedChange = {}, subtitle = subtitle, divider = false)
                    } else {
                        LiuliToggleRow(title = "林夏", checked = false, onCheckedChange = {}, subtitle = subtitle, divider = false, subtitleMaxLines = maxLines)
                    }
                }
            }
        }
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(subtitle, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    @Test fun 限两行_恰两行且有溢出() {
        assertEquals(200, subtitle.replace("\n", "").length)
        val r = layout(2)
        assertEquals(2, r.lineCount)
        assertTrue(r.hasVisualOverflow)
    }

    @Test fun 不传_不限行零回归() {
        // 只断行数：Robolectric 假字宽下「宽度溢出」恒报 true（与本加法无关），溢出位不作零回归依据。
        assertEquals(10, layout(null).lineCount)
    }
}
