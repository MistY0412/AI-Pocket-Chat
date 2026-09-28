package com.situ.aichat.ui.contextlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.appCardSurface

/*
 * 日志页暖陶小零件（四期·图纸四 §4.0 词汇表暖陶列）：卡、组标签、导航行、键值行、药丸、提示框、失败红条、统计四格。
 * 琉璃对应件在 `ui/liuli/contextlog/LiuliLogParts.kt`；两张脸内容 / 顺序 / 文案完全相同，只换这里的外壳。
 */

/** 状态药丸的色调（两张脸共用同一套 status / accent token）。 */
enum class LogPillTone { SUCCESS, ERROR, NEUTRAL, USER }

/** 提示框色调：告警 = warning 家族 + WarningAmber；说明 = info 家族 + Info。 */
enum class LogNoticeTone { WARNING, INFO }

/** 卡：appCardSurface + 卡内 16dp；有标题时标题 caption·次要色，下方 8dp。 */
@Composable
fun LogCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.cardInset),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().appCardSurface().padding(contentPadding)) {
        if (title != null) {
            Text(title, style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary)
            Spacer(Modifier.height(AppSpacing.s))
        }
        content()
    }
}

/** 组标签 / 节头：caption·次要色，上 8dp。 */
@Composable
fun LogGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary, modifier = modifier.padding(top = AppSpacing.s))
}

/** 导航行：强调色标题 + 次要副行 + 右箭头，触达 ≥ 48dp。 */
@Composable
fun LogNavRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = title, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.label, color = AppTheme.colors.accent.text)
            subtitle?.let { Text(it, style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary) }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.accent.text, modifier = Modifier.size(18.dp))
    }
}

/** 键值行：键次要色、值主色右对齐单行省略。 */
@Composable
fun LogKvRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary)
        Spacer(Modifier.width(AppSpacing.m))
        Text(
            value,
            style = AppTheme.typography.secondary,
            color = AppTheme.colors.text.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 状态药丸（沿用原 `SourceBadge` 内边距）：成功 / 失败 / 中性 / 「你」。 */
@Composable
fun LogPill(text: String, tone: LogPillTone, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val (bg, fg) = when (tone) {
        LogPillTone.SUCCESS -> c.status.successContainer to c.status.onSuccess
        LogPillTone.ERROR -> c.status.errorContainer to c.status.onError
        LogPillTone.NEUTRAL -> c.surface.sunken to c.text.secondary
        LogPillTone.USER -> c.accent.container to c.accent.onContainer
    }
    Box(modifier.clip(AppTheme.shapes.full).background(bg).padding(horizontal = AppSpacing.s, vertical = 2.dp)) {
        Text(text, style = AppTheme.typography.caption.copy(fontWeight = FontWeight(520)), color = fg)
    }
}

/** 提示框：容器色底 + 字色 14% 描边 + 12dp 内距；左 16dp 图标。 */
@Composable
fun LogNotice(tone: LogNoticeTone, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors.status
    val (bg, fg) = if (tone == LogNoticeTone.WARNING) c.warningContainer to c.onWarning else c.infoContainer to c.onInfo
    val shape = AppTheme.shapes.medium
    // [modifier] 挂在圆角裁切之后：可点的告警条涟漪不漏出圆角（PITFALLS §1d）。
    Row(Modifier.fillMaxWidth().clip(shape).then(modifier).background(bg).border(1.dp, fg.copy(alpha = 0.14f), shape).padding(AppSpacing.m)) {
        Icon(
            if (tone == LogNoticeTone.WARNING) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
            contentDescription = null, tint = fg, modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(AppSpacing.s))
        Column(Modifier.weight(1f)) { content() }
    }
}

/**
 * 失败红条卡：卡左缘 3dp 红条（原列表 `LogListCard` 同款）+ 卡体；[failed] = false 时就是普通卡体。
 * 整卡可点时 clickable 排在卡的圆角裁切之后（涟漪不漏出圆角·PITFALLS §1d）。
 */
@Composable
fun LogStripCard(
    failed: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(modifier.fillMaxWidth().appCardSurface().then(click).height(IntrinsicSize.Min)) {
        if (failed) Box(Modifier.width(3.dp).fillMaxHeight().background(AppTheme.colors.status.onError))
        Row(Modifier.weight(1f).padding(contentPadding), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** 统计四格（今天）：值 bodyEmphasis 等宽数字、标签 10.5sp，列间 1dp × 28dp 竖线；[errorIndex] 那格值走 onError。 */
@Composable
fun LogStatRow(items: List<Pair<String, String>>, errorIndex: Int?) {
    LogCard(contentPadding = PaddingValues(vertical = AppSpacing.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (value, label) ->
                if (i > 0) Box(Modifier.width(1.dp).height(28.dp).background(AppTheme.colors.surface.stroke))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value,
                        style = AppTheme.typography.bodyEmphasis.copy(fontFeatureSettings = "tnum"),
                        color = if (i == errorIndex) AppTheme.colors.status.onError else AppTheme.colors.text.primary,
                        maxLines = 1,
                    )
                    Text(label, style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary)
                }
            }
        }
    }
}
