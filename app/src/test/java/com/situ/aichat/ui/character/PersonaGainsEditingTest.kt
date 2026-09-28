package com.situ.aichat.ui.character

import com.situ.aichat.data.model.CustomGain
import com.situ.aichat.data.model.PersonaGains
import com.situ.aichat.data.model.PersonaVocab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-2（琉璃 2.0 卷五 §7）：「她吃哪套」编辑纯函数（自暖陶 `PersonaGainsSection` 只搬）。
 * 断言从暖陶行为独立反推：回到「正常」= 摘键；新项去空白 / 很敏感 / 手写；12 字上限超出不收；
 * 查重大小写与前后空白不敏感、命中给原标签；默认只显非「正常」项。
 */
class PersonaGainsEditingTest {

    private fun gain(id: String, label: String, level: Int = 2, origin: String = CustomGain.ORIGIN_MANUAL) =
        CustomGain(id = id, label = label, level = level, origin = origin)

    @Test
    fun `三档次序是 不吃这套 正常 很敏感`() {
        assertEquals(listOf(0, 1, 2), PERSONA_GAIN_LEVELS)
    }

    @Test
    fun `系统项改回正常即摘键 - 其余档写入`() {
        val g = PersonaGains(system = mapOf("g01" to 2, "g02" to 0))
        assertEquals(mapOf("g02" to 0), g.withSystemLevel("g01", PersonaVocab.LEVEL_NORMAL).system)
        assertEquals(mapOf("g01" to 0, "g02" to 0), g.withSystemLevel("g01", 0).system)
        assertEquals(mapOf("g01" to 2, "g02" to 0, "g05" to 2), g.withSystemLevel("g05", 2).system)
    }

    @Test
    fun `新增专属项 - 去空白 很敏感 手写来源 追加在尾`() {
        val g = PersonaGains(custom = listOf(gain("a", "旧的")))
        val next = g.withNewCustom("  甜言蜜语 ", id = "new")
        assertEquals(2, next.custom.size)
        val added = next.custom.last()
        assertEquals("new", added.id)
        assertEquals("甜言蜜语", added.label)
        assertEquals(2, added.level)
        assertEquals("manual", added.origin)
        assertTrue(g.withNewCustom("x").custom.last().id.isNotBlank())
    }

    @Test
    fun `专属项改档与删除只动目标那条`() {
        val g = PersonaGains(custom = listOf(gain("a", "甲", 2), gain("b", "乙", 2)))
        assertEquals(listOf(0, 2), g.withCustomLevel("a", 0).custom.map { it.level })
        assertEquals(listOf("b"), g.withoutCustom("a").custom.map { it.id })
    }

    @Test
    fun `输入上限 12 字 - 13 字不接收`() {
        assertTrue(acceptsCustomGainDraft("一二三四五六七八九十一二"))
        assertFalse(acceptsCustomGainDraft("一二三四五六七八九十一二三"))
        assertTrue(acceptsCustomGainDraft(""))
    }

    @Test
    fun `查重 - 大小写与前后空白不敏感 命中返回原标签`() {
        val custom = listOf(gain("a", "Coffee"))
        assertEquals("Coffee", customGainDuplicateOf("  coffee ", listOf("被夸"), custom))
        assertEquals("被夸", customGainDuplicateOf("被夸 ", listOf("被夸"), custom))
        assertNull(customGainDuplicateOf("奶茶", listOf("被夸"), custom))
    }

    @Test
    fun `可提交三条件 - 非空白 不重复 未满`() {
        assertTrue(customGainSubmittable("奶茶", null, full = false))
        assertFalse(customGainSubmittable("   ", null, full = false))
        assertFalse(customGainSubmittable("奶茶", "奶茶", full = false))
        assertFalse(customGainSubmittable("奶茶", null, full = true))
    }

    @Test
    fun `可见系统项 - 默认只含非正常 展开全含`() {
        val group = PersonaVocab.GAIN_GROUPS.first() // g01 g02 g03
        val g = PersonaGains(system = mapOf("g02" to 0, "g03" to 1))
        assertEquals(listOf("g02"), g.visibleSystemKeys(group, showAll = false))
        assertEquals(listOf("g01", "g02", "g03"), g.visibleSystemKeys(group, showAll = true))
    }

    @Test
    fun `计数边界 - 不同条数 正常条数 满十条`() {
        val g = PersonaGains(system = mapOf("g01" to 2, "g02" to 0, "g03" to 1))
        assertEquals(2, g.differingCount())
        assertEquals(25, g.normalSystemCount()) // 27 − 2（g03 显式 1 也算正常）
        assertEquals(27, PersonaGains().normalSystemCount())
        assertFalse(PersonaGains(custom = List(9) { gain("$it", "x$it") }).isCustomFull())
        assertTrue(PersonaGains(custom = List(10) { gain("$it", "x$it") }).isCustomFull())
    }
}
