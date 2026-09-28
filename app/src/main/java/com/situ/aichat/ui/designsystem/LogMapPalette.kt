package com.situ.aichat.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/**
 * 上下文地图色（四期·图纸四·feature 层·设计语言 §1.1）：前置区 / 每轮会变 / 聊天记录 / 末尾块 / 时间标记短线。
 * 出处 = 过审效果图 `fable5_artifacts/mockups/phase4_log_and_saver_mockup.html` 附录 A。
 * 两张脸 × 深浅四套，按 [LocalAppSkin] + [LocalIsDarkTheme] 取；状态色、强调色一律用既有语义 token，不进这里。
 */
data class LogMapColors(val prefix: Color, val variable: Color, val history: Color, val tail: Color, val tick: Color)

/** 暖陶浅。 */
internal val LogMapClayLight = LogMapColors(Color(0xFFD9B8A6), Color(0xFFD98C7A), Color(0xFFB9C7B3), Color(0xFFD2BE8C), Color(0xF2FFFFFF))

/** 暖陶深。 */
internal val LogMapClayDark = LogMapColors(Color(0xFF8A6150), Color(0xFFB0583F), Color(0xFF556650), Color(0xFF7F6C3C), Color(0xD9FFFFFF))

/** 琉璃浅。 */
internal val LogMapLiuliLight = LogMapColors(Color(0xFFB7ABF6), Color(0xFFFF9E7A), Color(0xFFA6CFF0), Color(0xFFF0B5D3), Color(0xFAFFFFFF))

/** 琉璃深。 */
internal val LogMapLiuliDark = LogMapColors(Color(0xFF6454C4), Color(0xFFC8663F), Color(0xFF2F6594), Color(0xFF8F4474), Color(0xE6FFFFFF))

/** 「每轮会变」斜纹：块底色上 135° 白色 45% 条纹，条宽 3dp、周期 7dp（效果图 `.hatch`）。 */
val LogMapHatchColor: Color = Color.White.copy(alpha = 0.45f)

@Composable
@ReadOnlyComposable
fun logMapColors(): LogMapColors {
    val dark = LocalIsDarkTheme.current
    return if (LocalAppSkin.current == AppSkin.LIULI) {
        if (dark) LogMapLiuliDark else LogMapLiuliLight
    } else {
        if (dark) LogMapClayDark else LogMapClayLight
    }
}

/**
 * 上下文阅读器色（四期·图纸五·feature 层）：概览细条四色 + 搜索命中 / 当前处。
 * 出处 = 定稿草图 context_reader_sketch/gen.py 的 --sSys/--sMe/--sAi/--sAdd/--mark/--markOn（命中 alpha = 草图透明度 × 255 四舍五入）。
 */
data class LogReaderColors(val system: Color, val user: Color, val assistant: Color, val added: Color, val hit: Color, val hitCurrent: Color)

/** 暖陶浅。 */
internal val LogReaderClayLight = LogReaderColors(Color(0xFFD9B8A6), Color(0xFFC98F6F), Color(0xFFB9C7B3), Color(0xFFD2BE8C), Color(0x57DEAA3C), Color(0xFFEFC15C))

/** 暖陶深。 */
internal val LogReaderClayDark = LogReaderColors(Color(0xFF8A6150), Color(0xFFA8704F), Color(0xFF556650), Color(0xFF7F6C3C), Color(0x4DE3C27A), Color(0xFFC9A24A))

/** 琉璃浅。 */
internal val LogReaderLiuliLight = LogReaderColors(Color(0xFFB7ABF6), Color(0xFF8E7DFF), Color(0xFFF0B5D3), Color(0xFFA6CFF0), Color(0x6BFFC450), Color(0xFFFFC857))

/** 琉璃深。 */
internal val LogReaderLiuliDark = LogReaderColors(Color(0xFF6454C4), Color(0xFF9C8CF5), Color(0xFF8F4474), Color(0xFF2F6594), Color(0x4DFFC85A), Color(0xFFD9A43A))

@Composable
@ReadOnlyComposable
fun logReaderColors(): LogReaderColors {
    val dark = LocalIsDarkTheme.current
    return if (LocalAppSkin.current == AppSkin.LIULI) {
        if (dark) LogReaderLiuliDark else LogReaderLiuliLight
    } else {
        if (dark) LogReaderClayDark else LogReaderClayLight
    }
}
