package com.situ.aichat.ui.liuli.chat.sheets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.situ.aichat.gift.GiftSendService
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
 * T2-7（琉璃 2.0 卷三 §4.5-6·卷二 O-2）：送礼面板分类标签行向左右各「出血」20dp——横向滚动容器不再裁掉首尾标签
 * 的形状外柔影，而标签起点位置不变。量法：标签行（横向滚动节点）宽 = 网格内容宽（纵向滚动节点）+ 40；首个标签左缘
 * = 网格内容左缘。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliGiftSheetChipRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sheet() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliGiftSheet(
                        characterName = "小夏",
                        avatarPath = null,
                        balance = 9999,
                        onSendGift = { GiftSendService.InChatSendOutcome.SpendFailed },
                        onSendDiy = { _, _, _, _ -> GiftSendService.InChatSendOutcome.SpendFailed },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 一律取**未裁剪**边界（dp）：`boundsInRoot` 会被祖先的布局边界裁掉——出血的那 20dp 正落在网格内容区之外，
     * 裁后量出来就和网格一样宽（首版测试即踩此坑）。
     */
    private fun gridContent() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).getUnclippedBoundsInRoot()

    private fun chipRow() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).getUnclippedBoundsInRoot()

    @Test fun firstChip_startsAtGridContentLeft() {
        sheet()
        val chip = compose.onNodeWithText("全部").getUnclippedBoundsInRoot()
        assertEquals("首个标签左缘 = 网格内容左缘（出血不挪标签）", gridContent().left.value, chip.left.value, 0.5f)
    }

    @Test fun chipRow_bleedsTwentyEachSide() {
        sheet()
        val grid = gridContent()
        val row = chipRow()
        assertEquals("标签行宽 = 网格内容宽 + 40dp", (grid.right - grid.left).value + 40f, (row.right - row.left).value, 0.5f)
        assertEquals("左出血 20dp", grid.left.value - 20f, row.left.value, 0.5f)
    }
}
