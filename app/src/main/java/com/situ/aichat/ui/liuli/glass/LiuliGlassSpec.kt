package com.situ.aichat.ui.liuli.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.situ.aichat.ui.designsystem.Palette

/** 琉璃玻璃全部落值（琉璃 2.0 卷一图纸 §4.2·唯一调参点）。 */
object LiuliGlassSpec {
    // ── 自研毛玻璃（毛玻璃档 / 安卓 13 以下）：设计稿 `.gl.frost` ──
    val blurRadius = 24.dp
    const val SATURATION = 1.5f
    val frostLight = Color.White.copy(alpha = 0.62f)
    val frostDark = Palette.DuskFrost.copy(alpha = 0.72f)
    /** 只着色兜底（内容层 / 独立窗口 / 宿主关门 / 安卓 12 以下）：厚到能压住身后内容。 */
    val fallbackLight = Color.White.copy(alpha = 0.88f)
    val fallbackDark = Palette.DuskFrost.copy(alpha = 0.86f)
    /**
     * 只着色兜底**先铺的不透明垫底**（卷一复核 R1 🔴-1）：88% 染色挡不住玻璃自己画在形状下面的投影——
     * 圆钮里透出「八角亮斑」、长条里透出亮内框（扫码页实拍）。先垫一层实色，兜底玻璃整片不透明，投影只留在形状外。
     * 取值 = 各档 `surface.raised`（与三种弹层壳的纸面同色），故染色叠上去后与「纸垫底」弹层观感一致。
     */
    val fallbackUnderlayLight = Color.White
    val fallbackUnderlayDark = Palette.DuskRaised
    /** 顶沿 1px 高光（稿 spec）。 */
    val specularLight = Color.White.copy(alpha = 0.95f)
    val specularDark = Color.White.copy(alpha = 0.26f)
    /** 白色细边（稿 rim）：可见宽度 [rimWidth]。 */
    val rimLight = Color.White.copy(alpha = 0.78f)
    val rimDark = Color.White.copy(alpha = 0.16f)
    val rimWidth = 0.5.dp
    /** 顶部高光带（稿 sheen）：顶端此色 → [SHEEN_STOP] 高处透明。 */
    val sheenLight = Color.White.copy(alpha = 0.55f)
    val sheenDark = Color.White.copy(alpha = 0.08f)
    const val SHEEN_STOP = 0.42f

    // ── Haze（标准 / 通透）：模拟器实拍调定值（总规划 §7） ──
    val hazeStandardLight = Color.White.copy(alpha = 0.30f)
    val hazeStandardDark = Palette.DuskBase.copy(alpha = 0.50f)
    val hazeSheerLight = Color.White.copy(alpha = 0.14f)
    val hazeSheerDark = Palette.DuskBase.copy(alpha = 0.28f)
    /** 壁纸加厚（卷三 §0.2-8·卷四起独立配方）：regular + 加厚底色；0.63 门槛按它算，勿改。 */
    val hazeThickLight = Color.White.copy(alpha = 0.52f)
    val hazeThickDark = Palette.DuskBase.copy(alpha = 0.62f)
    /** 透镜（卷四 §0.2-2）：clear 样式 + 比底栏亮一层的白。 */
    val hazeLensLight = Color.White.copy(alpha = 0.30f)
    val hazeLensDark = Color.White.copy(alpha = 0.10f)

    // ── 影 ──
    val shadowElevation = 8.dp
    val buttonShadowElevation = 2.dp

    // ── 零件描边（Chip / Field / SaveBar / DraftBar / PopupMenu 沿用·卷二换零件时再议） ──
    val hairlineLight = Palette.DawnInk.copy(alpha = 0.09f)
    val hairlineDark = Color.White.copy(alpha = 0.10f)
    val hairlineWidth = 0.5.dp
}
