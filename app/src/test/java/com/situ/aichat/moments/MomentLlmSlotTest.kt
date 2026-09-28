package com.situ.aichat.moments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MomentLlmSlot] 单测：上限 2 + count>0 释放守卫（1:1 iOS interactionSemaphore + releaseLLMSlot）。
 * 断言反推 iOS：`guard count < 2`（第 3 个 acquire 失败）；release `if count > 0`（过度释放不变负、不增容）。
 */
class MomentLlmSlotTest {

    @Test
    fun `caps concurrent acquisitions at two`() {
        val slot = MomentLlmSlot()
        assertTrue("first acquire succeeds", slot.tryAcquire())
        assertTrue("second acquire succeeds", slot.tryAcquire())
        assertFalse("third acquire fails (cap = 2)", slot.tryAcquire())
    }

    @Test
    fun `release frees a slot for re-acquisition`() {
        val slot = MomentLlmSlot()
        slot.tryAcquire()
        slot.tryAcquire()
        assertFalse(slot.tryAcquire())
        slot.release()
        assertTrue("a freed slot can be re-acquired", slot.tryAcquire())
        assertFalse("still capped at 2 after one release", slot.tryAcquire())
    }

    @Test
    fun `over-release is a no-op and does not inflate capacity`() {
        val slot = MomentLlmSlot()
        // 过度释放（无对应 acquire）：count 不会变负，容量仍是 2。
        slot.release()
        slot.release()
        assertTrue(slot.tryAcquire())
        assertTrue(slot.tryAcquire())
        assertFalse("over-release must not grant extra slots", slot.tryAcquire())
    }

    @Test
    fun `full acquire-release cycle returns to empty`() {
        val slot = MomentLlmSlot()
        slot.tryAcquire()
        slot.tryAcquire()
        slot.release()
        slot.release()
        // 两个槽都释放后，又能连取 2 个。
        assertTrue(slot.tryAcquire())
        assertTrue(slot.tryAcquire())
        assertFalse(slot.tryAcquire())
    }

    // ── 朋友圈发布页重构·甲 T1-3（§3.3.4·E8）：等槽 = 先试一次，拿不到每 2 秒再试，共 31 次（约 60 秒）──

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime 读虚拟时钟
    @Test
    fun `acquireWaiting gives up after 31 tries spanning exactly 60 seconds`() = runTest {
        val slot = MomentLlmSlot()
        slot.tryAcquire()
        slot.tryAcquire()
        val start = testScheduler.currentTime
        assertFalse(slot.acquireWaiting(31, 2_000))
        assertEquals("31 次尝试之间 30 个间隔 × 2 秒", 60_000L, testScheduler.currentTime - start)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime 读虚拟时钟
    @Test
    fun `acquireWaiting succeeds once a slot frees up mid-wait`() = runTest {
        val slot = MomentLlmSlot()
        slot.tryAcquire()
        slot.tryAcquire()
        val start = testScheduler.currentTime
        launch {
            delay(10_000)
            slot.release()
        }
        assertTrue(slot.acquireWaiting(31, 2_000))
        assertTrue("释放后下一次轮询（≤ 2 秒）即拿到", testScheduler.currentTime - start <= 12_000L)
        assertFalse("拿到后两个槽又都占满", slot.tryAcquire())
    }
}
