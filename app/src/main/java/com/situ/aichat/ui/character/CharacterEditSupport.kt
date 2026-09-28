package com.situ.aichat.ui.character

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.util.Calendar
import java.util.TimeZone

// 角色编辑页的生日日期规则与写死中文（琉璃 2.0 卷五 §3.2：自 CharacterEditScreen **只搬不改**抽出·两张脸共用）。

@OptIn(ExperimentalMaterial3Api::class)
internal object PastOrPresentDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
    override fun isSelectableYear(year: Int): Boolean = year <= Calendar.getInstance().get(Calendar.YEAR)
}

/**
 * 2000-01-01, matching iOS's default birthday seed. 取 **UTC 零点**：只用作 M3 `DatePicker` 的初值，而它按 UTC 读日期——
 * 原先取本地零点，东八区打开生日对话框会落在 1999-12-31（琉璃 2.0 卷五复核 R1·两张脸同病同修）。
 */
internal fun defaultBirthdayMillis(): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2000, Calendar.JANUARY, 1)
    }.timeInMillis

internal fun yearsSince(millis: Long): Int {
    val birth = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
    return age
}

/** 暖陶编辑页里写死的中文（卷五：收成常量供两张脸共用·字面逐字不改）。 */
internal object CharacterEditText {
    const val MEETINGS_SECTION = "见面回忆"
    const val MEETINGS_OPEN = "查看见面回忆"
    const val MEETINGS_FOOTER = "线下见面的纪念卡、只读回顾与规则兜底摘要的手动重试。"
}
