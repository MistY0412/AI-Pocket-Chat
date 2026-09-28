package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.ui.chat.VoiceRecordingOverlay
import com.situ.aichat.ui.liuli.chat.LiuliChromeTone
import com.situ.aichat.ui.liuli.designsystem.LiuliAmbientBackground
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliSnackbarHost
import com.situ.aichat.ui.liuli.designsystem.rememberLiuliInstantSheetState
import com.situ.aichat.ui.liuli.glass.LiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.LiuliGlassHost
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.currentLiuliGlassEngine
import com.situ.aichat.ui.liuli.glass.liuliGlassElevation
import com.situ.aichat.ui.liuli.glass.liuliGlass
import com.situ.aichat.ui.liuli.page.LiuliFloatingBar
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.SCROLL_EDGE_OVERSCAN
import com.situ.aichat.ui.moments.ComposeMentionPickerBody
import com.situ.aichat.ui.moments.ComposeMomentFace
import com.situ.aichat.ui.moments.SHEET_BOTTOM_RESERVE
import com.situ.aichat.ui.moments.composeMentionDoneText
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 发布页·琉璃脸（朋友圈发布页·乙 §4.3–§4.10）：宿主 = 玻璃宿主（content 先铺柔光底再放天色，天色底部渐隐）；
 * 纸面 = 整屏一层 Panel 玻璃（左右 / 底伸出屏外·顶不伸·毛玻璃档不伸）；顶栏随天色换深 / 浅调（Y-1）；
 * 工具栏 = 悬浮玻璃底条；弹层 / 对话框用琉璃件，由骨架在宿主 **content** 层调用（PITFALLS §2 #25）。
 * 本文件只 import 琉璃件与 AppTheme 系 token，不碰暖陶 App* 组件（LiuliTheme 边界规矩）。
 */
internal object LiuliComposeMomentFace : ComposeMomentFace {
    override val skyFadesOut: Boolean = true
    override val tileCorner: Dp = 10.dp // = LiuliShapes.small

    @Composable
    override fun Host(modifier: Modifier, content: @Composable BoxScope.() -> Unit, overlay: @Composable BoxScope.() -> Unit) {
        LiuliGlassHost(
            modifier.fillMaxSize(),
            content = {
                LiuliAmbientBackground(Modifier.matchParentSize())
                content()
            },
            overlay = overlay,
        )
    }

    @Composable
    override fun TopBar(lightSky: Boolean, canPublish: Boolean, onCancel: () -> Unit, onPublish: () -> Unit) {
        // Y-1：天是暗的（白天之外四桶）→ 深调玻璃 + 浅字；白天 → 浅调（聊天壁纸同款局部换调）。
        LiuliChromeTone(dark = !lightSky, thick = false) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = LiuliPageGeometry.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiuliButton(onClick = onCancel, style = LiuliButtonStyle.Glass, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.weight(1f))
                LiuliButton(
                    onClick = onPublish,
                    style = LiuliButtonStyle.Prominent,
                    enabled = canPublish,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.moment_compose_publish))
                }
            }
        }
    }

    @Composable
    override fun Sheet(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        val dark = LocalIsDarkTheme.current
        // 整屏玻璃伸出屏外（PITFALLS §2 #24·照卷六·三·下甲 R1 锁屏层写法）：左右 / 底伸出，顶边是纸面上沿不伸；自研毛玻璃档不伸。
        val overscan = if (currentLiuliGlassEngine() == LiuliGlassEngine.FROSTED_BLUR) 0.dp else SCROLL_EDGE_OVERSCAN
        Box(modifier) {
            // 屏上圆角（复核 R1·§11 O-4）：伸出后 38dp 顶角大半落在屏外，外面再按纸面形状裁一刀，屏上仍是设计稿的圆角纸面；
            // 玻璃自带的投影随之被裁，这里按屏上形状补回同一档投影（投影在不透明的玻璃底下，只露出顶沿之上那一圈）。
            // 投影框与裁切框的底都伸出屏外：屏底不落投影边 / 裁切边。
            Box(
                Modifier
                    .matchParentSize()
                    .overscanned(overscan, sides = false)
                    .shadow(liuliGlassElevation(LiuliGlassRole.Panel), LiuliShapes.sheet, clip = false),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .overscanned(overscan, sides = false)
                    .clip(LiuliShapes.sheet),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .overscanned(overscan, sides = true)
                        .liuliGlass(LiuliShapes.sheet, dark, role = LiuliGlassRole.Panel),
                )
            }
            content()
        }
    }

    @Composable
    override fun Toolbar(content: @Composable RowScope.() -> Unit) {
        LiuliFloatingBar(content = content)
    }

    @Composable
    override fun RecordingCard(level: Float, durationMs: Long, cancelling: Boolean) {
        val dark = LocalIsDarkTheme.current
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LiuliPageGeometry.floatingBarInset)
                .padding(bottom = 8.dp)
                .liuliGlass(LiuliShapes.overlay, dark, role = LiuliGlassRole.Panel),
        ) {
            VoiceRecordingOverlay(level, durationMs, cancelling)
        }
    }

    @Composable
    override fun Snackbar(state: SnackbarHostState, modifier: Modifier) {
        // 宿主自带导航栏内距，这里只补键盘多出的那段。
        LiuliSnackbarHost(
            state,
            modifier
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
                .padding(bottom = SHEET_BOTTOM_RESERVE),
        )
    }

    @Composable
    override fun tileRim(): Modifier =
        Modifier.border(1.dp, LiuliMaterials.cardRim(LocalIsDarkTheme.current), RoundedCornerShape(tileCorner))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun MentionPicker(
        characters: List<CharacterEntity>,
        selected: List<String>,
        availability: Map<String, MentionAvailability>,
        onToggle: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        LiuliSheetShell(onDismissRequest = onDismiss, sheetState = rememberLiuliInstantSheetState()) {
            ComposeMentionPickerBody(
                characters = characters,
                selected = selected,
                availability = availability,
                dividerColor = LiuliMaterials.divider(LocalIsDarkTheme.current),
                done = { LiuliButton(onClick = onDismiss, style = LiuliButtonStyle.Text) { Text(composeMentionDoneText(selected)) } },
                onToggle = onToggle,
            )
        }
    }

    @Composable
    override fun KeepDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onDismissRequest: () -> Unit) {
        LiuliDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.moment_compose_keep_title),
            body = stringResource(R.string.moment_compose_keep_body),
            content = {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    LiuliButton(onClick = onDiscard, style = LiuliButtonStyle.Text, danger = true) {
                        Text(stringResource(R.string.moment_compose_not_keep))
                    }
                    LiuliButton(onClick = onKeep, style = LiuliButtonStyle.Prominent) {
                        Text(stringResource(R.string.moment_compose_keep))
                    }
                }
            },
        )
    }
}

/** 往屏外伸：底边伸 [over]、[sides] 时左右各伸 [over]，顶边不伸（纸面上沿）；自身仍按原大小占位。 */
private fun Modifier.overscanned(over: Dp, sides: Boolean): Modifier = layout { measurable, constraints ->
    val o = over.roundToPx()
    val side = if (sides) o else 0
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + side * 2, constraints.maxHeight + o))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-side, 0) }
}
