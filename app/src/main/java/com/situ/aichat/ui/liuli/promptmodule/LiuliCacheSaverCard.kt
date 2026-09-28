package com.situ.aichat.ui.liuli.promptmodule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppSaverIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliTileTone
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliToggleRow
import com.situ.aichat.ui.promptmodule.CacheSaverCardState
import com.situ.aichat.ui.promptmodule.recentLineText
import com.situ.aichat.ui.promptmodule.saverNoteText

/**
 * 琉璃省钱卡（时间感知四期·图纸二 §4.2·效果图 phase4_log_and_saver_mockup 第一节琉璃两屏·09-26 过审）：
 * 与暖陶 [com.situ.aichat.ui.promptmodule.CacheSaverCard] 同内容同顺序、换琉璃组 / 行零件；
 * 状态与文字拼装两张脸共用（[CacheSaverCardState] / [recentLineText] / [saverNoteText]）。
 */
@Composable
internal fun LiuliCacheSaverCard(
    state: CacheSaverCardState,
    onToggle: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = LiuliPageGeometry.gutter)) {
        LiuliGroup {
            LiuliToggleRow(
                title = stringResource(R.string.pm_saver_title),
                checked = state.enabled,
                onCheckedChange = onToggle,
                subtitle = stringResource(R.string.pm_saver_subtitle),
                icon = AppSaverIcons.Yen,
                tileColor = LiuliTileTone.Mint,
                divider = false,
            )
            if (state.providerWontCache) {
                Text(
                    stringResource(R.string.pm_saver_no_cache_hint),
                    style = AppTypography.caption,
                    color = colors.text.tertiary,
                    modifier = Modifier.padding(start = LiuliPageGeometry.dividerInsetTile, end = LiuliPageGeometry.groupPadH, bottom = 10.dp),
                )
            }
            if (state.enabled) {
                LiuliRowBase(
                    onClick = onOpenLog,
                    onClickLabel = stringResource(R.string.pm_saver_open_log_a11y),
                    minHeight = 48.dp,
                    divider = true,
                    dividerInset = LiuliPageGeometry.dividerInsetPlain,
                ) {
                    Icon(AppSaverIcons.Bars, contentDescription = null, tint = colors.text.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        recentLineText(state.recent),
                        style = AppTypography.settingsRowValue.copy(fontFeatureSettings = "tnum"),
                        color = colors.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pm_saver_open_log),
                        style = AppTypography.caption.copy(fontWeight = FontWeight.W600),
                        color = colors.accent.text,
                    )
                }
            }
            LiuliRowBase(onClick = null, minHeight = 0.dp, divider = true, dividerInset = LiuliPageGeometry.dividerInsetPlain) {
                Text(
                    saverNoteText(),
                    style = AppTypography.caption.copy(lineHeight = 18.7.sp),
                    color = colors.text.secondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                )
            }
        }
    }
}
