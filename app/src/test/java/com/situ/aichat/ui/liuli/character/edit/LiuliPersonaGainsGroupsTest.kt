package com.situ.aichat.ui.liuli.character.edit

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.R
import com.situ.aichat.data.model.CustomGain
import com.situ.aichat.data.model.PersonaGains
import com.situ.aichat.data.model.PersonaOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-5（琉璃 2.0 卷五 §7）：「她吃哪套」三护栏 / 改档摘键 / 展开收起 + 「她的固定反应」（E12 / E13）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliPersonaGainsGroupsTest {

    @get:Rule val compose = createComposeRule()

    private var latest: PersonaGains? = null
    private var latestOps: List<PersonaOperator>? = null
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(gains: PersonaGains) {
        compose.setContent {
            LiuliEditTestHost {
                Column(Modifier.verticalScroll(rememberScrollState())) { LiuliPersonaGainsGroup(gains = gains, onChange = { latest = it }) }
            }
        }
    }

    private fun custom(n: Int) = List(n) { CustomGain(id = "c$it", label = "专属$it", level = 2, origin = CustomGain.ORIGIN_MANUAL) }

    private val draftField get() = compose.onNode(hasSetTextAction() and hasContentDescription("她在意的事"))

    @Test fun 输入13字不接收() {
        show(PersonaGains())
        compose.onNodeWithText("添加一件她在意的事").performClick()
        draftField.performTextInput("一二三四五六七八九十一二三")
        draftField.assertExists()
        compose.onNode(hasSetTextAction() and hasText("一二三四五六七八九十一二三")).assertDoesNotExist()
    }

    @Test fun 重复标签显查重提示且添加禁用() {
        show(PersonaGains())
        val g01 = ctx.getString(R.string.persona_gain_g01)
        compose.onNodeWithText("添加一件她在意的事").performClick()
        draftField.performTextInput(g01)
        compose.onNodeWithText("这条和「$g01」重了。").assertExists()
        compose.onNodeWithText("添加").assertIsNotEnabled()
    }

    @Test fun 满10条触发行禁用并显满提示() {
        show(PersonaGains(custom = custom(10)))
        compose.onNodeWithText("添加一件她在意的事").assertIsNotEnabled().performClick()
        draftField.assertDoesNotExist()
        compose.onNodeWithText("最多 10 项，删掉一条再加。").assertExists()
    }

    @Test fun 新增后回调含新项且很敏感() {
        show(PersonaGains())
        compose.onNodeWithText("添加一件她在意的事").performClick()
        draftField.performTextInput("被叫全名")
        compose.onNodeWithText("添加").performClick()
        val added = latest!!.custom.single()
        assertEquals("被叫全名", added.label)
        assertEquals(2, added.level)
        assertEquals(CustomGain.ORIGIN_MANUAL, added.origin)
    }

    @Test fun 系统项改到正常回调里该键消失() {
        show(PersonaGains(system = mapOf("g01" to 2)))
        compose.onNodeWithText(ctx.getString(R.string.persona_gain_g01)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.persona_gain_level_normal)).performClick()
        assertNull(latest!!.system["g01"])
        assertFalse(latest!!.system.containsKey("g01"))
    }

    @Test fun 展开其余与收起切换() {
        show(PersonaGains(system = mapOf("g01" to 0)))
        compose.onNodeWithText(ctx.getString(R.string.persona_gain_g02)).assertDoesNotExist()
        compose.onNodeWithText("展开其余 26 项（均为「正常」）").performClick()
        compose.onNodeWithText(ctx.getString(R.string.persona_gain_g02)).assertExists()
        compose.onNodeWithText("收起").performScrollTo().performClick()
        compose.onNodeWithText(ctx.getString(R.string.persona_gain_g02)).assertDoesNotExist()
    }

    @Test fun 专属项删除回调() {
        show(PersonaGains(custom = custom(1)))
        compose.onNodeWithContentDescription("删除这一项").performClick()
        assertEquals(emptyList<CustomGain>(), latest!!.custom)
    }

    private fun showOps(ops: List<PersonaOperator>) {
        compose.setContent {
            LiuliEditTestHost { Column { LiuliPersonaOperatorsGroup(operators = ops, onChange = { latestOps = it }) } }
        }
    }

    @Test fun 算子为空整组不画() {
        showOps(emptyList())
        compose.onNodeWithText("她的固定反应").assertDoesNotExist()
    }

    @Test fun 算子开关与删除回调() {
        val ops = listOf(PersonaOperator(id = "o1", condition = "c01", action = "a01", enabled = true), PersonaOperator(id = "o2", condition = "zz", action = "a01"))
        showOps(ops)
        compose.onNodeWithText("她的固定反应").assertExists()
        compose.onNodeWithText("想道歉的时候").assertExists()
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertEquals(false, latestOps!!.first { it.id == "o1" }.enabled)
        compose.onNodeWithContentDescription("删除这条反应").performClick()
        assertEquals(listOf("o2"), latestOps!!.map { it.id })
    }
}
