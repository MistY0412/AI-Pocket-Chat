package com.situ.aichat.ui.contextlog.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.contextlog.model.LogAddedKind
import com.situ.aichat.ui.contextlog.model.LogCardPos
import com.situ.aichat.ui.contextlog.model.LogFormat
import com.situ.aichat.ui.contextlog.model.LogHighlight
import com.situ.aichat.ui.contextlog.model.LogOutlineSection
import com.situ.aichat.ui.contextlog.model.LogReaderHit
import com.situ.aichat.ui.contextlog.model.LogReaderItem
import com.situ.aichat.ui.contextlog.model.LogReaderRow
import com.situ.aichat.ui.contextlog.model.LogReaderView
import com.situ.aichat.ui.contextlog.model.LogSentMessage
import com.situ.aichat.ui.contextlog.model.LogSentPiece
import com.situ.aichat.ui.contextlog.model.LogSentRole
import com.situ.aichat.ui.contextlog.model.highlightsIn
import com.situ.aichat.ui.contextlog.model.pieceText
import com.situ.aichat.ui.contextlog.model.visiblePieceText
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.LogReaderColors
import com.situ.aichat.ui.designsystem.logReaderColors

private val W640 = FontWeight(640)

/** 节正文 / 「展开全部原文」的起点：对齐节名（行内距 + 箭头 + 间距）。 */
private val SectionTextStart = AppSpacing.s + 16.dp + AppSpacing.xs

/** 命中高亮（§4.6 锁定）：命中底色；当前处加深，深色时当前处的字用底色（金底上读得清）。 */
fun highlighted(text: String, highlights: List<LogHighlight>, colors: LogReaderColors, currentOn: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        highlights.forEach { addStyle(SpanStyle(background = if (it.current) colors.hitCurrent else colors.hit, color = if (it.current) currentOn else Color.Unspecified), it.start, it.end) }
    }

/** 表头的角色名：系统 / 你 / 角色名（空 → 原 role）/ 工具 / 原 role。 */
@Composable
@ReadOnlyComposable
fun sentRoleLabel(message: LogSentMessage, characterName: String): String = when (message.role) {
    LogSentRole.SYSTEM -> stringResource(R.string.clog_role_system)
    LogSentRole.USER -> stringResource(R.string.clog_role_user)
    LogSentRole.ASSISTANT -> characterName.ifBlank { message.rawRole }
    LogSentRole.TOOL -> stringResource(R.string.clog_role_tool)
    LogSentRole.OTHER -> message.rawRole
}

/** 表头其余部分：「 #N」+（有字时「 · N 字」）。 */
@Composable
@ReadOnlyComposable
fun readerHeaderTail(m: LogSentMessage): String = buildString {
    append(" #").append(m.index)
    if (m.chars > 0) append(" · ").append(stringResource(R.string.clog_msg_chars, LogFormat.grouped(m.chars.toLong())))
}

/** 目录区底（surface.sunken）的形状：顶段圆上两角、底段圆下两角、单段四角（8dp = AppShapes.small）、中段直角（§4.4）。 */
fun readerTocShape(pos: LogCardPos): Shape = when (pos) {
    LogCardPos.TOP -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    LogCardPos.BOTTOM -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    LogCardPos.SINGLE -> RoundedCornerShape(8.dp)
    LogCardPos.MIDDLE -> RectangleShape
}

/** 每条的复制小钮：触达 48dp、图标 18dp。 */
@Composable
fun LogCopyIconButton(onClick: () -> Unit, contentDescription: String) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppTheme.colors.text.secondary)
    }
}

/**
 * 消息卡一行的内容（§4.5·两张脸共用）：按行类型分发，高亮按 §4.6 口径就地算（`highlightsIn(nav, …)`）；
 * 行壳（分段卡 / 分隔线 / 上下留白 / 目录底）由脸给，表头徽标走 [pill] 槽（暖陶 LogPill / 琉璃 LiuliLogPill）。
 */
