package com.situ.aichat.ui.liuli.chat

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.situ.aichat.tts.EmotionType
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.designsystem.DarkAppColors
import com.situ.aichat.ui.designsystem.LightAppColors
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-2 / T2-6 纯函数档（图纸 2026-09-05 卷二A §7）：心情色的**族映射 / 派生 / 绕位轮转 / 动效档**。
 *
 * 琉璃 2.0 卷一（图纸 2026-09-25 §7 T1-6）：派生改为「柔光底四光晕 + 非平静族各向情绪色混 浅 0.22 / 深 0.28」（卷三 §0.2-5 起浅深都 0.16），
 * 旧的时段档（22:00–06:00）随旧四色退役。断言从规格独立反推（族表逐条重打、光晕字面量与混合比重打、
 * 槽位表重打），不照抄实现输出。E4 / E5 / E6 / E16 / E17 在此闭环。
 */
class LiuliMoodPaletteTest {

    /** 图纸 §0 ② 4 的族表——在测试里**重新打一遍**（不引用实现的 when）。 */
    private val expectedFamilies = mapOf(
        EmotionType.HAPPY to LiuliMoodFamily.JOY,
        EmotionType.EXCITED to LiuliMoodFamily.JOY,
        EmotionType.PLAYFUL to LiuliMoodFamily.JOY,
        EmotionType.LOVE to LiuliMoodFamily.SHY,
        EmotionType.SHY to LiuliMoodFamily.SHY,
        EmotionType.SAD to LiuliMoodFamily.SAD,
        EmotionType.SIGH to LiuliMoodFamily.SAD,
        EmotionType.ANGRY to LiuliMoodFamily.ANGER,
        EmotionType.SCARED to LiuliMoodFamily.ANGER,
        EmotionType.THINKING to LiuliMoodFamily.CALM,
        EmotionType.SHOCKED to LiuliMoodFamily.CALM,
        EmotionType.NEUTRAL to LiuliMoodFamily.CALM,
    )

    @Test fun family_table_coversAll12EmotionTypes_withNoGap() {
        // 穷举：枚举有多少个，表就必须有多少条（新增情绪不许静默落进某个族）。
        assertEquals(EmotionType.entries.size, expectedFamilies.size)
        EmotionType.entries.forEach { emotion ->
            assertEquals("$emotion 归族不符图纸 §0 ② 4", expectedFamilies[emotion], liuliMoodFamily(emotion))
        }
    }

    @Test fun emptyOrUnknownEmoji_fallsBackToCalm() {
        // E4：新会话 moodEmoji 为空 → NEUTRAL → calm。
        assertEquals(LiuliMoodFamily.CALM, liuliMoodFamily(EmotionType.from("")))
        assertEquals(LiuliMoodFamily.CALM, liuliMoodFamily(EmotionType.from(null)))
        // E5：字典外 emoji → NEUTRAL → calm。
        assertEquals(LiuliMoodFamily.CALM, liuliMoodFamily(EmotionType.from("🐳")))
    }

    @Test fun sameFamilyDifferentEmoji_yieldsSameColors() {
        // E6：😊 与 🥳 同属 joy → 四色逐位相同（族不变就不换色）。
        val a = liuliMoodBlobColors(liuliMoodFamily(EmotionType.from("😊")), LiuliLightAppColors)
        val b = liuliMoodBlobColors(liuliMoodFamily(EmotionType.from("🥳")), LiuliLightAppColors)
        assertEquals(a, b)
    }

    /** 图纸 §4.1 / §4.3 的四个光晕——在测试里重打字面量（不引用 `LiuliAmbientSpec`）。 */
    private val glowsLight = listOf(Color(0xFFFFCDB8), Color(0xFFD6C3FF), Color(0xFFBDE0FF), Color(0xFFFFD6EE))
    private val glowsDark = listOf(Color(0xFF4A3590), Color(0xFF6B2D6A), Color(0xFF1E4A7A), Color(0xFF3F2C80))

    private fun argbs(colors: List<Color>): List<Int> = colors.map { it.toArgb() }

    @Test fun calm_isAmbientGlowsUnchanged_bothModes() {
        // E16：平静族（含空 / 未知 emoji 落到的中性）= 原样柔光底。
        assertEquals(argbs(glowsLight), argbs(liuliMoodBlobColors(LiuliMoodFamily.CALM, LiuliLightAppColors)))
        assertEquals(argbs(glowsDark), argbs(liuliMoodBlobColors(LiuliMoodFamily.CALM, LiuliDarkAppColors)))
    }

