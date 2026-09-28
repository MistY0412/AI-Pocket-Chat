package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogSource
import com.situ.aichat.ui.designsystem.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * D-3 上下文日志 UI 共享件 + 取值口径（状态圈 / 时间·耗时格式 / 主名与来源徽标判定）。
 * 颜色全经 [AppTheme] semantic/feature token（陶土 accent·status 双档·economy.gold），不直引 Palette。
 */

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** 列表用短时间「14:32」。 */
fun formatLogTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

/** 耗时「2.4s」；null → null（不显）。 */
fun formatDuration(millis: Long?): String? =
    millis?.let { String.format(Locale.ROOT, "%.1fs", it / 1000.0) }

/** 卡片主名：有角色名显角色名，取不到（用户级/故事等）回退显来源（spec §3.8 坑）。 */
fun primaryName(entry: LogListRow): String =
    entry.characterName.ifBlank { entry.source }

/** 来源徽标只在非「对话」时显（1:1 iOS：对话是默认场景不挂徽标）。 */
fun showSourceBadge(entry: LogListRow): Boolean = entry.source != LogSource.CHAT

/** 角色名为空时主名已回退显来源 → 不再重复显徽标（避免「故事生成 · 故事生成」）。 */
fun showSourceBadgeResolved(entry: LogListRow): Boolean =
    showSourceBadge(entry) && entry.characterName.isNotBlank()

/** 成功/失败状态圈（成功=success 双档 ✓、失败=error 双档 ✕；26dp 软圆角圆，Fable-5 无锐角）。 */
@Composable
fun StatusGlyph(isSuccess: Boolean, modifier: Modifier = Modifier) {
    val container = if (isSuccess) AppTheme.colors.status.successContainer else AppTheme.colors.status.errorContainer
    val on = if (isSuccess) AppTheme.colors.status.onSuccess else AppTheme.colors.status.onError
    Box(
        modifier = modifier
            .size(26.dp)
            .background(container, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = on,
            modifier = Modifier.size(15.dp),
        )
    }
}
