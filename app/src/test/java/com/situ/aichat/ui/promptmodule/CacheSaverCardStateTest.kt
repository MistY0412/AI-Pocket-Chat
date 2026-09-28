package com.situ.aichat.ui.promptmodule

import com.situ.aichat.diagnostics.LogListRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T1-3（时间感知四期·图纸二 §3.5 · E22）：命中行三态。期望从规格独立写出。
 */
class CacheSaverCardStateTest {

    private fun row(id: Long, hit: Int, miss: Int, ok: Boolean = true) =
        LogListRow(id = id, timestampMillis = id, isSuccess = ok, cacheHitTokens = hit, cacheMissTokens = miss)

    @Test
    fun `没有记录_NoRecords`() {
        assertEquals(RecentCacheLine.NoRecords, recentCacheLineOf(emptyList()))
    }

    @Test
    fun `有记录但全无缓存数_条数照计_百分比null`() {
        assertEquals(RecentCacheLine.Rate(3, null), recentCacheLineOf(listOf(row(1, 0, 0), row(2, 0, 0), row(3, 0, 0))))
    }

    @Test
    fun `加权命中率_680比320得68`() {
        assertEquals(RecentCacheLine.Rate(1, 68), recentCacheLineOf(listOf(row(1, 680, 320))))
    }

    @Test
    fun `加权而非逐条平均_无缓存数的条目只计条数`() {
        // 逐条平均 = (90% + 10%) / 2 = 50%；加权 = (900 + 10) / (1000 + 100) = 82.7% → 83。第三条无缓存数：计入条数、不计入命中率。
        val rows = listOf(row(1, 900, 100), row(2, 10, 90), row(3, 0, 0))
        assertEquals(RecentCacheLine.Rate(3, 83), recentCacheLineOf(rows))
    }

    @Test
    fun `窗口常量为20`() {
        assertEquals(20, RECENT_CHAT_WINDOW)
    }

    @Test
    fun `卡片默认态_不可见_关_无灰字_无记录`() {
        assertEquals(CacheSaverCardState(false, false, false, RecentCacheLine.NoRecords), CacheSaverCardState())
    }
}