    @Test fun nonCalmFamilies_tintEachGlowTowardEmotion_lightDark016() {
        val lightEmotion = LightAppColors.emotion
        val darkEmotion = DarkAppColors.emotion
        val cases = listOf(
            Triple(LiuliMoodFamily.JOY, lightEmotion.joy, darkEmotion.joy),
            Triple(LiuliMoodFamily.SHY, lightEmotion.shy, darkEmotion.shy),
            Triple(LiuliMoodFamily.SAD, lightEmotion.sad, darkEmotion.sad),
            Triple(LiuliMoodFamily.ANGER, lightEmotion.anger, darkEmotion.anger),
        )
        cases.forEach { (family, lightMood, darkMood) ->
            assertEquals(
                "$family 浅",
                argbs(glowsLight.map { lerp(it, lightMood, 0.16f) }),
                argbs(liuliMoodBlobColors(family, LiuliLightAppColors)),
            )
            assertEquals(
                "$family 深",
                argbs(glowsDark.map { lerp(it, darkMood, 0.16f) }),
                argbs(liuliMoodBlobColors(family, LiuliDarkAppColors)),
            )
        }
    }

    @Test fun fiveFamilies_arePairwiseDistinct_bothModes() {
        listOf(LiuliLightAppColors, LiuliDarkAppColors).forEach { scheme ->
            val all = LiuliMoodFamily.entries.map { argbs(liuliMoodBlobColors(it, scheme)) }
            assertEquals("五族两两不同（isDark=${scheme.isDark}）", LiuliMoodFamily.entries.size, all.toSet().size)
        }
    }

    @Test fun slots_rotateOneStepPerSendTurn_andStayAPermutation() {
        // 卷一图纸 §4.3 / §4.4 槽位表在此重打一遍（0.12/0.10 · 0.88/0.34 · 0.08/0.66 · 0.82/0.92）。
        assertEquals(0.12f, MOOD_SLOTS[0].x, 1e-6f)
        assertEquals(0.10f, MOOD_SLOTS[0].y, 1e-6f)
        assertEquals(0.88f, MOOD_SLOTS[1].x, 1e-6f)
        assertEquals(0.34f, MOOD_SLOTS[1].y, 1e-6f)
        assertEquals(0.08f, MOOD_SLOTS[2].x, 1e-6f)
        assertEquals(0.66f, MOOD_SLOTS[2].y, 1e-6f)
        assertEquals(0.82f, MOOD_SLOTS[3].x, 1e-6f)
        assertEquals(0.92f, MOOD_SLOTS[3].y, 1e-6f)
        // 第 0 轮 = 原位；每 +1 轮整体挪一格；四点始终占满四个槽（是轮换不是塌缩）。
        repeat(4) { i -> assertEquals(MOOD_SLOTS[i], liuliMoodSlot(i, 0)) }
        repeat(4) { i -> assertEquals(MOOD_SLOTS[(i + 1) % 4], liuliMoodSlot(i, 1)) }
        repeat(9) { turn ->
            assertEquals(4, (0..3).map { liuliMoodSlot(it, turn) }.toSet().size)
        }
        // 绕一圈回到原位。
        repeat(4) { i -> assertEquals(MOOD_SLOTS[i], liuliMoodSlot(i, 4)) }
    }

    @Test fun reduceMotion_freezesSlotRotation() {
        // 复核 R1 🟡-4（图纸 §0 ② 4 / E14「RM 不轮转」）：减弱动画时相位恒 0，发送再多四点也不挪位。
        assertEquals(0, liuliMoodSlotTurn(sendTurn = 5, reduceMotion = true))
        assertEquals(5, liuliMoodSlotTurn(sendTurn = 5, reduceMotion = false))
        repeat(4) { i -> assertEquals(MOOD_SLOTS[i], liuliMoodSlot(i, liuliMoodSlotTurn(7, reduceMotion = true))) }
    }

    @Test fun reduceMotion_makesTargetsLandImmediately() {
        // T2-6：减弱动画 → 换色与绕位都是 snap（目标即终值）。
        assertTrue(liuliMoodColorSpec(reduceMotion = true) is SnapSpec)
        assertTrue(liuliMoodSlotSpec(reduceMotion = true) is SnapSpec)
    }

    @Test fun normalMotion_usesLockedColorTween() {
        // 图纸 §3.2 锁：换色 tween(2600, EaseOut)。
        val spec = liuliMoodColorSpec(reduceMotion = false)
        assertTrue(spec is TweenSpec)
        val tween = spec as TweenSpec
        assertEquals(2600, tween.durationMillis)
        assertEquals(0, tween.delay)
        assertEquals(AppMotion.EaseOut, tween.easing)
    }
}
