package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.ui.character.AgeMode
import com.situ.aichat.ui.character.CharacterEditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** T2-3（琉璃 2.0 卷五 §7）：基础信息 / 关系 / 角色设定组（E6–E9）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliEditTextGroupsTest {

    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(CharacterEditState(name = "小满"))
    private var opens = 0
    private var clears = 0

    private fun showBasic() {
        compose.setContent {
            LiuliEditTestHost {
                Column {
                    LiuliEditBasicGroup(
                        state = state,
                        onUpdate = { t -> state = t(state) },
                        onOpenBirthday = { opens++ },
                        onClearBirthday = { clears++ },
                    )
                }
            }
        }
    }

    @Test fun 生日未设显示点击设置() {
        showBasic()
        compose.onNodeWithText("点击设置").performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("清除生日").assertDoesNotExist()
    }

    @Test fun 生日已设显示星座与清除行() {
        val millis = Calendar.getInstance().apply { clear(); set(2000, Calendar.MARCH, 25) }.timeInMillis // 白羊座
        state = state.copy(birthdayMillis = millis)
        showBasic()
        compose.onNodeWithText("星座：白羊座 ♈").assertExists()
        compose.onNodeWithText("清除生日").performClick()
        assertEquals(1, clears)
    }

    @Test fun 切固定年龄显示输入且只收数字() {
        showBasic()
        compose.onNode(hasSetTextAction() and hasContentDescription("固定年龄")).assertDoesNotExist()
        compose.onNodeWithText("固定年龄").performClick() // 分段控件那一项
        assertEquals(AgeMode.FIXED, state.ageModeRaw)
        compose.onNode(hasSetTextAction() and hasContentDescription("固定年龄")).performTextInput("2a5")
        assertEquals("25", state.fixedAge)
    }

    @Test fun 随时间增长且有生日显示当前年龄() {
        val millis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1); add(Calendar.YEAR, -30) }.timeInMillis
        state = state.copy(birthdayMillis = millis)
        showBasic()
        compose.onNodeWithText("30 岁").assertExists()
    }

    @Test fun 点快捷标签恋人写入关系() {
        compose.setContent {
            LiuliEditTestHost {
                Column { LiuliEditRelationshipGroup(state = state, isEditing = true, onUpdate = { t -> state = t(state) }) }
            }
        }
        compose.onNodeWithText("恋人").performClick()
        assertEquals("恋人", state.relationshipName)
    }

    @Test fun 三个多行框最小高96() {
        state = state.copy(personalityDescription = "性格甲", appearanceDescription = "外貌乙", backstory = "背景丙")
        compose.setContent {
            LiuliEditTestHost {
                Column { LiuliEditSetupGroup(state = state, onUpdate = { t -> state = t(state) }) }
            }
        }
        listOf("性格甲", "外貌乙", "背景丙").forEach { v ->
            val b = compose.onNode(hasSetTextAction() and hasText(v)).getUnclippedBoundsInRoot()
            val h = (b.bottom - b.top).value
            assertTrue("$v 框高 $h 应 ≥ 96", h >= 96f - 0.5f)
            assertTrue("$v 框高 $h 单行文字不应长高超过 96", h <= 96f + 0.5f)
        }
    }
}
