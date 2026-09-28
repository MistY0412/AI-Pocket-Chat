package com.situ.aichat.ui.liuli.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.unit.dp

/** 懒列表里一组行拼成的半透明卡片，本行是哪一段（卷三 §3.3）。整组只有一行 = [Single]。 */
enum class LiuliSegmentPosition { Single, Top, Middle, Bottom }

/** 第 [index] 行（共 [count] 行）在组里的段位（纯函数·T1）。 */
fun liuliSegmentPosition(index: Int, count: Int): LiuliSegmentPosition = when {
    count <= 1 -> LiuliSegmentPosition.Single
    index <= 0 -> LiuliSegmentPosition.Top
    index >= count - 1 -> LiuliSegmentPosition.Bottom
    else -> LiuliSegmentPosition.Middle
}

/** 分段卡的圆角（= `LiuliShapes.group` 20）与白边宽（= [liuliCardMaterial] 的 1dp）。 */
private val SEGMENT_CORNER = 20.dp
private val SEGMENT_RIM = 1.dp

/**
 * 分段卡（卷三 §0.2-11·材质值 = [liuliCardMaterial]）：每行只画属于自己的一截——
 * ① 柔影：按「整组圆角矩形」下移后模糊，只画本段竖向范围内、整组形状之外的部分（顶段 / 单段向上、底段 / 单段向下
 * 多画一截）——段间不叠画、不透进卡内；② 底：本段形状（顶段圆上两角、底段圆下两角、中段直角、单段四角）；③ 内容；
 * ④ 顶沿 1px 高光（只顶段 / 单段）；⑤ 白边：描整组圆角矩形、裁到本段——段与段之间不出横边。
 * 本段不是顶 / 底的那一侧，「整组」向外延伸 reach + 圆角，延伸处的圆角落在本段之外，看不见。
 */
fun Modifier.liuliCardSegment(position: LiuliSegmentPosition, dark: Boolean): Modifier = drawWithCache {
    val top = position == LiuliSegmentPosition.Top || position == LiuliSegmentPosition.Single
    val bottom = position == LiuliSegmentPosition.Bottom || position == LiuliSegmentPosition.Single
    val r = SEGMENT_CORNER.toPx()
    val offsetY = LiuliMaterials.cardShadowOffsetY.toPx()
    val blur = LiuliMaterials.cardShadowBlur.toPx()
    val reach = offsetY + blur * 2f
    val groupTop = if (top) 0f else -(reach + r)
    val groupBottom = if (bottom) size.height else size.height + reach + r
    val group = RoundRect(0f, groupTop, size.width, groupBottom, CornerRadius(r))
    val groupPath = Path().apply { addRoundRect(group) }
    val shadowPath = Path().apply { addRoundRect(group.translate(Offset(0f, offsetY))) }
    val shadowPaint = Paint().apply {
        color = LiuliMaterials.cardShadow(dark)
        nativePaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
    val topCorner = if (top) CornerRadius(r) else CornerRadius.Zero
    val bottomCorner = if (bottom) CornerRadius(r) else CornerRadius.Zero
    val segmentPath = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, topCorner, topCorner, bottomCorner, bottomCorner))
    }
    val rimWidth = SEGMENT_RIM.toPx()
    val half = rimWidth / 2f
    val rimPath = Path().apply {
        addRoundRect(RoundRect(half, groupTop + half, size.width - half, groupBottom - half, CornerRadius(r - half)))
    }
    val fill = LiuliMaterials.cardFill(dark)
    val highlight = LiuliMaterials.highlight(dark)
    val rim = LiuliMaterials.cardRim(dark)
    onDrawWithContent {
        clipRect(
            left = -reach,
            top = if (top) -reach else 0f,
            right = size.width + reach,
            bottom = if (bottom) size.height + reach else size.height,
        ) {
            clipPath(groupPath, ClipOp.Difference) {
                drawIntoCanvas { it.drawPath(shadowPath, shadowPaint) }
            }
        }
        clipPath(segmentPath) {
            drawRect(fill)
            this@onDrawWithContent.drawContent()
            if (top) drawRect(highlight, size = Size(size.width, 1f))
            drawPath(rimPath, rim, style = Stroke(rimWidth))
        }
    }
}
