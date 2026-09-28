package com.situ.aichat.ui.liuli.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.theme.AIPocketChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-5：列表行骨架与它的两个附件（图纸 2026-09-06 卷三 §7 T2-5 · §3.2「列表行」· A-15）。
 *
 * 钉：文字左缘 = 行内 82（琉璃 2.0 卷三：卡内行距 16 + 头像 54 + 缝 12·**与卡内发丝起点同一个数**）、行高 78
 * （头像 54 + 上下 12）、未读丸高 20 且 >99 显 "99+"。卡内发丝（列表项画·卷三 §4.8）另在
 * `LiuliChatListContentTest` 里量（它住在列表项上，不在行内）。
 *
 * `Density(2f)`：Robolectric 的假字高恒 32**像素**、不随 sp 变——1x 密度下两行字 32 + 6 + 32 = 70dp 会盖过
 * 头像 54 成为行高，错版与对版一样绿；2x 下同样的 32px 只有 16dp，行高才真的由头像决定（PITFALLS §1d）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliListRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showRow(unread: Int = 3) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    Column {
                        LiuliListRow(
                            modifier = Modifier.testTag("row"),
                            avatar = { Box(Modifier.size(LiuliHomeGeometry.rowAvatar)) },
                            primary = { Text("小满") },
                            secondary = { Text("你: 晚安") },
                            trailing = { LiuliUnreadPill(count = unread, modifier = Modifier.testTag("pill")) },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun 文字左缘等于行内八十二() {
        showRow()
        val left = compose.onNodeWithText("小满").getUnclippedBoundsInRoot().left
        assertEquals(82f, left.value, 0.5f)
        // 「卡内发丝起于文字左缘」= 两个数必须相等（发丝由列表项画·起点同一个 dividerInset）。
        assertEquals(LiuliHomeGeometry.dividerInset.value, left.value, 0.5f)
    }

    @Test fun 行高等于头像加上下内距() {
        showRow()
        val bounds = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        assertEquals(78f, (bounds.bottom - bounds.top).value, 0.5f)
    }

    @Test fun 未读丸高二十() {
        showRow(unread = 3)
        val pill = compose.onNodeWithTag("pill").getUnclippedBoundsInRoot()
        assertEquals(20f, (pill.bottom - pill.top).value, 0.01f)
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test fun 未读过百显九九加() {
        showRow(unread = 100)
        compose.onNodeWithText("99+").assertIsDisplayed()
    }
}
