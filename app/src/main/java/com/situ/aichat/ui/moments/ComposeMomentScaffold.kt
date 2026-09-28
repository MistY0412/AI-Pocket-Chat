package com.situ.aichat.ui.moments

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.ui.chat.ChatImageViewer
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.designsystem.AppMomentIcons
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.diary.DiaryMicButton
import kotlinx.coroutines.delay
import java.time.ZoneId

// 发布页共用骨架（朋友圈发布页·乙 §3.3.3 / §3.3.4 / §4.1 / §4.7）：两层摆放、键盘联动几何、工具栏条目、弹层时机、入口路由。
// 只用 AppTheme token / 甲卷 VM / 日记与聊天的 internal 件；不调用任何 App* / Liuli* 组件——那些只在两个脸对象里。

// ── §4.1 几何与键盘联动（锁定）：天色 / 纸面顶都从状态栏底算起。 ──
internal val SKY_FULL = 204.dp
internal val SKY_SLIM = 56.dp
internal val SHEET_TOP_FULL = 176.dp
internal val SHEET_TOP_SLIM = 48.dp
internal val IME_COLLAPSE = 160.dp
internal val TOP_BAR = 56.dp
internal val STAMP_TOP = 64.dp
internal val STAMP_DATE_GAP = 8.dp
internal val SLIM_TEXT_SIDE = 96.dp
internal val SHEET_BOTTOM_RESERVE = 80.dp

/** 时钟刷新周期（锁定 60s·对齐整分）。 */
private const val MINUTE_MS = 60_000L

/**
 * 键盘收起进度 p = 键盘底部高度 / [IME_COLLAPSE]，钳 0..1（0 = 满天色，1 = 窄天色）。**只许在布局 / 绘制阶段调**
 * （`Modifier.layout {}` / `graphicsLayer {}` / `drawBehind {}` 的 Density 接收者）——键盘每一帧只重排不重组。
 */
internal fun Density.composeCollapse(ime: WindowInsets): Float =
    (ime.getBottom(this) / IME_COLLAPSE.toPx()).coerceIn(0f, 1f)

/** 天色块高度 = 状态栏 + lerp(204, 56, p)（布局阶段读 insets）。 */
internal fun Modifier.composeSkyHeight(status: WindowInsets, ime: WindowInsets): Modifier = layout { measurable, constraints ->
    val height = status.getTop(this) + lerp(SKY_FULL, SKY_SLIM, composeCollapse(ime)).roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(constraints.maxWidth, height) { placeable.place(0, 0) }
}

/** 纸面框：顶 = 状态栏 + lerp(176, 48, p)、底到屏底；自身占满（布局阶段读 insets）。 */
internal fun Modifier.composeSheetFrame(status: WindowInsets, ime: WindowInsets): Modifier = layout { measurable, constraints ->
    val top = status.getTop(this) + lerp(SHEET_TOP_FULL, SHEET_TOP_SLIM, composeCollapse(ime)).roundToPx()
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, (height - top).coerceAtLeast(0)))
    layout(constraints.maxWidth, height) { placeable.place(0, top) }
}

/** 当下时刻（整分对齐刷新）。 */
@Composable
internal fun rememberComposeNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(MINUTE_MS - value % MINUTE_MS)
        value = System.currentTimeMillis()
    }
}

/** 入口路由：收集 VM 的状态与「说一段」五条流，组装 UI 模型交给骨架（两张脸的入口各传自己的脸）。 */
@Composable
internal fun ComposeMomentRoute(onClose: () -> Unit, face: ComposeMomentFace, viewModel: ComposeMomentViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val availability by viewModel.mentionAvailability.collectAsStateWithLifecycle()
    val recording by viewModel.voice.voiceRecording.collectAsStateWithLifecycle()
    val level by viewModel.voice.voiceLevel.collectAsStateWithLifecycle()
    val durationMs by viewModel.voice.voiceDurationMs.collectAsStateWithLifecycle()
    val cancelling by viewModel.voice.voiceCancelling.collectAsStateWithLifecycle()
    val transcribing by viewModel.voice.isTranscribing.collectAsStateWithLifecycle()
    val nowMillis by rememberComposeNow()
    val page = rememberComposeMomentPageState(viewModel, onClose)
    val actions = remember(viewModel) {
        ComposeMomentActions(
            onContentChange = viewModel::setContent,
            onRemoveImage = viewModel::removeImage,
            onMoveImage = viewModel::moveImage,
            onToggleMention = viewModel::toggleMention,
            onClearMentions = { viewModel.state.value.mentions.forEach(viewModel::removeMention) },
            onStartVoice = { viewModel.voice.startVoice() },
            onVoiceDrag = { viewModel.voice.updateVoiceDrag(it) },
            onFinishVoice = { viewModel.voice.finishVoice() },
        )
    }
    val ui = ComposeMomentUi(
        state = state,
        characters = characters,
        availability = availability,
        voice = ComposeMomentVoiceUi(recording, level, durationMs, cancelling, transcribing),
        nowMillis = nowMillis,
        zone = ZoneId.systemDefault(),
    )
    ComposeMomentScaffold(face, ui, page, actions)
}

