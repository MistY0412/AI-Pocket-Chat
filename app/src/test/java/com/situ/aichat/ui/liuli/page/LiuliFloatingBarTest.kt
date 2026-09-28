package com.situ.aichat.ui.liuli.page

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.liuli.designsystem.LiuliCircleButton
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-P3（琉璃 2.0 卷六·一 §3.13）：悬浮玻璃底条放在 `LiuliPage(bottomBar = …)` 里的几何。
 * 条内距锁定 左右 8 / 上下 4（图纸 §3.13·§9），故玻璃边 = 首钮左缘 − 8 / 末钮右缘 + 8 / 钮底 + 4；
 * 期望：左右各距根 12、底距根 12（Robolectric 导航栏 0）、条高 56（内件为两枚 48 触达钮）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliFloatingBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun 底条左右底各离12且高56() {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliPage(
                        title = "日记",
                        onBack = {},
                        collapsed = false,
                        bottomBar = {
                            LiuliFloatingBar {
                                LiuliCircleButton(onClick = {}, contentDescription = "加图") {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                LiuliCircleButton(onClick = {}, contentDescription = "改写") {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                    ) {}
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        // 圆钮语义边框 = 视觉 40；它在 Row 里占的版位是 48 触达框（minimumInteractiveComponentSize），故按触达框量。
        val px = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        fun touch(cd: String) = compose.onNodeWithContentDescription(cd).fetchSemanticsNode().touchBoundsInRoot
        val first = touch("加图")
        val last = touch("改写")
        assertEquals("触达框 48", 48f, (first.bottom - first.top) / px, 0.5f)
        val barLeft = first.left / px - 8f
        val barRight = last.right / px + 8f
        val barBottom = first.bottom / px + 4f
        val barTop = first.top / px - 4f
        assertEquals(12f, barLeft - root.left.value, 0.5f)
        assertEquals(12f, root.right.value - barRight, 0.5f)
        assertEquals(12f, root.bottom.value - barBottom, 0.5f)
        assertEquals(56f, barBottom - barTop, 0.5f)
    }
}
