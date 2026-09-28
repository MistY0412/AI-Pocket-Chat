package com.situ.aichat.ui.story

import com.situ.aichat.story.StoryNarrativePerson
import com.situ.aichat.story.StoryRoleType
import com.situ.aichat.story.StoryWritingTechniques
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 创建屏共用件（琉璃 2.0 卷六·三·上 §3.13·T1-8）：可建判据 / 选退角色 / 参考模板 / 我也参演 / chip 选中 / 文本行变淡 / 人称序。 */
class StoryCreationSupportTest {

    private val base = StoryCreationForm()

    @Test fun 可建判据() {
        assertFalse(storyCreationCanCreate(base.copy(isCustomGenre = true, customGenreName = "  ", includeUserRole = true)))
        assertTrue(storyCreationCanCreate(base.copy(isCustomGenre = true, customGenreName = "赛博", includeUserRole = true)))
        assertFalse(storyCreationCanCreate(base))
        assertFalse(storyCreationCanCreate(base.copy(customRoles = listOf(CustomRoleDraft(" ")))))
        assertTrue(storyCreationCanCreate(base.copy(customRoles = listOf(CustomRoleDraft("阿澈")))))
    }

    @Test fun 选退角色_首个且未参演为主角_其余配角_退选清定位与描述() {
        val a = storyFormToggleCharacter(base, "a", true)
        assertEquals(StoryRoleType.PROTAGONIST, a.selectedRoles["a"])
        assertEquals(StoryRoleType.SUPPORTING, storyFormToggleCharacter(base.copy(includeUserRole = true), "a", true).selectedRoles["a"])
        val ab = storyFormToggleCharacter(a, "b", true)
        assertEquals(StoryRoleType.SUPPORTING, ab.selectedRoles["b"])
        val withDesc = ab.copy(roleDescriptions = mapOf("a" to "青梅竹马"))
        val off = storyFormToggleCharacter(withDesc, "a", false)
        assertFalse(off.selectedRoles.containsKey("a"))
        assertFalse(off.roleDescriptions.containsKey("a"))
    }

    @Test fun 参考模板_选题材连带技法_选无只清参考() {
        val f = base.copy(customGenreTechniques = "我写的技法")
        val picked = storyFormWithReferenceGenre(f, "悬疑")
        assertEquals("悬疑", picked.referenceGenre)
        assertEquals(StoryWritingTechniques.genreTechniques("悬疑"), picked.customGenreTechniques)
        val none = storyFormWithReferenceGenre(picked.copy(customGenreTechniques = "留着"), null)
        assertNull(none.referenceGenre)
        assertEquals("留着", none.customGenreTechniques)
    }

    @Test fun 我也参演_打开且有昵称才换名() {
        assertEquals("阿满", storyFormWithIncludeUser(base, true, "阿满").userRoleName)
        assertEquals(base.userRoleName, storyFormWithIncludeUser(base, true, "").userRoleName)
        val off = storyFormWithIncludeUser(base.copy(includeUserRole = true), false, "阿满")
        assertEquals(base.userRoleName, off.userRoleName)
        assertFalse(off.includeUserRole)
    }

    @Test fun 题材chip_自定义题材时一律不选中() {
        val g = base.selectedGenre
        assertTrue(storyGenreChipSelected(base, g))
        assertFalse(storyGenreChipSelected(base.copy(isCustomGenre = true), g))
    }

    @Test fun 文本行标题变淡_人称三项顺序() {
        assertTrue(storyTextEditTitleMuted("", false))
        assertFalse(storyTextEditTitleMuted("", true))
        assertFalse(storyTextEditTitleMuted("x", false))
        assertEquals(listOf(StoryNarrativePerson.SECOND, StoryNarrativePerson.FIRST, StoryNarrativePerson.THIRD), storyNarrativeOptions)
    }
}
