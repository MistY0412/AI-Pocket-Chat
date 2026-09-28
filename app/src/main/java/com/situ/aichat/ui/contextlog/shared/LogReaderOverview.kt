package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogTokenFormat
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogMapCut
import com.situ.aichat.ui.contextlog.model.LogReaderFilter
import com.situ.aichat.ui.contextlog.model.LogReaderHit
import com.situ.aichat.ui.contextlog.model.LogReaderStrip
import com.situ.aichat.ui.contextlog.model.LogReaderView
import com.situ.aichat.ui.contextlog.model.LogStripKind
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.logReaderColors

/**
 * 阅读器概览卡内容（四期·图纸五 §4.2·两张脸共用；外壳由脸给：暖陶 LogCard / 琉璃 LiuliGroup + LiuliLogFreeRow）：
 * 条数行 → 按「谁说的」上色的细条（缓存断点红虚线）→ 图例 → 断点行（点去地图）。
 */
@Composable
fun LogReaderOverview(view: LogReaderView, entry: LogEntryEntity, onOpenMap: () -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${view.messages.size}", style = AppTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.text.primary, modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(AppSpacing.xs))
            Text(stringResource(R.string.clog_reader_count_unit), style = AppTheme.typography.caption, color = colors.text.secondary, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.clog_reader_size, LogTokenFormat.compact(entry.promptTokens), LogFormat.grouped(view.totalChars.toLong())),
                style = AppTheme.typography.captionNumeric, color = colors.text.secondary, modifier = Modifier.alignByBaseline(),
            )
        }
        if (view.strip.total > 0) {
            Spacer(Modifier.height(AppSpacing.s))
            ReaderStrip(view.strip)
        }
        Spacer(Modifier.height(AppSpacing.s))
        val rc = logReaderColors()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LogLegendItem(rc.system, false, stringResource(R.string.clog_reader_legend_system))
            LogLegendItem(rc.user, false, stringResource(R.string.clog_role_user))
            LogLegendItem(rc.assistant, false, readerCharacterName(entry))
            LogLegendItem(rc.added, false, stringResource(R.string.clog_reader_legend_added))
            if (view.strip.runs.any { it.kind == LogStripKind.OTHER }) LogLegendItem(colors.text.tertiary, false, stringResource(R.string.clog_role_tool))
        }
        view.frame?.let { frame ->
            Spacer(Modifier.height(AppSpacing.xs))
            CutNavRow(frame, onOpenMap)
        }
    }
}

/** 角色名（空 → 「角色」）：芯片 / 图例 / 回复全文徽标共用。 */
@Composable
fun readerCharacterName(entry: LogEntryEntity): String = entry.characterName.ifBlank { stringResource(R.string.clog_reader_role_assistant) }

/** 筛选芯片的类名（§4.3 芯片映射）。 */
@Composable
fun readerFilterLabel(filter: LogReaderFilter, entry: LogEntryEntity): String = when (filter) {
    LogReaderFilter.ALL -> stringResource(R.string.clog_reader_filter_all)
    LogReaderFilter.SYSTEM -> stringResource(R.string.clog_reader_legend_system)
    LogReaderFilter.USER -> stringResource(R.string.clog_role_user)
    LogReaderFilter.ASSISTANT -> readerCharacterName(entry)
    LogReaderFilter.ADDED -> stringResource(R.string.clog_reader_legend_added)
    LogReaderFilter.TOOL -> stringResource(R.string.clog_role_tool)
}

/** 页脚「共 N 条 · N 字（· 已截断）」。 */
@Composable
@ReadOnlyComposable
fun readerFooterText(view: LogReaderView): String = stringResource(
    if (view.truncated) R.string.clog_reader_footer_truncated else R.string.clog_reader_footer,
    view.messages.size, LogFormat.grouped(view.totalChars.toLong()),
)

/** 搜索计数（§4.7）：关键词空白 → null（不显示）；没有可跳的命中 →「无结果」；否则「第几处 / 共几处」（到上限加「+」）。 */
@Composable
@ReadOnlyComposable
fun readerHitCountText(query: String, nav: List<LogReaderHit>, current: Int, capped: Boolean): String? = when {
    query.isBlank() -> null
    nav.isEmpty() -> stringResource(R.string.clog_reader_no_hits)
    capped -> stringResource(R.string.clog_reader_hits_capped, current + 1, nav.size)
    else -> stringResource(R.string.clog_reader_hits, current + 1, nav.size)
}

/**
 * 细条（§4.2 第 2 条·锁定数值）：外高 24dp、条高 16dp 纵向居中、圆角 8dp 裁切；各截按字数占比、截间隙每侧 0.75dp（首截左 / 末截右不扣、
 * 没有最小宽）；断点红线在裁切之外、全高、2dp、虚线 3 / 3dp——横坐标与色条同一套算法（分母 = 各截之和·PITFALLS §2 #35）。
 */
@Composable
private fun ReaderStrip(strip: LogReaderStrip) {
    val rc = logReaderColors()
    val other = AppTheme.colors.text.tertiary
    val cutColor = AppTheme.colors.status.onError
    fun colorOf(kind: LogStripKind): Color = when (kind) {
        LogStripKind.SYSTEM -> rc.system
        LogStripKind.USER -> rc.user
        LogStripKind.ASSISTANT -> rc.assistant
        LogStripKind.ADDED -> rc.added
        LogStripKind.OTHER -> other
    }
    Box(Modifier.fillMaxWidth().height(24.dp).clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize()) {
            val top = 4.dp.toPx()
            val barH = 16.dp.toPx()
            val g = 0.75.dp.toPx()
            val w = size.width
            val clip = Path().apply { addRoundRect(RoundRect(0f, top, w, top + barH, CornerRadius(8.dp.toPx()))) }
            clipPath(clip) {
                var acc = 0
                strip.runs.forEachIndexed { i, run ->
                    val x0 = acc.toFloat() / strip.total * w
                    val x1 = (acc + run.chars).toFloat() / strip.total * w
                    val left = if (i == 0) x0 else x0 + g
                    val right = if (i == strip.runs.lastIndex) x1 else x1 - g
                    if (right - left > 0f) drawRect(colorOf(run.kind), topLeft = Offset(left, top), size = Size(right - left, barH))
                    acc += run.chars
                }
            }
            strip.cutFraction?.let { f ->
                val x = f * w
                drawLine(
                    cutColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

/** 断点行（§4.2 第 4 条）：只在末尾追加 / 挪位块变了 → 次要色，其余 → 错误色；右侧「看地图 ›」。 */
@Composable
private fun CutNavRow(frame: LogMapCut, onOpenMap: () -> Unit) {
    val c = if (frame.kind == CacheBreakKind.TAIL || frame.kind == CacheBreakKind.SAVED_BLOCK) AppTheme.colors.text.secondary else AppTheme.colors.status.onError
    val accent = AppTheme.colors.accent.text
    val openMap = stringResource(R.string.clog_reader_open_map)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = openMap, onClick = onOpenMap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp), tint = c)
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            breakNavText(frame.kind, frame.moduleName), style = AppTheme.typography.caption.copy(fontWeight = FontWeight(640)), color = c,
            modifier = Modifier.weight(1f), maxLines = 2,
        )
        Spacer(Modifier.width(AppSpacing.s))
        Text(openMap, style = AppTheme.typography.settingsRowValue, color = accent)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
    }
}
