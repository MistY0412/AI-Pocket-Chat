package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.graphics.Color
import com.situ.aichat.data.model.GlassTier
import com.situ.aichat.ui.designsystem.LiuliDarkAppColors
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import com.situ.aichat.ui.designsystem.NON_TEXT
import com.situ.aichat.ui.designsystem.Palette
import com.situ.aichat.ui.designsystem.assertContrast
import com.situ.aichat.ui.designsystem.over
import com.situ.aichat.ui.liuli.chat.LiuliMoodFamily
import com.situ.aichat.ui.liuli.chat.liuliMoodBlobColors
import com.situ.aichat.ui.liuli.chat.liuliWallpaperIsDark
import com.situ.aichat.ui.liuli.glass.LiuliGlassRole
import com.situ.aichat.ui.liuli.glass.LiuliGlassSpec
import com.situ.aichat.ui.liuli.glass.liuliFallbackUnderlay
import com.situ.aichat.ui.liuli.glass.liuliFrostedTint
import com.situ.aichat.ui.liuli.glass.liuliHazeRecipe
import org.junit.Test

/**
 * 琉璃对比度看门（琉璃 2.0 卷三 §0.2-15·§7 T1-3）：自 `ColorContrastTest` 只搬不改迁入卷一 / 卷二的三例，
 * 卷三新增「文字 × 底」全写在这里（旧文件已过行数线）。合成口径同迁入例：逐层 alpha 覆实底
 * （[assertContrast] / [over] 住测试源集 `ContrastTestKit.kt`）。
 */
class LiuliColorContrastTest {

    /**
     * T1-4（琉璃 2.0 卷一图纸 2026-09-25 §7）：玻璃上的字压在**每一档玻璃的合成底**上都要 ≥ 4.5。
     *
     * 合成口径 = 决定底色的那一步：玻璃染色（含透明度）覆在身后底 `u` 上（模糊 / 折射只搬运身后像素、不改平均明度）。
     * 身后底取页面底色 + 四个柔光光晕原色（最坏情况 = 字恰好压在光晕中心）。染色集 = 毛玻璃（模糊 / 兜底）+
     * Haze 标准 / 通透 × 三角色；毛玻璃另算顶部高光带最亮处；三壳（弹层 / 对话框 / 菜单）= 兜底染色覆 `surface.raised`
     * 纸面。四色：玻璃上主 / 次字、失败红、强调字 `accent.text`。琉璃昼夜两方案全测。
     * 透镜（卷四 [LiuliGlassRole.Lens]）不在染色集里：它按真实调色在 `LiuliHazeDarkResponseTest` 断言。
     */
    @Test fun liuliGlass_textOnEveryTierComposite() {
        val body = 4.5
        listOf(
            Triple(LiuliLightAppColors, false, "liuli-light"),
            Triple(LiuliDarkAppColors, true, "liuli-dark"),
        ).forEach { (c, dark, name) ->
            val onGlass = if (dark) LiuliOnGlassDark else LiuliOnGlassLight
            val unders = listOf(c.surface.base) + LiuliAmbientSpec.glows(dark)
            val frostTints = listOf(liuliFrostedTint(dark, blurred = true), liuliFrostedTint(dark, blurred = false))
            val hazeTints = listOf(GlassTier.STANDARD, GlassTier.SHEER).flatMap { tier ->
                LiuliGlassRole.entries.filter { it != LiuliGlassRole.Lens }.map { role -> liuliHazeRecipe(role, tier, dark).tint }
            }
            val sheen = if (dark) LiuliGlassSpec.sheenDark else LiuliGlassSpec.sheenLight
            fun checkFour(bg: Color, label: String) {
                assertContrast(onGlass.primary, bg, body, "$name 玻璃主字×$label")
                assertContrast(onGlass.secondary, bg, body, "$name 玻璃次字×$label")
                assertContrast(c.status.onError, bg, body, "$name 失败红×$label")
                assertContrast(c.accent.text, bg, body, "$name 强调字×$label")
            }
            (frostTints + hazeTints).forEachIndexed { ti, t ->
                unders.forEachIndexed { ui, u ->
                    checkFour(over(t, t.alpha, u), "染色#$ti 覆 底#$ui")
                }
            }
            frostTints.forEachIndexed { ti, t ->
                unders.forEachIndexed { ui, u ->
                    checkFour(over(sheen, sheen.alpha, over(t, t.alpha, u)), "毛玻璃#$ti 高光带顶 覆 底#$ui")
                }
            }
            val fb = liuliFrostedTint(dark, blurred = false)
            val shellBg = over(fb, fb.alpha, c.surface.raised)
            assertContrast(onGlass.primary, shellBg, body, "$name 壳内主文字×纸面兜底玻璃")
            assertContrast(onGlass.secondary, shellBg, body, "$name 壳内次文字×纸面兜底玻璃")
            assertContrast(c.status.onError, shellBg, body, "$name 壳内错误红×纸面兜底玻璃")
            // 复核 R1 🔴-1：只着色兜底自带不透明垫底 → 它的真实底色就是「染色覆垫底」，与身后无关。
            val fallbackBg = over(fb, fb.alpha, liuliFallbackUnderlay(dark))
            checkFour(fallbackBg, "只着色兜底（染色覆不透明垫底）")
        }
    }

