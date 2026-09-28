package com.situ.aichat.ui.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 琉璃 2.0 卷六·三·下乙 T1-2：导演台页内态（图纸 §3.2·两张脸共用）。
 * 期望值从原暖陶式子独立反推：dirty 判据（编辑模式与已存走向逐字相同 = 没改）/ 保存路由（看 directionCommitted）/
 * 节拍原文交出不 trim / 防重入 / 弃改与撤回二段式。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoryDirectorEditorStateTest {

    private val submitted = mutableListOf<String>()
    private val overwritten = mutableListOf<String>()
    private val savedBeats = mutableListOf<String>()
    private var dismissCount = 0

    private fun StoryDirectorEditorState.saveWith(savedDirection: String?, initialBeats: String, directionCommitted: Boolean) =
        save(
            savedDirection = savedDirection,
            initialBeats = initialBeats,
            directionCommitted = directionCommitted,
            onSubmitFlow = { submitted += it },
            onOverwriteDirection = { overwritten += it },
            onSaveBeats = { savedBeats += it },
            onDismiss = { dismissCount++ },
        )

    @Test
    fun 初值_走向与节拍取构造值_三个布尔全false() {
        val s = StoryDirectorEditorState("旧", "节拍")
        assertEquals("旧", s.flowText)
        assertEquals("节拍", s.beatsText)
        assertFalse(s.saving)
        assertFalse(s.confirmDiscard)
        assertFalse(s.confirmWithdraw)
    }

    @Test
    fun flowDirty_与已存走向trim后相同算没改_空白不算改() {
        val s = StoryDirectorEditorState("旧", "")
        assertFalse(s.flowDirty("旧"))
        s.flowText = " 旧 "
        assertFalse(s.flowDirty("旧"))
        s.flowText = "新"
        assertTrue(s.flowDirty("旧"))
        s.flowText = "   "
        assertFalse(s.flowDirty("旧"))
        s.flowText = "新"
        assertTrue(s.flowDirty(null))
    }

    @Test
    fun beatsDirty_trim后与底稿比() {
        val s = StoryDirectorEditorState("", "节拍")
        assertFalse(s.beatsDirty("节拍"))
        s.beatsText = " 节拍 "
        assertFalse(s.beatsDirty("节拍"))
        s.beatsText = "改了"
        assertTrue(s.beatsDirty("节拍"))
    }

    @Test
    fun save_编辑模式走覆盖写口_交出trim后的走向() {
        val s = StoryDirectorEditorState("旧", "节拍")
        s.flowText = " 新 "
        s.saveWith(savedDirection = "旧", initialBeats = "节拍", directionCommitted = true)
        assertEquals(listOf("新"), overwritten)
        assertEquals(emptyList<String>(), submitted)
        assertEquals(emptyList<String>(), savedBeats)
        assertEquals(1, dismissCount)
    }

    @Test
    fun save_创建模式走创建路() {
        val s = StoryDirectorEditorState("", "节拍")
        s.flowText = " 新 "
        s.saveWith(savedDirection = null, initialBeats = "节拍", directionCommitted = false)
        assertEquals(listOf("新"), submitted)
        assertEquals(emptyList<String>(), overwritten)
        assertEquals(1, dismissCount)
    }

    @Test
    fun save_节拍改过交原文不trim() {
        val s = StoryDirectorEditorState("", "节拍")
        s.beatsText = " 改过的节拍 "
        s.saveWith(savedDirection = null, initialBeats = "节拍", directionCommitted = false)
        assertEquals(listOf(" 改过的节拍 "), savedBeats)
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun save_连点两次_各回调只发一次() {
        val s = StoryDirectorEditorState("", "节拍")
        s.flowText = "新"
        s.beatsText = "改了"
        s.saveWith(savedDirection = null, initialBeats = "节拍", directionCommitted = false)
        s.saveWith(savedDirection = null, initialBeats = "节拍", directionCommitted = false)
        assertEquals(listOf("新"), submitted)
        assertEquals(listOf("改了"), savedBeats)
        assertEquals(1, dismissCount)
        assertTrue(s.saving)
    }

    @Test
    fun requestDismiss_没改直接关_改过先问() {
        val clean = StoryDirectorEditorState("旧", "节拍")
        clean.requestDismiss("旧", "节拍") { dismissCount++ }
        assertEquals(1, dismissCount)
        assertFalse(clean.confirmDiscard)

        dismissCount = 0
        val dirty = StoryDirectorEditorState("旧", "节拍")
        dirty.flowText = "新"
        dirty.requestDismiss("旧", "节拍") { dismissCount++ }
        assertTrue(dirty.confirmDiscard)
        assertEquals(0, dismissCount)
    }

    @Test
    fun restoreAiBeats_发一次关一次_保存中不响应() {
        var restored = 0
        val s = StoryDirectorEditorState("", "节拍")
        s.restoreAiBeats({ restored++ }, { dismissCount++ })
        assertEquals(1, restored)
        assertEquals(1, dismissCount)
        assertTrue(s.saving)
        s.restoreAiBeats({ restored++ }, { dismissCount++ })
        assertEquals(1, restored)
        assertEquals(1, dismissCount)
    }

    @Test
    fun 撤回二段式_先弹确认_保存中不弹_确认后发撤回并关() {
        val s = StoryDirectorEditorState("旧", "")
        s.requestWithdraw()
        assertTrue(s.confirmWithdraw)

        val busy = StoryDirectorEditorState("旧", "")
        busy.saving = true
        busy.requestWithdraw()
        assertFalse(busy.confirmWithdraw)

        var withdrawn = 0
        s.confirmWithdrawAndClose({ withdrawn++ }, { dismissCount++ })
        assertEquals(1, withdrawn)
        assertEquals(1, dismissCount)
        assertTrue(s.saving)
        assertFalse(s.confirmWithdraw)
    }

    @Test
    fun 弃改确认_只关不写() {
        val s = StoryDirectorEditorState("旧", "节拍")
        s.flowText = "新"
        s.confirmDiscard = true
        s.discardAndClose { dismissCount++ }
        assertEquals(1, dismissCount)
        assertFalse(s.confirmDiscard)
        assertEquals(emptyList<String>(), overwritten)
        assertNull(submitted.firstOrNull())
    }
}
