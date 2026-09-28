package com.situ.aichat.ui.liuli.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.components.CharacterAvatar
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.contacts.ContactsViewModel
import com.situ.aichat.ui.contacts.RecentEvent
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliAvatarRing
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.liuliCardMaterial
import com.situ.aichat.ui.liuli.designsystem.liuliPressable
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import com.situ.aichat.util.DateFormatters
import com.situ.aichat.util.StreakManager

/** 卡内小件落值（卷三 §4.9·关系小标 11 与待重生成红点照旧行）。 */
private val PILL_H_PAD = 8.dp
private val PILL_V_PAD = 1.dp
private val FALLBACK_DOT = 11.dp
private val FALLBACK_DOT_RING = 1.5.dp
private const val MYSTERY_ALPHA = 0.6f
/** 火苗徽章（卷四 §4.8-2）：挂头像右下、外移 6 / 下移 4、高 18、1.5 纸色描边；图标 11 + 1 + 数字 11/700 tnum。 */
private val STREAK_BADGE_OFFSET_X = 6.dp
private val STREAK_BADGE_OFFSET_Y = 4.dp
private val STREAK_BADGE_HEIGHT = 18.dp
private val STREAK_BADGE_RIM = 1.5.dp
/** 白字压两端 4.90 / 6.21·卷四 §0.1-H。 */
private val STREAK_BADGE_START = Color(0xFFC24A17)
private val STREAK_BADGE_END = Color(0xFFAE381C)
private val STREAK_BADGE_ICON = 11.dp
/** 关系 · 职业一行、最近动态一行的件间距与小星（卷四 §4.8-3 / -4）。 */
private val RELATION_ROW_GAP = 5.dp
private val EVENT_ROW_GAP = 3.dp
private val EVENT_ICON = 11.dp

