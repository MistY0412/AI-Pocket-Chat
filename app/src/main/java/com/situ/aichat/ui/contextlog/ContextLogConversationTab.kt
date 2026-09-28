package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.components.CharacterAvatar
import com.situ.aichat.ui.contextlog.model.LogCharacterRowModel
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogTodayStats
import com.situ.aichat.ui.contextlog.shared.characterMetaText
import com.situ.aichat.ui.contextlog.shared.chineseUnits
import com.situ.aichat.ui.contextlog.shared.systemMetaText
import com.situ.aichat.ui.designsystem.AppListDivider
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme

/** 「按对话」分段（四期·图纸四 §4.1a·暖陶）：今天四格 → 告警条 → 角色行 → 系统任务行。 */
internal fun LazyListScope.conversationTabItems(
    state: ContextLogHomeUiState,
    onShowFailures: () -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val tab = state.conversation
    if (tab.empty) {
        item(key = "conv-empty") {
            Text(
                stringResource(R.string.clog_home_empty),
                style = AppTheme.typography.secondary,
                color = AppTheme.colors.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.hero),
            )
        }
        return
    }
    tab.today?.let { today ->
        item(key = "conv-today-label") { LogGroupLabel(stringResource(R.string.clog_section_today)) }
        item(key = "conv-today") { TodayStats(today) }
    }
    if (state.conversationAlerts.isNotEmpty()) {
        item(key = "conv-alerts") { LogAlertNotice(state.conversationAlerts, state.alertKinds, onShowFailures) }
    }
    if (tab.characters.isNotEmpty()) {
        item(key = "conv-chars-label") { LogGroupLabel(stringResource(R.string.clog_section_characters)) }
        item(key = "conv-chars") {
            LogCard(contentPadding = PaddingValues(0.dp)) {
                tab.characters.forEachIndexed { i, row ->
                    if (i > 0) AppListDivider(startInset = ROW_DIVIDER_INSET)
                    CharacterListRow(row, isSystem = false, onClick = { onOpenCharacter(row.key) })
                }
            }
        }
    }
    tab.system?.let { system ->
        item(key = "conv-system-label") { LogGroupLabel(stringResource(R.string.clog_section_system)) }
        item(key = "conv-system") {
            LogCard(contentPadding = PaddingValues(0.dp)) {
                CharacterListRow(system, isSystem = true, onClick = { onOpenCharacter(system.key) })
            }
        }
    }
}

/** 行间分隔线起点 = 行内距 16 + 头像 40 + 间距 12。 */
private val ROW_DIVIDER_INSET = 68.dp

@Composable
private fun TodayStats(today: LogTodayStats) {
    val rate = today.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_rate_none)
    LogStatRow(
        items = listOf(
            today.calls.toString() to stringResource(R.string.clog_stat_calls),
            today.failures.toString() to stringResource(R.string.clog_stat_failures),
            rate to stringResource(R.string.clog_stat_cache),
            LogFormat.bigTokens(today.totalTokens, chineseUnits()) to stringResource(R.string.clog_stat_tokens),
        ),
        errorIndex = 1,
    )
}

/** 角色行 / 系统任务行（§4.0 列表行·暖陶）：头像 40 + 名字 / 元信息 + 右列（命中率 + 失败药丸或「缓存」）+ 箭头。 */
@Composable
private fun CharacterListRow(row: LogCharacterRowModel, isSystem: Boolean, onClick: () -> Unit) {
    val name = if (isSystem) stringResource(R.string.clog_system_tasks) else row.name
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .padding(horizontal = AppSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSystem) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AppTheme.colors.surface.sunken), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = AppTheme.colors.text.secondary, modifier = Modifier.size(20.dp))
            }
        } else {
            CharacterAvatar(name = row.name, avatarPath = row.avatarPath, size = 40.dp)
        }
        Spacer(Modifier.width(AppSpacing.m))
        Column(Modifier.weight(1f)) {
            Text(name, style = AppTheme.typography.settingsRowTitle, color = AppTheme.colors.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isSystem) systemMetaText(row) else characterMetaText(row),
                style = AppTheme.typography.caption,
                color = AppTheme.colors.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpacing.s))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_rate_none),
                style = AppTheme.typography.settingsRowTitle.copy(fontFeatureSettings = "tnum"),
                color = AppTheme.colors.text.primary,
            )
            if (row.failures > 0) {
                LogPill(stringResource(R.string.clog_fail_pill, row.failures), LogPillTone.ERROR)
            } else {
                Text(stringResource(R.string.clog_cache_caption), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
            }
        }
        Spacer(Modifier.width(AppSpacing.xs))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.text.secondary, modifier = Modifier.size(18.dp))
    }
}
