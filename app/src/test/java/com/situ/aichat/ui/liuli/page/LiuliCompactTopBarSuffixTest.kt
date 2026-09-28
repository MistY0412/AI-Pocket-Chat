package com.situ.aichat.ui.liuli.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下甲 T2-T1–T2：[LiuliCompactTopBar] 的两处加法——书名后缀 `titleSuffix` 与「有两侧圆钮才让位」。
 * T1 = 零回归钉（不传后缀 / 不传两侧 → 仍只有一个标题文字节点）；T2 = 让位生效（长书名省略、后缀不压右侧圆钮）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliCompactTopBarSuffixTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    @Test
    fun t1_noSuffixNoSides_singleTitleNode() {
        show { Box(Modifier.fillMaxSize()) { LiuliCompactTopBar(title = "故事", visible = true) } }
        compose.onNodeWithText("故事").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(1)
    }

    @Test
    fun t2_sidesAndSuffix_titleGivesWay_suffixClearOfTrailing() {
        // Robolectric 的假字宽极窄（中文约 0.76dp / 字·PITFALLS 1e）：30 字在它那儿撑不满一行，测不出让位；
        // 故取足够长的书名逼出省略号，再断后缀右缘不越过右侧圆钮左缘。
        val longTitle = "对面楼的灯亮了又灭灭了又亮".repeat(40)
        show {
            Box(Modifier.fillMaxSize()) {
                LiuliCompactTopBar(
                    title = longTitle,
                    visible = true,
                    leading = { Box(Modifier.size(40.dp).testTag("leading")) },
                    trailing = { Box(Modifier.size(40.dp).testTag("trailing")) },
                    titleSuffix = "· 第2章",
                )
            }
        }
        compose.onNodeWithText("· 第2章").assertIsDisplayed()
        compose.onAllNodesWithText(longTitle).assertCountEquals(1)
        val suffix = compose.onNodeWithText("· 第2章").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(longTitle).getUnclippedBoundsInRoot()
        val trailing = compose.onNodeWithTag("trailing").getUnclippedBoundsInRoot()
        val leading = compose.onNodeWithTag("leading").getUnclippedBoundsInRoot()
        assertTrue("后缀右缘 ${suffix.right} ≤ 右钮左缘 ${trailing.left}", suffix.right.value <= trailing.left.value + 0.5f)
        assertTrue("书名左缘 ${title.left} ≥ 左钮右缘 ${leading.right}", title.left.value >= leading.right.value - 0.5f)
    }
}