@Composable
fun LogReaderRowContent(
    item: LogReaderItem, view: LogReaderView, nav: List<LogReaderHit>, current: LogReaderHit?, c: LogReaderControls,
    onCopyMessage: (LogSentMessage) -> Unit, pill: @Composable (LogSentMessage) -> Unit,
) {
    val colors = logReaderColors()
    val on = if (AppTheme.colors.isDark) AppTheme.colors.surface.base else Color.Unspecified
    fun hl(text: String, message: Int, piece: Int, from: Int, to: Int) = highlighted(text, highlightsIn(nav, message, piece, from, to, current), colors, on)
    fun msg(i: Int) = view.messages[i - 1]
    when (val row = item.row) {
        is LogReaderRow.Header -> msg(row.message).let { m -> LogReaderHeaderRow(m, pill) { onCopyMessage(m) } }
        is LogReaderRow.TimePill -> {
            val t = pieceText(msg(row.message).pieces[0])
            TimePillRow(hl(t, row.message, 0, 0, t.length), row.message)
        }
        is LogReaderRow.TextBlock -> {
            val t = pieceText(msg(row.message).pieces[row.piece])
            val s = hl(t.substring(row.start, row.end), row.message, row.piece, row.start, row.end)
            SelectionContainer { Text(if (row.ellipsis) s + AnnotatedString("…") else s, style = AppTheme.typography.secondary, color = AppTheme.colors.text.primary) }
        }
        is LogReaderRow.Added -> {
            val piece = msg(row.message).pieces[row.piece] as LogSentPiece.Added
            val visible = visiblePieceText(piece, row.expanded)
            val shown = if (visible.length != piece.text.length) visible.length - 1 else visible.length // 去掉折叠追加的「…」
            AddedBox(piece, hl(visible, row.message, row.piece, 0, shown))
        }
        is LogReaderRow.Section -> {
            val i = row.index
            SectionRow(view.outline!!.sections[i], row.open) { c.sectionsToggled = if (i in c.sectionsToggled) c.sectionsToggled - i else c.sectionsToggled + i }
        }
        is LogReaderRow.SectionBlock -> {
            val prompt = pieceText(view.messages[0].pieces[0])
            val s = hl(prompt.substring(row.start, row.end), 1, 0, row.start, row.end)
            SelectionContainer {
                Text(s, style = AppTheme.typography.secondary, color = AppTheme.colors.text.primary, modifier = Modifier.padding(start = SectionTextStart, end = AppSpacing.s))
            }
        }
        is LogReaderRow.SectionsToggle -> TextButtonRow(
            if (row.allOpen) stringResource(R.string.clog_collapse) else stringResource(R.string.clog_reader_expand_all),
            Modifier.fillMaxWidth(), start = SectionTextStart, label = null,
        ) {
            val all = view.outline?.sections.orEmpty().indices
            c.sectionsToggled = if (row.allOpen) view.defaultOpen.toList() else all.filter { it !in view.defaultOpen }
        }
        is LogReaderRow.Cut -> if (item.tocPos != null) Box(Modifier.padding(horizontal = AppSpacing.s)) { LogCutFrame(row.frame) } else LogCutFrame(row.frame)
        is LogReaderRow.Toggle -> {
            val label = stringResource(if (row.expanded) R.string.clog_collapse else R.string.clog_expand)
            TextButtonRow(label, Modifier, start = 0.dp, label = label) {
                c.expanded = if (row.message in c.expanded) c.expanded - row.message else c.expanded + row.message
            }
        }
        is LogReaderRow.Empty -> Text(
            stringResource(R.string.clog_reader_empty_body), style = AppTheme.typography.caption, color = AppTheme.colors.text.secondary,
            modifier = Modifier.padding(vertical = AppSpacing.xs),
        )
        is LogReaderRow.Collapsed -> Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { c.showAll = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.clog_middle_collapsed, row.hidden), style = AppTheme.typography.caption, color = AppTheme.colors.accent.text, textAlign = TextAlign.Center)
        }
    }
}

/** 表头：徽标 +「 #N · N 字」+ 复制钮；行高 ≥ 48dp（阅读器与回复全文页共用）。 */
@Composable
fun LogReaderHeaderRow(m: LogSentMessage, pill: @Composable (LogSentMessage) -> Unit, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        pill(m)
        Text(readerHeaderTail(m), style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary, modifier = Modifier.weight(1f), maxLines = 1)
        LogCopyIconButton(onClick = onCopy, contentDescription = stringResource(R.string.clog_reader_copy_message))
    }
}

