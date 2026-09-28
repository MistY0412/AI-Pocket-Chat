package com.situ.aichat.ui.character

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-3（琉璃 2.0 卷五 §7）：语音段常量与语速吸附。「语速 1.25x」是改前暖陶的逐字渲染串（重新打字的字面量）。
 */
class CharacterVoiceTextTest {

    @Test
    fun `语速值两位小数加 x`() {
        assertEquals("1.00x", CharacterVoiceText.speedValue(1.0))
        assertEquals("0.55x", CharacterVoiceText.speedValue(0.55))
    }

    @Test
    fun `语速吸附到 0点05 一格`() {
        assertEquals(1.0, snapTtsSpeed(1.013f), 0.0)
        assertEquals(1.05, snapTtsSpeed(1.026f), 0.0)
        assertEquals(0.5, snapTtsSpeed(0.5f), 0.0)
        assertEquals(2.0, snapTtsSpeed(2.0f), 0.0)
    }

    @Test
    fun `暖陶渲染串与改前字面相同`() {
        assertEquals("语速 1.25x", "${CharacterVoiceText.SPEED_TITLE} ${CharacterVoiceText.speedValue(1.25)}")
        assertEquals("音调 -3", "${CharacterVoiceText.PITCH_TITLE} ${-3}")
        assertEquals("默认（zh-CN）", CharacterVoiceText.SYSTEM_VOICE_DEFAULT)
    }

    @Test
    fun `情绪标签 - 已知取中文 未知原样`() {
        assertEquals("自动（跟随心情）", emotionLabel("auto"))
        assertEquals("耳语（仅 2.6）", emotionLabel("whisper"))
        assertEquals("weird", emotionLabel("weird"))
        assertEquals(10, EMOTION_OPTIONS.size)
    }
}
