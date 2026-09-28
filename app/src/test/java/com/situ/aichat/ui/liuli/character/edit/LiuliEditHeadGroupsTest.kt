package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T2-2（琉璃 2.0 卷五 §7）：头像块 + 聊天壁纸组（E4 / E5）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliEditHeadGroupsTest {

    @get:Rule val compose = createComposeRule()

    private var picks = 0
    private var removes = 0

    private fun showAvatar(path: String?) {
        compose.setContent {
            LiuliEditTestHost {
                LiuliEditAvatarBlock(name = "小满", avatarPath = path, onPickAvatar = { picks++ }, onRemoveAvatar = { removes++ })
            }
        }
    }

    private fun showWallpaper(path: String?) {
        compose.setContent {
            LiuliEditTestHost {
                Column { LiuliEditWallpaperGroup(wallpaperPath = path, onPick = { picks++ }, onRemove = { removes++ }) }
            }
        }
    }

    @Test fun 无头像时没有移除头像() {
        showAvatar(null)
        compose.onNodeWithText("移除头像").assertDoesNotExist()
    }

    @Test fun 有头像时出现移除头像且点击回调() {
        showAvatar("/nope/avatar.jpg")
        compose.onNodeWithText("移除头像").performClick()
        assertEquals(1, removes)
        assertEquals(0, picks)
    }

    @Test fun 点头像与相机徽章各触发选图一次() {
        showAvatar(null)
        compose.onNodeWithText("小").performClick()
        assertEquals(1, picks)
        compose.onNodeWithContentDescription("更换头像").performClick()
        assertEquals(2, picks)
    }

    @Test fun 壁纸空态显示添加且点击选图() {
        showWallpaper(null)
        compose.onNodeWithText("聊天壁纸").assertExists()
        compose.onNodeWithText("移除壁纸").assertDoesNotExist()
        compose.onNodeWithText("添加聊天壁纸").performClick()
        assertEquals(1, picks)
    }

    @Test fun 壁纸已设显示更换与移除且移除回调一次() {
        showWallpaper("/nope/wallpaper.jpg")
        compose.onNodeWithText("添加聊天壁纸").assertDoesNotExist()
        compose.onNodeWithText("更换").assertExists()
        compose.onNodeWithText("移除壁纸").performClick()
        assertEquals(1, removes)
        assertEquals(0, picks)
    }
}
