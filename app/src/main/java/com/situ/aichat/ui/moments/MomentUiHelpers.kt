package com.situ.aichat.ui.moments

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.util.DateFormatters

/**
 * 朋友圈作者显示名（对齐 iOS 各 `authorName`/`commentAuthorName`）：用户固定 [meLabel]（iOS
 * `String(localized:"Me")`），角色取名字、查不到回落 [aiLabel]。卡片 / 详情 / 通知列表共用，避免重复。
 */
internal fun momentAuthorName(
    authorTypeRaw: String,
    characterUuid: String?,
    characterDict: Map<String, CharacterEntity>,
    meLabel: String,
    aiLabel: String,
): String = when (MomentAuthorType.fromRaw(authorTypeRaw)) {
    MomentAuthorType.USER -> meLabel
    MomentAuthorType.CHARACTER -> characterUuid?.let { characterDict[it]?.name } ?: aiLabel
}

/** 用户显示名（原 MomentDetailScreen :98 / ComposeMomentScreen :122 同式·纯·T1）：昵称为空 → [meLabel]。 */
internal fun momentUserName(userProfile: UserProfileEntity?, meLabel: String): String =
    userProfile?.nickname?.ifBlank { null } ?: meLabel

/** 相对时间四串（原 MomentDetailScreen :91–96 / MomentNotificationListScreen :78–83 逐字同·合一）。 */
@Composable
internal fun momentRelativeTimeStrings(): DateFormatters.RelativeTimeStrings = DateFormatters.RelativeTimeStrings(
    justNow = stringResource(R.string.relative_time_just_now),
    minutesAgo = stringResource(R.string.relative_time_minutes_ago),
    hoursAgo = stringResource(R.string.relative_time_hours_ago),
    yesterday = stringResource(R.string.relative_time_yesterday),
)
