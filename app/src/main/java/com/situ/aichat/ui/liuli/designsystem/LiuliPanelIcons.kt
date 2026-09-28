package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 琉璃「+」面板图标（卷四 §0.2-10·设计稿 ⑦ 甲）：1.8 线宽圆头的统一线条图标，白色由 `tint` 上色。暖陶的 `AppPanelIcons` 不动。
 *
 * 范式同 `AppPanelIcons`：24×24 视口、[PathParser] 直接吃 SVG d 串、占位黑（渲染时被 tint 整体重染）。
 */
object LiuliPanelIcons {

    private val PLACEHOLDER = SolidColor(Color.Black)
    private const val W = 1.8f

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun ImageVector.Builder.stroke(d: String) = addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        stroke = PLACEHOLDER,
        strokeLineWidth = W,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

    private fun ImageVector.Builder.solid(d: String) = addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = PLACEHOLDER,
    )

    /** 照片：圆角相框 + 日点 + 山。 */
    val Photo: ImageVector by lazy {
        builder("LiuliPanelPhoto")
            .stroke("M7.5 4.5 H16.5 A4 4 0 0 1 20.5 8.5 V15.5 A4 4 0 0 1 16.5 19.5 H7.5 A4 4 0 0 1 3.5 15.5 V8.5 A4 4 0 0 1 7.5 4.5 Z")
            .stroke("M10.8 10 A1.8 1.8 0 1 1 7.2 10 A1.8 1.8 0 1 1 10.8 10 Z")
            .stroke("M4.5 17.5 L9 13.3 L12.2 16.3 L14.8 13.9 L19.5 18")
            .build()
    }

    /** 表情：圆脸 + 笑弧 + 两点眼。 */
    val Sticker: ImageVector by lazy {
        builder("LiuliPanelSticker")
            .stroke("M20.5 12 A8.5 8.5 0 1 1 3.5 12 A8.5 8.5 0 1 1 20.5 12 Z")
            .stroke("M8.6 14.2 A4.3 4.3 0 0 0 15.4 14.2")
            .solid("M10.35 9.6 A1.15 1.15 0 1 1 8.05 9.6 A1.15 1.15 0 1 1 10.35 9.6 Z")
            .solid("M15.95 9.6 A1.15 1.15 0 1 1 13.65 9.6 A1.15 1.15 0 1 1 15.95 9.6 Z")
            .build()
    }

    /** 红包：竖信封 + 折口弧 + 圆封印。 */
    val RedPacket: ImageVector by lazy {
        builder("LiuliPanelRedPacket")
            .stroke("M8 3.5 H16 A3 3 0 0 1 19 6.5 V17.5 A3 3 0 0 1 16 20.5 H8 A3 3 0 0 1 5 17.5 V6.5 A3 3 0 0 1 8 3.5 Z")
            .stroke("M5 8.5 C7.2 10.7 9.5 11.7 12 11.7 C14.5 11.7 16.8 10.7 19 8.5")
            .stroke("M14 14.6 A2 2 0 1 1 10 14.6 A2 2 0 1 1 14 14.6 Z")
            .build()
    }

    /** 送礼：盒盖 + 盒身 + 中缝 + 蝴蝶结双环。 */
    val Gift: ImageVector by lazy {
        builder("LiuliPanelGift")
            .stroke("M4.7 8.5 H19.3 A1.2 1.2 0 0 1 20.5 9.7 V10.8 A1.2 1.2 0 0 1 19.3 12 H4.7 A1.2 1.2 0 0 1 3.5 10.8 V9.7 A1.2 1.2 0 0 1 4.7 8.5 Z")
            .stroke("M5 12 V18 A2 2 0 0 0 7 20 H17 A2 2 0 0 0 19 18 V12")
            .stroke("M12 8.5 V20")
            .stroke("M12 8.5 C10.8 8.5 8.2 8 8.2 6.2 C8.2 5.2 9 4.6 9.9 4.8 C11.2 5.1 12 7 12 8.5 Z")
            .stroke("M12 8.5 C13.2 8.5 15.8 8 15.8 6.2 C15.8 5.2 15 4.6 14.1 4.8 C12.8 5.1 12 7 12 8.5 Z")
            .build()
    }

    /** 见面：并肩两个人。 */
    val Meet: ImageVector by lazy {
        builder("LiuliPanelMeet")
            .stroke("M11.5 8.5 A3 3 0 1 1 5.5 8.5 A3 3 0 1 1 11.5 8.5 Z")
            .stroke("M18.5 8.5 A3 3 0 1 1 12.5 8.5 A3 3 0 1 1 18.5 8.5 Z")
            .stroke("M3.5 19 A5 5 0 0 1 13.5 19")
            .stroke("M10.5 19 A5 5 0 0 1 20.5 19")
            .build()
    }

    /** 约见面：日历本 + 表头线 + 双挂脚 + 体内小心。 */
    val FutureMeet: ImageVector by lazy {
        builder("LiuliPanelFutureMeet")
            .stroke("M7.5 5 H16.5 A3.5 3.5 0 0 1 20 8.5 V16.5 A3.5 3.5 0 0 1 16.5 20 H7.5 A3.5 3.5 0 0 1 4 16.5 V8.5 A3.5 3.5 0 0 1 7.5 5 Z")
            .stroke("M4 10 H20")
            .stroke("M9 3 V7")
            .stroke("M15 3 V7")
            .solid("M12 17.6 C10.1 16.3 9.2 15.3 9.2 14.2 C9.2 13.4 9.8 12.8 10.6 12.8 C11.2 12.8 11.7 13.2 12 13.7 C12.3 13.2 12.8 12.8 13.4 12.8 C14.2 12.8 14.8 13.4 14.8 14.2 C14.8 15.3 13.9 16.3 12 17.6 Z")
            .build()
    }
}
