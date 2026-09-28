package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.PersonaCompileMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * T2-4（琉璃 2.0 卷五 §7）：本性滑杆「现在」竖线（E10）+ 生成行六态（E11）。
 * 竖线中心按琉璃拇指 20 独立反推：滑杆左缘 + 10 + (宽 − 20) × current / 100；mdpi 下 1dp = 1px。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
class LiuliAnchorSliderRowTest {

    @get:Rule val compose = createComposeRule()

    private fun showSlider(anchor: Int, current: Int) {
        compose.setContent {
            LiuliEditTestHost {
                Column {
                    LiuliAnchorSliderRow(name = "温暖度", hint = "低=冷淡，高=温暖", anchor = anchor, current = current, basis = null, onChange = {}, divider = false)
                }
            }
        }
    }

    @Test fun 偏移3不显竖线与标签() {
        showSlider(anchor = 50, current = 53)
        compose.onNodeWithTag(LIULI_NOW_MARKER_TAG).assertDoesNotExist()
        compose.onNodeWithText("现在 53").assertDoesNotExist()
    }

    @Test fun 偏移30显竖线且中心按琉璃拇指定位() {
        showSlider(anchor = 50, current = 80)
        compose.onNodeWithText("现在 80").assertExists()
        val slider = compose.onNodeWithContentDescription("温暖度").getUnclippedBoundsInRoot()
        val marker = compose.onNodeWithTag(LIULI_NOW_MARKER_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val width = (slider.right - slider.left).value
        val expected = slider.left.value + 10f + (width - 20f) * 80 / 100f
        val actual = (marker.left.value + marker.right.value) / 2f
        assertTrue("竖线中心 $actual 应为 $expected（±1dp）", abs(actual - expected) <= 1f)
        assertEquals(2f, (marker.right - marker.left).value, 0.01f)
        assertEquals(8f, (marker.bottom - marker.top).value, 0.01f)
    }

    @Test fun 现在标签贴右边时不出界() {
        showSlider(anchor = 10, current = 100)
        val slider = compose.onNodeWithContentDescription("温暖度").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("现在 100").getUnclippedBoundsInRoot()
        assertTrue("标签右缘 ${label.right} 不应越过滑杆右缘 ${slider.right}", label.right.value <= slider.right.value + 0.5f)
    }

    private var compiles = 0

    private fun showCompile(
        meta: PersonaCompileMeta = PersonaCompileMeta(),
        stale: Boolean = false,
        blank: Boolean = false,
        compiling: Boolean = false,
        needsSave: Boolean = false,
    ) {
        compose.setContent {
            LiuliEditTestHost {
                Column { LiuliPersonaCompileRow(meta, stale, blank, compiling, needsSave, onCompile = { compiles++ }) }
            }
        }
    }

    @Test fun 从未生成_标题说明与生成钮可点() {
        showCompile()
        compose.onNodeWithText("从人设生成").assertExists()
        compose.onNodeWithText("读一遍你写的性格描述", substring = true).assertExists()
        compose.onNodeWithText("生成").assertIsEnabled().performClick()
        assertEquals(1, compiles)
    }

    @Test fun 人设空_钮禁用() {
        showCompile(blank = true)
        compose.onNodeWithText("生成").assertIsNotEnabled().performClick()
        assertEquals(0, compiles)
    }

    @Test fun 生成中_转圈文案且禁用() {
        showCompile(compiling = true)
        compose.onNodeWithText("生成中…").assertIsNotEnabled()
    }

    @Test fun 已生成_重新生成且无说明() {
        showCompile(meta = PersonaCompileMeta(source = PersonaCompileMeta.SOURCE_COMPILED, compiledAt = 10L))
        compose.onNodeWithText("已按当前人设生成").assertExists()
        compose.onNodeWithText("重新生成").assertIsEnabled()
        compose.onNodeWithText("读一遍你写的性格描述", substring = true).assertDoesNotExist()
    }

    @Test fun 未保存与过期两条提示() {
        showCompile(stale = true, needsSave = true)
        compose.onNodeWithText("人设改过还没保存", substring = true).assertExists()
        compose.onNodeWithText("你修改过性格描述", substring = true).assertExists()
    }

    @Test fun 失败晚于成功_只显失败提示() {
        showCompile(meta = PersonaCompileMeta(source = PersonaCompileMeta.SOURCE_COMPILED, compiledAt = 10L, lastFailedAt = 20L, droppedCount = 3))
        compose.onNodeWithText("这次没能读懂人设", substring = true).assertExists()
        compose.onNodeWithText("已忽略 3 条", substring = true).assertDoesNotExist()
    }

    @Test fun 成功晚于失败_显丢条数() {
        showCompile(meta = PersonaCompileMeta(source = PersonaCompileMeta.SOURCE_COMPILED, compiledAt = 30L, lastFailedAt = 20L, droppedCount = 3))
        compose.onNodeWithText("已忽略 3 条重复或无法识别的项。").assertExists()
    }
}
