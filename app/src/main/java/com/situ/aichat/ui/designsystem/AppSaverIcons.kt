package com.situ.aichat.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 省钱模式卡自绘图标（时间感知四期·图纸二 §4.5·照 [AppProfileIcons] 笔法：24 网格·线宽 1.7·圆头圆角·
 * **单色矢量**，`stroke` 用占位黑、由 `Icon` 的 `tint` 整体重染）。
 * [Yen] = 圆框里的「¥」（开关行瓦片）/ [Bars] = 三根竖条（命中行）。
 */
object AppSaverIcons {

    private val PLACEHOLDER = SolidColor(Color.Black)
    private const val W = 1.7f

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    /** 省钱：圆 cx12 cy12 r9 + 「¥」（两斜笔交于中心 · 竖笔 · 两横）。 */
    val Yen: ImageVector by lazy {
        builder("SaverYen").apply {
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 0f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, -18f, 0f)
                close()
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(8.5f, 7.5f)
                lineTo(12f, 12f)
                lineToRelative(3.5f, -4.5f)
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 12f)
                verticalLineToRelative(5.5f)
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 12.5f)
                horizontalLineToRelative(6f)
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 15f)
                horizontalLineToRelative(6f)
            }
        }.build()
    }

    /** 命中率：三根竖条（x5 高 9 / x12 高 15 / x19 高 6，底对齐 y20）。 */
    val Bars: ImageVector by lazy {
        builder("SaverBars").apply {
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, 20f)
                verticalLineTo(11f)
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 20f)
                verticalLineTo(5f)
            }
            path(stroke = PLACEHOLDER, strokeLineWidth = W, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(19f, 20f)
                verticalLineToRelative(-6f)
            }
        }.build()
    }
}
