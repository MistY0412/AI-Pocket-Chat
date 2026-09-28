package com.situ.aichat.ui.moments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 圈子卡片 / 详情页「@ 提醒了 小满、阿澈」一行（朋友圈发布页·乙 §4.12）：14dp「@」+ 前缀 secondary、名字深陶 520。
 * 调用方只在名字非空时渲染（没提醒的帖外观零变化·K-4）；颜色读 [AppTheme.colors]，两张脸自动切换。
 */
@Composable
internal fun MomentMentionLine(names: List<String>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(AppMomentIcons.At, contentDescription = null, tint = colors.accent.text, modifier = Modifier.size(14.dp))
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.moment_mention_line_prefix))
                append(" ")
                withStyle(SpanStyle(color = colors.accent.text, fontWeight = FontWeight(520))) {
                    append(names.joinToString(stringResource(R.string.moment_mention_separator)))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.text.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
