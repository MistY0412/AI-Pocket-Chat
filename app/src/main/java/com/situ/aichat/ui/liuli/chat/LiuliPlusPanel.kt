package com.situ.aichat.ui.liuli.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.ui.chat.ChatInputPanelState
import com.situ.aichat.ui.chat.ChatPanelItem
import com.situ.aichat.ui.chat.ChatSheetsState
import com.situ.aichat.ui.chat.ChatViewModel
import com.situ.aichat.ui.chat.QuoteTextOnlyHintState
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.liuli.designsystem.LiuliPanelIcons
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.liuli.designsystem.LiuliTileTone
import com.situ.aichat.ui.liuli.designsystem.liuliPressable
import com.situ.aichat.ui.liuli.designsystem.liuliToneFill
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 琉璃「+」变形面板（图纸 2026-09-05 卷二B §4.4 · 契约 §5.2 · A-5）：一片**导航层玻璃**，自「+」原地长出
 * （`scaleIn` 的变换原点钉在圆钮那一点），宽 = 输入区宽、底距导航栏 12、圆角 24。
 *
 * 与暖陶的分叉只有「住哪一层」：暖陶把面板画在内容层白卡里，琉璃画在 overlay 玻璃片上（分叉 1·§3.3）。
 * **机制零碰**：高度仍是 `ChatInputPanelState.regionPx(ime − navBar)`（键盘高度实时自适应硬指标·
 * PLUS_PANEL 契约），六入口构造与 `photoPicker` 自 [LiuliChatPanelRegion] **整段搬**（含三处引用拦截与
 * vision 显隐），逐字未改。内容层那一份退为透明占位——托盘、面板、列表底三者读同一个 `regionPx`。
 */