    /**
     * 卷四二级屏新增的「文字 / 图标 × 底」（图纸 2026-09-06 卷四 §8 C2 · 契约 §6.5）：
     * ① 图标砖上的**白图标**（琉璃 2.0 卷二 §4.2 起 = [LiuliTileTone] 六色渐变的起止两端·夜档同色，故只算一遍）；
     * ② 分组纸面 `surface.raised` 上的正文两档（主 / 次）与钴蓝值字 `accent.text`；
     * ③ 危险行红字 `status.onError` × 分组纸面；
     * ④ 选项卡选中态 `accent.onContainer` × `accent.container`（A-6）。
     *
     * 砖上白图标按 **WCAG 1.4.11 非文字对比 3:1** 钉，不按正文 4.5：砖是**分类装饰**（`contentDescription = null`，
     * 行的语义全由标题文字承载）。卷二乙版（用户 09-25 选：六色各加深一档）图纸作者实测最紧 3.08。
     *
     * 组标题 / 脚注：琉璃 2.0 卷二 §4.3 起改走 `text.secondary`（原 `text.tertiary` 压纸面仅 2.65:1·卷四 D-4）。它们压在
     * 裸柔光底上（不在卡里），光晕正中的最坏值见卷二复核 R1 待定 P-1（交卷三统一定「裸柔光底上的字」），本例不断言。
     */
    @Test fun liuliSecondaryPageSurfaces_meetWcag() {
        val body = 4.5
        LiuliTileTone.entries.forEach { tone ->
            assertContrast(Palette.White, tone.start, NON_TEXT, "琉璃图标块 ${tone.name} 白图标×渐变起点")
            assertContrast(Palette.White, tone.end, NON_TEXT, "琉璃图标块 ${tone.name} 白图标×渐变终点")
        }
        listOf(
            LiuliLightAppColors to "liuli-light",
            LiuliDarkAppColors to "liuli-dark",
        ).forEach { (c, name) ->
            val paper = c.surface.raised
            assertContrast(c.text.primary, paper, body, "$name 行标题×分组纸面")
            assertContrast(c.text.secondary, paper, body, "$name 行副标 / 右值×分组纸面")
            assertContrast(c.accent.text, paper, body, "$name 统计值钴蓝×分组纸面")
            assertContrast(c.status.onError, paper, body, "$name 危险行红字×分组纸面")
            assertContrast(c.status.onWarning, paper, body, "$name 「未配置」警示值×分组纸面")
            // 外观页选项卡选中态（A-6）。
            assertContrast(c.accent.onContainer, c.accent.container, body, "$name 选项卡选中字×accent.container")
            // 分段条纸面态：选中段落在 surface.raised、未选段落在 surface.sunken。
            assertContrast(c.text.primary, paper, body, "$name 分段选中字×选中片")
            assertContrast(c.text.secondary, c.surface.sunken, body, "$name 分段未选字×轨底")
        }
        // 卷四 §4.9：资料页头部去掉了照片头图与底遮罩（原「头图白名 × 渐变底 + 遮罩」一例随之删除）。
    }

