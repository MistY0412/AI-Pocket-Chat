package com.situ.aichat.ui.liuli.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import com.situ.aichat.tts.EmotionType
import com.situ.aichat.ui.chat.ChatWallpaper
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.rememberReduceMotion
import com.situ.aichat.ui.designsystem.AppColors
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.liuli.designsystem.LiuliAmbientSpec
import com.situ.aichat.ui.liuli.designsystem.drawLiuliAmbient

/**
 * 琉璃聊天屏背景（图纸 2026-09-05 卷二A §4.1 · 契约 FABLE5_THEME_LIULI_PROPOSAL §5.4）。
 *
 * 无壁纸 → **柔光底 + 心情微调**（琉璃 2.0 卷一图纸 §4.4）：底图就是晨光柔光底（四个径向光晕·
 * [LiuliAmbientSpec]）；角色当前情绪归到 5 个色族，平静族 = 原样柔光底，其余四族把四个光晕各向该族情绪色
 * 混一点（浅 22% / 深 28%）。四个光晕按 [MOOD_SLOTS] 站位（不用 `Modifier.blur`——API 29–30 无模糊也同样成立、
 * 零帧成本）；只在**族**变化时换色（2600ms EaseOut），每发送成功一条绕位轮转一步（gentle 弹簧）。
 * 有壁纸 → 画壁纸（照抄暖陶 `ChatScreen` 壁纸层）。
 * 见面态由调用方不渲染本件（舞台层铺满）。
 */

/** 心情色族（12 类 [EmotionType] 归并·图纸 §0 ② 4 落定）。 */
internal enum class LiuliMoodFamily { JOY, SHY, SAD, ANGER, CALM }

/** 四个光晕的站位 = 柔光底同一张表（琉璃 2.0 卷一图纸 §4.4）。 */
internal val MOOD_SLOTS: List<Offset> = LiuliAmbientSpec.centers

/** 换色时长（族变化才动·图纸 §3.2 锁）。 */
private const val MOOD_COLOR_MS = 2600

/** 非平静族：四个光晕各向该族情绪色混这么多；平静族 = 原样柔光底（琉璃 2.0 卷一图纸 §4.4）。 */
private const val MOOD_TINT_LIGHT = 0.16f
private const val MOOD_TINT_DARK = 0.16f

/** 12 类情绪 → 5 色族（图纸 §0 ② 4 · 穷举无 else 兜底：新增情绪必须显式归族）。 */
internal fun liuliMoodFamily(emotion: EmotionType): LiuliMoodFamily = when (emotion) {
    EmotionType.HAPPY, EmotionType.EXCITED, EmotionType.PLAYFUL -> LiuliMoodFamily.JOY
    EmotionType.LOVE, EmotionType.SHY -> LiuliMoodFamily.SHY
    EmotionType.SAD, EmotionType.SIGH -> LiuliMoodFamily.SAD
    EmotionType.ANGRY, EmotionType.SCARED -> LiuliMoodFamily.ANGER
    EmotionType.THINKING, EmotionType.SHOCKED, EmotionType.NEUTRAL -> LiuliMoodFamily.CALM
}

/**
 * 心情光晕色（琉璃 2.0 卷一图纸 §4.4）：平静族 = 柔光底原样四色；其余四族 = 四个光晕各自
 * `lerp(光晕, 该族情绪色, 浅深都 0.16（卷三 §0.2-5）)`。纯函数（T1-6）。
 */
internal fun liuliMoodBlobColors(family: LiuliMoodFamily, colors: AppColors): List<Color> {
    val glows = LiuliAmbientSpec.glows(colors.isDark)
    val emotion = colors.emotion
    val mood = when (family) {
        LiuliMoodFamily.JOY -> emotion.joy
        LiuliMoodFamily.SHY -> emotion.shy
        LiuliMoodFamily.SAD -> emotion.sad
        LiuliMoodFamily.ANGER -> emotion.anger
        LiuliMoodFamily.CALM -> return glows
    }
    val t = if (colors.isDark) MOOD_TINT_DARK else MOOD_TINT_LIGHT
    return glows.map { lerp(it, mood, t) }
}

/**
 * 换色动效档（图纸 §4.1）：族变化 2600ms EaseOut；[reduceMotion] 直落终值。抽成纯函数便于 T2-6 钉住
 * 「RM 下目标即终值」与 2600 / EaseOut 落值（不必靠截屏取色）。
 */
internal fun liuliMoodColorSpec(reduceMotion: Boolean): AnimationSpec<Color> =
    if (reduceMotion) snap() else tween(MOOD_COLOR_MS, easing = AppMotion.EaseOut)

/** 绕位动效档（图纸 §4.1）：gentle 弹簧；[reduceMotion] 直落。 */
internal fun liuliMoodSlotSpec(reduceMotion: Boolean): FiniteAnimationSpec<Offset> =
    if (reduceMotion) snap() else AppMotion.gentleSpring()

/** 第 [index] 个色点在第 [sendTurn] 轮的站位（每发送成功一条整体绕位一步·纯函数）。 */
internal fun liuliMoodSlot(index: Int, sendTurn: Int): Offset =
    MOOD_SLOTS[((index + sendTurn) % MOOD_SLOTS.size + MOOD_SLOTS.size) % MOOD_SLOTS.size]

/**
 * 绕位相位（图纸 §0 ② 4 / E14「RM 不轮转」·复核 R1 🟡-4）：减弱动画时相位恒 0——四点站原位不动，
 * 发送再多也不「瞬跳」一格（瞬跳正是 RM 用户要避开的突变）。纯函数（T2-6）。
 */
internal fun liuliMoodSlotTurn(sendTurn: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else sendTurn

@Composable
internal fun LiuliChatBackground(
    moodEmoji: String,
    sendTurn: Int,
    wallpaper: ChatWallpaper?,
    peeked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    if (wallpaper != null) {
        // 照抄暖陶壁纸层（ChatScreen.kt:535-550）：暖 peek 命中即第一帧就在；冷加载柔和淡入一次。
        val wallpaperAlpha = remember { Animatable(if (peeked) 1f else 0f) }
        LaunchedEffect(Unit) { wallpaperAlpha.animateTo(1f, tween(AppMotion.SMOOTH_MS, easing = AppMotion.EaseOut)) }
        Image(
            wallpaper.sharp,
            contentDescription = null,
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface.base)
                .graphicsLayer { alpha = wallpaperAlpha.value },
            contentScale = ContentScale.Crop,
        )
        return
    }

    val reduceMotion = rememberReduceMotion()
    val family = remember(moodEmoji) { liuliMoodFamily(EmotionType.from(moodEmoji)) }
    val targets = liuliMoodBlobColors(family, colors)
    val colorSpec = liuliMoodColorSpec(reduceMotion)
    val offsetSpec = liuliMoodSlotSpec(reduceMotion)
    val animatedColors: List<State<Color>> = targets.mapIndexed { i, target ->
        animateColorAsState(targetValue = target, animationSpec = colorSpec, label = "moodBlob$i")
    }
    val slotTurn = liuliMoodSlotTurn(sendTurn, reduceMotion)
    val animatedSlots: List<State<Offset>> = targets.indices.map { i ->
        animateOffsetAsState(targetValue = liuliMoodSlot(i, slotTurn), animationSpec = offsetSpec, label = "moodSlot$i")
    }
    Canvas(modifier.fillMaxSize()) {
        drawLiuliAmbient(
            base = colors.surface.base,
            glows = animatedColors.map { it.value },
            centers = animatedSlots.map { it.value },
        )
    }
}
