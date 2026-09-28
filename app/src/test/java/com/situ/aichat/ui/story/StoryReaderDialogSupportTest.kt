package com.situ.aichat.ui.story

import com.situ.aichat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 琉璃 2.0 卷六·三·下乙 T1-1：阅读器弹层与对话框两张脸共用的纯式子（图纸 §3.2）。
 * 期望值从原暖陶式子独立反推（续写正文二选 / 结局前两项次序与映射 / 重写指令只判空串 / 自由输入 trim 判空 / 输入框先交出后关）。
 */
class StoryReaderDialogSupportTest {

    @Test
    fun 续写确认正文_挂着未答选择走提醒版() {
        assertEquals(R.string.story_alert_continue_pending_msg, storyContinueAlertBodyRes(true))
        assertEquals(R.string.story_alert_continue_msg, storyContinueAlertBodyRes(false))
    }

    @Test
    fun 结局三选前两项_开放式在前_AI自由发挥在后() {
        assertEquals(
            listOf(R.string.story_ending_open to "open", R.string.story_ending_ai to "ai"),
            storyEndingQuickOptions,
        )
    }

    @Test
    fun 重写指令_只有空串算直接重写_不trim() {
        assertNull(storyRewriteInstruction(""))
        assertEquals("换个视角", storyRewriteInstruction("换个视角"))
        // 原式只判空串：一个空格照原样交出（输入框确认那一步已 trim 过，这里不再管）。
        assertEquals(" ", storyRewriteInstruction(" "))
    }

    @Test
    fun 自由输入可提交文本_trim后为空即null() {
        assertNull(storyCustomChoiceSubmitText(""))
        assertNull(storyCustomChoiceSubmitText("   "))
        assertEquals("走", storyCustomChoiceSubmitText("  走  "))
    }

    @Test
    fun 自由输入聚焦等待_250毫秒() {
        assertEquals(250L, STORY_CUSTOM_CHOICE_FOCUS_DELAY_MS)
    }

    @Test
    fun 输入框确认_trim后先交出再关框() {
        val events = mutableListOf<String>()
        storyDialogInputConfirm("  小结  ", onConfirm = { events += "confirm:$it" }, onDismiss = { events += "dismiss" })
        assertEquals(listOf("confirm:小结", "dismiss"), events)
    }
}
