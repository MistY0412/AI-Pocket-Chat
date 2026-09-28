package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.situ.aichat.data.model.GlassTier

/** Haze 完整玻璃（模糊 + 折射 + 高光）的 API 门：RuntimeShader 需安卓 13（API 33）。 */
internal const val HAZE_MIN_SDK = 33

/** 自研毛玻璃实时模糊的 API 门：RenderEffect 需安卓 12（API 31）。 */
internal const val BLUR_MIN_SDK = 31

/** 本机能否用「标准 / 通透」两档（外观页据此置灰）。 */
val hazeGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= HAZE_MIN_SDK

/**
 * 玻璃片的三种角色（设计稿 ② 玻璃规则）：
 * - [Bar]：浮在柔光底上的小件——顶栏、输入栏、底栏、日期胶囊这类条与胶囊；
 * - [Panel]：大面板——底部面板、对话框、菜单、加号面板、长按菜单、提示条（卷四起 = 标准底色·不再加厚）；
 * - [Button]：圆钮 / 小药丸钮（影缩到 2dp）。
 * - [Lens]：底栏选中 / 分段条滑块的透镜（clear 样式 + 亮一层的白·卷四）。
 */
enum class LiuliGlassRole { Bar, Panel, Button, Lens }

/** 实际生效档：安卓 13 以下只有毛玻璃（纯函数·T1）。 */
internal fun GlassTier.effective(sdk: Int): GlassTier = if (sdk < HAZE_MIN_SDK) GlassTier.FROSTED else this

/** 三种画法：Haze 玻璃 / 自研实时模糊 / 只着色。 */
internal enum class LiuliGlassEngine { HAZE, FROSTED_BLUR, TINT_ONLY }

/** 宿主与玻璃片共用的引擎判定（纯函数·T1）。 */
internal fun resolveGlassEngine(tier: GlassTier, sdk: Int): LiuliGlassEngine = when {
    tier.effective(sdk) != GlassTier.FROSTED -> LiuliGlassEngine.HAZE
    sdk >= BLUR_MIN_SDK -> LiuliGlassEngine.FROSTED_BLUR
    else -> LiuliGlassEngine.TINT_ONLY
}

/** Haze 配方：[clearStyle] = 用官方 `GlassStyle.clear`（否则 `regular`）；[tint] = 叠在折射内容上的底色（含透明度）。 */
internal data class LiuliHazeRecipe(val clearStyle: Boolean, val tint: Color)

/**
 * Haze 配方表（纯函数·T1·卷四 §0.2-1 / -2）：透镜恒 clear + 透镜白；壁纸加厚（[thick]）→ regular + 加厚底色；
 * 大面板 → regular + 标准底色；其余小件在通透档用 clear + 薄底色，标准档用 regular + 标准底色。
 */
internal fun liuliHazeRecipe(role: LiuliGlassRole, tier: GlassTier, dark: Boolean, thick: Boolean = false): LiuliHazeRecipe = when {
    role == LiuliGlassRole.Lens ->
        LiuliHazeRecipe(clearStyle = true, tint = if (dark) LiuliGlassSpec.hazeLensDark else LiuliGlassSpec.hazeLensLight)
    thick ->
        LiuliHazeRecipe(clearStyle = false, tint = if (dark) LiuliGlassSpec.hazeThickDark else LiuliGlassSpec.hazeThickLight)
    role == LiuliGlassRole.Panel ->
        LiuliHazeRecipe(clearStyle = false, tint = if (dark) LiuliGlassSpec.hazeStandardDark else LiuliGlassSpec.hazeStandardLight)
    tier == GlassTier.SHEER ->
        LiuliHazeRecipe(clearStyle = true, tint = if (dark) LiuliGlassSpec.hazeSheerDark else LiuliGlassSpec.hazeSheerLight)
    else ->
        LiuliHazeRecipe(clearStyle = false, tint = if (dark) LiuliGlassSpec.hazeStandardDark else LiuliGlassSpec.hazeStandardLight)
}

/** 毛玻璃染色（含透明度）：[blurred] = 身后真有模糊；否则是「只着色」兜底，要厚到能把身后内容压住（纯函数·T1）。 */
internal fun liuliFrostedTint(dark: Boolean, blurred: Boolean): Color = when {
    blurred && dark -> LiuliGlassSpec.frostDark
    blurred -> LiuliGlassSpec.frostLight
    dark -> LiuliGlassSpec.fallbackDark
    else -> LiuliGlassSpec.fallbackLight
}

/** 只着色兜底的不透明垫底（卷一复核 R1 🔴-1·纯函数·T1）：染色画在它上面，兜底玻璃整片不透明。 */
internal fun liuliFallbackUnderlay(dark: Boolean): Color =
    if (dark) LiuliGlassSpec.fallbackUnderlayDark else LiuliGlassSpec.fallbackUnderlayLight

/** 影高：圆钮 / 透镜 2dp，其余 8dp（纯函数·T1）。 */
internal fun liuliGlassElevation(role: LiuliGlassRole): Dp =
    if (role == LiuliGlassRole.Button || role == LiuliGlassRole.Lens) LiuliGlassSpec.buttonShadowElevation else LiuliGlassSpec.shadowElevation
