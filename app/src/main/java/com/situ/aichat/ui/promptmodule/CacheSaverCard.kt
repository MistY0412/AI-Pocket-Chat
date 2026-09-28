package com.situ.aichat.ui.promptmodule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.SettingsSwitchRow
import com.situ.aichat.ui.designsystem.AppListDivider
import com.situ.aichat.ui.designsystem.AppSaverIcons
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.appCardSurface

/**
 * 暖陶省钱卡（时间感知四期·图纸二 §4.1·效果图 phase4_log_and_saver_mockup 第一节·09-26 过审）：
 * 开关行（¥ 瓦片）→ 灰字（当前服务商不会省钱·开关开关都出）→ 命中行（开着才出·点进日志）→ 说明（恒显示）。
 * 只在全局提示词模块页出现（角色专属页不出·[CacheSaverCardState.visible]）。本卡无自有动画，开关动画与 reduceMotion 由零件处理。
 */
@Composable
internal fun CacheSaverCard(
    state: CacheSaverCardState,
    onToggle: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val haptics = LocalAppHaptics.current
    val openLogA11y = stringResource(R.string.pm_saver_open_log_a11y)
    Column(modifier.fillMaxWidth().appCardSurface()) {
        SettingsSwitchRow(
            title = stringResource(R.string.pm_saver_title),
            checked = state.enabled,
            onCheckedChange = onToggle,
            subtitle = stringResource(R.string.pm_saver_subtitle),
            icon = AppSaverIcons.Yen,
        )
        if (state.providerWontCache) {
            // 58 = 行内距 16 + 瓦片 30 + 缝 12，与标题左缘对齐。
            Text(
                stringResource(R.string.pm_saver_no_cache_hint),
                style = AppTypography.caption,
                color = colors.text.tertiary,
                modifier = Modifier.padding(start = 58.dp, end = AppSpacing.rowInset, bottom = 10.dp),
            )
        }
        if (state.enabled) {
            AppListDivider(startInset = 0.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = openLogA11y) { haptics.light(); onOpenLog() }
                    .padding(horizontal = AppSpacing.rowInset, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppSaverIcons.Bars, contentDescription = null, tint = colors.text.secondary, modifier = Modifier.size(14.dp))
                Text(
                    recentLineText(state.recent),
                    style = AppTypography.settingsRowValue.copy(fontFeatureSettings = "tnum"),
                    color = colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.pm_saver_open_log),
                    style = AppTypography.caption.copy(fontWeight = FontWeight.W600),
                    color = colors.accent.text,
                )
            }
        }
        AppListDivider(startInset = 0.dp)
        Text(
            saverNoteText(),
            style = AppTypography.caption.copy(lineHeight = 18.7.sp),
            color = colors.text.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.rowInset, end = AppSpacing.rowInset, top = 10.dp, bottom = 12.dp),
        )
    }
}
