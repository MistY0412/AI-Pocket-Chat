package com.situ.aichat.ui.diary

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.data.model.DiaryVisibility
import com.situ.aichat.prompt.diary.DiaryGuideAnswers
import com.situ.aichat.ui.components.AppHaptics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2（琉璃 2.0 卷六·一 §3.7·T2-S1）：撰写页页级状态件（两张脸共用）真组合跑——VM 用 MockK 假掉，
 * 断言每个动作落到 VM 的哪个方法、带什么实参（规格 = 暖陶原屏内联 lambda 的行为），以及关闭守卫 / 发布仪式防连点。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class ComposeDiaryPageStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val vm = mockk<ComposeDiaryViewModel>(relaxed = true).also {
        every { it.aiDraftError } returns emptyFlow()
        every { it.voiceMessage } returns emptyFlow()
        every { it.hasUnsavedChanges } returns false
    }
    private var closed = 0
    private lateinit var page: ComposeDiaryPageState

    private fun show() {
        compose.setContent {
            page = rememberComposeDiaryPageState(vm, onClose = { closed++ }, onNavigateToApiConfig = {})
        }
        compose.waitForIdle()
    }

    @Test fun 没改动时关闭直接走() {
        show()
        compose.runOnIdle { page.attemptClose() }
        assertEquals(1, closed)
        assertFalse(page.showDiscard)
    }

    @Test fun 有改动时关闭先问() {
        every { vm.hasUnsavedChanges } returns true
        show()
        compose.runOnIdle { page.attemptClose() }
        assertEquals(0, closed)
        assertTrue(page.showDiscard)
    }

    @Test fun 选心情计数加一并转给VM() {
        show()
        compose.runOnIdle { page.toggleMood("😌", "平静") }
        assertEquals(1, page.moodSelectTick)
        verify { vm.toggleMood("😌", "平静") }
    }

    @Test fun 记下连点两次只震一次只存一次() {
        val haptics = mockk<AppHaptics>(relaxed = true)
        show()
        compose.runOnIdle {
            page.record(haptics)
            page.record(haptics)
        }
        compose.waitForIdle()
        verify(exactly = 1) { haptics.success() }
        verify(exactly = 1) { vm.save(false, any()) }
    }

    @Test fun 放弃并关闭() {
        show()
        compose.runOnIdle { page.discardAndClose() }
        verify { vm.discard() }
        assertEquals(1, closed)
    }

    @Test fun 三问生成收起弹层并转给VM() {
        val guide = DiaryGuideAnswers(event = "下班淋雨", feeling = "有点委屈", unsaid = "想被抱一下")
        show()
        compose.runOnIdle {
            page.showGuideSheet = true
            page.generate(guide)
        }
        assertFalse(page.showGuideSheet)
        verify { vm.generateAiDraft(guide) }
    }

    @Test fun 可见性对翻() {
        show()
        compose.runOnIdle { page.toggleVisibility(DiaryVisibility.OPEN_TO_AI) }
        verify { vm.setVisibility(DiaryVisibility.PRIVATE) }
    }

    @Test fun 素材芯片起笔句带换行() {
        show()
        compose.runOnIdle { page.pickMaterial("今天") }
        verify { vm.setContent("今天\n") }
    }
}
