package com.situ.aichat.ui.diary

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.DiaryCommentEntity
import com.situ.aichat.data.local.entity.DiaryEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1（琉璃 2.0 卷六·一 §3.4）：交换信「给 TA 留言」入口门控。规格（R6-1）独立反推：只有「交换信 + 作者角色仍在 +
 * 用户尚无顶层留言」才露出，返回的是作者本人；用户对角色评论的**回复**（有 parentCommentId）不算留言。
 */
class DiaryDetailPartsTest {

    private val xiaoman = CharacterEntity(uuid = "char-xm", name = "小满", creationDate = 0L)
    private val chars = mapOf(xiaoman.uuid to xiaoman)
    private val letter = DiaryEntryEntity(uuid = "e-letter", authorCharacterUuid = "char-xm")

    private fun comment(id: String, fromUser: Boolean, parent: String? = null) = DiaryCommentEntity(
        id = id, entryUuid = "e-letter", content = id, timestamp = 1L,
        characterUuid = if (fromUser) null else "char-xm", parentCommentId = parent, isFromUser = fromUser,
    )

    @Test fun `user's own diary has no note entry`() {
        assertNull(diaryExchangeNoteAuthor(DiaryEntryEntity(uuid = "e-mine"), emptyList(), chars))
    }

    @Test fun `letter with author present and no comments returns the author`() {
        assertEquals(xiaoman, diaryExchangeNoteAuthor(letter, emptyList(), chars))
    }

    @Test fun `letter where user already left a top-level note hides the entry`() {
        assertNull(diaryExchangeNoteAuthor(letter, listOf(comment("note", fromUser = true)), chars))
    }

    @Test fun `a user reply to a character comment is not a note`() {
        val comments = listOf(comment("root", fromUser = false), comment("reply", fromUser = true, parent = "root"))
        assertEquals(xiaoman, diaryExchangeNoteAuthor(letter, comments, chars))
    }

    @Test fun `letter whose author was deleted has no note entry`() {
        assertNull(diaryExchangeNoteAuthor(letter, emptyList(), emptyMap()))
    }
}
