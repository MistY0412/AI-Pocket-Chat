package com.situ.aichat.ui.character

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppDropdownField
import com.situ.aichat.ui.designsystem.AppDropdownMenuItem
import com.situ.aichat.ui.designsystem.AppLoadingRing
import com.situ.aichat.ui.designsystem.AppLoadingRingSize
import com.situ.aichat.ui.designsystem.AppSlider
import com.situ.aichat.ui.settings.systemVoiceQualityLabel
import kotlin.math.roundToInt

/**
 * Per-character remote-TTS voice settings (MiniMax-specific emotion/speed/pitch + the remote voice
 * id). The system voice id and live voice picker live on the TTS settings screen; here the voice id
 * is a plain field the user pastes. Emotion "auto" follows each message's mood (see TtsService).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSettingsSection(
    voiceIdentifier: String,
    remoteVoiceId: String,
    emotionRaw: String,
    speed: Double,
    pitch: Int,
    systemVoices: List<SystemVoiceOption>,
    previewBusy: Boolean,
    previewError: String?,
    onLoadSystemVoices: () -> Unit,
    onPreview: () -> Unit,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
) {
    SectionHeader(stringResource(R.string.char_section_voice))
    FormField(
        value = remoteVoiceId,
        onValueChange = { v -> onUpdate { it.copy(remoteVoiceID = v) } },
        label = CharacterVoiceText.REMOTE_ID_LABEL,
        placeholder = CharacterVoiceText.REMOTE_ID_PLACEHOLDER,
        footer = CharacterVoiceText.REMOTE_ID_FOOTER,
        singleLine = true,
    )

    // 系统音色（全局引擎=系统 TTS 时用）：从设备 TextToSpeech 列举，留空=默认 zh-CN。
    var sysVoiceOpen by remember { mutableStateOf(false) }
    AppDropdownField(
        value = voiceIdentifier.ifEmpty { CharacterVoiceText.SYSTEM_VOICE_DEFAULT },
        expanded = sysVoiceOpen,
        onExpandedChange = { open -> sysVoiceOpen = open; if (open) onLoadSystemVoices() },
        label = CharacterVoiceText.SYSTEM_VOICE_LABEL,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppDropdownMenuItem(
            text = CharacterVoiceText.SYSTEM_VOICE_DEFAULT,
            selected = voiceIdentifier.isEmpty(),
            onClick = { onUpdate { it.copy(voiceIdentifier = "") }; sysVoiceOpen = false },
        )
        systemVoices.forEach { v ->
            AppDropdownMenuItem(
                text = "${v.name}  ·  ${systemVoiceQualityLabel(v.quality)}",
                selected = voiceIdentifier == v.id,
                onClick = { onUpdate { it.copy(voiceIdentifier = v.id) }; sysVoiceOpen = false },
            )
        }
    }

    var emotionOpen by remember { mutableStateOf(false) }
    AppDropdownField(
        value = emotionLabel(emotionRaw),
        expanded = emotionOpen,
        onExpandedChange = { emotionOpen = it },
        label = CharacterVoiceText.EMOTION_LABEL,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EMOTION_OPTIONS.forEach { (raw, label) ->
            AppDropdownMenuItem(
                text = label,
                selected = emotionRaw == raw,
                onClick = { onUpdate { it.copy(ttsEmotionRaw = raw) }; emotionOpen = false },
            )
        }
    }

    Text(
        // contacts-character-4：对齐 iOS step 0.05 / "%.2fx"（MiniMax 语速参数粒度，非钱/概率）
        "${CharacterVoiceText.SPEED_TITLE} ${CharacterVoiceText.speedValue(speed)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppSlider(
        value = speed.toFloat().coerceIn(0.5f, 2.0f),
        onValueChange = { v -> onUpdate { it.copy(ttsSpeed = snapTtsSpeed(v)) } },
        valueRange = 0.5f..2.0f,
        steps = 29, // 0.5..2.0 step 0.05 = 31 档（内部 29 点），对齐 iOS
    )

    Text(
        "${CharacterVoiceText.PITCH_TITLE} $pitch",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppSlider(
        value = pitch.toFloat().coerceIn(-12f, 12f),
        onValueChange = { v -> onUpdate { it.copy(ttsPitch = v.roundToInt()) } },
        valueRange = -12f..12f,
        steps = 23,
    )

    AppButton(
        onClick = onPreview,
        enabled = !previewBusy,
        modifier = Modifier.fillMaxWidth(),
        style = AppButtonStyle.Tonal,
    ) {
        if (previewBusy) {
            AppLoadingRing(size = AppLoadingRingSize.Small)
            Spacer(Modifier.width(8.dp))
        }
        Text(CharacterVoiceText.PREVIEW)
    }
    if (previewError != null) {
        Text(
            previewError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SectionFooter(CharacterVoiceText.FOOTER)
}

/** 语音段写死的中文（卷五：收成常量供两张脸共用·字面逐字不改）。 */
internal object CharacterVoiceText {
    const val REMOTE_ID_LABEL = "音色 ID（远程 TTS）"
    const val REMOTE_ID_PLACEHOLDER = "留空 = 跟随全局默认音色"
    const val REMOTE_ID_FOOTER = "在「我的 → 语音 / TTS」里拉取并复制音色 ID（远程引擎用）。"
    const val SYSTEM_VOICE_LABEL = "系统音色"
    const val SYSTEM_VOICE_DEFAULT = "默认（zh-CN）"
    const val EMOTION_LABEL = "情绪（MiniMax 专属）"
    const val SPEED_TITLE = "语速"
    const val PITCH_TITLE = "音调"
    const val PREVIEW = "试听"
    const val FOOTER = "情绪 / 语速 / 音调为 MiniMax 远程 TTS 专属，其它引擎会忽略；情绪「自动」会跟随每条消息的心情映射。试听用「我的 → 语音 / TTS」选定的引擎合成本角色的声音。"
    /** contacts-character-4：对齐 iOS step 0.05 / "%.2fx"。 */
    fun speedValue(speed: Double): String = "${"%.2f".format(speed)}x"
}

/** 语速吸附到 0.05 一格（对齐 iOS）。 */
internal fun snapTtsSpeed(raw: Float): Double = (raw * 20).roundToInt() / 20.0

internal val EMOTION_OPTIONS: List<Pair<String, String>> = listOf(
    "auto" to "自动（跟随心情）",
    "happy" to "开心",
    "sad" to "伤心",
    "angry" to "生气",
    "fearful" to "害怕",
    "disgusted" to "厌恶",
    "surprised" to "惊讶",
    "calm" to "平静",
    "fluent" to "流畅（仅 2.6/2.8）",
    "whisper" to "耳语（仅 2.6）",
)

internal fun emotionLabel(raw: String): String =
    EMOTION_OPTIONS.firstOrNull { it.first == raw }?.second ?: raw
