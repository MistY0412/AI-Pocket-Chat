@file:OptIn(ExperimentalHazeApi::class)

package com.situ.aichat.ui.liuli.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.situ.aichat.data.model.GlassTier
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Haze 玻璃（标准 / 通透两档）。顺序：影（形状外）→ Haze 玻璃（自带模糊 / 折射 / 边缘高光与暗线）→ 按形状裁子内容
 * （Haze 不裁子内容；裁在玻璃之后，才不会切掉它画在边界上的高光）。**不叠自家光影**（图纸 §0.2-7）。
 */
@Composable
internal fun Modifier.liuliHazeGlass(
    shape: RoundedCornerShape,
    dark: Boolean,
    role: LiuliGlassRole,
    tier: GlassTier,
    haze: HazeState,
    thick: Boolean = false,
): Modifier {
    val recipe = liuliHazeRecipe(role, tier, dark, thick)
    val style = remember(recipe, shape, dark) {
        (if (recipe.clearStyle) GlassStyle.clear else GlassStyle.regular).then {
            shape(shape)
            tint(recipe.tint)
            if (dark) {
                val r = liuliHazeDarkResponse(recipe.clearStyle)
                specularIntensity(r.specularIntensity)
                edgeShadow(Color.Black.copy(alpha = r.edgeShadowAlpha))
                ambientResponse(r.ambientResponse)
                contrast(r.contrast)
                whitePoint(r.whitePoint)
                chromaMultiplier(r.chromaMultiplier)
            }
        }
    }
    val input = remember(haze) { HazeInput.Sources(haze) }
    return this
        .shadow(liuliGlassElevation(role), shape, clip = false)
        .hazeGlass(input = input, style = style)
        .clip(shape)
}

/**
 * 深色玻璃的光学响应（卷三复核 R1 🔴-1）。Haze 2.0.0 的内置 Regular / Clear 只按浅色外观调过：着色之前先把身后往白
 * 提 55% / 17%（`whitePoint`·源码 `GlassShaders.kt` `applyColorGrading`）——深色玻璃因此整片发灰（标准档压深色底读
 * #55535C、加厚玻璃压 #202020 壁纸读 (71, 70, 77)、玻璃次字 3.9:1）。深色一律改用 Haze 作者为下一版写的深色响应
 * （上游 main `273d6947`「Make built-in Glass styles follow system appearance」的 `regularDark` / `clearDark` 原值）：
 * 白点往黑压、对比略增、光影减弱。**按琉璃自己的深浅**（聊天屏壁纸局部换深也算），不看系统深色——上游读的是系统
 * `uiMode`，升级 Haze 后这里的显式写入仍排在后面、照样生效。浅色不写，保持内置响应。
 */
internal data class LiuliHazeDarkResponse(
    val specularIntensity: Float,
    val edgeShadowAlpha: Float,
    val ambientResponse: Float,
    val contrast: Float,
    val whitePoint: Float,
    val chromaMultiplier: Float,
)

/** [clearStyle] = 通透档（`GlassStyle.clear` 底）的深色响应，否则 regular 的（纯函数·T1 钉值）。 */
internal fun liuliHazeDarkResponse(clearStyle: Boolean): LiuliHazeDarkResponse =
    if (clearStyle) {
        LiuliHazeDarkResponse(0.42f, 0.25f, 0.22f, 0.12f, -0.18f, 1f)
    } else {
        LiuliHazeDarkResponse(0.38f, 0.32f, 0.08f, 0.08f, -0.22f, 1.1f)
    }
