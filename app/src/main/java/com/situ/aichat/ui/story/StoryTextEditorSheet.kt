package com.situ.aichat.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.situ.aichat.R
import com.situ.aichat.ui.designsystem.AppButton
import com.situ.aichat.ui.designsystem.AppButtonStyle
import com.situ.aichat.ui.designsystem.AppSheet
import com.situ.aichat.ui.designsystem.AppTextArea
import com.situ.aichat.ui.designsystem.AppTheme

/**
 * 可复用全屏文本编辑面板（1:1 iOS `StoryTextEditorSheet`）。标题 + 可选副标题 + 多行输入 + 可选字数上限 +
 * 可选「填入默认」按钮（writerIdentity/writingRules 用）。确认把文本交给 [onConfirm]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryTextEditorSheet(
    title: String,
    subtitle: String?,
    placeholder: String,
    initialText: String,
    maxLength: Int?,
    fillDefaultLabel: String?,
    fillDefault: (() -> String)?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initialText) }

    AppSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = AppTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = AppTheme.typography.secondary, color = AppTheme.colors.text.secondary) }

            AppTextArea(
                value = text,
                onValueChange = { new -> text = storyEditorClamp(new, maxLength) },
                placeholder = placeholder,
                minHeight = 200.dp,
                // 封顶后字段内部自行滚动，长文不再把下方按钮行顶出屏幕（忌口默认全文 ~24 行是最狠场景）
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fillDefaultLabel != null && fillDefault != null) {
                    AppButton(onClick = { text = fillDefault() }, style = AppButtonStyle.Tonal) { Text(fillDefaultLabel) }
                }
                Spacer(Modifier.weight(1f))
                maxLength?.let {
                    Text(
                        stringResource(R.string.story_editor_count_limited, text.length, it),
                        style = AppTheme.typography.secondary,
                        color = AppTheme.colors.text.secondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                AppButton(onClick = onDismiss, style = AppButtonStyle.Text) { Text(stringResource(R.string.action_cancel)) }
                AppButton(onClick = { onConfirm(text); onDismiss() }, style = AppButtonStyle.Primary) { Text(stringResource(R.string.action_confirm)) }
            }
        }
    }
}

/** 文本弹层规格（琉璃 2.0 卷六·三：书页创作三字段 + 创建屏六字段两张脸共用同一份）。 */
internal data class StoryTextSheetSpec(
    val title: String,
    val subtitle: String?,
    val placeholder: String,
    val initialText: String,
    val maxLength: Int?,
    val fillDefaultLabel: String?,
    val fillDefault: (() -> String)?,
    val onConfirm: (String) -> Unit,
)

/** 吃规格的重载（转给原签名·零行为差）。 */
@Composable
internal fun StoryTextEditorSheet(spec: StoryTextSheetSpec, onDismiss: () -> Unit) = StoryTextEditorSheet(
    title = spec.title, subtitle = spec.subtitle, placeholder = spec.placeholder, initialText = spec.initialText,
    maxLength = spec.maxLength, fillDefaultLabel = spec.fillDefaultLabel, fillDefault = spec.fillDefault,
    onConfirm = spec.onConfirm, onDismiss = onDismiss,
)

/** 输入截断（原 onValueChange 式·纯·T1）：有上限且超了 → 截到上限，否则原样。 */
internal fun storyEditorClamp(text: String, maxLength: Int?): String =
    if (maxLength != null && text.length > maxLength) text.take(maxLength) else text
