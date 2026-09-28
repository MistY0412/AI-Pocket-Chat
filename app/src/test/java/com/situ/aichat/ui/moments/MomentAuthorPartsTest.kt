package com.situ.aichat.ui.moments

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** T1-2（琉璃 2.0 卷六·二 §7）：作者页名字 / 头像（用户 = 昵称，空白回落「我」；角色 = 名字，缺席回落 AI 标签）。 */
class MomentAuthorPartsTest {

    private val profile = UserProfileEntity(nickname = "阿满", avatarPath = "/u.jpg")
    private val character = CharacterEntity(uuid = "c1", name = "小满", avatarPath = "/c1.jpg", creationDate = 1)

    @Test fun 用户有昵称() {
        assertEquals(MomentAuthorHeading("阿满", "/u.jpg"), momentAuthorHeading(true, null, profile, "我", "AI"))
    }

    @Test fun 用户昵称空白回落我() {
        val blank = profile.copy(nickname = "  ")
        assertEquals(MomentAuthorHeading("我", "/u.jpg"), momentAuthorHeading(true, null, blank, "我", "AI"))
    }

    @Test fun 角色在() {
        assertEquals(MomentAuthorHeading("小满", "/c1.jpg"), momentAuthorHeading(false, character, profile, "我", "AI"))
    }

    @Test fun 角色缺席回落AI() {
        assertEquals(MomentAuthorHeading("AI", null), momentAuthorHeading(false, null, profile, "我", "AI"))
    }
}
