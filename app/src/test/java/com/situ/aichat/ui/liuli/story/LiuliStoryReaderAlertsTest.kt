package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-A1–A3（琉璃 2.0 卷六·三·下乙 §7）：两个 VM 提示框的琉璃脸（直接测 [LiuliStoryReaderSheetFace.ReaderAlert]）。
 * 三组实参 = 暖陶 StoryReaderAlerts 那三种情形的原值（问下一章 / 可重试失败 / 不可重试失败）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderAlertsTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirmCount = 0
    private var dismissCount = 0

    private fun show(title: String, body: String, confirmText: String, dismissText: String?) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryReaderSheetFace.ReaderAlert(
                        onDismissRequest = { dismissCount++ },
                        title = title,
                        body = body,
                        confirmText = confirmText,
                        onConfirm = { confirmCount++ },
                        dismissText = dismissText,
                        onDismiss = { dismissCount++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun clickButton(text: String) {
        compose.onNode(hasText(text) and isButton).performClick()
        compose.waitForIdle()
    }

    private fun showAskNext() = show(ASK_TITLE, ASK_BODY, "立即生成", "稍后")

    @Test fun A1_问下一章_四样都在_立即生成发确认() {
        showAskNext()
        listOf(ASK_TITLE, ASK_BODY, "立即生成", "稍后").forEach { compose.onNodeWithText(it).assertExists() }
        clickButton("立即生成")
        assertEquals(1, confirmCount)
        assertEquals(0, dismissCount)
    }

    @Test fun A1_问下一章_稍后发关闭() {
        showAskNext()
        clickButton("稍后")
        assertEquals(1, dismissCount)
        assertEquals(0, confirmCount)
    }

    @Test fun A2_可重试失败_重试与确认两钮_重试发确认回调() {
        show("提示", "生成失败", confirmText = "重试", dismissText = "确定")
        compose.onNode(hasText("重试") and isButton).assertExists()
        compose.onNode(hasText("确定") and isButton).assertExists()
        clickButton("重试")
        assertEquals(1, confirmCount)
    }

    @Test fun A3_不可重试失败_只有一枚确定钮() {
        show("提示", "操作失败", confirmText = "确定", dismissText = null)
        compose.onAllNodes(isButton).assertCountEquals(1)
        compose.onNode(hasText("确定") and isButton).assertExists()
    }

    private companion object {
        const val ASK_TITLE = "要立刻看下一章吗？"
        const val ASK_BODY = "你的选择已经保存——稍后回到章末推进区随时能继续，开着追更也会自动更新。"
    }
}