/**
 * 琉璃联系人两列卡（琉璃 2.0 卷三 §4.9；卷四 §4.8 用户 09-26 选甲：🔥 连续天数改成头像右下的徽章、关系 · 职业并一行、
 * 最近纪事单独一行〔强调色 + 小星·没有就不占位〕、关系标签统一 `accent.container` 实底）。
 *
 * **语义逐字照抄旧行 `LiuliContactRow`（= 暖陶 F5）**：整卡 `combinedClickable`（点 = 进会话 / 分享落地、长按 = 动作面板 +
 * medium 触觉）+ `semantics(mergeDescendants)` 的一句 cd 与三个 customActions；头像那块 `clearAndSetSemantics{}` 必须排在
 * `clickable` **之前**（清空节点要最后生效）。卡面 = 半透明卡片 [liuliCardMaterial]；点击排在它（内含 `clip`）之后，ripple 不漏角。
 * 火苗 = **连续聊天天数**（`StreakManager`），不是亲密度（A-8）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiuliContactCard(
    row: ContactsViewModel.Row,
    nowMillis: Long,
    hasFallback: Boolean,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dark = LocalIsDarkTheme.current
    val haptics = LocalAppHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val character = row.character
    val streak = StreakManager.getStreakCount(character)
    val occupation = character.occupation.trim()
    val recentEventText = recentEventText(row.recentEvent, nowMillis)
    val a11yLabel = buildString {
        append(character.name)
        row.relationshipDisplay?.let { append("，").append(it) }
        occupation.takeIf { it.isNotEmpty() }?.let { append("，").append(it) }
        recentEventText?.let { append("，").append(it) }
        if (streak > 0) append("，连续 ").append(streak).append(" 天")
        if (hasFallback) append("，").append(stringResource(R.string.a11y_contact_fallback_pending))
    }
    val actionProfile = stringResource(R.string.a11y_contact_open_profile)
    val actionEdit = stringResource(R.string.action_edit)
    val actionDelete = stringResource(R.string.action_delete)

    Box(
        modifier = modifier
            .liuliPressable(interaction, enabled = true)
            .liuliCardMaterial(LiuliShapes.medium, dark)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = stringResource(R.string.a11y_contact_open_chat),
                onClick = { onOpen() },
                onLongClick = { haptics.medium(); onLongPress() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
                customActions = listOf(
                    CustomAccessibilityAction(actionProfile) { onOpenProfile(); true },
                    CustomAccessibilityAction(actionEdit) { onEdit(); true },
                    CustomAccessibilityAction(actionDelete) { onDelete(); true },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = LiuliHomeGeometry.contactPadTop,
                    bottom = LiuliHomeGeometry.contactPadBottom,
                    start = LiuliHomeGeometry.contactPadH,
                    end = LiuliHomeGeometry.contactPadH,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.clearAndSetSemantics {}.clickable(onClick = onOpenProfile)) {
                LiuliAvatarRing(LiuliHomeGeometry.contactRing) {
                    CharacterAvatar(character.name, character.avatarPath, LiuliHomeGeometry.contactAvatar)
                }
                if (streak > 0) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = STREAK_BADGE_OFFSET_X, y = STREAK_BADGE_OFFSET_Y)
                            .height(STREAK_BADGE_HEIGHT)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(STREAK_BADGE_START, STREAK_BADGE_END)))
                            .border(STREAK_BADGE_RIM, colors.surface.raised, CircleShape)
                            .padding(start = 4.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Palette.White, modifier = Modifier.size(STREAK_BADGE_ICON))
                        Spacer(Modifier.width(1.dp))
                        Text(
                            "$streak",
                            style = AppTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.W700, fontFeatureSettings = "tnum"),
                            color = Palette.White,
                        )
                    }
                }
                if (hasFallback) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(FALLBACK_DOT)
                            .clip(CircleShape)
                            .background(colors.surface.raised)
                            .padding(FALLBACK_DOT_RING)
                            .clip(CircleShape)
                            .background(colors.status.onError),
                    )
                }
            }
            Spacer(Modifier.height(LiuliHomeGeometry.contactNameGap))
            Text(
                character.name, style = AppTypography.listName, color = colors.text.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(LiuliHomeGeometry.contactLineGap))
            // 关系 · 职业一行（职业恒显，空 → 神秘占位淡 60%）。
            Row(
                horizontalArrangement = Arrangement.spacedBy(RELATION_ROW_GAP, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiuliRelationshipPill(row.relationshipDisplay)
                val (occText, occAlpha) = if (occupation.isNotEmpty()) {
                    occupation to 1f
                } else {
                    stringResource(R.string.contacts_occupation_mystery) to MYSTERY_ALPHA
                }
                Text(
                    occText, style = AppTypography.secondary, color = colors.text.secondary.copy(alpha = occAlpha),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            // 最近纪事单独一行（14 天内·没有就不占位）。
            if (recentEventText != null) {
                Spacer(Modifier.height(LiuliHomeGeometry.contactLineGap))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EVENT_ROW_GAP, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent.text, modifier = Modifier.size(EVENT_ICON))
                    Text(
                        recentEventText, style = AppTypography.caption, color = colors.accent.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 关系小标（§3.2·卷三 §4.9·卷四 §4.8-5）：`accent.container` 实底 + `accent.onContainer` 字 W600，8/1 内距；
 * 「初识」（无里程碑）与关系名**同一样式**、只差文字（不透明·浅 7.8 / 深 9.9:1·半透明方案压资料页裸柔光底不达标已弃）。
 * [fontSize] 默认 11sp（联系人卡），资料页头部用 13。
 */
@Composable
fun LiuliRelationshipPill(display: String?, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp) {
    val colors = AppTheme.colors
    Text(
        text = display ?: stringResource(R.string.contacts_relationship_initial),
        style = AppTypography.caption.copy(fontSize = fontSize, fontWeight = FontWeight.W600),
        color = colors.accent.onContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.accent.container)
            .padding(horizontal = PILL_H_PAD, vertical = PILL_V_PAD),
    )
}

/** 最近纪事文案（照暖陶 `ContactRow` 逐字·VM 已选出窗内事件，此处只格式化）。 */
@Composable
private fun recentEventText(event: RecentEvent?, nowMillis: Long): String? = when (event) {
    null -> null
    is RecentEvent.Milestone ->
        stringResource(R.string.contacts_recent_event_milestone, DateFormatters.relativeDay(event.atMillis, nowMillis), event.name)
    is RecentEvent.Meeting -> {
        val day = DateFormatters.relativeDay(event.atMillis, nowMillis)
        when {
            event.activity.isNotEmpty() -> stringResource(R.string.contacts_recent_event_meeting, day, event.activity)
            event.location.isNotEmpty() -> stringResource(R.string.contacts_recent_event_meeting_location, day, event.location)
            else -> stringResource(R.string.contacts_recent_event_meeting_plain, day)
        }
    }
}
