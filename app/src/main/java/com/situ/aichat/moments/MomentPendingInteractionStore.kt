package com.situ.aichat.moments

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * "Pending interaction" queue for asleep characters — 1:1 iOS `MomentGenerationService`
 * `PendingInteraction` + `addPendingInteraction`/`loadPendingInteractions`/`savePendingInteractions`
 * (Services/MomentGenerationService+Interaction.swift:95-140).
 *
 * When `autoInteractWithPost` finds a candidate character asleep (schedule system on), it enqueues the
 * interaction here instead of dropping it; the queue is drained the next time the app comes forward and
 * the character is awake (`processPendingInteractions`, wired in 7.2.5). Persisted as a JSON list in
 * SharedPreferences (iOS uses a `UserDefaults` JSON blob).
 *
 * **Android improvement over iOS:** iOS stores the post's timestamp + author and re-finds the post by a
 * ±0.5s timestamp window at drain time; here we keep the post's stable [PendingInteraction.postUuid]
 * (we hold it at enqueue), so the drain looks the post up exactly — and dedup is on
 * `(postUuid, characterUuid)` rather than a float window. The timestamp/author are kept too for parity
 * and for locating the post if it was regenerated.
 */
object MomentPendingInteractionStore {
    private const val PREFS = "moment_pending_interactions"
    private const val KEY_QUEUE = "MomentPendingInteractions"
    private const val TAG = "MomentPending"

    private val json = Json { ignoreUnknownKeys = true }

    /** 读—改—写互斥（乙 L-3）：[add] 与 [replaceKeepingNewcomers] 同锁，进程内原子。 */
    private val lock = Any()

    /** One queued interaction (iOS `PendingInteraction`, Codable). */
    @Serializable
    data class PendingInteraction(
        val postUuid: String,
        val postTimestampMillis: Long,
        val postAuthorUuid: String?,
        val characterUuid: String,
        val queuedAtMillis: Long,
        /** 被提醒项累计失败次数（乙 L-2）；非提醒项恒 0。满上限 = 已放弃：留队占位不再试，24h 过期出队（复核 R1）。 */
        val failedAttempts: Int = 0,
        /** 被提醒项上一次尝试的时刻（乙 L-2）；0 = 从没试过。 */
        val lastAttemptAtMillis: Long = 0L,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Enqueue an interaction for an asleep character (iOS `addPendingInteraction`). Deduped on
     * `(postUuid, characterUuid)` — re-queuing the same pairing is a no-op.
     */
    fun add(
        context: Context,
        postUuid: String,
        postTimestampMillis: Long,
        postAuthorUuid: String?,
        characterUuid: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        synchronized(lock) {
            val queue = load(context)
            if (queue.any { it.postUuid == postUuid && it.characterUuid == characterUuid }) return
            save(
                context,
                queue + PendingInteraction(
                    postUuid = postUuid,
                    postTimestampMillis = postTimestampMillis,
                    postAuthorUuid = postAuthorUuid,
                    characterUuid = characterUuid,
                    queuedAtMillis = nowMillis,
                ),
            )
        }
    }

    /**
     * drain 回写（乙 L-3）：以 [kept] 为准，并保留 [snapshot] 之后别处新入队的条目（按 (帖, 角色) 判新），在同一把锁里读—合并—写。
     */
    fun replaceKeepingNewcomers(context: Context, snapshot: List<PendingInteraction>, kept: List<PendingInteraction>) {
        synchronized(lock) {
            val seen = snapshot.mapTo(HashSet()) { it.postUuid to it.characterUuid }
            val newcomers = load(context).filter { (it.postUuid to it.characterUuid) !in seen }
            save(context, kept + newcomers)
        }
    }

    /** Load the queue (empty on missing / corrupt, like iOS's `?? []`). */
    fun load(context: Context): List<PendingInteraction> {
        val raw = prefs(context).getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            Log.w(TAG, "pending-interaction queue decode failed; resetting", e)
            emptyList()
        }
    }

    /** Persist the queue; an empty queue removes the key (iOS removes the UserDefaults object). */
    fun save(context: Context, queue: List<PendingInteraction>) {
        val editor = prefs(context).edit()
        if (queue.isEmpty()) editor.remove(KEY_QUEUE)
        else editor.putString(KEY_QUEUE, json.encodeToString(queue))
        editor.apply()
    }
}
