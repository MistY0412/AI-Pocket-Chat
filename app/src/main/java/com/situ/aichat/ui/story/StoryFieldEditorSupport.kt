package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R

/** 路由参数不认识（老链接 / 脏参数）→ 不渲染半截页，直接退出（原 StoryFieldEditorScreen :80–81 逐字）。 */
@Composable
internal fun StoryFieldEditorInvalidEffect(invalid: Boolean, onBack: () -> Unit) {
    // 路由参数不认识（老链接 / 脏参数）→ 不渲染半截页，直接退出。
    LaunchedEffect(invalid) { if (invalid) onBack() }
}

/** 返回要不要先问（原 leave() 判据·纯·T1）：有未保存改动才问。 */
internal fun storyFieldEditorNeedsConfirm(state: StoryFieldEditorState?): Boolean = state?.dirty == true

/** 内容区首行副标题（原 :113–117 取值式 + 空白守卫）：本书字段 = 「本书」、档案字段 = 书名、全局 = 无。 */
@Composable
internal fun storyFieldEditorSubtitle(state: StoryFieldEditorState): String? = when {
    state.field == null -> null
    state.isArchive -> state.bookTitle
    else -> stringResource(R.string.story_field_editor_sub_book)
}?.takeIf { it.isNotBlank() }

/** 三态段的文案（原 ModeSegment 的 `when`·纯·T1）。 */
@StringRes
internal fun storyFieldModeLabelRes(mode: StoryFieldMode): Int = when (mode) {
    StoryFieldMode.FOLLOW -> R.string.story_field_mode_follow
    StoryFieldMode.CUSTOM -> R.string.story_field_mode_custom
    StoryFieldMode.OFF -> R.string.story_field_mode_off
}
