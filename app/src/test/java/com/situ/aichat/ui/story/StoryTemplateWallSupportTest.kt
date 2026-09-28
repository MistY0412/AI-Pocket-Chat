package com.situ.aichat.ui.story

import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserStoryTemplateEntity
import com.situ.aichat.story.StoryRoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模板墙 / 开书弹层共用件（琉璃 2.0 卷六·三·上 §3.4·T1-3）。 */
class StoryTemplateWallSupportTest {

    @Test fun 区头文案_无我的模板用墙副标题_有则我的模板() {
        assertEquals(R.string.story_wall_subtitle, storyWallHeadRes(emptyList()))
        assertEquals(R.string.story_my_templates_header, storyWallHeadRes(listOf(UserStoryTemplateEntity(uuid = "u", name = "甜", createdAt = 0L, payloadJson = "{}"))))
    }

    @Test fun 模板名_全空白不可存() {
        assertFalse(storyTemplateNameValid("  "))
        assertTrue(storyTemplateNameValid(" 甜 "))
    }

    @Test fun 选角态_主演单选再点取消_点主演会退出配角() {
        val st = StoryOpenBookState("a")
        assertEquals(mapOf("a" to StoryRoleType.PROTAGONIST), st.selectedRoles)
        st.toggleLead("a")
        assertNull(st.leadId)
        st.toggleSupporting("b")
        assertEquals(mapOf("b" to StoryRoleType.SUPPORTING), st.selectedRoles)
        st.toggleLead("b")
        assertEquals(mapOf("b" to StoryRoleType.PROTAGONIST), st.selectedRoles)
        assertFalse(st.supportingIds.contains("b"))
    }

    @Test fun 可开书_无人可演时不可_我也入场即可() {
        val st = StoryOpenBookState(null)
        st.includeUserRole = false
        assertFalse(st.canStart)
        st.includeUserRole = true
        assertTrue(st.canStart)
    }

    @Test fun 配角候选排除主演_加配角门槛两人() {
        val a = CharacterEntity(uuid = "a", name = "林夏", creationDate = 0L)
        val b = CharacterEntity(uuid = "b", name = "阿澈", creationDate = 0L)
        val st = StoryOpenBookState("a")
        assertEquals(listOf(b), st.supportingCandidates(listOf(a, b)))
        assertFalse(storyOpenBookShowAddSupport(listOf(a)))
        assertTrue(storyOpenBookShowAddSupport(listOf(a, b)))
    }
}
