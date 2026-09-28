package com.situ.aichat.moments

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 发布页草稿（一份）：正文 / 图（磁盘路径·用户排好的顺序）/ 提醒了谁（点选顺序）。 */
@Serializable
data class MomentComposeDraft(
    val content: String = "",
    val images: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
) {
    /** 空 = 正文空白且无图无提醒——空草稿不存。 */
    val isEmpty: Boolean get() = content.isBlank() && images.isEmpty() && mentions.isEmpty()
}

/**
 * 发布页草稿单份存取（朋友圈发布页重构·甲 J-7·图纸 docs/handoff/2026-09-27-朋友圈发布页-甲-底子.md §3.5）：
 * SharedPreferences JSON，范式同 [MomentPendingInteractionStore]。只管存取不管图片文件（图片归属由发布页 VM 管）。
 */
object MomentComposeDraftStore {
    private const val PREFS = "moment_compose_draft"
    private const val KEY_DRAFT = "draft"
    private const val TAG = "MomentDraft"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读草稿；没有 / 坏数据 / 空草稿 → null（坏数据记一条 warn）。 */
    fun load(context: Context): MomentComposeDraft? {
        val raw = prefs(context).getString(KEY_DRAFT, null) ?: return null
        val draft = try {
            json.decodeFromString<MomentComposeDraft>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "compose draft decode failed; ignoring", e)
            return null
        }
        return draft.takeUnless { it.isEmpty }
    }

    /** 存草稿；空草稿 → 等同 [clear]。 */
    fun save(context: Context, draft: MomentComposeDraft) {
        if (draft.isEmpty) {
            clear(context)
            return
        }
        prefs(context).edit().putString(KEY_DRAFT, json.encodeToString(draft)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_DRAFT).apply()
    }
}
