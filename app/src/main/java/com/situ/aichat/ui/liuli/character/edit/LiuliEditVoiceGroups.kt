package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.tts.SystemVoiceOption
import com.situ.aichat.ui.character.CharacterEditState
import com.situ.aichat.ui.character.CharacterEditText
import com.situ.aichat.ui.character.CharacterVoiceText
import com.situ.aichat.ui.character.EMOTION_OPTIONS
import com.situ.aichat.ui.character.emotionLabel
import com.situ.aichat.ui.character.snapTtsSpeed
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliSpinner
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliInputRow
import com.situ.aichat.ui.liuli.page.LiuliMenuRow
import com.situ.aichat.ui.liuli.page.LiuliNavRow
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliSliderRow
import com.situ.aichat.ui.liuli.page.LiuliToggleRow
import com.situ.aichat.ui.settings.systemVoiceQualityLabel
import kotlin.math.roundToInt

/**
 * 语音组（§4.9）：远程音色 ID / 系统音色（展开即拉取）/ 情绪（两菜单互斥）/ 语速（0.05 吸附）/ 音调 / 试听。
 * 写死的中文一律读暖陶同一组常量 [CharacterVoiceText]。
 */
@Composable
internal fun LiuliEditVoiceGroup(
    state: CharacterEditState,
    systemVoices: List<SystemVoiceOption>,
    previewBusy: Boolean,
    previewError: String?,
    onLoadSystemVoices: () -> Unit,
    onPreview: () -> Unit,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var sysOpen by remember { mutableStateOf(false) }
    var emoOpen by remember { mutableStateOf(false) }
    LiuliGroup(modifier = modifier, header = stringResource(R.string.char_section_voice), footer = CharacterVoiceText.FOOTER) {
        LiuliInputRow(
            label = CharacterVoiceText.REMOTE_ID_LABEL,
            value = state.remoteVoiceID,
            onValueChange = { v -> onUpdate { it.copy(remoteVoiceID = v) } },
            placeholder = CharacterVoiceText.REMOTE_ID_PLACEHOLDER,
            supportingText = CharacterVoiceText.REMOTE_ID_FOOTER,
            supportingIsError = false,
            divider = false,
        )
        LiuliMenuRow(
            title = CharacterVoiceText.SYSTEM_VOICE_LABEL,
            value = state.voiceIdentifier.ifEmpty { CharacterVoiceText.SYSTEM_VOICE_DEFAULT },
            options = listOf(
                LiuliMenuEntry(CharacterVoiceText.SYSTEM_VOICE_DEFAULT, selected = state.voiceIdentifier.isEmpty()) {
                    onUpdate { it.copy(voiceIdentifier = "") }
                },
            ) + systemVoices.map { v ->
                LiuliMenuEntry("${v.name}  ·  ${systemVoiceQualityLabel(v.quality)}", selected = state.voiceIdentifier == v.id) {
                    onUpdate { it.copy(voiceIdentifier = v.id) }
                }
            },
            expanded = sysOpen,
            onExpandedChange = { open ->
                sysOpen = open
                if (open) {
                    emoOpen = false
                    onLoadSystemVoices()
                }
            },
        )
        LiuliMenuRow(
            title = CharacterVoiceText.EMOTION_LABEL,
            value = emotionLabel(state.ttsEmotionRaw),
            options = EMOTION_OPTIONS.map { (raw, label) ->
                LiuliMenuEntry(label, selected = state.ttsEmotionRaw == raw) { onUpdate { it.copy(ttsEmotionRaw = raw) } }
            },
            expanded = emoOpen,
            onExpandedChange = { open ->
                emoOpen = open
                if (open) sysOpen = false
            },
        )
        LiuliSliderRow(
            title = CharacterVoiceText.SPEED_TITLE,
            valueLabel = CharacterVoiceText.speedValue(state.ttsSpeed),
            value = state.ttsSpeed.toFloat().coerceIn(0.5f, 2f),
            onValueChange = { f -> onUpdate { it.copy(ttsSpeed = snapTtsSpeed(f)) } },
            valueRange = 0.5f..2f,
            steps = 29, // 0.5..2.0 step 0.05 = 31 档（内部 29 点），对齐 iOS（同暖陶）
        )
        LiuliSliderRow(
            title = CharacterVoiceText.PITCH_TITLE,
            valueLabel = "${state.ttsPitch}",
            value = state.ttsPitch.toFloat().coerceIn(-12f, 12f),
            onValueChange = { f -> onUpdate { it.copy(ttsPitch = f.roundToInt()) } },
            valueRange = -12f..12f,
            steps = 23,
        )
        LiuliRowBase(minHeight = 0.dp, verticalPadding = 12.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LiuliButton(onClick = onPreview, style = LiuliButtonStyle.Glass, enabled = !previewBusy, modifier = Modifier.fillMaxWidth()) {
                    if (previewBusy) LiuliSpinner()
                    Text(CharacterVoiceText.PREVIEW)
                }
                previewError?.let { Text(it, style = AppTypography.caption, color = colors.status.onError) }
            }
        }
    }
}

/** 提示词模块组（§4.9·仅编辑）：角色专属开关立即落库（VM `setModuleOverride`）；开 → 编辑入口，关 → 副标「使用全局」。 */
@Composable
internal fun LiuliEditModulesGroup(
    hasOverride: Boolean,
    onSetOverride: (Boolean) -> Unit,
    onEditModules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.pm_title),
        footer = stringResource(R.string.pm_use_character_override_footer),
    ) {
        LiuliToggleRow(
            title = stringResource(R.string.pm_use_character_override),
            checked = hasOverride,
            onCheckedChange = onSetOverride,
            subtitle = if (!hasOverride) stringResource(R.string.pm_using_global) else null,
            divider = false,
        )
        if (hasOverride) {
            LiuliNavRow(stringResource(R.string.pm_edit_character_modules), onClick = onEditModules)
        }
    }
}

/** 见面回忆组（§4.9·仅编辑）：一行入口（文案读暖陶同一组常量 [CharacterEditText]）。 */
@Composable
internal fun LiuliEditMeetingsGroup(onOpenMeetings: () -> Unit, modifier: Modifier = Modifier) {
    LiuliGroup(modifier = modifier, header = CharacterEditText.MEETINGS_SECTION, footer = CharacterEditText.MEETINGS_FOOTER) {
        LiuliNavRow(CharacterEditText.MEETINGS_OPEN, onClick = onOpenMeetings, divider = false)
    }
}
