package com.situ.aichat.ui.liuli.contextlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.diagnostics.FailureRateAlert
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.ui.components.CharacterAvatar
import com.situ.aichat.ui.contextlog.ContextLogHomeUiState
import com.situ.aichat.ui.contextlog.LogNoticeTone
import com.situ.aichat.ui.contextlog.LogPillTone
import com.situ.aichat.ui.contextlog.model.LogCharacterRowModel
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogTodayStats
import com.situ.aichat.ui.contextlog.shared.alertLineTexts
import com.situ.aichat.ui.contextlog.shared.characterMetaText
import com.situ.aichat.ui.contextlog.shared.chineseUnits
import com.situ.aichat.ui.contextlog.shared.systemMetaText
import com.situ.aichat.ui.designsystem.AppProfileIcons
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliAvatarRing
import com.situ.aichat.ui.liuli.designsystem.LiuliTileTone
import com.situ.aichat.ui.liuli.home.LiuliHomeGeometry
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliGroupHeader
import com.situ.aichat.ui.liuli.page.LiuliGroupIconTile
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliStatCard

/** 「按对话」分段（四期·图纸四 §4.1a·琉璃）：内容 / 顺序同暖陶——今天四格 → 告警条 → 角色组 → 系统任务组。 */
internal fun LazyListScope.liuliConversationTabItems(
    state: ContextLogHomeUiState,
    onShowFailures: () -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val tab = state.conversation
    if (tab.empty) {
        item(key = "conv-empty") { LiuliLogEmpty(stringResource(R.string.clog_home_empty)) }
        return
    }
    tab.today?.let { today ->
        item(key = "conv-today") {
            Column(Modifier.padding(horizontal = LiuliPageGeometry.gutter)) {
                LiuliGroupHeader(stringResource(R.string.clog_section_today))
                LiuliTodayStats(today)
            }
        }
    }
    if (state.conversationAlerts.isNotEmpty()) {
        item(key = "conv-alerts") { LiuliLogAlertNotice(state.conversationAlerts, state.alertKinds, onShowFailures) }
    }
    if (tab.characters.isNotEmpty()) {
        item(key = "conv-chars") {
            LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter), header = stringResource(R.string.clog_section_characters)) {
                tab.characters.forEachIndexed { i, row -> LiuliCharacterRow(row, isSystem = false, first = i == 0) { onOpenCharacter(row.key) } }
            }
        }
    }
    tab.system?.let { system ->
        item(key = "conv-system") {
            LiuliGroup(Modifier.padding(horizontal = LiuliPageGeometry.gutter), header = stringResource(R.string.clog_section_system)) {
                LiuliCharacterRow(system, isSystem = true, first = true) { onOpenCharacter(system.key) }
            }
        }
    }
}

/** 空态：居中次要字，上留 32。 */
@Composable
internal fun LiuliLogEmpty(text: String) {
    Text(
        text, style = AppTypography.secondary, color = AppTheme.colors.text.secondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = LiuliPageGeometry.gutter).padding(top = AppSpacing.hero),
    )
}

@Composable
private fun LiuliTodayStats(today: LogTodayStats) {
    LiuliStatCard(
        listOf(
            stringResource(R.string.clog_stat_calls) to today.calls.toString(),
            stringResource(R.string.clog_stat_failures) to today.failures.toString(),
            stringResource(R.string.clog_stat_cache) to (today.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_rate_none)),
            stringResource(R.string.clog_stat_tokens) to LogFormat.bigTokens(today.totalTokens, chineseUnits()),
        ),
    )
}

/** 告警条（琉璃壳·文字与暖陶同一件 [alertLineTexts]）：整条一个语义节点，点 = 去「失败」筛选。 */
@Composable
internal fun LiuliLogAlertNotice(alerts: List<FailureRateAlert>, kinds: Map<String, LlmFailureKind>, onShowFailed: () -> Unit) {
    val title = stringResource(R.string.contextlog_alert_title)
    val lines = alertLineTexts(alerts, kinds)
    val action = stringResource(R.string.contextlog_alert_action)
    val fg = AppTheme.colors.status.onWarning
    LiuliLogNotice(
        LogNoticeTone.WARNING,
        Modifier.clickable(onClickLabel = action, onClick = onShowFailed)
            .semantics(mergeDescendants = true) { contentDescription = "$title：${lines.joinToString("；")}。$action" },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppTypography.secondary.copy(fontWeight = FontWeight.SemiBold), color = fg)
                Spacer(Modifier.height(3.dp))
                lines.forEach { Text(it, style = AppTypography.caption, color = fg.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(9.dp))
            Text("$action ›", style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/** 角色行 / 系统任务行（§4.0 列表行·琉璃）：头像环 54 / 齿轮方块 + 名字 / 元信息 + 右列 + chevron。 */
@Composable
private fun LiuliCharacterRow(row: LogCharacterRowModel, isSystem: Boolean, first: Boolean, onClick: () -> Unit) {
    val name = if (isSystem) stringResource(R.string.clog_system_tasks) else row.name
    LiuliRowBase(
        onClick = onClick, onClickLabel = name, minHeight = LiuliPageGeometry.rowTwoLine,
        // 分隔线起点对齐名字（复核 R1：头像环 54 比设置瓦片宽，按瓦片算的起点会压在头像底下）
        divider = !first, dividerInset = if (isSystem) LiuliPageGeometry.dividerInsetTile else LiuliHomeGeometry.dividerInset,
    ) {
        if (isSystem) {
            LiuliGroupIconTile(Icons.Outlined.Settings, LiuliTileTone.Peach)
        } else {
            LiuliAvatarRing(LiuliHomeGeometry.rowAvatar) { CharacterAvatar(row.name, row.avatarPath, LiuliHomeGeometry.rowAvatarInner) }
        }
        Spacer(Modifier.width(LiuliPageGeometry.tileGap))
        Column(Modifier.weight(1f)) {
            Text(name, style = AppTypography.settingsRowTitle, color = AppTheme.colors.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isSystem) systemMetaText(row) else characterMetaText(row),
                style = AppTypography.caption, color = AppTheme.colors.text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpacing.s))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.cacheRatePercent?.let { "$it%" } ?: stringResource(R.string.clog_rate_none),
                style = AppTypography.settingsRowTitle.copy(fontFeatureSettings = "tnum"), color = AppTheme.colors.text.primary,
            )
            if (row.failures > 0) {
                LiuliLogPill(stringResource(R.string.clog_fail_pill, row.failures), LogPillTone.ERROR)
            } else {
                Text(stringResource(R.string.clog_cache_caption), style = AppTypography.caption, color = AppTheme.colors.text.secondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(AppProfileIcons.ChevronRight, contentDescription = null, tint = AppTheme.colors.text.tertiary, modifier = Modifier.size(12.dp))
    }
}
