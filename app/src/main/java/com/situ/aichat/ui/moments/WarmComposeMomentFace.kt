package com.situ.aichat.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.ui.chat.VoiceRecordingOverlay
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppDialog
import com.situ.aichat.ui.designsystem.AppShapes
import com.situ.aichat.ui.designsystem.AppSheet
import com.situ.aichat.ui.designsystem.AppSnackbarHost
import com.situ.aichat.ui.designsystem.AppSpacing
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.designsystem.appCardSurface
import com.situ.aichat.ui.designsystem.grainSurface
import com.situ.aichat.ui.offline.MeetingSky

/**
 * 发布页·暖陶脸（朋友圈发布页·乙 §4.3–§4.10）：纸面 = 浅色 base + 软影 / 深色 raised 明度分层 + 纸感颗粒；
 * 工具栏 = raised 底 + 顶 1dp 线（同日记撰写动作条）；弹层 / 对话框 / 提示条用暖陶 App* 件。布局与时机全在骨架。
 */
internal object WarmComposeMomentFace : ComposeMomentFace {
    override val skyFadesOut: Boolean = false
    override val tileCorner: Dp = 8.dp // = AppShapes.small

    @Composable
    override fun Host(modifier: Modifier, content: @Composable BoxScope.() -> Unit, overlay: @Composable BoxScope.() -> Unit) {
        Box(modifier.fillMaxSize().background(AppTheme.colors.surface.base)) {
            content()
            overlay()
        }
    }

    @Composable
    override fun TopBar(lightSky: Boolean, canPublish: Boolean, onCancel: () -> Unit, onPublish: () -> Unit) {
        val skyInk = if (lightSky) MeetingSky.Ink else MeetingSky.WarmWhite
        Row(
            Modifier.fillMaxSize().padding(start = AppSpacing.gutterForTextButton, end = AppSpacing.gutterForSolid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.action_cancel),
                style = AppTypography.label,
                color = skyInk,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(AppShapes.full)
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            AppButton(
                onClick = onPublish,
                style = AppButtonStyle.Primary,
                enabled = canPublish,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.moment_compose_publish))
            }
        }
    }

    @Composable
    override fun Sheet(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
        val colors = AppTheme.colors
        // 浅色纸 = base + 软影浮在天色上；深色靠明度分层（raised）不画影。
        val lifted = if (colors.isDark) {
            Modifier
        } else {
            Modifier.shadow(elevation = 12.dp, shape = AppShapes.sheet, ambientColor = colors.text.primary, spotColor = colors.text.primary)
        }
        Box(
            modifier
                .then(lifted)
                .clip(AppShapes.sheet)
                .background(if (colors.isDark) colors.surface.raised else colors.surface.base)
                .grainSurface(),
            content = content,
        )
    }

    @Composable
    override fun Toolbar(content: @Composable RowScope.() -> Unit) {
        val colors = AppTheme.colors
        Column(Modifier.fillMaxWidth().background(colors.surface.raised)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surface.stroke))
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }

    @Composable
    override fun RecordingCard(level: Float, durationMs: Long, cancelling: Boolean) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .appCardSurface(raised = true, cornerRadius = 28.dp),
        ) {
            VoiceRecordingOverlay(level, durationMs, cancelling)
        }
    }

    @Composable
    override fun Snackbar(state: SnackbarHostState, modifier: Modifier) {
        AppSnackbarHost(
            state,
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                .padding(bottom = SHEET_BOTTOM_RESERVE),
        )
    }

    @Composable
    override fun tileRim(): Modifier =
        Modifier.border(0.5.dp, AppTheme.colors.text.primary.copy(alpha = 0.06f), RoundedCornerShape(tileCorner))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun MentionPicker(
        characters: List<CharacterEntity>,
        selected: List<String>,
        availability: Map<String, MentionAvailability>,
        onToggle: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ComposeMentionPickerBody(
                characters = characters,
                selected = selected,
                availability = availability,
                dividerColor = AppTheme.colors.surface.stroke,
                done = { AppButton(onClick = onDismiss, style = AppButtonStyle.Text) { Text(composeMentionDoneText(selected)) } },
                onToggle = onToggle,
            )
        }
    }

    @Composable
    override fun KeepDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onDismissRequest: () -> Unit) {
        AppDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.moment_compose_keep_title),
            body = stringResource(R.string.moment_compose_keep_body),
            content = {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    AppButton(onClick = onDiscard, style = AppButtonStyle.Text, danger = true) {
                        Text(stringResource(R.string.moment_compose_not_keep))
                    }
                    AppButton(onClick = onKeep, style = AppButtonStyle.Primary) {
                        Text(stringResource(R.string.moment_compose_keep))
                    }
                }
            },
        )
    }
}
