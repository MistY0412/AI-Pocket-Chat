package com.situ.aichat.diagnostics

import com.situ.aichat.data.local.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.coroutineContext

/** T1-7（四期·图纸三 §3.3 / E12 / E13）：轮次关联元素的工厂与锚点。 */
class LogTraceTest {

    private fun msg(uuid: String, role: String) = MessageEntity(messageUUID = uuid, conversationUuid = "conv-1", roleRaw = role, content = "x", timestamp = 0L)

    private val history = listOf(msg("u0", "user"), msg("a0", "assistant"), msg("u1", "user"), msg("u2", "user"))

    @Test
    fun anchorOf_firstInTimeOrder_notSetOrder() {
        assertEquals("u1", LogTrace.anchorOf(history, linkedSetOf("u2", "u1")))
        assertEquals("u2", LogTrace.anchorOf(history, setOf("u2")))
    }

    @Test
    fun anchorOf_emptySetOrNoMatch_isNull() {
        assertNull("回合尾部没有用户消息（主动 / 恢复类·E13）", LogTrace.anchorOf(history, emptySet()))
        assertNull(LogTrace.anchorOf(history, setOf("nope")))
    }

    @Test
    fun newTurn_freshTurnIdEachTime_fieldsCarried() {
        val a = LogTrace.newTurn("conv-1", "char-1", "u1")
        val b = LogTrace.newTurn("conv-1", "char-1", "u1")
        assertNotNull(a.turnId)
        assertNotEquals("重新生成 = 新 turnId（E12）", a.turnId, b.turnId)
        assertEquals("锚点仍是原用户消息（E12）", "u1", b.anchorMessageUuid)
        assertEquals("conv-1", a.conversationUuid)
        assertEquals("char-1", a.characterUuid)
        val noAnchor = LogTrace.newTurn("conv-1", "char-1", anchorMessageUuid = null)
        assertNull(noAnchor.anchorMessageUuid)
        assertNotNull("没有锚点照发 turnId（E13）", noAnchor.turnId)
    }

    @Test
    fun forMessage_noTurn_carriesCharacter() {
        assertEquals(LogTrace("conv-1", "char-1", null, "img-1"), LogTrace.forMessage("conv-1", "char-1", "img-1"))
    }

    @Test
    fun isACoroutineContextElement() = runBlocking {
        val trace = LogTrace.newTurn("conv-1", "char-1", "u1")
        withContext(trace) { assertSame(trace, coroutineContext[LogTrace]) }
        assertNull(coroutineContext[LogTrace])
    }
}
