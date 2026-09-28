package com.situ.aichat.ui.liuli.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.designsystem.LiuliFabPill
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-P2（琉璃 2.0 卷六·一 §3.12）：玻璃浮动胶囊钮——点一次回调一次、轻触觉一次；触达高 48；
 * 图标不另念（无 contentDescription），整钮读屏 = 一个 Button、念「写一笔」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliFabPillTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var taps = 0

    private fun show() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    LiuliFabPill(icon = Icons.Filled.Edit, label = "写一笔", onClick = { taps++ })
                }
            }
        }
        compose.waitForIdle()
    }

    private val pill get() = compose.onNode(hasText("写一笔") and hasClickAction())

    @Test fun 点一次回调一次且轻震一次() {
        show()
        pill.performClick()
        compose.waitForIdle()
        assertEquals(1, taps)
        verify(exactly = 1) { haptics.light() }
    }

    @Test fun 触达高48() {
        show()
        pill.assertTouchHeightIsEqualTo(48.dp)
    }

    @Test fun 读屏是一个念写一笔的按钮且图标不另念() {
        show()
        val node = pill.fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals("写一笔", node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertNull("图标 contentDescription = null", node.config.getOrNull(SemanticsProperties.ContentDescription))
    }
}
