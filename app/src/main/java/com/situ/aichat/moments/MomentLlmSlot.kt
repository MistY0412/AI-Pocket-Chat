package com.situ.aichat.moments

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 1:1 port of iOS `MomentGenerationService.interactionSemaphore` (+ `tryAcquireLLMSlot` /
 * `releaseLLMSlot`, Services/MomentGenerationService+Interaction.swift:20-37).
 *
 * Caps concurrent **LLM calls** during 朋友圈 auto-interaction at [MAX_CONCURRENT] = 2, so a burst of
 * posts can't fan out into an API-request storm. Crucially it guards only the LLM call (not the whole
 * interaction pass, which can run 10+ minutes) — so a user rapidly posting a 3rd moment isn't rejected
 * while two comment chains sleep.
 *
 * A global app-wide singleton shared across every interaction coroutine. Implemented as an
 * `AtomicInteger` CAS counter (not a kotlinx `Semaphore`) to mirror iOS's `OSAllocatedUnfairLock`
 * counter exactly, including the `count > 0` release guard that makes a stray double-release a no-op
 * rather than a crash.
 */
@Singleton
class MomentLlmSlot @Inject constructor() {

    private val count = AtomicInteger(0)

    /** Try to take a slot; returns false when [MAX_CONCURRENT] are already in use (caller skips). */
    fun tryAcquire(): Boolean {
        while (true) {
            val current = count.get()
            if (current >= MAX_CONCURRENT) return false
            if (count.compareAndSet(current, current + 1)) return true
        }
    }

    /**
     * 等槽（被提醒者的评论保底用·J-3）：先试一次，拿不到每 [pollMs] 再试，共试 [maxAttempts] 次；可取消。
     * 拿到返回 true（调用方负责 [release]）；试满仍拿不到返回 false。
     */
    suspend fun acquireWaiting(maxAttempts: Int, pollMs: Long): Boolean {
        repeat(maxAttempts) { attempt ->
            if (tryAcquire()) return true
            if (attempt < maxAttempts - 1) delay(pollMs)
        }
        return false
    }

    /** Release a slot. Guarded so an over-release (future double-call) silently no-ops, never < 0. */
    fun release() {
        while (true) {
            val current = count.get()
            if (current <= 0) return
            if (count.compareAndSet(current, current - 1)) return
        }
    }

    private companion object {
        const val MAX_CONCURRENT = 2
    }
}
