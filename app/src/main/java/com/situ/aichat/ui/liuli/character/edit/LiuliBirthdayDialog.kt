package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.ui.character.PastOrPresentDates
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import java.util.Calendar

/**
 * 生日对话框（琉璃 2.0 卷五 §4.12）：[LiuliDialog] 玻璃小卡 + M3 `DatePicker`（透明容器·无标题 / 大标题 / 模式切换·
 * 同 `LiuliFutureMeetingFormSheet` 已放行的写法）；日期规则走共用件 [PastOrPresentDates]（与暖陶同一枚）。
 * 「确定」写入所选值（可为 null·同暖陶）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiuliBirthdayDialog(initialMillis: Long, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = 1900..Calendar.getInstance().get(Calendar.YEAR),
        selectableDates = PastOrPresentDates,
    )
    LiuliDialog(
        onDismissRequest = onDismiss,
        title = null,
        confirmText = stringResource(R.string.action_confirm),
        onConfirm = { onConfirm(state.selectedDateMillis) },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            )
        },
    )
}
