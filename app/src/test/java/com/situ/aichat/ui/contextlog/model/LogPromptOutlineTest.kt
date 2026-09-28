package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.prompt.ContextSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-1（四期·图纸五 §3.3）：输入用真拼法——模块文字以 "\n\n" 相连（= PromptBuilderModules prefixParts.joinToString），
 * 分段用真 [ContextSegment.fingerprintOf]。区间一律手算（a 9 字、b 10 字、c 9 字、世界书 12 字）。
 */
class LogPromptOutlineTest {

    private val a = "核心规则：别说教。" // 9
    private val b = "你是林晚，二十五岁。" // 10
    private val c = "今天的日程：上班。" // 9
    private val wb = "测试设定：天空是紫色的。" // 12

    private fun seg(name: String, text: String, fp: String? = ContextSegment.fingerprintOf(text), position: String = ContextSegment.POSITION_PREFIX) =
        ContextSegment(name, null, text.length, 0, position, fp)

    private fun join(vararg parts: String) = parts.joinToString("\n\n")

    private val abc = listOf(seg("核心规则", a), seg("身份", b), seg("日程", c))

    @Test
    fun lengthsAsAssumed() {
        assertEquals(listOf(9, 10, 9, 12), listOf(a.length, b.length, c.length, wb.length))
    }

    @Test
    fun threeModules_noGap() {
        val o = promptOutline(join(a, b, c), abc, emptyList())!!
        assertEquals(
            listOf(LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection("身份", 11, 21, false), LogOutlineSection("日程", 23, 32, false)),
            o.sections,
        )
        assertEquals(10, o.sections[1].chars)
    }

    @Test
    fun worldBookBeforeIdentity_becomesUnsegmentedSection() {
        val text = join(a, wb, b, c)
        val o = promptOutline(text, abc, emptyList())!!
        assertEquals(
            listOf(
                LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection(null, 11, 23, false),
                LogOutlineSection("身份", 25, 35, false), LogOutlineSection("日程", 37, 46, false),
            ),
            o.sections,
        )
        assertEquals(wb, text.substring(11, 23))
    }

    @Test
    fun worldBookAtEnd_trailingUnsegmented() {
        val text = join(a, b, c, wb)
        val o = promptOutline(text, abc, emptyList())!!
        assertEquals(LogOutlineSection(null, 34, 46, false), o.sections.last())
        assertEquals(4, o.sections.size)
        assertEquals(wb, text.substring(34, 46))
    }

    @Test
    fun fingerprintNotFound_segmentSkipped_textFallsIntoGap() {
        val o = promptOutline(join(a, b, c), listOf(seg("核心规则", a), seg("身份", b, fp = "0000000000000000"), seg("日程", c)), emptyList())!!
        assertEquals(
            listOf(LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection(null, 11, 21, false), LogOutlineSection("日程", 23, 32, false)),
            o.sections,
        )
    }

    @Test
    fun legacyNullFingerprints_cutByCountOrGiveUp() {
        val legacy = listOf(seg("核心规则", a, fp = null), seg("身份", b, fp = null), seg("日程", c, fp = null))
        assertEquals(
            listOf(LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection("身份", 11, 21, false), LogOutlineSection("日程", 23, 32, false)),
            promptOutline(join(a, b, c), legacy, emptyList())!!.sections,
        )
        val offByOne = listOf(seg("核心规则", a, fp = null), ContextSegment("身份", null, 11, 0, ContextSegment.POSITION_PREFIX, null), seg("日程", c, fp = null))
        assertNull("字数错一位 → 整个不拆", promptOutline(join(a, b, c), offByOne, emptyList()))
    }

    @Test
    fun noPrefixSegments_null() {
        assertNull(promptOutline(join(a, b), emptyList(), emptyList()))
        assertNull(promptOutline(join(a, b), listOf(seg("对话历史", a, fp = null, position = ContextSegment.POSITION_HISTORY), seg("回复风格", b, position = ContextSegment.POSITION_SUFFIX)), emptyList()))
    }

    @Test
    fun noneLocated_null() {
        assertNull(promptOutline(join(a, b), listOf(seg("身份", c)), emptyList()))
    }

    @Test
    fun changed_onlyWhenBothFingerprintsDiffer() {
        val prev = listOf(seg("核心规则", "旧的核心规则"), ContextSegment("身份", null, 10, 0, ContextSegment.POSITION_PREFIX, null))
        val o = promptOutline(join(a, b, c), abc, prev)!!
        assertTrue("上一轮同名段指纹不同", o.sections[0].changed)
        assertFalse("上一轮该段指纹为 null", o.sections[1].changed)
        assertFalse("上一轮没有同名段", o.sections[2].changed)
        assertFalse("指纹相同", promptOutline(join(a, b, c), abc, abc)!!.sections.any { it.changed })
    }

    @Test
    fun gapOfOnlyNewlines_noSection() {
        val text = a + "\n\n\n\n\n\n" + b
        val o = promptOutline(text, listOf(seg("核心规则", a), seg("身份", b)), emptyList())!!
        assertEquals(listOf(LogOutlineSection("核心规则", 0, 9, false), LogOutlineSection("身份", 15, 25, false)), o.sections)
    }
}
