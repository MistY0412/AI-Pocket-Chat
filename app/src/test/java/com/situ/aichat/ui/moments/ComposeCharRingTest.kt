package com.situ.aichat.ui.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T1-4（朋友圈发布页·乙 §7·E3 / E4）：字数环三态与环旁数字（上限 500·离上限 ≤ 50 起提示·期望从图纸 §4.7 独立反推）。 */
class ComposeCharRingTest {

    @Test fun 远离上限_不出数字() {
        assertEquals(CharRingTone.NORMAL, charRingTone(0))
        assertNull(charRingLabel(0))
        assertEquals(CharRingTone.NORMAL, charRingTone(449))
        assertNull(charRingLabel(449))
    }

    @Test fun 剩五十字起提示剩余() {
        assertEquals(CharRingTone.WARN, charRingTone(450))
        assertEquals("50", charRingLabel(450))
        assertEquals(CharRingTone.WARN, charRingTone(500))
        assertEquals("0", charRingLabel(500))
    }

    @Test fun 超了是负数() {
        assertEquals(CharRingTone.OVER, charRingTone(501))
        assertEquals("-1", charRingLabel(501))
        assertEquals("-38", charRingLabel(538))
    }

    @Test fun 上限就是VM上限500() {
        assertEquals(500, ComposeMomentViewModel.MAX_CHARS)
    }
}
