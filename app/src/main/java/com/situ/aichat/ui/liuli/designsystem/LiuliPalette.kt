package com.situ.aichat.ui.liuli.designsystem

import androidx.compose.ui.graphics.Color
import com.situ.aichat.ui.designsystem.Palette

/**
 * 琉璃专属字面量的**唯一出口**（图纸 2026-09-05 卷二C A-10 · §9 ⑤「禁裸 `Color(0x…)`」）。
 *
 * 为什么这些色不进 [com.situ.aichat.ui.designsystem.AppColors]：它们是琉璃这张脸自己的长相
 * ——红包哑光红 / 恒暗舞台卡 / 头像光环 —— 暖陶那张脸没有对应槽位（暖陶红包是陶红
 * `economy.redPacketStart/End`，两者**不可互换**）。`ui/designsystem/Palette.kt` 零碰。
 *
 * 深浅两档：红包 / 恒暗卡**双档同值**——它们本身就是「自带底色的面」，不随主题翻浅（同暖陶红包卡与
 * 见面剧场的既有判例）；卷三的头像光环与半透明卡金币字按 `dark` 取值（[avatarRing] / [goldOnCard]）。
 */
internal object LiuliPalette {

    // ── 红包卡（对版稿 `.card.red`·哑光红 160°·**非**暖陶陶红） ───────────────────────
    val packetRedTop = Color(0xFFC8443A)
    val packetRedBottom = Color(0xFFA93A31)

    /** 哑光红上的暖白字（对版稿 `color:#FFF3E6`）。 */
    val packetText = Color(0xFFFFF3E6)

    /** 红包卡上的金（图标 / 状态胶囊字·对版稿 `#F3D48B`）。 */
    val packetGold = Color(0xFFF3D48B)

    /** 烫金封印上的「福」字（对版稿 `color:#5A3A08`）。 */
    val sealInk = Color(0xFF5A3A08)

    // ── 线下卡恒暗舞台（对版稿 `.card.dark`·与见面剧场同源口径） ─────────────────────
    val stageInk = Color(0xFF111418)
    val stageText = Color(0xFFF2F4F8)

    /** 卡顶钴蓝微光的**基色**（用处见 `LiuliOfflineCards`：径向 60%×40% at (70%, 0)·0.22 透明度在用点施加）。 */
    val stageGlow = Color(0xFF6FA8FF)

    /** 恒暗卡图标块上的图标色（对版稿 `.card.dark .hd i svg{stroke:#9FC2FF}`）。 */
    val stageIcon = Color(0xFF9FC2FF)

    // ── 二级屏图标砖（契约 §6.5「图标砖色板」·琉璃 2.0 卷二 §4.2：六色渐变 · 乙版 · 白图标 ≥ 3） ─────────
    /**
     * 为什么仍按组起名：十个设置分组各认一块砖（像 iOS 设置里的彩砖），取值收敛到 [LiuliTileTone] 六色渐变
     * （用户 09-25 选乙：设计稿六色各加深一档）；相邻两组不同色（映射锁定·卷二 §4.2）。砖是**分类装饰**，
     * 白 16 图标压渐变两端色的对比按非文字 3:1 由 `ColorContrastTest` 钉。夜档同色。
     */
    val tilePersonalize = LiuliTileTone.Lilac   // 个性化
    val tileApi = LiuliTileTone.Sky             // API 与模型
    val tileChat = LiuliTileTone.Peach          // 聊天行为
    val tileMemory = LiuliTileTone.Mint         // 记忆与设定
    val tileVoice = LiuliTileTone.Gold          // 语音
    val tileCreation = LiuliTileTone.Rose       // AI 自动创作
    val tileStory = LiuliTileTone.Lilac         // 故事
    val tileWorld = LiuliTileTone.Sky           // 世界
    val tileSystem = LiuliTileTone.Peach        // 系统与通知 / 功能开关
    val tileData = LiuliTileTone.Mint           // 数据与诊断 / 关于

    /** 主页头像光环三色（过审稿 `ringG`·135°·纯装饰·卷三 §3.4）。 */
    private val RingLight = Triple(Color(0xFFFFB38A), Color(0xFFC98AF0), Color(0xFF8FC8FF))
    private val RingDark = Triple(Color(0xFFFF9E7A), Color(0xFFB77CEA), Color(0xFF6FB1FF))
    fun avatarRing(dark: Boolean): Triple<Color, Color, Color> = if (dark) RingDark else RingLight

    /**
     * 半透明卡上的金币数字（卷三 §0.2-6）：`economy.gold` 压半透明卡最坏 3.91 → 浅档压深到 #7E6119（最坏 4.68）；
     * 夜档 `GoldDark` 压深卡 6.69 不改。**只用于半透明卡**，不透明面仍用 `economy.gold`。
     */
    private val GoldOnCardLight = Color(0xFF7E6119)
    fun goldOnCard(dark: Boolean): Color = if (dark) Palette.GoldDark else GoldOnCardLight
}

/** 设置图标块六色（用户 09-25 选乙：设计稿六色各加深一档，白图标 ≥ 3:1）。135° 渐变 start → end。 */
enum class LiuliTileTone(val start: Color, val end: Color) {
    Peach(Color(0xFFE8703F), Color(0xFFDB5A58)),
    Lilac(Color(0xFF8E7CF0), Color(0xFF7361E0)),
    Sky(Color(0xFF3F93E6), Color(0xFF3F7FDE)),
    Mint(Color(0xFF2FA386), Color(0xFF238F77)),
    Rose(Color(0xFFDE659D), Color(0xFFC9538B)),
    Gold(Color(0xFFBF8718), Color(0xFFB8731C)),
}