@Composable
internal fun BoxScope.LiuliPlusPanel(
    viewModel: ChatViewModel,
    sheets: ChatSheetsState,
    inputPanel: ChatInputPanelState,
    replyTarget: MessageEntity?,
    quoteHint: QuoteTextOnlyHintState,
    isOfflineMode: Boolean,
    chatModelHasVision: Boolean,
    reduceMotion: Boolean,
    /** 面板 / 键盘当前高度（布局 lambda 里取值·组合期绝不读 ime——审计 P4）。 */
    regionPx: () -> Int,
) {
    val dark = LocalIsDarkTheme.current
    // 发图入口（拍板③「选完即发」）：系统 Photo Picker 多选，选完直接逐张成消息发出（照抄）。
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_CHAT_IMAGES_PER_PICK),
    ) { uris -> if (uris.isNotEmpty()) viewModel.sendImages(uris) }

    // 卷三 §4.5-1：每个入口认一色六色渐变（按入口认色、不按格子位置——缺项时也不重复相邻）。
    // 卷四 §4.7：顺序 照片 · 表情 · 红包 · 送礼 · 见面 · 约见面，色按意思（照片 Sky · 表情 Gold · 红包 Peach · 送礼 Rose ·
    // 见面 Mint · 约见面 Lilac）；每个入口的构造、动作与显隐逐字不动。
    val items = buildList<Pair<ChatPanelItem, LiuliTileTone>> {
        // 「照片」按**聊天对话模型的视觉能力**显隐（2026-08-29 用户拍板·照抄）。
        if (chatModelHasVision) {
            add(
                ChatPanelItem(LiuliPanelIcons.Photo, "照片") {
                    inputPanel.dismiss(reduceMotion)
                    // 引用一期 E·拦截②：带引用时不拉起系统选图器，只弹提示。
                    if (replyTarget != null) {
                        quoteHint.trigger()
                        return@ChatPanelItem
                    }
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } to LiuliTileTone.Sky,
            )
        }
        add(
            ChatPanelItem(LiuliPanelIcons.Sticker, "表情") {
                inputPanel.dismiss(reduceMotion)
                // 引用一期 E·拦截③：带引用时不开表情面板，只弹提示。
                if (replyTarget != null) {
                    quoteHint.trigger()
                    return@ChatPanelItem
                }
                sheets.showPicker = true
            } to LiuliTileTone.Gold,
        )
        // 见面期隐藏 红包 / 送礼 / 见面 / 约见面（2026-06-21 用户拍板·照抄）。
        if (!isOfflineMode) {
            add(ChatPanelItem(LiuliPanelIcons.RedPacket, "红包") { inputPanel.dismiss(reduceMotion); sheets.showRedPacketSheet = true } to LiuliTileTone.Peach)
            add(ChatPanelItem(LiuliPanelIcons.Gift, "送礼") { inputPanel.dismiss(reduceMotion); sheets.showGiftSheet = true } to LiuliTileTone.Rose)
            add(ChatPanelItem(LiuliPanelIcons.Meet, "见面") { inputPanel.dismiss(reduceMotion); sheets.showManualMeetingSheet = true } to LiuliTileTone.Mint)
            add(ChatPanelItem(LiuliPanelIcons.FutureMeet, "约见面") { inputPanel.dismiss(reduceMotion); sheets.showFutureMeetingSheet = true } to LiuliTileTone.Lilac)
        }
    }

    AnimatedVisibility(
        visible = inputPanel.panelOpen,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                start = LiuliChatGeometry.inputSide,
                end = LiuliChatGeometry.inputSide,
                bottom = LiuliChatGeometry.panelBottom,
            )
            // 高度只在布局相位算（键盘动画帧只重排、不重组）；与内容层占位同帧同数。
            // 几何（复核 R1 🟡-3 改定·图纸 §3.2 公式勘误）：托盘自带 inputBottom 离屏底，三片行底 = 面板区顶 +
            // inputBottom；面板顶要落在三片行底 − panelTop、面板底落在导航栏顶 + panelBottom ⇒
            // h = regionPx − panelBottom + inputBottom − panelTop（panelBottom == inputBottom ⇒ = regionPx − 6dp）。
            // 旧式 regionPx − (panelTop + panelBottom) 会让面板顶离三片行 18dp 而非 6dp（装机实测 18.7dp）。
            .layout { measurable, constraints ->
                val inset = (LiuliChatGeometry.panelTop + LiuliChatGeometry.panelBottom - LiuliChatGeometry.inputBottom).roundToPx()
                val h = (regionPx() - inset).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            },
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(PANEL_FADE_IN_MS)) +
                scaleIn(AppMotion.gentleSpring(), initialScale = PANEL_MORPH_SCALE, transformOrigin = PlusOrigin)
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(PANEL_FADE_OUT_MS)) +
                scaleOut(AppMotion.gentleSpring(), targetScale = PANEL_MORPH_SCALE, transformOrigin = PlusOrigin)
        },
        label = "liuliPlusPanel",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .liuliGlass(RoundedCornerShape(LiuliChatGeometry.panelCorner), dark = dark, role = LiuliGlassRole.Panel)
                .padding(horizontal = PANEL_CONTENT_SIDE),
            // 卷四 §4.7：两排（或一排）在面板里上下居中。
            verticalArrangement = Arrangement.spacedBy(PANEL_ROW_GAP, Alignment.CenterVertically),
        ) {
            items.chunked(PANEL_COLUMNS).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    rowItems.forEach { (item, tone) -> LiuliPanelTile(item, tone, Modifier.weight(1f)) }
                    // 不足一排的格留在左侧，尾列等宽占位补齐（绝不拉伸 / 居中·照抄暖陶排布）。
                    repeat(PANEL_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 面板一格：60dp 圆角 18 方底（六色渐变）+ 30dp 白图标 + 标签（卷四）。禁用态只降透明**不改结构**（REDLINES §7）。 */
@Composable
private fun LiuliPanelTile(item: ChatPanelItem, tone: LiuliTileTone, modifier: Modifier = Modifier) {
    val haptics = LocalAppHaptics.current
    val onGlass = LiuliTheme.onGlass
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .liuliPressable(interactionSource = interaction, enabled = item.enabled)
            .clickable(
                enabled = item.enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptics.selection()
                    item.onClick()
                },
            )
            .alpha(if (item.enabled) 1f else TILE_DISABLED_ALPHA),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(LiuliChatGeometry.panelTileSize)
                .liuliToneFill(tone, RoundedCornerShape(LiuliChatGeometry.panelTileCorner)),
            contentAlignment = Alignment.Center,
        ) {
            // 标签即无障碍名（整格的语义由 clickable 的 Role.Button + 标签文字合成·照抄暖陶口径）。
            Icon(item.icon, null, Modifier.size(TILE_ICON_SIZE), tint = Palette.White)
        }
        Spacer(Modifier.height(TILE_LABEL_GAP))
        Text(item.label, style = AppTypography.caption, color = onGlass.secondary, textAlign = TextAlign.Center)
    }
}

// 落值（图纸 §3.2 面板一节 / §4.10 表·孤值即打回；卷四 §4.7 改四列 / 行距 20 / 图标 30）。
private const val PANEL_COLUMNS = 4
private val PANEL_CONTENT_SIDE = 12.dp
private val PANEL_ROW_GAP = 20.dp
private val TILE_ICON_SIZE = 30.dp
private val TILE_LABEL_GAP = 8.dp
private const val TILE_DISABLED_ALPHA = 0.38f
private const val PANEL_FADE_IN_MS = 120
private const val PANEL_FADE_OUT_MS = 160
private const val PANEL_MORPH_SCALE = 0.3f

/** 变形原点 = 「+」圆钮那一点（左 6% / 顶缘）——面板从它身上长出来，而不是从中心炸开。 */
private val PlusOrigin = TransformOrigin(0.06f, 0f)

/** 一次最多选几张（照抄暖陶 `ChatBottomBar` 的同名 private 常量·两侧同值）。 */
private const val MAX_CHAT_IMAGES_PER_PICK = 9
