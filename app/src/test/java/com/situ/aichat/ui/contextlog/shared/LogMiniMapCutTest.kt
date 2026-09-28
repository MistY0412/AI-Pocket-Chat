package com.situ.aichat.ui.contextlog.shared

import com.situ.aichat.ui.contextlog.model.LogMiniPart
import com.situ.aichat.ui.contextlog.model.LogMiniStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** 复核 R1：迷你地图红线照横条真实排法取点（权重归一 + 2dp 间隙），不再是「宽 × 占比」。 */
class LogMiniMapCutTest {

    private val parts = listOf(
        LogMiniPart(0.2f, LogMiniStyle.PREFIX), LogMiniPart(0.1f, LogMiniStyle.VARIABLE), LogMiniPart(0.7f, LogMiniStyle.HISTORY),
    )

    @Test
    fun boundary_isGapMiddle_insidePart_isProportional() {
        // 宽 1000、间隙 2 → 可分 996：三段宽 199.2 / 99.6 / 697.2，第二段起点 201.2
        assertEquals("恰在前置区与每轮会变交界 → 间隙正中", 200.2f, miniCutX(parts, 0.2f, 1000f, 2f), 1e-3f)
        assertEquals("每轮会变中点", 201.2f + 49.8f, miniCutX(parts, 0.25f, 1000f, 2f), 1e-3f)
        assertEquals(0f, miniCutX(parts, 0f, 1000f, 2f), 1e-3f)
        assertEquals(1000f, miniCutX(parts, 1f, 1000f, 2f), 1e-3f)
    }

    @Test
    fun tinyPart_minWeight_shiftsLaterBoundaries() {
        // 0.001 的部分按最小权重 0.01 排：交界按真实宽度走，不按占比
        val p = listOf(LogMiniPart(0.001f, LogMiniStyle.PREFIX), LogMiniPart(0.999f, LogMiniStyle.HISTORY))
        val unit = 998f / (0.01f + 0.999f)
        assertEquals(0.01f * unit + 1f, miniCutX(p, 0.001f, 1000f, 2f), 1e-2f)
    }
}
