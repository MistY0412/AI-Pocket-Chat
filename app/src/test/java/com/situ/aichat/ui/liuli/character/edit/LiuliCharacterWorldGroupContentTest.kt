package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.ui.character.CharacterWorldUiState
import com.situ.aichat.ui.character.CityUi
import com.situ.aichat.ui.character.RegionUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-8（琉璃 2.0 卷五 §7·E17）：世界组四态文案 / D8 锁 / 离开确认 / 住址三态 / 城市弹层搬家。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliCharacterWorldGroupContentTest {

    @get:Rule val compose = createComposeRule()

    private var joins = 0
    private var leaves = 0
    private val moves = mutableListOf<String>()

    private val cities = listOf(
        CityUi(id = "yunye", name = "云野镇", isUserHome = true, isCurrentAddress = true),
        CityUi(id = "haiwan", name = "海湾市", isUserHome = false, isCurrentAddress = false),
    )

    private fun show(state: CharacterWorldUiState) {
        compose.setContent {
            LiuliEditTestHost {
                Column {
                    LiuliCharacterWorldGroupContent(state, onJoin = { joins++ }, onLeave = { leaves++ }, onSelectRegion = {}, onMove = { moves += it })
                }
            }
        }
    }

    private fun state(wb: Boolean = false, native: Boolean = false, joined: Boolean = false, sameCity: Boolean = true) = CharacterWorldUiState(
        loaded = true, joined = joined, nativeOrigin = native, worldbookBound = wb, homeCityName = "云野镇", sameCityAsUser = sameCity,
        regions = listOf(RegionUi("r1", "东岸")), citiesOfRegion = cities, selectedRegionId = "r1",
    )

    @Test fun 世界书锁_副标脚注且开关禁用() {
        show(state(wb = true))
        compose.onNodeWithText("TA 挂着世界书，二选一").assertExists()
        compose.onNodeWithText("想让 TA 进世界？", substring = true).assertExists()
        compose.onNode(isToggleable()).assertIsNotEnabled()
    }

    @Test fun 原住民_锁开关且住址行不可点() {
        show(state(native = true, joined = true, sameCity = false))
        compose.onNodeWithText("土生土长的原住民，生来就在世界里").assertExists()
        compose.onNodeWithText("原住民角色一直住在世界里", substring = true).assertExists()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("住在哪儿").assertHasNoClickAction()
        compose.onNodeWithText("云野镇 · 原住民").assertExists()
    }

    @Test fun 未加入_打开即加入() {
        show(state())
        compose.onNodeWithText("TA 会住进云野镇，有自己的生活").assertExists()
        compose.onNodeWithText("住在哪儿").assertDoesNotExist()
        compose.onNode(isToggleable()).assertIsEnabled().performClick()
        assertEquals(1, joins)
    }

    @Test fun 已加入_关开关出离开确认_确认才离开() {
        show(state(joined = true))
        compose.onNodeWithText("TA 已经是镇上的一员了").assertExists()
        compose.onNodeWithText("关掉即离开世界", substring = true).assertExists()
        compose.onNode(isToggleable()).performClick()
        assertEquals(0, leaves)
        compose.onNodeWithText("让 TA 离开世界？").assertExists()
        compose.onNodeWithText("让 TA 离开").performClick()
        assertEquals(1, leaves)
        compose.onNodeWithText("让 TA 离开世界？").assertDoesNotExist()
    }

    @Test fun 住址尾巴_同城与异地() {
        show(state(joined = true, sameCity = false))
        compose.onNodeWithText("云野镇 · 异地").assertExists()
    }

    @Test fun 城市弹层_点当前城无动作_点他城出搬家确认_确认回调() {
        show(state(joined = true))
        compose.onNodeWithText("云野镇 · 和你同城").performClick()
        compose.onNodeWithText("TA 住在哪儿").assertExists()
        compose.onNodeWithText("和你同城 · 默认").assertExists()
        compose.onNodeWithText("云野镇").performClick()
        compose.onNodeWithText("搬去云野镇？").assertDoesNotExist()
        compose.onNodeWithText("海湾市").performClick()
        compose.onNodeWithText("搬去海湾市？").assertExists()
        compose.onNodeWithText("搬").performClick()
        assertEquals(listOf("haiwan"), moves)
    }
}
