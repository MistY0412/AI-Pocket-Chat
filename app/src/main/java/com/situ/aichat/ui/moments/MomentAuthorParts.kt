package com.situ.aichat.ui.moments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.ui.components.CharacterAvatar
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.appCardSurface
import com.situ.aichat.ui.ourdays.OurDaysFormat
import java.time.LocalDate

/** 作者页要显示的名字 / 头像（琉璃 2.0 卷六·二：两张脸共用）。 */
internal data class MomentAuthorHeading(val name: String, val avatarPath: String?)

/** 原 MomentAuthorScreen :68–69 两式逐字（纯函数·T1）：用户 = 昵称（空 → 「我」）+ 用户头像；角色 = 名字（查不到 → AI）+ 角色头像。 */
internal fun momentAuthorHeading(
    isUser: Boolean,
    character: CharacterEntity?,
    userProfile: UserProfileEntity?,
    meLabel: String,
    aiLabel: String,
): MomentAuthorHeading = MomentAuthorHeading(
    name = if (isUser) (userProfile?.nickname?.ifBlank { null } ?: meLabel) else (character?.name ?: aiLabel),
    avatarPath = if (isUser) userProfile?.avatarPath else character?.avatarPath,
)

/** 那一天页的标题（原 DayMomentsScreen :63–67 逐字）：「M 月 d 日 · 朋友圈」；日期解析失败 → 「动态」。 */
@Composable
internal fun momentDayTitle(date: LocalDate?): String = if (date != null) {
    stringResource(R.string.moment_day_title, OurDaysFormat.date(date, stringResource(R.string.our_days_fmt_md)))
} else {
    stringResource(R.string.tab_moments)
}

@Composable
internal fun MomentAuthorHeader(
    name: String,
    avatarPath: String?,
    postCount: Int,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    avatarFrame: @Composable (avatar: @Composable () -> Unit) -> Unit = { avatar ->
        Box(
            modifier = Modifier.size(68.dp).appCardSurface(cornerRadius = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            avatar()
        }
    },
) {
    // 静默头（契约 §2.3·D1 拍板 2026-07-13）：原 120dp 渐变染色 banner 整体删除（连同硬编码橙/蓝与彩色投影），
    // 头像+文字直接坐在 base+grain 上；器物感 = 白瓷描边圈——68dp appCardSurface 圆环（raised 白底 + rest 软影
    // + 发丝线）内衬 64dp 头像，即 2dp 白瓷边（Hub 头像墙同笔法·升软影版）。用户页与角色页同一规格。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        avatarFrame { CharacterAvatar(name = name, avatarPath = avatarPath, size = 64.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.text.primary)
            Text(
                stringResource(R.string.moment_author_posts_count, postCount),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.text.secondary,
            )
        }
    }
}

@Composable
internal fun MomentAuthorEmptyState(isUser: Boolean) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 空态去裸 emoji（契约 §1-3）：自绘评论泡，tertiary 装饰档。
        Icon(AppMomentIcons.CommentBubble, contentDescription = null, tint = colors.text.tertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.moment_author_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.text.primary)
        Text(
            stringResource(if (isUser) R.string.moment_user_empty_desc else R.string.moment_character_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 分节标签（§4.3·样式逐字取自日页「这一天」F18）。 */
@Composable
internal fun DayMomentsGroupLabel(text: String) {
    Text(
        text,
        style = AppTypography.caption.copy(fontSize = 12.sp, letterSpacing = 1.5.sp),
        color = AppTheme.colors.text.tertiary,
        // 22 = 卡片 gutter 20 + 内缩 2（与日页「这一天」标签的 start = 2 内缩同口径）。
        modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 6.dp),
    )
}

/** 空态（§4.5·结构照 `MomentAuthorEmptyState` 逐项）。 */
@Composable
internal fun DayMomentsEmptyState() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppMomentIcons.CommentBubble, contentDescription = null, tint = colors.text.tertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.moment_day_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.text.primary)
        Text(
            stringResource(R.string.moment_day_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text.secondary,
            textAlign = TextAlign.Center,
        )
    }
}
