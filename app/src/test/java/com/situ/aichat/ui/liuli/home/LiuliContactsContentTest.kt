package com.situ.aichat.ui.liuli.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.contacts.ContactsViewModel
import com.situ.aichat.ui.contacts.RecentEvent
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-6：琉璃联系人的长相与语义（图纸 2026-09-06 卷三 §7 T2-6 · §4.3 B / §4.4 · E6 / E7）。
 *
 * 无 VM——直接驱动 [LiuliContactsContent]。重点钉三件容易在换脸时掉的东西：**a11y 那一句合并 cd 与三个
 * customActions**（暖陶 F5 逐字）、**头像点 = 资料页且不顺带进会话**、关系 · 职业 / 纪事两行（卷四）与火苗的显隐门。
 *
 * 火苗用真的 `StreakManager`（`streakCount` + 今天的 `lastChatDate`），不是自己造一个数——换脸不许换算法。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliContactsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private var opened: String? = null
    private var profiled: String? = null
    private var edited: String? = null
    private var deleted: String? = null
    private var created = 0
    private var cancelledShare = 0
    private var queries = mutableListOf<String>()

    private fun character(
        uuid: String,
        name: String,
        occupation: String = "",
        streak: Int = 0,
    ) = CharacterEntity(
        uuid = uuid,
        name = name,
        creationDate = now,
        occupation = occupation,
        streakCount = streak,
        lastChatDate = if (streak > 0) now else null,
    )

    private fun row(
        uuid: String = "a",
        name: String = "小满",
        relationship: String? = null,
        occupation: String = "",
        streak: Int = 0,
        recentEvent: RecentEvent? = null,
    ) = ContactsViewModel.Row(
        character = character(uuid, name, occupation, streak),
        relationshipDisplay = relationship,
        recentEvent = recentEvent,
    )

    private fun show(
        rows: List<ContactsViewModel.Row>,
        query: String = "",
        shareMode: Boolean = false,
        fallbackUuids: Set<String> = emptySet(),
    ) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliContactsContent(
                        rows = rows,
                        query = query,
                        shareMode = shareMode,
                        fallbackUuids = fallbackUuids,
                        nowMillis = now,
                        onQueryChange = { queries += it },
                        onCancelShare = { cancelledShare++ },
                        onCreateCharacter = { created++ },
                        onOpenRow = { opened = it.character.uuid },
                        onOpenProfile = { profiled = it.character.uuid },
                        onEdit = { edited = it.character.uuid },
                        onRequestDelete = { deleted = it.character.uuid },
                        onLongPress = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun 无里程碑显初识有里程碑显称谓() {
        show(listOf(row(uuid = "a", name = "小满"), row(uuid = "b", name = "阿泠", relationship = "恋人")))
        compose.onNodeWithText("初识").assertIsDisplayed()
        compose.onNodeWithText("恋人").assertIsDisplayed()
    }

    /** 卷四 §4.8-3 / -4：职业恒在关系标签旁、纪事单独一行在其下、职业空 → 神秘占位。 */
    @Test fun 职业恒在关系标签旁且纪事单独一行() {
        show(
            listOf(
                row(uuid = "a", name = "甲", recentEvent = RecentEvent.Milestone("恋人", now - 86_400_000L), occupation = "画师"),
                row(uuid = "c", name = "丙"),
            ),
        )
        val job = compose.onNodeWithText("画师", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pill = compose.onAllNodesWithText("初识", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val event = compose.onNodeWithText("成为恋人", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("职业与关系标签同一行（竖直居中对齐）", (pill.top + pill.bottom).value / 2f, (job.top + job.bottom).value / 2f, 1f)
        assertTrue("职业在关系标签右边", job.left >= pill.right)
        assertTrue("纪事单独一行、在职业之下", event.top >= job.bottom)
        compose.onNodeWithText("TA的职业很神秘", useUnmergedTree = true).assertIsDisplayed()
    }

    /** 卷四：火苗徽章住头像块（对读屏隐身），天数只由整卡那句 cd 读出；徽章的像素显隐见 `LiuliContactCardPixelTest`。 */
    @Test fun 火苗只在连续天数大于零时出现() {
        show(listOf(row(uuid = "a", name = "小满", streak = 7), row(uuid = "b", name = "阿泠", streak = 0)))
        compose.onNodeWithContentDescription("小满，连续 7 天").assertIsDisplayed()
        compose.onNodeWithContentDescription("阿泠").assertIsDisplayed()
        compose.onNodeWithContentDescription("连续 0 天", substring = true).assertDoesNotExist()
    }

    @Test fun 合并成一句cd且带三个自定义动作() {
        show(listOf(row(uuid = "a", name = "小满", relationship = "恋人", occupation = "画师", streak = 3)))
        val node = compose.onNodeWithContentDescription("小满，恋人，画师，连续 3 天").fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf("查看资料", "编辑", "删除"), actions.map { it.label })
        actions[0].action()
        actions[1].action()
        actions[2].action()
        assertEquals("a", profiled)
        assertEquals("a", edited)
        assertEquals("a", deleted)
        assertEquals("三个自定义动作都不该顺带进会话", null, opened)
    }

    /** 卷四 §4.8-6：职业与纪事都读，顺序 职业 → 纪事。 */
    @Test fun 有纪事有职业时读屏句两项都读() {
        show(listOf(row(uuid = "a", name = "甲", occupation = "画师", streak = 3, recentEvent = RecentEvent.Milestone("恋人", now - 86_400_000L))))
        val cd = compose.onNodeWithContentDescription("甲，画师，", substring = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue("读屏句应为「甲，画师，<纪事>，连续 3 天」（实得 $cd）", Regex("^甲，画师，[^，]*成为恋人，连续 3 天$").matches(cd))
    }

    @Test fun 待重生成红点只在兜底集里出现() {
        show(listOf(row(uuid = "a", name = "小满")), fallbackUuids = setOf("a"))
        compose.onNodeWithContentDescription("小满，有见面摘要待重新生成").assertIsDisplayed()
    }

    @Test fun 点头像去资料页而不是进会话() {
        show(listOf(row(uuid = "a", name = "小满")))
        // 头像块对读屏是 clearAndSetSemantics{}（隐身·动作走卡级 customActions），只能按坐标点：
        // 卷三两列卡——光环 64 水平居中、顶内距 16 ⇒ 圆心 = (卡宽 / 2, 16 + 32)。
        compose.onNodeWithContentDescription("小满").performTouchInput {
            click(Offset(width / 2f, (16 + 32).dp.toPx()))
        }
        compose.waitForIdle()
        assertEquals("a", profiled)
        assertEquals("点头像绝不顺带进会话", null, opened)
    }

    // ── 卷三 §4.9：两列卡（E18 / E19）─────────────────────────────────────────

    /** 2x 密度：两卡各 (411 − 40 − 12) / 2 = 179.5dp，1x 下取整成 179 / 180 像素，等宽断言失真。 */
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
    @Test fun 三个联系人排两行且末行只有左卡() {
        show(listOf(row(uuid = "a", name = "小满"), row(uuid = "b", name = "阿泠"), row(uuid = "c", name = "林夏")))
        val a = compose.onNodeWithContentDescription("小满").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithContentDescription("阿泠").getUnclippedBoundsInRoot()
        val c = compose.onNodeWithContentDescription("林夏").getUnclippedBoundsInRoot()
        assertEquals("首行两卡同顶", a.top.value, b.top.value, 0.5f)
        assertEquals("两卡等宽", (a.right - a.left).value, (b.right - b.left).value, 0.5f)
        assertEquals("两卡间距 12", 12f, (b.left - a.right).value, 0.5f)
        assertTrue("第三张在第二行", c.top > a.bottom)
        assertEquals("末行左卡与首行左卡左缘对齐", a.left.value, c.left.value, 0.5f)
        assertEquals("末行单卡仍是半宽（右侧空位）", (a.right - a.left).value, (c.right - c.left).value, 0.5f)
        assertEquals("行间距 12", 12f, (c.top - a.bottom).value, 0.5f)
    }

    @Test fun 点行进会话() {
        show(listOf(row(uuid = "a", name = "小满")))
        compose.onNodeWithContentDescription("小满").performClick()
        compose.waitForIdle()
        assertEquals("a", opened)
        assertEquals("点行不该同时开资料页", null, profiled)
    }

    @Test fun 分享态显分享条且可取消() {
        show(listOf(row()), shareMode = true)
        compose.onNodeWithText("选择要把分享内容发给的角色").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals(1, cancelledShare)
    }

    @Test fun 空搜索结果给清除钮而全空态给新建钮() {
        show(emptyList(), query = "查无此人")
        compose.onNodeWithText("未找到匹配的角色").assertIsDisplayed()
        compose.onNodeWithText("清除搜索").performClick()
        compose.waitForIdle()
        assertEquals(listOf(""), queries)
    }

    @Test fun 全空态三件齐全且CTA回调恰一次() {
        show(emptyList())
        compose.onNodeWithText("还没有联系人").assertIsDisplayed()
        // 文案 2026-09-06 用户拍板改「右上角」（两张脸的「+」都在右上·旧字是 FAB 时代遗留）。
        compose.onNodeWithText("点右上角 + 创建你的第一个 AI 角色").assertIsDisplayed()
        compose.onNodeWithText("新建角色").performClick()
        compose.waitForIdle()
        assertEquals(1, created)
    }
}
