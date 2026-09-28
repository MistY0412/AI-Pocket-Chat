package com.situ.aichat.ui.navigation

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.designsystem.AppElevation
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.liuli.designsystem.LiuliAmbientSpec
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin
import com.situ.aichat.ui.liuli.designsystem.drawLiuliAmbient

/*
 * 预测返回卡片后面的「舞台」+ 当前页柔影 / 白边（微图纸 docs/handoff/2026-09-28-预测返回舞台底.md §4）。
 * 取色全走既有 token（按脸 × 深浅），本文件不含字面色值；浓度与几何在 [BackCardTone] / [BackCardSpec]。
 */

/** 一张脸 × 一种深浅下的取色。 */
@Immutable
internal data class BackCardStageStyle(
    val base: Color,
    /** 琉璃柔光底的四团光晕；null = 暖陶瓷白底。 */
    val glows: List<Color>?,
    /** 影色（不透明）：遮罩、舞台加深、柔影共用这一个色相，各自浓度见 [tone]。 */
    val tint: Color,
    /** 琉璃玻璃白边；null = 暖陶不画。 */
    val rim: Color?,
    val tone: BackCardTone,
)

/**
 * 影色：琉璃昼 = 琉璃卡片柔影的色相（晨光紫）；暖陶浅 = 暖陶投影墨；深色两张脸 = 遮罩黑（`surface.scrim`，
 * 与改动前的纯黑同值）。舞台：琉璃 = 与琉璃页面同一套柔光底；暖陶 = `surface.base`。
 */
@Composable
internal fun rememberBackCardStageStyle(): BackCardStageStyle {
    val colors = AppTheme.colors
    val liuli = LocalAppSkin.current == AppSkin.LIULI
    return remember(colors, liuli) {
        val dark = colors.isDark
        BackCardStageStyle(
            base = colors.surface.base,
            glows = if (liuli) LiuliAmbientSpec.glows(dark) else null,
            tint = when {
                dark -> colors.surface.scrim
                liuli -> LiuliMaterials.cardShadow(dark = false).copy(alpha = 1f)
                else -> AppElevation.shadowInk
            },
            rim = if (liuli) LiuliMaterials.cardRim(dark) else null,
            tone = BackCardTone.of(liuli = liuli, dark = dark),
        )
    }
}

/**
 * 舞台：画在上一页外层（满屏、不缩、在上一页卡片之下），卡片之间的缝露出的就是它。琉璃的柔光底与页面自己的
 * 柔光底同坐标，松手后上一页放回全屏正好盖回原位。[fraction] = [scrimFraction]（确认后随收尾淡掉加深层）。
 */
internal fun DrawScope.drawBackCardStage(style: BackCardStageStyle, fraction: Float) {
    val glows = style.glows
    if (glows != null) drawLiuliAmbient(style.base, glows) else drawRect(style.base)
    val deepen = style.tone.stageDeepen * fraction
    if (deepen > 0f) drawRect(style.tint, alpha = deepen)
}

/** 柔影两支画笔：模糊滤镜属重对象，每页只建一次（首次画影时），之后逐帧只改颜色。 */
internal class BackCardShadowPaints(density: Density) {
    val softOffsetY: Float = with(density) { BackCardSpec.SHADOW_Y_DP.dp.toPx() }
    val soft: Paint = blurPaint(with(density) { BackCardSpec.SHADOW_BLUR_DP.dp.toPx() })
    val contact: Paint = blurPaint(with(density) { BackCardSpec.CONTACT_BLUR_DP.dp.toPx() })

    private fun blurPaint(radius: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
}

/**
 * 当前页柔影：画在当前页外层（满屏、不缩、在当前页卡片之下），按卡片这一帧的矩形投下柔影 + 贴边影。
 * 浓度 = tone × 浮现度 × 卡片透明度（松手后随卡片一起淡没）；[cornerPx] 整场会话为常数。
 */
internal fun DrawScope.drawBackCardShadow(
    style: BackCardStageStyle, card: CardTransform, cornerPx: Float, paints: BackCardShadowPaints,
) {
    val k = cardEdgeEmergence(card.scale) * card.alpha
    if (k <= 0f) return
    val w = size.width * card.scale
    val h = size.height * card.scale
    val left = (size.width - w) / 2f + card.translationX
    val top = (size.height - h) / 2f + card.translationY
    val softAlpha = style.tone.shadow * k
    paints.soft.color = style.tint.copy(alpha = softAlpha).toArgb()
    paints.contact.color = style.tint.copy(alpha = softAlpha * BackCardSpec.CONTACT_ALPHA_RATIO).toArgb()
    val dy = paints.softOffsetY
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.drawRoundRect(left, top + dy, left + w, top + h + dy, cornerPx, cornerPx, paints.soft)
        native.drawRoundRect(left, top, left + w, top + h, cornerPx, cornerPx, paints.contact)
    }
}

/** 琉璃白边：在当前页卡片图层里、页面内容之上沿卡片外形描一圈（外半被卡片裁掉，可见 [BackCardSpec.RIM_DP]）。 */
internal fun DrawScope.drawBackCardRim(style: BackCardStageStyle, outline: Outline, emergence: Float) {
    val rim = style.rim ?: return
    if (emergence <= 0f) return
    drawOutline(outline, rim, alpha = emergence, style = Stroke(width = BackCardSpec.RIM_DP.dp.toPx() * 2f))
}
