package com.situ.aichat.ui.liuli.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.Palette

/** 琉璃 2.0 零件材质（卷二图纸 §3.1·设计稿 ③ P 表逐值·唯一调参点）。 */
object LiuliMaterials {
    /** 半透明卡片底（稿 card）。 */
    fun cardFill(dark: Boolean): Color = if (dark) Palette.DuskCard.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.52f)

    /** 卡片 / 标签 / 输入框的 1dp 白边（稿 cardRim）。 */
    fun cardRim(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.09f else 0.78f)

    /** 顶沿 1px 高光（稿 spec）。 */
    fun highlight(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.26f else 0.95f)

    /** 卡片与分段滑片的柔影色（稿 shadow）。 */
    fun cardShadow(dark: Boolean): Color = if (dark) Color.Black.copy(alpha = 0.35f) else Palette.DawnShadow.copy(alpha = 0.14f)

    /** 卡内分隔线（稿 div）。 */
    fun divider(dark: Boolean): Color = if (dark) Color.White.copy(alpha = 0.07f) else Palette.DawnInk.copy(alpha = 0.08f)

    /** 行按压底（= 分段轨色）。 */
    fun pressTint(dark: Boolean): Color = if (dark) Color.White.copy(alpha = 0.07f) else Palette.DawnInk.copy(alpha = 0.06f)

    /** 未选标签 / 搜索槽 / 未选选项卡底（稿 chipBg）。 */
    fun chipFill(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.07f else 0.55f)

    /** 输入框底（稿 fieldBg）。 */
    fun fieldFill(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.06f else 0.62f)

    /** 分段轨（稿 segBg）。 */
    fun segTrack(dark: Boolean): Color = if (dark) Color.White.copy(alpha = 0.07f) else Palette.DawnInk.copy(alpha = 0.06f)

    /** 开关关态 / 滑杆与进度条的空轨（稿 offBg）。 */
    fun offTrack(dark: Boolean): Color = if (dark) Color.White.copy(alpha = 0.14f) else Palette.DawnInk.copy(alpha = 0.14f)

    /** 输入框聚焦外环（稿 focusRing）。 */
    fun focusRing(dark: Boolean): Color = if (dark) Palette.DuskIris.copy(alpha = 0.18f) else Palette.DawnIris.copy(alpha = 0.16f)

    /** 底部面板把手（稿 div2）。 */
    fun handle(dark: Boolean): Color = if (dark) Color.White.copy(alpha = 0.22f) else Palette.DawnInk.copy(alpha = 0.18f)

    /** 稿纸（卷六设计稿 `.paper`·写日记的纸 / TA 的信）：顶 → 底的暖白渐变 + 白边。 */
    fun paperTop(dark: Boolean): Color = if (dark) Palette.DuskPaper else Palette.DawnPaper
    fun paperBottom(dark: Boolean): Color = if (dark) Palette.DuskPaperShade else Palette.DawnPaperShade
    fun paperRim(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.08f else 0.90f)

    /** 危险钮底（稿 dangerBg）：危险字色的 10%（深 14%）。 */
    fun dangerFill(danger: Color, dark: Boolean): Color = danger.copy(alpha = if (dark) 0.14f else 0.10f)

    /** 主色渐变上的投影色（稿 accShadow·乙版起点色）。 */
    val accentShadow: Color = Palette.DawnIris.copy(alpha = 0.32f)

    /** 主色三段渐变（用户选乙·135°：起点左上、终点右下；浅深同值）。 */
    val accentBrush: Brush = Brush.linearGradient(
        0f to Palette.DawnIris,
        0.55f to Palette.DawnOrchid,
        1f to Palette.DawnRose,
    )

    /** 同一条渐变的横向版（滑杆 / 进度条的填充）。 */
    val accentHorizontalBrush: Brush = Brush.horizontalGradient(
        0f to Palette.DawnIris,
        0.55f to Palette.DawnOrchid,
        1f to Palette.DawnRose,
    )

    /** 卡片柔影几何（稿 `0 8px 22px`）：下移 8dp，模糊半径 11dp（= CSS 模糊 22 的一半）。 */
    val cardShadowOffsetY: Dp = 8.dp
    val cardShadowBlur: Dp = 11.dp

    /** 分段滑片柔影几何（稿 `0 2px 8px`）。 */
    val thumbShadowOffsetY: Dp = 2.dp
    val thumbShadowBlur: Dp = 4.dp

    /** 选中标签柔影几何（稿 `0 4px 10px`）。 */
    val chipShadowOffsetY: Dp = 4.dp
    val chipShadowBlur: Dp = 5.dp
}

