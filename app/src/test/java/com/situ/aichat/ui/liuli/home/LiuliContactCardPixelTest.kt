package com.situ.aichat.ui.liuli.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.contacts.ContactsViewModel
import com.situ.aichat.ui.designsystem.LiuliLightAppColors
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * T2-10（琉璃 2.0 卷四 §4.8 / §7）像素部分：火苗徽章住头像块里（`clearAndSetSemantics{}`·对读屏隐身，读屏只读整卡那句），
 * 语义树里找不到它，故同卷三 T2-3 的判例改用 NATIVE 真渲染从像素量：
 * ① 徽章在头像右下——徽章橙红渐变区（#C24A17 → #AE381C）的右缘 = 光环右缘 + 6 − 1.5（纸色描边）、底缘 = 光环底 + 4 − 1.5（±0.5dp）；
 * ② 连续 0 天不画徽章（同一区域零橙红像素）；③「初识」与「恋人」两枚关系标签底色像素相同（±3·卷四统一样式）。
 * xhdpi（1dp = 2px）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
class LiuliContactCardPixelTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private var view: View? = null

    private fun row(uuid: String, name: String, streak: Int = 0, relationship: String? = null) = ContactsViewModel.Row(
        character = CharacterEntity(
            uuid = uuid,
            name = name,
            creationDate = now,
            streakCount = streak,
            lastChatDate = if (streak > 0) now else null,
        ),
        relationshipDisplay = relationship,
        recentEvent = null,
    )

    private fun show(rows: List<ContactsViewModel.Row>): Bitmap {
        compose.setContent {
            view = LocalView.current
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliContactsContent(
                        rows = rows, query = "", shareMode = false, fallbackUuids = emptySet(), nowMillis = now,
                        onQueryChange = {}, onCancelShare = {}, onCreateCharacter = {}, onOpenRow = {}, onOpenProfile = {},
                        onEdit = {}, onRequestDelete = {}, onLongPress = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val v = checkNotNull(view)
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }

    private val density get() = checkNotNull(view).resources.displayMetrics.density

    private fun isBadgeOrange(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return r in 150..215 && g in 35..95 && b in 10..50
    }

    /** 光环外框（dp·根坐标）：光环 64 水平居中、顶内距 16。返回 (右, 底)。 */
    private fun ringRightBottom(cd: String): Pair<Float, Float> {
        val card = compose.onNodeWithContentDescription(cd).getUnclippedBoundsInRoot()
        val cx = (card.left.value + card.right.value) / 2f
        return (cx + 32f) to (card.top.value + 16f + 64f)
    }

    /** 光环右下角附近的橙红像素外框（dp）；没有则 null。 */
    private fun orangeBox(bmp: Bitmap, ringRight: Float, ringBottom: Float): FloatArray? {
        val d = density
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (y in ((ringBottom - 30f) * d).toInt() until ((ringBottom + 12f) * d).toInt()) {
            for (x in ((ringRight - 40f) * d).toInt() until ((ringRight + 12f) * d).toInt()) {
                if (isBadgeOrange(bmp.getPixel(x, y))) {
                    minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                }
            }
        }
        return if (maxX < 0) null else floatArrayOf(minX / d, minY / d, (maxX + 1) / d, (maxY + 1) / d)
    }

    @Test fun streakBadge_sitsAtAvatarBottomRight() {
        val bmp = show(listOf(row("a", "小满", streak = 7)))
        val (ringRight, ringBottom) = ringRightBottom("小满，连续 7 天")
        val box = checkNotNull(orangeBox(bmp, ringRight, ringBottom)) { "连续 7 天应画出火苗徽章" }
        assertEquals("徽章渐变区右缘 = 光环右 + 6 − 1.5", ringRight + 6f - 1.5f, box[2], 0.5f)
        assertEquals("徽章渐变区底缘 = 光环底 + 4 − 1.5", ringBottom + 4f - 1.5f, box[3], 0.5f)
    }

    @Test fun zeroStreak_drawsNoBadge() {
        val bmp = show(listOf(row("a", "阿泠", streak = 0)))
        val (ringRight, ringBottom) = ringRightBottom("阿泠")
        assertEquals("连续 0 天不画徽章", null, orangeBox(bmp, ringRight, ringBottom)?.toList())
    }

    @Test fun initialAndNamedRelationshipTags_shareTheSameFill() {
        val bmp = show(listOf(row("a", "小满"), row("b", "阿泠", relationship = "恋人")))
        val d = density
        fun fillLeftOf(label: String): Int {
            // 语义框 = 文字本体（内距在框外）：取文字左侧 4dp（8dp 横向内距之中）、竖直正中 = 纯标签底。
            val b = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            return bmp.getPixel(((b.left.value - 4f) * d).toInt(), (((b.top.value + b.bottom.value) / 2f) * d).toInt())
        }
        val a = fillLeftOf("初识")
        val b = fillLeftOf("恋人")
        val ok = listOf(16, 8, 0).all { s -> abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF)) <= 3 }
        assertTrue("「初识」#${Integer.toHexString(a)} 与「恋人」#${Integer.toHexString(b)} 标签底应同色 ±3", ok)
        // 取样点确在标签里：= 不透明的 accent.container（否则两点都落在卡面上也会「同色」）。
        val c = LiuliLightAppColors.accent.container.toArgb()
        val inPill = listOf(16, 8, 0).all { sh -> abs(((a shr sh) and 0xFF) - ((c shr sh) and 0xFF)) <= 3 }
        assertTrue("取样 #${Integer.toHexString(a)} 应 = accent.container #${Integer.toHexString(c)}", inPill)
    }
}
