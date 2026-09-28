package com.situ.aichat.moments

import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** T2-2（朋友圈发布页·乙 §7·E28）：前台一轮 = 先处理待互动队列，再补丢失的互动。 */
class MomentForegroundPassTest {

    @Test
    fun 先drain后恢复() = runTest {
        val interaction = mockk<MomentInteractionService>(relaxed = true)
        val recovery = mockk<MomentRecoveryService>(relaxed = true)

        runMomentForegroundPass(interaction, recovery)

        coVerifyOrder {
            interaction.processPendingInteractions(any(), any())
            recovery.recoverIfNeeded(any())
        }
    }
}