    /**
     * 琉璃 2.0 卷二半透明卡片族（图纸 2026-09-25 卷二 §7 T1-3·§0.1-C）：半透明卡（浅 白 52% / 深 #241F3A 58%）
     * 压在页面底或四个柔光光晕原色上（最坏 = 卡恰好压在光晕中心），再各叠标签底 / 输入框底 / 分段轨 / 按压底 /
     * 危险底；五种字（主 / 次 / 强调 / 危险红 / 警示）全 ≥ 4.5。另：分段选中片（卷四 = 画出来的透镜最淡处）上的主字；渐变三站上的白字
     * （选中标签 / 主按钮）。合成口径同 [liuliGlass_textOnEveryTierComposite]（逐层 alpha 覆实底）。
     */
    @Test fun liuliCardFamily_textOnTranslucentMaterials() {
        val body = 4.5
        listOf(
            Triple(LiuliLightAppColors, false, "liuli-light"),
            Triple(LiuliDarkAppColors, true, "liuli-dark"),
        ).forEach { (c, dark, name) ->
            val unders = listOf(c.surface.base) + LiuliAmbientSpec.glows(dark)
            unders.forEachIndexed { ui, u ->
                val cardFill = LiuliMaterials.cardFill(dark)
                val card = over(cardFill, cardFill.alpha, u)
                fun onCard(fill: Color): Color = over(fill, fill.alpha, card)
                val track = onCard(LiuliMaterials.segTrack(dark))
                listOf(
                    "卡片" to card,
                    "标签底" to onCard(LiuliMaterials.chipFill(dark)),
                    "输入框底" to onCard(LiuliMaterials.fieldFill(dark)),
                    "分段轨" to track,
                    "按压底" to onCard(LiuliMaterials.pressTint(dark)),
                    "危险底" to onCard(LiuliMaterials.dangerFill(c.status.onError, dark)),
                ).forEach { (label, bg) ->
                    val where = "$name $label（卡压底#$ui）"
                    assertContrast(c.text.primary, bg, body, "$where 主字")
                    assertContrast(c.text.secondary, bg, body, "$where 次字")
                    assertContrast(c.accent.text, bg, body, "$where 强调字")
                    assertContrast(c.status.onError, bg, body, "$where 危险红")
                    // 警示字不上危险底（卷二复核 R1 裁决 D-1）：危险底只由「玻璃钮 + danger」画、钮字恒为危险红
                    // （`LiuliButton` 的 contentColor），界面上不存在「警示字 × 危险底」；此对实测最坏 4.40，不断言。
                    if (label != "危险底") assertContrast(c.status.onWarning, bg, body, "$where 警示字")
                }
                // 卷四：选中片 = 画出来的透镜，取最淡处（底端白 浅 0.55 / 深 0.07）。
                val lensBottom = if (dark) LiuliPaintedLensSpec.FILL_BOTTOM_DARK else LiuliPaintedLensSpec.FILL_BOTTOM_LIGHT
                assertContrast(c.text.primary, over(Color.White, lensBottom, track), body, "$name 分段选中字×透镜最淡处（卡压底#$ui）")
            }
        }
        listOf(Palette.DawnIris, Palette.DawnOrchid, Palette.DawnRose).forEachIndexed { i, stop ->
            assertContrast(Palette.White, stop, body, "选中标签 / 主按钮白字×主色渐变第 $i 站")
        }
    }

    /** 两套琉璃配色（浅 / 深）。 */
    private val schemes = listOf(
        Triple(LiuliLightAppColors, false, "liuli-light"),
        Triple(LiuliDarkAppColors, true, "liuli-dark"),
    )

    /** 光晕两两各半混合（i ≠ j）：字恰好压在两团光晕交界处的最坏情况。 */
    private fun pairwiseHalves(glows: List<Color>): List<Color> =
        glows.indices.flatMap { i -> glows.indices.filter { it != i }.map { j -> over(glows[j], 0.5f, glows[i]) } }