/** 骨架（§3.3.4·锁定结构）：content 层 = 天色 + 天上文字 + 弹层 / 对话框 / 查看器；overlay 层 = 纸面 / 顶栏 / 底部工具区 / 提示条。 */
@Composable
internal fun ComposeMomentScaffold(face: ComposeMomentFace, ui: ComposeMomentUi, page: ComposeMomentPageState, actions: ComposeMomentActions) {
    val locale = LocalConfiguration.current.locales[0]
    val moment = remember(ui.nowMillis / MINUTE_MS, ui.zone, locale) { composeSkyMoment(ui.nowMillis, ui.zone, locale) }
    val haptics = LocalAppHaptics.current
    val status = WindowInsets.statusBars
    val ime = WindowInsets.ime
    val title = stringResource(R.string.moment_compose_title)
    ComposeSkyStatusBarIcons(moment.lightSky)
    face.Host(
        modifier = Modifier.semantics { paneTitle = title },
        content = {
            ComposeMomentSkyBackdrop(
                moment, fadeOut = face.skyFadesOut, status = status, ime = ime,
                modifier = Modifier.fillMaxWidth().composeSkyHeight(status, ime).clipToBounds(),
            )
            ComposeMomentSkyText(moment, status = status, ime = ime)
            if (page.showPicker) face.MentionPicker(ui.characters, ui.state.mentions, ui.availability, actions.onToggleMention, page::closePicker)
            if (page.showKeepDialog) face.KeepDialog(onKeep = page::keepAndClose, onDiscard = page::discardAndClose, onDismissRequest = { page.showKeepDialog = false })
            page.viewerPath?.let { ChatImageViewer(imagePath = it, onDismiss = page::closeViewer) }
        },
        overlay = {
            face.Sheet(Modifier.fillMaxSize().composeSheetFrame(status, ime)) { ComposeMomentSheetBody(face, ui, page, actions) }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(TOP_BAR)) {
                face.TopBar(lightSky = moment.lightSky, canPublish = ui.state.canPublish, onCancel = page::attemptClose, onPublish = { page.publish(haptics) })
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (ui.voice.recording) face.RecordingCard(ui.voice.level, ui.voice.durationMs, ui.voice.cancelling)
                face.Toolbar { ComposeMomentToolbarItems(ui, page, actions) }
            }
            face.Snackbar(page.snackbarHostState, Modifier.align(Alignment.BottomCenter))
        },
    )
}

/**
 * 状态栏图标随天色（§11 T-1·复核 R1 裁决·PITFALLS §1d「强制深底场景必须接管系统栏图标色」）：天色压在状态栏后面，
 * 白天亮天 = 深图标、其余四桶暗天 = 浅图标（= 设计稿状态栏）；离页还原为跟随 App 深浅（同 `ui/theme/Theme.kt`）。
 * 用 SideEffect 写：与主题的 SideEffect 同一帧时排在它后面，不被覆盖；只在天色明暗 / App 深浅变时重跑。
 */
@Composable
private fun ComposeSkyStatusBarIcons(lightSky: Boolean) {
    val view = LocalView.current
    val appIsDark = AppTheme.colors.isDark
    val controller = remember(view) { (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) } }
    SideEffect { controller?.isAppearanceLightStatusBars = lightSky }
    DisposableEffect(controller, appIsDark) {
        onDispose { controller?.isAppearanceLightStatusBars = !appIsDark }
    }
}

/** 工具栏条目（§4.7·两脸同）：加图 · 说一段 · 提醒谁看 · 弹簧 · 字数环。 */
@Composable
internal fun RowScope.ComposeMomentToolbarItems(ui: ComposeMomentUi, page: ComposeMomentPageState, actions: ComposeMomentActions) {
    ComposeMomentToolIcon(
        Icons.Filled.Add,
        stringResource(R.string.moment_compose_add_image),
        enabled = composeMomentCanAddImage(ui.state.images),
        onClick = page::pickImages,
    )
    DiaryMicButton(onStart = actions.onStartVoice, onDrag = actions.onVoiceDrag, onFinish = actions.onFinishVoice)
    ComposeMomentToolIcon(
        AppMomentIcons.At,
        stringResource(R.string.moment_compose_mention_title),
        enabled = ui.characters.isNotEmpty(),
        onClick = page::openPicker,
    )
    Spacer(Modifier.weight(1f))
    ComposeMomentCharRing(ui.state.charCount, modifier = Modifier.padding(end = 8.dp))
}
