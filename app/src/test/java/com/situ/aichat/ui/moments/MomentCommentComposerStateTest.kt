package com.situ.aichat.ui.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T2-S2（琉璃 2.0 卷六·二 §7）：详情评论草稿——发送把文字 + 回复目标原样交出，然后两者清空；空白不能发。 */
class MomentCommentComposerStateTest {

    @Test fun 发送交出草稿后清空() {
        val state = MomentCommentComposerState()
        state.text = "好呀"
        state.replyTarget = MomentReplyTarget("c1", "小满")
        var got: Triple<String, String?, String?>? = null
        state.send { t, id, name -> got = Triple(t, id, name) }
        assertEquals(Triple("好呀", "c1", "小满"), got)
        assertEquals("", state.text)
        assertNull(state.replyTarget)
    }

    @Test fun 空白不能发() {
        val state = MomentCommentComposerState()
        state.text = "  "
        assertFalse(state.canSend)
        state.text = "好"
        assertTrue(state.canSend)
    }
}
