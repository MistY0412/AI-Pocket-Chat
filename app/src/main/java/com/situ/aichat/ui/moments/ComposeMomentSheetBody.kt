package com.situ.aichat.ui.moments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.diary.DiaryTranscribingPill

/**
 * 纸面内容（朋友圈发布页·乙 §4.4 内容容器 + §4.5）：输入 → 落笔中 → 提醒签 → 拖动提示 → 图片格。两脸同。
 * 滚动视口止于工具栏上沿（底部让出 max(导航栏, 键盘) + [SHEET_BOTTOM_RESERVE]），光标自动滚进视口时不被工具栏挡。
 */
@Composable
internal fun ComposeMomentSheetBody(face: ComposeMomentFace, ui: ComposeMomentUi, page: ComposeMomentPageState, actions: ComposeMomentActions) {
    val reduceMotion = rememberReduceMotion()
    var dragging by remember { mutableStateOf(false) }
    val mentioned = remember(ui.state.mentions, ui.characters) {
        val byUuid = ui.characters.associateBy { it.uuid }
        ui.state.mentions.mapNotNull { byUuid[it] }
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(bottom = SHEET_BOTTOM_RESERVE),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
        ) {
            ComposeMomentInput(ui.state.content, actions.onContentChange)
            if (ui.voice.transcribing) {
                Spacer(Modifier.height(12.dp))
                DiaryTranscribingPill(reduceMotion)
            }
            if (mentioned.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ComposeMentionChip(mentioned, onOpen = page::openPicker, onClear = actions.onClearMentions)
            }
            if (ui.state.images.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                if (dragging) {
                    Text(
                        stringResource(R.string.moment_compose_drag_hint),
                        style = AppTypography.caption,
                        color = AppTheme.colors.text.tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ComposeMomentGrid(
                    images = ui.state.images,
                    corner = face.tileCorner,
                    rim = face.tileRim(),
                    onOpen = page::openViewer,
                    onRemove = actions.onRemoveImage,
                    onMove = actions.onMoveImage,
                    onAdd = page::pickImages,
                    onDraggingChange = { dragging = it },
                )
            }
        }
    }
}

/** 正文输入：16/28 正文·深陶光标；空时下层画提示语（同样式·secondary）。不截断（超字数靠字数环）。进页不请求焦点（拍板①）。 */
@Composable
private fun ComposeMomentInput(content: String, onValueChange: (String) -> Unit) {
    val colors = AppTheme.colors
    val style = AppTypography.body.copy(lineHeight = 28.sp, color = colors.text.primary)
    val hint = stringResource(R.string.moment_compose_hint)
    BasicTextField(
        value = content,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(colors.accent.primary),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp),
        decorationBox = { inner ->
            Box {
                if (content.isEmpty()) Text(hint, style = style.copy(color = colors.text.secondary))
                inner()
            }
        },
    )
}
