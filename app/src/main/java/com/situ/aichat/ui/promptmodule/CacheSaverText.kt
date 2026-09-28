package com.situ.aichat.ui.promptmodule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 省钱卡的两段文字拼装（时间感知四期·图纸二 §4.4·两张脸共用·拼装顺序锁定）。
 */

/** 命中行：`NoRecords` → 「还没有对话记录」；`Rate` → 「最近 N 次对话 · 缓存命中」+ 空格 + 数值段（百分比或 —，status.onSuccess·W700）。 */
@Composable
internal fun recentLineText(recent: RecentCacheLine): AnnotatedString = when (recent) {
    RecentCacheLine.NoRecords -> AnnotatedString(stringResource(R.string.pm_saver_recent_none))
    is RecentCacheLine.Rate -> {
        val label = stringResource(R.string.pm_saver_recent_label, recent.count)
        val value = recent.percent?.let { stringResource(R.string.pm_saver_recent_percent, it) }
            ?: stringResource(R.string.pm_saver_recent_unknown)
        val valueStyle = SpanStyle(color = AppTheme.colors.status.onSuccess, fontWeight = FontWeight.W700)
        buildAnnotatedString {
            append(label)
            append(" ")
            withStyle(valueStyle) { append(value) }
        }
    }
}

/** 说明：`1a + [1b] + 1c + "\n" + [2b] + 2c + "\n" + [3b] + 3c`，方括号段 text.primary·W600。 */
@Composable
internal fun saverNoteText(): AnnotatedString {
    val strong = SpanStyle(color = AppTheme.colors.text.primary, fontWeight = FontWeight.W600)
    val n1a = stringResource(R.string.pm_saver_note_1a)
    val n1b = stringResource(R.string.pm_saver_note_1b)
    val n1c = stringResource(R.string.pm_saver_note_1c)
    val n2b = stringResource(R.string.pm_saver_note_2b)
    val n2c = stringResource(R.string.pm_saver_note_2c)
    val n3b = stringResource(R.string.pm_saver_note_3b)
    val n3c = stringResource(R.string.pm_saver_note_3c)
    return buildAnnotatedString {
        append(n1a)
        withStyle(strong) { append(n1b) }
        append(n1c)
        append("\n")
        withStyle(strong) { append(n2b) }
        append(n2c)
        append("\n")
        withStyle(strong) { append(n3b) }
        append(n3c)
    }
}