/**
 * 只画在形状**外面**的柔影（设计稿 CSS `box-shadow` 口径）。卷一复核 R1 教训：Compose `shadow()` 画在形状
 * **下面**，半透明面会把它透出来成一块暗斑 —— 半透明面一律用本件。[blur] 为 `BlurMaskFilter` 半径。
 * （取平台画笔用 `nativePaint`：图纸原文的 `asFrameworkPaint()` 在 Compose 1.12 已弃用、编译报警，二者返回同一个
 * 底层 `android.graphics.Paint`·卷二图纸 §11 D-3。）
 */
fun Modifier.liuliSoftShadow(shape: Shape, color: Color, offsetY: Dp, blur: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val inside = Path().apply { addOutline(outline) }
    val shadow = Path().apply {
        addOutline(outline)
        translate(Offset(0f, offsetY.toPx()))
    }
    val paint = Paint().apply {
        this.color = color
        nativePaint.maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    onDrawBehind {
        clipPath(inside, ClipOp.Difference) {
            drawIntoCanvas { it.drawPath(shadow, paint) }
        }
    }
}

/**
 * 琉璃半透明卡片（设计稿「内容卡片是半透明卡片、不是真玻璃」）：形状外柔影 → 裁形状 → 半透明底 → 内容
 * → 顶沿 1px 高光 → 1dp 白边。
 */
fun Modifier.liuliCardMaterial(shape: RoundedCornerShape, dark: Boolean): Modifier = this
    .liuliSoftShadow(shape, LiuliMaterials.cardShadow(dark), LiuliMaterials.cardShadowOffsetY, LiuliMaterials.cardShadowBlur)
    .clip(shape)
    .background(LiuliMaterials.cardFill(dark))
    .drawWithContent {
        drawContent()
        drawRect(LiuliMaterials.highlight(dark), size = size.copy(height = 1f))
    }
    .border(1.dp, LiuliMaterials.cardRim(dark), shape)

/** 主色渐变实底（主按钮 / 发送钮 / 麦克风 / 聊天卡片主钮·卷三 §0.2-14）的影高与顶沿迎光。 */
val LiuliAccentFillElevation: Dp = 6.dp
private const val ACCENT_FILL_SPECULAR = 0.50f

/**
 * 主色渐变实底（卷三 §3.2）：6dp 主色影（不透明面·用 `shadow()`）→ 裁 [shape] → 三段 135° 渐变 → 顶沿 1px 白 50%。
 * 内容（字 / 图标）画在最上面，颜色由调用方给（白）。
 */
fun Modifier.liuliAccentFill(shape: Shape): Modifier = this
    .shadow(
        elevation = LiuliAccentFillElevation,
        shape = shape,
        clip = false,
        ambientColor = LiuliMaterials.accentShadow,
        spotColor = LiuliMaterials.accentShadow,
    )
    .clip(shape)
    .drawBehind {
        drawRect(LiuliMaterials.accentBrush)
        drawRect(Color.White.copy(alpha = ACCENT_FILL_SPECULAR), size = size.copy(height = 1f))
    }

private const val TONE_SPECULAR = 0.35f

/** 六色渐变图标块底（卷三 §3.2·设置砖 / 主页图标块 / 加号面板格子共用）：裁 [shape] → 135° 两色渐变 → 顶沿 1px 白 35%（画在内容之后）。 */
fun Modifier.liuliToneFill(tone: LiuliTileTone, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(tone.start, tone.end)))
    .drawWithContent {
        drawContent()
        drawRect(Color.White.copy(alpha = TONE_SPECULAR), size = size.copy(height = 1f))
    }

/** 琉璃稿纸（琉璃 2.0 卷六·一）：形状外柔影（同卡片）→ 裁形状 → 竖向暖白渐变 → 1dp 白边。不透明——纸上写字，不透景。 */
fun Modifier.liuliPaperMaterial(shape: RoundedCornerShape, dark: Boolean): Modifier = this
    .liuliSoftShadow(shape, LiuliMaterials.cardShadow(dark), LiuliMaterials.cardShadowOffsetY, LiuliMaterials.cardShadowBlur)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(LiuliMaterials.paperTop(dark), LiuliMaterials.paperBottom(dark))))
    .border(1.dp, LiuliMaterials.paperRim(dark), shape)
