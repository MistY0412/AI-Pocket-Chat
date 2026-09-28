package com.situ.aichat.ui.moments

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.moments.MentionAvailability
import java.time.ZoneId

/** 发布页的「脸」（乙 L-5）：两张脸各实现一个 object（`WarmComposeMomentFace` / `LiuliComposeMomentFace`），只管长相；布局与时机全在骨架。 */
internal interface ComposeMomentFace {
    /** 天色底部是否渐隐（琉璃 true：玻璃纸面下透出柔光底；暖陶 false）。 */
    val skyFadesOut: Boolean
    /** 图格与「+」格圆角（暖陶 8dp·琉璃 10dp）。 */
    val tileCorner: Dp
    /** 页面宿主：content = 天色 + 天上文字 + 弹层 / 对话框 / 查看器；overlay = 纸面 / 顶栏 / 底部工具区 / 提示条。 */
    @Composable fun Host(modifier: Modifier, content: @Composable BoxScope.() -> Unit, overlay: @Composable BoxScope.() -> Unit)
    @Composable fun TopBar(lightSky: Boolean, canPublish: Boolean, onCancel: () -> Unit, onPublish: () -> Unit)
    @Composable fun Sheet(modifier: Modifier, content: @Composable BoxScope.() -> Unit)
    @Composable fun Toolbar(content: @Composable RowScope.() -> Unit)
    @Composable fun RecordingCard(level: Float, durationMs: Long, cancelling: Boolean)
    @Composable fun Snackbar(state: SnackbarHostState, modifier: Modifier)
    /** 图格描边（圆角 = [tileCorner]）。 */
    @Composable fun tileRim(): Modifier
    @Composable fun MentionPicker(
        characters: List<CharacterEntity>,
        selected: List<String>,
        availability: Map<String, MentionAvailability>,
        onToggle: (String) -> Unit,
        onDismiss: () -> Unit,
    )
    @Composable fun KeepDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onDismissRequest: () -> Unit)
}

/** 「说一段」五条流的一帧快照。 */
internal data class ComposeMomentVoiceUi(
    val recording: Boolean = false, val level: Float = 0f, val durationMs: Long = 0L,
    val cancelling: Boolean = false, val transcribing: Boolean = false,
)

/** 骨架消费的整页 UI 模型（路由从 VM 收集后组装；测试直接构造）。 */
internal data class ComposeMomentUi(
    val state: ComposeMomentState,
    val characters: List<CharacterEntity>,
    val availability: Map<String, MentionAvailability>,
    val voice: ComposeMomentVoiceUi,
    val nowMillis: Long,
    val zone: ZoneId,
)

/** 纸面 / 工具栏上的写动作（全部转给 VM；页级流程——关页 / 选图 / 弹层——在 [ComposeMomentPageState]）。 */
internal class ComposeMomentActions(
    val onContentChange: (String) -> Unit,
    val onRemoveImage: (String) -> Unit,
    val onMoveImage: (from: Int, to: Int) -> Unit,
    val onToggleMention: (String) -> Unit,
    val onClearMentions: () -> Unit,
    val onStartVoice: () -> Unit,
    val onVoiceDrag: (Float) -> Unit,
    val onFinishVoice: () -> Unit,
)
