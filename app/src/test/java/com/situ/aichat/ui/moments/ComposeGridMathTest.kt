package com.situ.aichat.ui.moments

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** T1-3（朋友圈发布页·乙 §7·E8）：三列图片格的落点序号与让位序号（纯函数·期望从图纸 §4.6 独立反推）。 */
class ComposeGridMathTest {

    private val cell = 100f
    private val gap = 8f
    private val pitch = cell + gap

    private fun center(i: Int) = Offset((i % 3) * pitch + cell / 2f, (i / 3) * pitch + cell / 2f)

    @Test fun 九格中心各落其序号() {
        for (i in 0 until 9) assertEquals("格 $i", i, composeGridTargetIndex(center(i), cell, gap, 9))
    }

    @Test fun 缝里算右下那一格_左上越界钳0() {
        // x = 104（第一条缝中间）→ floor(104 / 108) = 0；x = 108 起算第 2 列。
        assertEquals(0, composeGridTargetIndex(Offset(104f, 50f), cell, gap, 9))
        assertEquals(1, composeGridTargetIndex(Offset(108f, 50f), cell, gap, 9))
        assertEquals(0, composeGridTargetIndex(Offset(-40f, -40f), cell, gap, 9))
    }

    @Test fun 越右被钳到第三列_越下被钳到末格() {
        assertEquals(2, composeGridTargetIndex(Offset(1_000f, 50f), cell, gap, 9))
        assertEquals(5, composeGridTargetIndex(Offset(1_000f, pitch + 50f), cell, gap, 9))
        assertEquals(8, composeGridTargetIndex(Offset(50f, 5_000f), cell, gap, 9))
    }

    @Test fun 指到空位钳到最后一张() {
        // 4 张图：第 5、8 格都是空位 → 3。
        assertEquals(3, composeGridTargetIndex(center(5), cell, gap, 4))
        assertEquals(3, composeGridTargetIndex(center(8), cell, gap, 4))
        assertEquals(2, composeGridTargetIndex(center(2), cell, gap, 4))
    }

    @Test fun 让位_往后拖区间内前移一位() {
        // from 0 → to 2：1、2 前移；0 之外、2 之后不动。
        assertEquals(0, composeGridVisualIndex(1, 0, 2))
        assertEquals(1, composeGridVisualIndex(2, 0, 2))
        assertEquals(3, composeGridVisualIndex(3, 0, 2))
    }

    @Test fun 让位_往前拖区间内后移一位() {
        // from 3 → to 1：1、2 后移；0 与 4 不动。
        assertEquals(2, composeGridVisualIndex(1, 3, 1))
        assertEquals(3, composeGridVisualIndex(2, 3, 1))
        assertEquals(0, composeGridVisualIndex(0, 3, 1))
        assertEquals(4, composeGridVisualIndex(4, 3, 1))
    }

    @Test fun 让位_原地不动() {
        for (j in 0 until 5) assertEquals(j, composeGridVisualIndex(j, 2, 2))
    }
}