    /**
     * 卷三 §0.2-5 / §0.1-D：直接压在**裸柔光底**上的字（组标题 / 脚注、页说明、空态、泡下时间戳与回执、系统行）。
     * 底 = 页面底色 ∪ 四光晕 ∪ 四个非平静心情族的混色光晕 ∪ 光晕两两各半；主 / 次 / 强调字全 ≥ 4.5。
     * 图纸预算最紧：浅 次 4.62 / 强调 4.63；深 次 4.63 / 强调 4.62。
     */
    @Test fun liuliText_onBareAmbient() {
        val body = 4.5
        val moodFamilies = LiuliMoodFamily.entries.filter { it != LiuliMoodFamily.CALM }
        assert(moodFamilies.size == 4) { "非平静族应为四个（实得 ${moodFamilies.size}）" }
        schemes.forEach { (c, dark, name) ->
            val glowSets = listOf(LiuliAmbientSpec.glows(dark)) + moodFamilies.map { liuliMoodBlobColors(it, c) }
            val bgs = listOf(c.surface.base) + glowSets.flatMap { it + pairwiseHalves(it) }
            bgs.forEachIndexed { i, bg ->
                assertContrast(c.text.primary, bg, body, "$name 主字×裸柔光底#$i")
                assertContrast(c.text.secondary, bg, body, "$name 次字×裸柔光底#$i")
                assertContrast(c.accent.text, bg, body, "$name 强调字×裸柔光底#$i")
            }
        }
    }

    /**
     * 卷三 §0.2-8 / §0.1-D：有壁纸时的聊天玻璃件。灰阶 g = 0..255（Rec.601 亮度 = g / 255）按 0.63 门槛选深浅，
     * 玻璃按壁纸加厚配方（Haze 加厚 + 毛玻璃）合成；玻璃上主 / 次字全 ≥ 4.5。
     */
    @Test fun liuliChatChrome_onWallpaperGrays() {
        val body = 4.5
        for (g in 0..255) {
            val gray = Color(g, g, g)
            val dark = liuliWallpaperIsDark(g / 255f)
            val onGlass = if (dark) LiuliOnGlassDark else LiuliOnGlassLight
            listOf(
                "Haze 加厚" to liuliHazeRecipe(LiuliGlassRole.Bar, GlassTier.SHEER, dark, thick = true).tint,
                "毛玻璃" to liuliFrostedTint(dark, blurred = true),
            ).forEach { (label, tint) ->
                val bg = over(tint, tint.alpha, gray)
                assertContrast(onGlass.primary, bg, body, "$label 主字×灰 $g（${if (dark) "深" else "浅"}）")
                assertContrast(onGlass.secondary, bg, body, "$label 次字×灰 $g（${if (dark) "深" else "浅"}）")
            }
        }
    }

    /** 卷三 §0.2-6：「我」页钱包小卡（半透明卡）上的金币数字——卡压页面底 / 四光晕 / 光晕两两各半，全 ≥ 4.5。 */
    @Test fun liuliGoldOnCard() {
        val body = 4.5
        schemes.forEach { (c, dark, name) ->
            val glows = LiuliAmbientSpec.glows(dark)
            val unders = listOf(c.surface.base) + glows + pairwiseHalves(glows)
            val fill = LiuliMaterials.cardFill(dark)
            unders.forEachIndexed { i, u ->
                assertContrast(LiuliPalette.goldOnCard(dark), over(fill, fill.alpha, u), body, "$name 金币字×半透明卡（压底#$i）")
            }
        }
    }

    /**
     * 卷四 §0.1-H 新增的「文字 × 底」：① 关系小标签 `accent.onContainer × accent.container`（两套·不透明）；
     * ② 联系人卡火苗徽章白字 × 渐变两端 #C24A17 / #AE381C（重打字面量·图纸算 4.90 / 6.21）；③ 最近动态强调字
     * `accent.text` × 半透明卡压 {页面底 + 四光晕}。「+」面板白图标 × 六色两端已由 [liuliSecondaryPageSurfaces_meetWcag] 覆盖。
     */
    @Test fun liuliVol4_newPairs() {
        val body = 4.5
        schemes.forEach { (c, dark, name) ->
            assertContrast(c.accent.onContainer, c.accent.container, body, "$name 关系标签字×accent.container")
            val fill = LiuliMaterials.cardFill(dark)
            (listOf(c.surface.base) + LiuliAmbientSpec.glows(dark)).forEachIndexed { i, u ->
                assertContrast(c.accent.text, over(fill, fill.alpha, u), body, "$name 最近动态强调字×半透明卡（压底#$i）")
            }
        }
        listOf(Color(0xFFC24A17), Color(0xFFAE381C)).forEachIndexed { i, stop ->
            assertContrast(Palette.White, stop, body, "火苗徽章白字×渐变第 $i 端")
        }
    }
}
