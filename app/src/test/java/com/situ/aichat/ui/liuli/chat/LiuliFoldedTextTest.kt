package com.situ.aichat.ui.liuli.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppTypography
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1-2 + T2-12 长文折叠（图纸 2026-09-05 卷二C §7 · A-1 · E13 / E14 / E16）：
 * ① 判据纯函数四格（恰阈值不折 / 超一行才折 / 用户泡不折 / 未显形不折）+ `expand` 幂等
 * ② 折叠泡上出「展开全文」、正文被裁矮；点开后标签消失、高度变大、会话记账里有这条
 * ③ 12 行不折、用户泡不折。
 *
 * 阈值期望值**从规格反推**（A-1：> 12 行才折、折后露 10 行），不引实现常量做断言值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliFoldedTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)

    // ── T1-2 判据纯函数 ───────────────────────────────────────────────────────

    @Test fun shouldFold_needsMoreThanTwelveLines() {
        assertFalse(liuliShouldFold(12, revealed = true, isUser = false))
        assertTrue(liuliShouldFold(13, revealed = true, isUser = false))
    }

    @Test fun shouldFold_neverForUserBubble() {
        assertFalse(liuliShouldFold(30, revealed = true, isUser = true))
    }

    @Test fun shouldFold_neverBeforeReveal() {
        assertFalse(liuliShouldFold(30, revealed = false, isUser = false))
    }

    @Test fun foldState_expandIsIdempotent() {
        val state = LiuliFoldState()
        assertFalse(state.isExpanded("m1"))
        state.expand("m1")
        state.expand("m1")
        assertTrue(state.isExpanded("m1"))
        assertEquals(1, state.expandedCount)
    }

    @Test fun foldState_isFolded_combinesThresholdAndMemory() {
        val state = LiuliFoldState()
        assertTrue(state.isFolded("m1", 13))
        assertFalse(state.isFolded("m1", 12))
        state.expand("m1")
        assertFalse(state.isFolded("m1", 13))
    }

    // ── T2-12 泡上行为 ────────────────────────────────────────────────────────

    /** 每行一个字 + 硬换行 —— Robolectric 的字形宽失真影响不到「有几个换行符」这件事。 */
    private fun linesOf(n: Int) = (1..n).joinToString("\n") { "第${it}行" }

    private fun setFoldable(text: String, isUser: Boolean = false, revealed: Boolean = true): LiuliFoldState {
        val state = LiuliFoldState()
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.width(260.dp).testTag(FOLD_BOX)) {
                    LiuliFoldableText(
                        text = text,
                        style = AppTypography.body,
                        color = Color.Black,
                        revealed = revealed,
                        isUser = isUser,
                        expanded = expanded,
                        onExpand = { state.expand("m1"); expanded = true },
                        fadeColor = Color.White,
                    )
                }
            }
        }
        return state
    }

    @Test fun thirteenLines_foldsAndOffersExpand() {
        setFoldable(linesOf(13))
        compose.onNodeWithText("展开全文").assertIsDisplayed()
        // 折后只露 10 行：第 11 行起被裁掉（`maxLines` 裁的是排版，节点仍在但不在可视区内）。
        compose.onNodeWithText(linesOf(13)).assertExists()
    }

    @Test fun twelveLines_staysWhole() {
        setFoldable(linesOf(12))
        compose.onNodeWithText("展开全文").assertDoesNotExist()
    }

    @Test fun userBubble_neverFolds() {
        setFoldable(linesOf(30), isUser = true)
        compose.onNodeWithText("展开全文").assertDoesNotExist()
    }

    @Test fun unrevealed_neverFolds() {
        setFoldable(linesOf(30), revealed = false)
        compose.onNodeWithText("展开全文").assertDoesNotExist()
    }

    @Test fun expanding_growsHeight_dropsLabel_andRemembersInSession() {
        val state = setFoldable(linesOf(20))
        val foldedBounds = compose.onNodeWithText(linesOf(20)).getUnclippedBoundsInRoot()
        val foldedHeight = foldedBounds.bottom - foldedBounds.top
        compose.onNodeWithText("展开全文").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("展开全文").assertDoesNotExist()
        val fullBounds = compose.onNodeWithText(linesOf(20)).getUnclippedBoundsInRoot()
        val fullHeight = fullBounds.bottom - fullBounds.top
        assertTrue("展开后正文应更高：$foldedHeight → $fullHeight", fullHeight > foldedHeight)
        assertTrue(state.isExpanded("m1"))
    }

    /**
     * 复核 R1 🔴-1（REDLINES「a11y 48dp」）：「展开全文」是新交互面——点击面 ≥ 48 高、居中外溢，
     * 版位仍是一行字：48 框的下半截必须外溢到折叠件整体底边之下；若版位被撑成 48，框底会与件底重合。
     * （卷三：戳已挪到泡下，改拿折叠件自身的底边作参照；Robolectric 字形度量是假的，故不拿字高本身当期望值。）
     */
    @Test fun expandLabel_has48dpTouchTarget_withoutGrowingTheRow() {
        setFoldable(linesOf(20))
        val label = compose.onNodeWithText("展开全文").getUnclippedBoundsInRoot()
        val box = compose.onNodeWithTag(FOLD_BOX).getUnclippedBoundsInRoot()
        assertTrue("触达高 ${label.bottom - label.top} 应 ≥ 48", (label.bottom - label.top).value >= 47.5f)
        assertTrue(
            "版位没被撑高：触达框底 ${label.bottom} 应外溢到折叠件底 ${box.bottom} 之下，而不是与之重合",
            label.bottom.value > box.bottom.value + 3f,
        )
    }

    private companion object {
        const val FOLD_BOX = "foldBox"
    }
}
