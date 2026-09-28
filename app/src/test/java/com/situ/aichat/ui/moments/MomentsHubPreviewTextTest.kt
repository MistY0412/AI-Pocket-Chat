package com.situ.aichat.ui.moments

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.DiaryEntryEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T1（琉璃 2.0 卷六·一 §3.8）：动态枢纽预览文字（暖陶 / 琉璃共用一份）。规格独立反推：取前 30 个字符、
 * 换行变空格；作者 = 用户「我」/ 角色活名 / 查不到回落「AI」；日记预览 = 「心情 emoji 空格 正文」，无正文 → null（用默认描述）。
 */
class MomentsHubPreviewTextTest {

    @Test fun `post preview keeps the first 30 chars and turns newlines into spaces`() {
        // 31 个字符，第 5 个是换行：前 30 个里含那个换行 → 变空格，第 31 个「尾」被截掉。
        val content = "一二三四\n六七八九十一二三四五六七八九十一二三四五六七八九十尾"
        assertEquals(31, content.length)
        assertEquals("一二三四 六七八九十一二三四五六七八九十一二三四五六七八九十", momentsHubPostPreview(content))
        assertEquals(30, momentsHubPostPreview(content).length)
    }

    @Test fun `preview author has three states`() {
        val chars = mapOf("c1" to CharacterEntity(uuid = "c1", name = "小满", creationDate = 0L))
        assertEquals("我", momentsHubPreviewAuthor(MomentPostEntity(uuid = "p1", authorTypeRaw = "user"), chars, "我", "AI"))
        assertEquals(
            "小满",
            momentsHubPreviewAuthor(MomentPostEntity(uuid = "p2", authorTypeRaw = "character", characterUuid = "c1"), chars, "我", "AI"),
        )
        assertEquals(
            "AI",
            momentsHubPreviewAuthor(MomentPostEntity(uuid = "p3", authorTypeRaw = "character", characterUuid = "gone"), chars, "我", "AI"),
        )
    }

    @Test fun `diary preview - null and blank fall back to default`() {
        assertNull(momentsHubDiaryPreview(null))
        assertNull(momentsHubDiaryPreview(DiaryEntryEntity(uuid = "d0", content = "  \n  ", moodEmoji = "😌")))
    }

    @Test fun `diary preview - with mood prefixes emoji and a space, without mood is body only`() {
        assertEquals("😌 今天风很大", momentsHubDiaryPreview(DiaryEntryEntity(uuid = "d1", content = "今天风很大", moodEmoji = "😌")))
        assertEquals("今天风很大", momentsHubDiaryPreview(DiaryEntryEntity(uuid = "d2", content = "今天风很大")))
        assertEquals("空串心情 = 无心情", "今天风很大", momentsHubDiaryPreview(DiaryEntryEntity(uuid = "d3", content = "今天风很大", moodEmoji = "")))
    }
}
