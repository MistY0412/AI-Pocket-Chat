package com.situ.aichat.ui.moments

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.DiaryEntryEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.MomentAuthorType

// 动态枢纽卡片的预览文字（琉璃 2.0 卷六·一 §3.8：暖陶 / 琉璃两份逐字副本收成一份·两张脸共用）。

/** 动态枢纽预览截取字数（两张脸同值·原暖陶字面 30 / 琉璃 `PREVIEW_CHARS`）。 */
internal const val MOMENTS_HUB_PREVIEW_CHARS = 30

/** 一段正文的预览：前 30 字、换行变空格（纯函数·T1）。 */
internal fun momentsHubPostPreview(content: String): String =
    content.take(MOMENTS_HUB_PREVIEW_CHARS).replace("\n", " ")

/** 动态条预览作者名（本地化「我」/角色名/「AI」回落·取法同旧 Hero 预览·§4.5）。（原暖陶 `previewAuthor` 逐字·改名） */
internal fun momentsHubPreviewAuthor(
    post: MomentPostEntity,
    charactersByUuid: Map<String, CharacterEntity>,
    meLabel: String,
    aiLabel: String,
): String = when (MomentAuthorType.fromRaw(post.authorTypeRaw)) {
    MomentAuthorType.USER -> meLabel
    MomentAuthorType.CHARACTER -> post.characterUuid?.let { charactersByUuid[it]?.name } ?: aiLabel
}

/** 日记卡 body 的核心（纯函数·T1）：「心情 emoji + 正文前 30」；null = 该用默认描述。 */
internal fun momentsHubDiaryPreview(entry: DiaryEntryEntity?): String? {
    val e = entry ?: return null
    val body = momentsHubPostPreview(e.content)
    if (body.isBlank()) return null
    val emoji = e.moodEmoji
    return if (!emoji.isNullOrEmpty()) "$emoji $body" else body
}

/** 日记卡 body：最新一篇非草稿日记的「心情 emoji + 正文前30」；无则默认描述。 */
@Composable
internal fun momentsHubDiaryPreviewText(state: MomentsHubState): String =
    momentsHubDiaryPreview(state.latestDiary) ?: stringResource(R.string.moment_hub_diary_desc)

/** 故事卡 body：连载进度（第N章·标题 / 尚未生成 / 无故事兜底·原暖陶 `storyPreviewText` 逐字）。 */
@Composable
internal fun momentsHubStoryPreviewText(state: MomentsHubState): String = when (val s = storyHubStatus(state.latestStory)) {
    is StoryHubStatus.Chapter -> stringResource(R.string.story_hub_chapter, s.number, s.title)
    StoryHubStatus.NoChapter -> stringResource(R.string.story_hub_no_chapter)
    StoryHubStatus.None -> stringResource(R.string.moment_hub_story_desc)
}
