package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.ui.character.CharacterEditState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-7（琉璃 2.0 卷五 §7·E15）：`focusVoice = true` 进页一次性滚到语音组；状态恢复后闩锁不再滚。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliCharacterEditFocusTest {

    @get:Rule val compose = createComposeRule()

    private var voiceY: Int? = null

    private fun content(focus: Boolean, scroll: ScrollState): @androidx.compose.runtime.Composable () -> Unit = {
        LiuliEditTestHost {
            LiuliCharacterEditPage(isEditing = true, canSave = true, saving = false, onCancel = {}, onSave = {}, focusVoiceSection = focus, scrollState = scroll) { onY ->
                LiuliCharacterEditContent(
                    state = CharacterEditState(name = "小满"), isEditing = true, compiling = false, personaNeedsSave = false,
                    systemVoices = emptyList(), previewBusy = false, previewError = null, hasModuleOverride = false,
                    onUpdate = {}, onPickAvatar = {}, onPickWallpaper = {}, onRemoveWallpaper = {}, onOpenBirthday = {},
                    onCompilePersona = {}, onLoadSystemVoices = {}, onPreviewVoice = {}, onSetModuleOverride = {},
                    onEditModules = {}, onOpenMeetings = {},
                    onVoiceSectionY = { voiceY = it; onY(it) },
                    worldGroup = {}, worldBookGroup = null,
                )
            }
        }
    }

    @Test fun 深链进来滚到语音组() {
        val scroll = ScrollState(0)
        compose.setContent(content(focus = true, scroll = scroll))
        compose.waitForIdle()
        val y = voiceY!!
        assertTrue("语音组在首屏之下才有滚动的意义（y = $y）", y > 891)
        assertEquals(minOf(y, scroll.maxValue), scroll.value)
    }

    @Test fun 不带深链不滚() {
        val scroll = ScrollState(0)
        compose.setContent(content(focus = false, scroll = scroll))
        compose.waitForIdle()
        assertEquals(0, scroll.value)
    }

    @Test fun 状态恢复后闩锁不再滚() {
        val scroll = ScrollState(0)
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content(focus = true, scroll = scroll))
        compose.waitForIdle()
        assertTrue(scroll.value > 0)
        runBlocking { scroll.scrollTo(0) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(0, scroll.value)
    }
}