/** 时间标记居中胶囊（文字 = 片段原文，含【时间 · …】）+ 右侧「 #N」。 */
@Composable
private fun TimePillRow(text: AnnotatedString, index: Int) {
    val accent = AppTheme.colors.accent.text
    Row(Modifier.fillMaxWidth().padding(vertical = AppSpacing.s), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(AppTheme.shapes.full).background(AppTheme.colors.accent.primary.copy(alpha = 0.10f))
                    .drawBehind { dashedOutline(accent, size.height / 2) }
                    .padding(horizontal = AppSpacing.m, vertical = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = accent)
                Spacer(Modifier.width(AppSpacing.xs))
                SelectionContainer { Text(text, style = AppTheme.typography.caption, color = accent, textAlign = TextAlign.Center) }
            }
        }
        Text(" #$index", style = AppTheme.typography.settingsRowSubtitle, color = AppTheme.colors.text.secondary)
    }
}

/** 「App 附加」虚线框（原图纸四 AddedBox 视觉逐值不变·正文可选中 + 高亮）。 */
@Composable
private fun AddedBox(piece: LogSentPiece.Added, text: AnnotatedString) {
    val accent = AppTheme.colors.accent.text
    Column(
        Modifier.fillMaxWidth()
            .clip(AppTheme.shapes.small)
            .background(AppTheme.colors.accent.primary.copy(alpha = 0.10f))
            .drawBehind { dashedOutline(accent, 8.dp.toPx()) } // = AppShapes.small 的圆角
            .padding(horizontal = AppSpacing.s, vertical = AppSpacing.xs),
    ) {
        Text(
            if (piece.kind == LogAddedKind.TIME_MARKER) {
                stringResource(R.string.clog_added_time)
            } else {
                stringResource(R.string.clog_added_note, LogFormat.grouped(piece.text.length.toLong()))
            },
            style = AppTheme.typography.settingsRowSubtitle.copy(fontWeight = W640),
            color = accent,
        )
        SelectionContainer { Text(text, style = AppTheme.typography.caption, color = AppTheme.colors.text.primary) }
    }
}

/** 1dp 虚线圆角描边（dash 4 / 4dp）：附加框与时间胶囊共用。 */
private fun DrawScope.dashedOutline(color: Color, corner: Float) {
    val w = 1.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}

/** 目录一节：箭头 + 节名（未分段 → 「未分段内容（世界书等）」）+（「这段变了」）+ 字数；点击开合。 */
@Composable
private fun SectionRow(section: LogOutlineSection, open: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.clog_collapse else R.string.clog_expand), onClick = onToggle)
            .padding(horizontal = AppSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.text.secondary,
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            section.title ?: stringResource(R.string.clog_reader_unsegmented), style = AppTheme.typography.secondary, color = colors.text.primary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (section.changed) {
            Spacer(Modifier.width(AppSpacing.s))
            Text(
                stringResource(R.string.clog_reader_changed), style = AppTheme.typography.caption.copy(fontWeight = FontWeight(520)), color = colors.status.onError,
                modifier = Modifier.clip(AppTheme.shapes.full).background(colors.status.errorContainer).padding(horizontal = AppSpacing.s, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(AppSpacing.s))
        Text(stringResource(R.string.clog_msg_chars, LogFormat.grouped(section.chars.toLong())), style = AppTheme.typography.captionNumeric, color = colors.text.secondary)
    }
}

/**
 * 强调色文字钮行（触达 ≥ 48dp·settingsRowSubtitle W640·accent.text）：「展开 / 收起」（原图纸四展开钮逐值不变）与目录
 * 「展开全部原文 / 收起」共用；[start] = 文字起点缩进，[label] = 读屏动作名（null = 不设）。
 */
@Composable
private fun TextButtonRow(text: String, modifier: Modifier, start: Dp, label: String?, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(start = start),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = AppTheme.typography.settingsRowSubtitle.copy(fontWeight = W640), color = AppTheme.colors.accent.text)
    }
}
