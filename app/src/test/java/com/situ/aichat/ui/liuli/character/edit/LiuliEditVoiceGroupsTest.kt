package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.tts.SystemVoiceOption
import com.situ.aichat.ui.character.CharacterEditState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-6（琉璃 2.0 卷五 §7）：语音组（E14）+ 提示词模块组（E16）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliEditVoiceGroupsTest {

    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(CharacterEditState(name = "小满"))
    private var loads = 0
    private var previews = 0
    private val voices = listOf(SystemVoiceOption(id = "v1", name = "晓晓", quality = 400, localeTag = "zh-CN"))

    private fun showVoice(busy: Boolean = false, error: String? = null) {
        compose.setContent {
            LiuliEditTestHost {
                Column {
                    LiuliEditVoiceGroup(
                        state = state,
                        systemVoices = voices,
                        previewBusy = busy,
                        previewError = error,
                        onLoadSystemVoices = { loads++ },
                        onPreview = { previews++ },
                        onUpdate = { t -> state = t(state) },
                    )
                }
            }
        }
    }

    @Test fun 展开系统音色触发加载一次且选中写入() {
        showVoice()
        compose.onNodeWithText("系统音色").performClick()
        assertEquals(1, loads)
        compose.onNodeWithText("晓晓", substring = true).performClick()
        assertEquals("v1", state.voiceIdentifier)
    }

    @Test fun 两菜单互斥_开情绪即关系统音色() {
        showVoice()
        compose.onNodeWithText("系统音色").performClick()
        compose.onNodeWithText("晓晓", substring = true).assertExists()
        compose.onNodeWithText("情绪（MiniMax 专属）").performClick()
        compose.onNodeWithText("晓晓", substring = true).assertDoesNotExist()
        compose.onNodeWithText("开心").performClick()
        assertEquals("happy", state.ttsEmotionRaw)
    }

    @Test fun 语速值按两位小数显示() {
        state = state.copy(ttsSpeed = 1.25)
        showVoice()
        compose.onNodeWithText("1.25x").assertExists()
        compose.onNodeWithText("语速").assertExists()
    }

    @Test fun 试听忙时禁用且报错文本显示() {
        showVoice(busy = true, error = "合成失败：网络不可用")
        compose.onNodeWithText("试听").assertIsNotEnabled().performClick()
        assertEquals(0, previews)
        compose.onNodeWithText("合成失败：网络不可用").assertExists()
    }

    @Test fun 试听空闲可点() {
        showVoice()
        compose.onNodeWithText("试听").performClick()
        assertEquals(1, previews)
    }

    private var overrides = mutableListOf<Boolean>()
    private var edits = 0

    private fun showModules(has: Boolean) {
        compose.setContent {
            LiuliEditTestHost {
                Column { LiuliEditModulesGroup(hasOverride = has, onSetOverride = { overrides += it }, onEditModules = { edits++ }) }
            }
        }
    }

    @Test fun 模块关_显使用全局副标无编辑入口_开关立即回调() {
        showModules(false)
        compose.onNodeWithText("当前使用全局配置").assertExists()
        compose.onNodeWithText("编辑角色专属模块").assertDoesNotExist()
        compose.onNode(isToggleable()).performClick()
        assertEquals(listOf(true), overrides)
    }

    @Test fun 模块开_显编辑入口() {
        showModules(true)
        compose.onNodeWithText("当前使用全局配置").assertDoesNotExist()
        compose.onNodeWithText("编辑角色专属模块").performClick()
        assertEquals(1, edits)
    }
}
