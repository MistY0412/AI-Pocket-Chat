package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.ui.chat.ChatSheetsState
import com.situ.aichat.ui.chat.ChatViewModel
import com.situ.aichat.ui.chat.QuoteTextOnlyHintState
import com.situ.aichat.ui.chat.rememberChatInputPanelState
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * T2-4 琉璃「+」变形面板（图纸 2026-09-05 卷二B §7）：六入口的显隐规则（见面态 / vision 门）、
 * 点一格必先收面板、以及带引用时的三处拦截之一（「表情」）。
 *
 * 面板状态机是**借来的**（`ChatInputPanelState`·PLUS_PANEL 机制零碰），所以这里用**真状态机**驱动，
 * 看 `panelOpen` 真的翻回 false，而不是 mock 掉 `dismiss` 只验「调用过」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliPlusPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel = mockk<ChatViewModel>(relaxed = true)
    private val haptics = mockk<AppHaptics>(relaxed = true)
    private val sheets = ChatSheetsState()
    private val quoteHint = QuoteTextOnlyHintState()
    private var panelOpen: () -> Boolean = { false }
    private var dismissAll: () -> Unit = {}

    private fun setPanel(
        isOfflineMode: Boolean = false,
        chatModelHasVision: Boolean = true,
        replyTarget: MessageEntity? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                val focusRequester = remember { FocusRequester() }
                val inputPanel = rememberChatInputPanelState(
                    keyboard = LocalSoftwareKeyboardController.current,
                    focusManager = LocalFocusManager.current,
                    fieldFocus = focusRequester,
                    minHeightPx = MIN_PANEL_PX,
                    maxHeightPx = MAX_PANEL_PX,
                )
                panelOpen = { inputPanel.panelOpen }
                dismissAll = { inputPanel.dismiss(reduceMotion = true) }
                // 无键盘时开面板 → 走兜底高度（PLUS_PANEL 机制·本测不碰它，只要区域够高能摆下格子）。
                remember { inputPanel.openPanel(currentImePx = 0, fallbackPx = FALLBACK_PANEL_PX) }
                Box(Modifier.fillMaxSize()) {
                    LiuliPlusPanel(
                        viewModel = viewModel,
                        sheets = sheets,
                        inputPanel = inputPanel,
                        replyTarget = replyTarget,
                        quoteHint = quoteHint,
                        isOfflineMode = isOfflineMode,
                        chatModelHasVision = chatModelHasVision,
                        reduceMotion = true,
                        regionPx = { FALLBACK_PANEL_PX },
                    )
                }
            }
        }
    }

    @Test fun onlineWithVision_showsAllSixEntries() {
        setPanel()
        listOf("送礼", "红包", "表情", "照片", "见面", "约见面").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun withoutVision_hidesPhotoOnly() {
        setPanel(chatModelHasVision = false)
        compose.onNodeWithText("照片").assertDoesNotExist()
        listOf("送礼", "红包", "表情", "见面", "约见面").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun offlineMode_keepsOnlyStickerAndPhoto() {
        // 见面期隐藏 送礼 / 红包 / 见面 / 约见面（2026-06-21 用户拍板）。
        setPanel(isOfflineMode = true)
        listOf("送礼", "红包", "见面", "约见面").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithText("表情").assertIsDisplayed()
        compose.onNodeWithText("照片").assertIsDisplayed()
    }

    @Test fun tappingSticker_dismissesPanel_andOpensPicker() {
        setPanel()
        assertTrue("前提：面板是开着的", panelOpen())
        compose.onNodeWithText("表情").performClick()
        compose.waitForIdle()
        assertFalse("点一格必先收面板", panelOpen())
        assertTrue("表情面板被请出来", sheets.showPicker)
    }

    @Test fun tappingStickerWithQuote_showsHintInstead() {
        // 引用一期 E·拦截③：带引用时不开表情面板，只弹提示。
        setPanel(replyTarget = MessageEntity(messageUUID = "q", conversationUuid = "c", roleRaw = "assistant", content = "在", timestamp = 1L))
        compose.onNodeWithText("表情").performClick()
        compose.waitForIdle()
        assertFalse("带引用绝不开表情面板", sheets.showPicker)
        assertTrue("改弹「引用时只能发文字」提示", quoteHint.visible)
        assertEquals("面板照样收起", false, panelOpen())
    }

    /**
     * 复核 R1 🟡-3（图纸 §3.2 公式勘误）+ 卷四 §4.7（两排上下居中）：面板盒高 = regionPx − panelTop，底 = 根底 − panelBottom
     * （Robolectric 无导航栏、1px = 1dp）。卷四起去掉内距顶 16、两排居中 ⇒ 首排顶到盒顶 = 末排底到盒底（±1dp）。
     * 盒子公式错多少，两段留白就差多少（原「首格顶 = 盒顶 + 16」随居中改写）。
     */
    @Test fun panelBox_isRegionMinusPanelTop_andRowsAreCenteredInIt() {
        setPanel()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val boxBottom = (root.bottom - LiuliChatGeometry.panelBottom).value
        val boxTop = boxBottom - (FALLBACK_PANEL_PX.dp - LiuliChatGeometry.panelTop).value
        val firstRowTop = compose.onNodeWithText("照片").getUnclippedBoundsInRoot().top.value
        val lastRowBottom = compose.onNodeWithText("见面").getUnclippedBoundsInRoot().bottom.value
        val topGap = firstRowTop - boxTop
        val bottomGap = boxBottom - lastRowBottom
        assertTrue("首排顶留白 $topGap 与末排底留白 $bottomGap 应相等（±1dp）", abs(topGap - bottomGap) <= 1f)
        assertTrue("两排都在盒内", topGap > 0f && bottomGap > 0f)
    }

    /** 卷四 §4.7：按 (行, 列) 读出的入口顺序。 */
    private fun orderOf(labels: List<String>): List<List<String>> {
        val pos = labels.map { it to compose.onNodeWithText(it).getUnclippedBoundsInRoot() }
        return pos.groupBy { it.second.top.value.toInt() }.toSortedMap().values
            .map { row -> row.sortedBy { it.second.left.value }.map { it.first } }
    }

    @Test fun order_withVision_fourThenTwo() {
        setPanel()
        assertEquals(
            listOf(listOf("照片", "表情", "红包", "送礼"), listOf("见面", "约见面")),
            orderOf(listOf("送礼", "红包", "表情", "照片", "见面", "约见面")),
        )
        // 第二排左起：见面与首排照片同列。
        assertEquals(
            compose.onNodeWithText("照片").getUnclippedBoundsInRoot().left.value,
            compose.onNodeWithText("见面").getUnclippedBoundsInRoot().left.value,
            0.5f,
        )
    }

    @Test fun order_withoutVision_stickerFirst() {
        setPanel(chatModelHasVision = false)
        assertEquals(
            listOf(listOf("表情", "红包", "送礼", "见面"), listOf("约见面")),
            orderOf(listOf("送礼", "红包", "表情", "见面", "约见面")),
        )
    }

    @Test fun order_offline_photoThenStickerOneRow() {
        setPanel(isOfflineMode = true)
        assertEquals(listOf(listOf("照片", "表情")), orderOf(listOf("照片", "表情")))
    }

    /** 格子 60：未合并树里标签文字顶 − 整格顶 = 方块 60 + 标签间距 8。 */
    @Test fun tile_is60dp() {
        setPanel()
        val cell = compose.onNodeWithText("表情").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("表情", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(60f + 8f, (label.top - cell.top).value, 0.5f)
    }

    @Test fun tappingGift_opensGiftSheet_notOthers() {
        setPanel()
        compose.onNodeWithText("送礼").performClick()
        compose.waitForIdle()
        assertTrue(sheets.showGiftSheet)
        assertFalse(sheets.showRedPacketSheet)
        assertFalse(sheets.showPicker)
        dismissAll()
    }

    private companion object {
        const val MIN_PANEL_PX = 180
        const val MAX_PANEL_PX = 900
        const val FALLBACK_PANEL_PX = 780
    }
}
