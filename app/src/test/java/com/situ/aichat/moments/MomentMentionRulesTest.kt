package com.situ.aichat.moments

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.util.StringListJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-1（朋友圈发布页重构·甲 §7）：「提醒谁看」三条纯规则。断言从图纸 §3.2 / J-1 / J-10 与 E6 / E14 / E15 独立反推。
 */
class MomentMentionRulesTest {

    private fun ch(uuid: String, name: String = "名$uuid") = CharacterEntity(uuid = uuid, name = name, creationDate = 0L)

    private fun userPost(vararg mentions: String, author: String? = null) = MomentPostEntity(
        uuid = "p1",
        authorTypeRaw = "user",
        characterUuid = author,
        mentionedCharacterUuidsJson = StringListJson.encode(mentions.toList()),
    )

    private val all = listOf(ch("c1"), ch("c2"), ch("c3"))

    // ── owedMentions ──

    @Test
    fun owed_按帖上顺序() {
        val owed = MomentMentionRules.owedMentions(userPost("c3", "c1", "c2"), all, emptySet())
        assertEquals(listOf("c3", "c1", "c2"), owed.map { it.uuid })
    }

    @Test
    fun owed_重复提醒只出一次_保首次位置() {
        val owed = MomentMentionRules.owedMentions(userPost("c2", "c1", "c2"), all, emptySet())
        assertEquals(listOf("c2", "c1"), owed.map { it.uuid })
    }

    @Test
    fun owed_已评论的不再欠() {
        val owed = MomentMentionRules.owedMentions(userPost("c1", "c2"), all, setOf("c1"))
        assertEquals(listOf("c2"), owed.map { it.uuid })
    }

    @Test
    fun owed_已删角色跳过() {
        val owed = MomentMentionRules.owedMentions(userPost("gone", "c1"), all, emptySet())
        assertEquals(listOf("c1"), owed.map { it.uuid })
    }

    @Test
    fun owed_帖主本人不欠() {
        val owed = MomentMentionRules.owedMentions(userPost("c1", "c2", author = "c1"), all, emptySet())
        assertEquals(listOf("c2"), owed.map { it.uuid })
    }

    @Test
    fun owed_AI帖一律为空() {
        val aiPost = userPost("c2").copy(authorTypeRaw = "character", characterUuid = "c1")
        assertTrue(MomentMentionRules.owedMentions(aiPost, all, emptySet()).isEmpty())
    }

    @Test
    fun owed_没提醒任何人为空() {
        assertTrue(MomentMentionRules.owedMentions(userPost(), all, emptySet()).isEmpty())
    }

    // ── displayNames ──

    @Test
    fun displayNames_保序去重_跳已删与空名() {
        val chars = mapOf(
            "c1" to ch("c1", "小雨"),
            "c2" to ch("c2", "  "),
            "c3" to ch("c3", "阿哲"),
        )
        val names = MomentMentionRules.displayNames(userPost("c3", "gone", "c1", "c2", "c3"), chars)
        assertEquals(listOf("阿哲", "小雨"), names)
    }

    @Test
    fun displayNames_无提醒为空() {
        assertTrue(MomentMentionRules.displayNames(userPost(), mapOf("c1" to ch("c1"))).isEmpty())
    }

    // ── sanitize ──

    @Test
    fun sanitize_去重保序_去不存在() {
        val out = MomentMentionRules.sanitize(listOf("c2", "gone", "c1", "c2", "c3"), setOf("c1", "c2"))
        assertEquals(listOf("c2", "c1"), out)
    }

    @Test
    fun sanitize_空表仍为空() {
        assertTrue(MomentMentionRules.sanitize(emptyList(), setOf("c1")).isEmpty())
    }
}
