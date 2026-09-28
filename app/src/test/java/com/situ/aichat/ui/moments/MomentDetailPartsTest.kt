package com.situ.aichat.ui.moments

import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentCommentEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.moments.MomentCommentTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-3（琉璃 2.0 卷六·二 §7）：详情评论行模型 / 缩进 / 用户显示名。期望按规格独立写字面量：
 * 能删 = 本人评论 ∨ 自己的帖；用户评论头像 = 传入的用户头像，角色评论 = 角色头像；缩进每级 28、两级封顶。
 */
class MomentDetailPartsTest {

    private val characters = mapOf(
        "c1" to CharacterEntity(uuid = "c1", name = "小满", avatarPath = "/c1.jpg", creationDate = 1),
    )

    // u1（用户·顶层·t=1）→ a1（小满回复 u1·t=2）→ u2（用户回复 a1·t=3）；a2（小满·顶层·t=4）
    private val comments = listOf(
        MomentCommentEntity(uuid = "a2", content = "角色顶层", timestamp = 4, authorTypeRaw = "character", characterUuid = "c1", postUuid = "p"),
        MomentCommentEntity(uuid = "u2", content = "用户再回", timestamp = 3, authorTypeRaw = "user", postUuid = "p", parentCommentUuid = "a1"),
        MomentCommentEntity(uuid = "u1", content = "用户顶层", timestamp = 1, authorTypeRaw = "user", postUuid = "p"),
        MomentCommentEntity(uuid = "a1", content = "角色回复", timestamp = 2, authorTypeRaw = "character", characterUuid = "c1", postUuid = "p", parentCommentUuid = "u1"),
    )

    private fun post(authorRaw: String) = MomentPostWithRelations(
        post = MomentPostEntity(uuid = "p", authorTypeRaw = authorRaw, characterUuid = if (authorRaw == "character") "c1" else null),
        comments = comments,
        likes = emptyList(),
    )

    @Test fun 角色帖只有用户自己的评论能删() {
        val rows = momentCommentRows(post("character"), characters, "/u.jpg", "我", "AI")
        assertEquals(listOf("u1", "a1", "u2", "a2"), rows.map { it.comment.uuid })
        assertEquals(listOf(true, false, true, false), rows.map { it.canDelete })
    }

    @Test fun 用户帖全部能删() {
        val rows = momentCommentRows(post("user"), characters, "/u.jpg", "我", "AI")
        assertEquals(listOf(true, true, true, true), rows.map { it.canDelete })
    }

    @Test fun 层级与评论树同序同值() {
        val rows = momentCommentRows(post("character"), characters, "/u.jpg", "我", "AI")
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.level })
        assertEquals(MomentCommentTreeBuilder.flatten(comments).map { it.comment.uuid to it.level }, rows.map { it.comment.uuid to it.level })
    }

    @Test fun 头像与作者名() {
        val rows = momentCommentRows(post("character"), characters, "/u.jpg", "我", "AI")
        assertEquals(listOf("/u.jpg", "/c1.jpg", "/u.jpg", "/c1.jpg"), rows.map { it.authorAvatarPath })
        assertEquals(listOf("我", "小满", "我", "小满"), rows.map { it.authorName })
    }

    @Test fun 缩进两级封顶() {
        assertEquals(0.dp, momentCommentIndent(0))
        assertEquals(28.dp, momentCommentIndent(1))
        assertEquals(56.dp, momentCommentIndent(2))
        assertEquals(56.dp, momentCommentIndent(5))
    }

    @Test fun 用户显示名() {
        assertEquals("阿满", momentUserName(UserProfileEntity(nickname = "阿满"), "我"))
        assertEquals("我", momentUserName(UserProfileEntity(nickname = " "), "我"))
        assertEquals("我", momentUserName(null, "我"))
    }
}
