package com.situ.aichat.ui.liuli.story

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
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliField
import com.situ.aichat.ui.liuli.designsystem.LiuliSheetShell
import com.situ.aichat.ui.liuli.designsystem.LiuliTheme
import com.situ.aichat.ui.story.StoryTextSheetSpec
import com.situ.aichat.ui.story.storyEditorClamp

/**
 * 琉璃文本编辑弹层（琉璃 2.0 卷六·三·上 §4.14）：与暖陶 [com.situ.aichat.ui.story.StoryTextEditorSheet] 吃同一份规格
 * （书页创作三字段 + 创建屏六字段）与同一条截断式；玻璃弹层题头带关闭圆，字数上限 / 填入默认 / 取消确认同暖陶。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliStoryTextEditorSheet(spec: StoryTextSheetSpec, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(spec.initialText) }
    val onGlass = LiuliTheme.onGlass
    LiuliSheetShell(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = spec.title,
        subtitle = spec.subtitle,
    ) {
        // 修饰链 = 暖陶原链（LiuliSheetShell 约定：内容自带滚动 / 内距 / 导航栏 / 键盘）。
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiuliField(
                value = text,
                onValueChange = { new -> text = storyEditorClamp(new, spec.maxLength) },
                placeholder = spec.placeholder,
                singleLine = false,
                minHeight = 200.dp,
                // 封顶后字段内部自行滚动，长文不再把下方按钮行顶出屏幕（忌口默认全文 ~24 行是最狠场景）
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fill = spec.fillDefault
                if (spec.fillDefaultLabel != null && fill != null) {
                    LiuliButton(onClick = { text = fill() }, style = LiuliButtonStyle.Glass) { Text(spec.fillDefaultLabel) }
                }
                Spacer(Modifier.weight(1f))
                spec.maxLength?.let {
                    Text(stringResource(R.string.story_editor_count_limited, text.length, it), style = AppTypography.secondary, color = onGlass.secondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                LiuliButton(onClick = onDismiss, style = LiuliButtonStyle.Text) { Text(stringResource(R.string.action_cancel)) }
                LiuliButton(onClick = { spec.onConfirm(text); onDismiss() }, style = LiuliButtonStyle.Prominent) { Text(stringResource(R.string.action_confirm)) }
            }
        }
    }
}
