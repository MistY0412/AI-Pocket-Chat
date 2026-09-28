package com.situ.aichat.ui.liuli.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LocalAppColors
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Color as AndroidColor

/**
 * T2-7 表情回应徽章（图纸 2026-09-05 卷二B §7 · A-8「纯瞬态」）。
 *
 * **怎么数**：徽章与四颗小心整组挂在 `clearAndSetSemantics {}` 之下（纯装饰·对读屏隐形，见 §4.6），
 * 所以文字节点选不中——本件在语义树里留下的唯一痕迹，是**每条在场的爆点各一个空节点**。
 * 数根的子节点 = 数「几条泡正在回应」，正是这里要钉的事（只在被点那条、不叠第二枚）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliReactionBurstTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = mockk<AppHaptics>(relaxed = true)
    private val state = LiuliReactionState()

    /** 同屏两条泡：一条被回应、一条不该被波及。 */
    private fun setRows(reduceMotion: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                Box(Modifier.size(200.dp)) {
                    LiuliReactionBurst(state.burst, false, "m1", reduceMotion, Modifier.matchParentSize())
                }
                Box(Modifier.size(200.dp)) {
                    LiuliReactionBurst(state.burst, false, "m2", reduceMotion, Modifier.matchParentSize())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun burstNodes() = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().children.size

    // ── 状态语义（纯逻辑） ──────────────────────────────────────────────────────

    @Test fun state_carriesTargetAndEmoji_andBumpsTokenEveryTime() {
        assertEquals("初始无爆点", null, state.burst)
        state.play("m1", "❤️")
        val first = requireNotNull(state.burst)
        assertEquals("m1", first.messageUuid)
        assertEquals("❤️", first.emoji)
        state.play("m1", "❤️")
        assertNotEquals("同泡同表情再来一次也要换 token（重启而非叠加）", first.token, state.burst?.token)
    }

    @Test fun heartOffsets_areTheFourTabledPairs_andClampOutOfRange() {
        assertEquals((-14).dp to 52.dp, liuliHeartOffsets(0))
        assertEquals((-4).dp to 44.dp, liuliHeartOffsets(1))
        assertEquals(6.dp to 60.dp, liuliHeartOffsets(2))
        assertEquals(16.dp to 48.dp, liuliHeartOffsets(3))
        assertEquals("越界钳回表内，绝不崩", liuliHeartOffsets(0), liuliHeartOffsets(-1))
        assertEquals(liuliHeartOffsets(3), liuliHeartOffsets(9))
    }

    // ── 渲染（Robolectric） ────────────────────────────────────────────────────

    @Test fun idle_drawsNothing() {
        setRows(reduceMotion = true)
        assertEquals("没人回应时两条泡上都不该有东西", 0, burstNodes())
    }

    @Test fun play_marksOnlyTheTargetRow() {
        state.play("m1", "❤️")
        setRows(reduceMotion = true)
        assertEquals("只有被点那条泡上有爆点", 1, burstNodes())
    }

    @Test fun replay_onSameRow_doesNotStackASecondBadge() {
        state.play("m1", "❤️")
        setRows(reduceMotion = true)
        compose.runOnUiThread { state.play("m1", "❤️") }
        compose.waitForIdle()
        assertEquals("重复双击重启计时，绝不叠第二枚", 1, burstNodes())
    }

    @Test fun badge_letsGoAfterItsHold() {
        state.play("m1", "❤️")
        setRows(reduceMotion = true)
        assertEquals("前提：确实弹出来了", 1, burstNodes())
        // 纯瞬态：驻留期满自己散场，不留痕（时长常量本身由 badgeHold 一并钉）。
        compose.waitUntil(BADGE_WAIT_MS) { burstNodes() == 0 }
        assertEquals(0, burstNodes())
    }

    // ── 卷三 §4.3：徽章在泡外侧（NATIVE 量像素） ──────────────────────────────────

    /**
     * 整行真渲染（夜档：徽章无投影、恰是一枚实心圆，像素包围盒 = 徽章边界）压在纯红底上；泡的边界取自语义树，
     * 泡下时间戳取自未合并树（[stampBoundsBelow]）；泡与时间戳之外的非红像素 = 徽章。
     */
    private fun measureBadge(isUser: Boolean): Triple<Rect, Rect, Rect> {
        var view: View? = null
        state.play("m1", "❤️")
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppHaptics provides haptics,
                LocalIsDarkTheme provides true,
                LocalAppColors provides LiuliDarkAppColors,
            ) {
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    LiuliMessageRow(
                        message = MessageEntity(
                            messageUUID = "m1",
                            conversationUuid = "c",
                            roleRaw = if (isUser) "user" else "assistant",
                            content = "嗨",
                            timestamp = 1_756_000_000_000L,
                            isContentRevealed = true,
                        ),
                        topPadding = 40.dp,
                        characterName = "云野",
                        avatarPath = null,
                        userName = "我",
                        userAvatarPath = null,
                        customStickers = emptyList(),
                        isVoicePlaying = false,
                        voiceProgress = { 0f },
                        actions = liuliNoopRowActions,
                        canRegenerate = false,
                        deliveryRead = null,
                        flightTracking = false,
                        reaction = state,
                        reduceMotion = true,
                        fold = LiuliFoldState(),
                    )
                }
            }
        }
        compose.waitForIdle()
        val bubble = compose.onNodeWithText("嗨").fetchSemanticsNode().boundsInRoot
        val stamp = compose.stampBoundsBelow(bubble)
        val v = checkNotNull(view)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        for (y in 0 until (stamp.bottom + 60).toInt().coerceAtMost(bmp.height)) {
            for (x in 0 until bmp.width) {
                val inBubble = x >= bubble.left - 1 && x < bubble.right + 1 && y >= bubble.top - 1 && y < bubble.bottom + 1
                val inStamp = x >= stamp.left - 1 && x < stamp.right + 1 && y >= stamp.top - 1 && y < stamp.bottom + 1
                if (inBubble || inStamp || bmp.getPixel(x, y) == AndroidColor.RED) continue
                l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x + 1); b = maxOf(b, y + 1)
            }
        }
        assertTrue("应找到徽章像素", l != Int.MAX_VALUE)
        return Triple(bubble, stamp, Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()))
    }

    private fun assertOutside(isUser: Boolean) {
        val (bubble, stamp, badge) = measureBadge(isUser)
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val side = if (isUser) "用户泡" else "AI 泡"
        assertTrue("$side：徽章 $badge 与泡 $bubble 不相交", !badge.overlaps(bubble))
        assertTrue("$side：徽章 $badge 与泡下时间戳 $stamp 不相交", !badge.overlaps(stamp))
        val gap = if (isUser) bubble.left - badge.right else badge.left - bubble.right
        assertEquals("$side：徽章离泡外侧 4dp", 4f * density, gap, 0.5f)
        assertEquals("$side：徽章底 = 泡底", bubble.bottom, badge.bottom, 0.5f)
        assertEquals("$side：徽章直径 26dp", 26f * density, badge.width, 0.5f)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun aiBubble_badgeSitsOutsideOnTheRight() = assertOutside(isUser = false)

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun userBubble_badgeSitsOutsideOnTheLeft() = assertOutside(isUser = true)

    private companion object {
        const val BADGE_WAIT_MS = 5_000L
    }
}
