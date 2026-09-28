package com.situ.aichat.ui.liuli.glass

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.situ.aichat.ui.liuli.designsystem.LocalGlassTier
import com.situ.aichat.ui.liuli.designsystem.liuliPaintedLens

/**
 * 透镜（卷四 §0.2-2 / -3）：宿主在场且走 Haze 引擎 → Haze 通透玻璃透镜（[LiuliGlassRole.Lens]·身后内容轻微放大弯折）；
 * 否则（毛玻璃档 / 安卓 13 以下 / 内容层）→ 画出来的透镜 [liuliPaintedLens]——毛玻璃透镜压在毛玻璃栏上看不出来。
 */
fun Modifier.liuliLens(shape: RoundedCornerShape, dark: Boolean): Modifier = composed {
    val host = LocalLiuliGlassHost.current
    val engine = if (host == null) LiuliGlassEngine.TINT_ONLY else resolveGlassEngine(LocalGlassTier.current, Build.VERSION.SDK_INT)
    if (liuliLensUsesHaze(hasHost = host != null, engine = engine)) {
        liuliGlass(shape, dark = dark, role = LiuliGlassRole.Lens)
    } else {
        liuliPaintedLens(shape, dark)
    }
}

/** 透镜走不走 Haze（纯函数·T1）。 */
internal fun liuliLensUsesHaze(hasHost: Boolean, engine: LiuliGlassEngine): Boolean = hasHost && engine == LiuliGlassEngine.HAZE
