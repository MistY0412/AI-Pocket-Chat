package com.situ.aichat.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-4（琉璃 2.0 卷三 §7）：暖陶纯贴纸（卷三起琉璃也用它·贴纸 120 取代琉璃旧 110）。
 * ① 合并朗读句 ② 长按转交菜单恰一次 ③ 单张内置贴纸占 120dp 见方（已知内置 id 在加载中也先占位 120·防跳动）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class StickerStackTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var longClicks = 0

    private fun setStack() {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                StickerStack(
                    content = "[sticker:开心_1]",
                    customStickers = emptyList(),
                    onLongClick = { longClicks++ },
                    a11yDescription = "云野在刚才说：[表情包]",
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun mergedSentence_replacesRawTag() {
        setStack()
        compose.onNodeWithContentDescription("云野在刚才说：[表情包]").assertIsDisplayed()
    }

    @Test fun longPress_handsOverToMenuOnce() {
        setStack()
        compose.onNodeWithContentDescription("云野在刚才说：[表情包]").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, longClicks)
    }

    @Test fun singleBuiltInSticker_is120dpSquare() {
        setStack()
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val bounds = compose.onNodeWithContentDescription("云野在刚才说：[表情包]").fetchSemanticsNode().boundsInRoot
        assertEquals(120f, bounds.width / density, 0.5f)
        assertEquals(120f, bounds.height / density, 0.5f)
    }
}
