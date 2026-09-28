package com.situ.aichat.ui.character

import androidx.annotation.StringRes
import com.situ.aichat.R
import com.situ.aichat.data.local.dao.WorldBookSummary

/** 世界段「加入世界」副标（四态·W13 §4.1）。 */
@StringRes internal fun worldJoinSubtitleRes(worldbookBound: Boolean, nativeOrigin: Boolean, joined: Boolean): Int = when {
    worldbookBound -> R.string.char_world_join_sub_wb
    nativeOrigin -> R.string.char_world_join_sub_native
    joined -> R.string.char_world_join_sub_on
    else -> R.string.char_world_join_sub_off
}

/** 世界段脚注（四态）。 */
@StringRes internal fun worldFooterRes(worldbookBound: Boolean, nativeOrigin: Boolean, joined: Boolean): Int = when {
    worldbookBound -> R.string.char_world_foot_wb
    nativeOrigin -> R.string.char_world_foot_native
    joined -> R.string.char_world_foot_on
    else -> R.string.char_world_foot_off
}

/** 住址行尾巴：原住民 / 和你同城 / 异地。 */
@StringRes internal fun worldAddressTailRes(nativeOrigin: Boolean, sameCityAsUser: Boolean): Int = when {
    nativeOrigin -> R.string.char_world_addr_native
    sameCityAsUser -> R.string.char_world_addr_same_city
    else -> R.string.char_world_addr_remote
}

/** 世界观入口卡的五态（复核 R1 🟡-2③ 优先级：原住民 → 已加入 → 未绑 → 单本 → 多本）。 */
internal sealed interface WorldBookCardState {
    data object NativeLocked : WorldBookCardState
    data object WorldLocked : WorldBookCardState
    data object None : WorldBookCardState
    data class Single(val name: String, val entryCount: Int) : WorldBookCardState
    data class Multi(val firstName: String, val extra: Int) : WorldBookCardState
}

internal fun worldBookCardState(nativeOrigin: Boolean, joinedWorld: Boolean, bound: List<WorldBookSummary>): WorldBookCardState = when {
    nativeOrigin -> WorldBookCardState.NativeLocked
    joinedWorld -> WorldBookCardState.WorldLocked
    bound.isEmpty() -> WorldBookCardState.None
    bound.size == 1 -> WorldBookCardState.Single(bound.first().book.name, bound.first().entryCount)
    else -> WorldBookCardState.Multi(bound.first().book.name, bound.size - 1)
}
