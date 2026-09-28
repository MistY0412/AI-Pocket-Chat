package com.situ.aichat.ui.liuli.page

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-3（琉璃 2.0 卷四 §3.6 / §7）：顶部渐进模糊带的遮罩停点（纯函数）。卷四复核 R1 加 Haze 路径的两组停点：
 * 遮挡色（屏顶最浓 → 状态栏底 75% → 渐隐段中点 25% → 带底 0）与保险渐隐（渐隐段前 60% 全实 → 带底 0）。
 * 琉璃 2.0 卷六·一 T1-6：随件从 `ui.liuli.chat.LiuliChatScrollEdgeTest` 移来（四例原样），追加屏底镜像与页壳带高三例。
 */
class LiuliScrollEdgeTest {

    @Test fun stops_solidThroughStatusBar_thenTwoSegmentFade() {
        val stops = liuliScrollEdgeStops(overscanPx = 32f, statusBarPx = 40f, heightPx = 134f)
        val solid = 72f / 134f
        val expected = listOf(0f to 1f, solid to 1f, (solid + (1f - solid) * 0.5f) to 0.35f, 1f to 0f)
        assertEquals(4, stops.size)
        expected.zip(stops).forEachIndexed { i, (e, a) ->
            assertEquals("停点 $i 位置", e.first, a.first, 1e-4f)
            assertEquals("停点 $i 浓度", e.second, a.second, 1e-4f)
        }
    }

    @Test fun stops_positionsMonotonic_from0To1() {
        listOf(Triple(32f, 0f, 94f), Triple(64f, 96f, 344f), Triple(32f, 40f, 134f)).forEach { (o, s, h) ->
            val p = liuliScrollEdgeStops(o, s, h).map { it.first }
            assertEquals(0f, p.first(), 0f)
            assertEquals(1f, p.last(), 0f)
            p.zipWithNext().forEach { (a, b) -> assertTrue("位置应递增：$p", b > a) }
        }
    }

    @Test fun washStops_fadeFromTopToZero() {
        val stops = liuliScrollEdgeWashStops(overscanPx = 32f, statusBarPx = 40f, heightPx = 134f, top = 0.4f)
        val solid = 72f / 134f
        val expected = listOf(0f to 0.4f, solid to 0.3f, (solid + (1f - solid) * 0.5f) to 0.1f, 1f to 0f)
        assertEquals(4, stops.size)
        expected.zip(stops).forEachIndexed { i, (e, a) ->
            assertEquals("停点 $i 位置", e.first, a.first, 1e-4f)
            assertEquals("停点 $i 浓度", e.second, a.second, 1e-4f)
        }
    }

    @Test fun safetyStops_solidUntil60PercentOfFade_thenZeroAtBottom() {
        val stops = liuliScrollEdgeSafetyStops(overscanPx = 32f, statusBarPx = 40f, heightPx = 134f)
        val solid = 72f / 134f
        val expected = listOf(0f to 1f, (solid + (1f - solid) * 0.6f) to 1f, 1f to 0f)
        assertEquals(3, stops.size)
        expected.zip(stops).forEachIndexed { i, (e, a) ->
            assertEquals("停点 $i 位置", e.first, a.first, 1e-4f)
            assertEquals("停点 $i 浓度", e.second, a.second, 1e-4f)
        }
        listOf(Triple(32f, 0f, 94f), Triple(64f, 96f, 344f)).forEach { (o, s, h) ->
            val p = liuliScrollEdgeSafetyStops(o, s, h).map { it.first } + liuliScrollEdgeWashStops(o, s, h, 0.5f).map { it.first }
            p.take(3).zipWithNext().forEach { (a, b) -> assertTrue("保险停点应递增：$p", b > a) }
            p.drop(3).zipWithNext().forEach { (a, b) -> assertTrue("遮挡色停点应递增：$p", b > a) }
        }
    }

    // ── 琉璃 2.0 卷六·一 T1-6 ──

    @Test fun mirroredStops_flipPositionsAndReverse() {
        val top = listOf(0f to 1f, 0.5f to 1f, 0.75f to 0.35f, 1f to 0f)
        val expected = listOf(0f to 0f, 0.25f to 0.35f, 0.5f to 1f, 1f to 1f)
        val actual = top.mirroredEdgeStops()
        assertEquals(4, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertEquals("停点 $i 位置", e.first, a.first, 1e-6f)
            assertEquals("停点 $i 浓度", e.second, a.second, 1e-6f)
        }
    }

    @Test fun bottomWashStops_clearAtBandTop_densestAtScreenBottom() {
        // 屏底带：带高 164 = 导航栏 48 + 带在导航栏之上 84 + 屏外 32；遮挡色从带顶 0 → 屏底 0.4（顶带算式的镜像·独立复算）。
        val s = 80f / 164f
        val m = s + (1f - s) / 2f
        val expected = listOf(0f to 0f, (1f - m) to 0.1f, (1f - s) to 0.3f, 1f to 0.4f)
        val actual = liuliScrollEdgeWashStops(overscanPx = 32f, statusBarPx = 48f, heightPx = 164f, top = 0.4f).mirroredEdgeStops()
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertEquals("停点 $i 位置", e.first, a.first, 1e-4f)
            assertEquals("停点 $i 浓度", e.second, a.second, 1e-4f)
        }
    }

    @Test fun pageEdgeBelowStatus_restingReachesTitleTop_collapsedCoversCapsulePlusTail() {
        assertEquals("未收起 = 导航行 44 + 标题带顶距 2", 46.dp, liuliPageEdgeBelowStatus(collapsed = false, hasSubBar = false))
        assertEquals("未收起不看 subBar", 46.dp, liuliPageEdgeBelowStatus(collapsed = false, hasSubBar = true))
        assertEquals("收起 = 胶囊 44 + 尾巴 12", 56.dp, liuliPageEdgeBelowStatus(collapsed = true, hasSubBar = false))
        assertEquals("收起 + subBar = 44 + 56 + 12", 112.dp, liuliPageEdgeBelowStatus(collapsed = true, hasSubBar = true))
    }
}
