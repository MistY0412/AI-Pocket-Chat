package com.situ.aichat.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.local.entity.MessageEntity
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
 * T2-4（琉璃 2.0 卷三 §7）：暖陶语音泡——卷三起琉璃也用它，琉璃旧语音泡测试的行为口径迁到这里。
 * ① 泡宽随时长 `140 + (秒−1)×20` 钳 140–260（1s / 5s / 20s = 140 / 220 / 260）② 静止态时长 `N"`
 * ③ AI 有转写 → 「转文字」开合；用户语音无开关 ④ 点泡 = `onToggle` 恰一次。期望值从规格反推。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class VoiceMessageBubbleTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private var toggles = 0

    private fun voice(content: String, role: String, seconds: Double) = MessageEntity(
        messageUUID = "v1",
        conversationUuid = "c",
        roleRaw = role,
        content = content,
        timestamp = 1_756_000_000_000L,
        isVoiceMessage = true,
        audioDuration = seconds,
    )

    private fun setBubble(message: MessageEntity, isUser: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                VoiceMessageBubble(
                    message = message,
                    isUser = isUser,
                    isPlaying = false,
                    progress = { 0f },
                    customStickers = emptyList(),
                    onToggle = { toggles++ },
                    onLongClick = {},
                )
            }
        }
    }

    /** 泡宽（dp）：点击行（合并了时长文案）的边界宽。 */
    private fun bubbleWidthDp(label: String): Float {
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        return compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.width / density
    }

    @Test fun width_growsWithDuration_andClamps() {
        setBubble(voice("嗯", "user", 1.0), isUser = true)
        assertEquals(140f, bubbleWidthDp("1\""), 0.5f)
    }

    @Test fun width_fiveSeconds_is220() {
        setBubble(voice("嗯", "user", 5.0), isUser = true)
        assertEquals(220f, bubbleWidthDp("5\""), 0.5f)
    }

    @Test fun width_twentySeconds_clampsTo260() {
        setBubble(voice("嗯", "user", 20.0), isUser = true)
        assertEquals(260f, bubbleWidthDp("20\""), 0.5f)
    }

    @Test fun aiVoice_withTranscript_togglesOpenAndClosed() {
        setBubble(voice("今天的风刚好", "assistant", 3.0), isUser = false)
        compose.onNodeWithText("今天的风刚好").assertDoesNotExist()
        compose.onNodeWithText("转文字").performClick()
        compose.onNodeWithText("今天的风刚好").assertIsDisplayed()
        compose.onNodeWithText("收起").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("今天的风刚好").assertDoesNotExist()
    }

    @Test fun userVoice_hasNoTranscriptToggle() {
        setBubble(voice("我说了一句", "user", 3.0), isUser = true)
        compose.onNodeWithText("转文字").assertDoesNotExist()
    }

    @Test fun tappingBubble_togglesPlaybackExactlyOnce() {
        setBubble(voice("嗯", "assistant", 3.0), isUser = false)
        compose.onNodeWithText("3\"").performClick()
        assertEquals(1, toggles)
    }
}
