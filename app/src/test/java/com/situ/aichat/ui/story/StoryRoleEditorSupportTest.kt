package com.situ.aichat.ui.story

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.data.local.entity.StoryCharacterRoleEntity
import com.situ.aichat.story.StoryRoleType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 角色编辑配置四构造器 + 弹层态四动作（琉璃 2.0 卷六·三·上 §3.11·T1-7）：权限矩阵 / 保存变换 / 移出 / 起草 / 捕获过期回归钉。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class StoryRoleEditorSupportTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<StoryCharacterRoleEntity>()
    private val deleted = mutableListOf<String>()

    private fun editConfig(role: StoryCharacterRoleEntity): StoryRoleEditorConfig {
        var out: StoryRoleEditorConfig? = null
        compose.setContent { out = storyRoleEditConfig(role, onDraftPersona = { _, _, _ -> "起草" }, onSave = { saved += it }, onDelete = { deleted += it }) }
        compose.waitForIdle()
        return out!!
    }

    @Test fun 我那一行_名定位锁_无反差无移出_保存不清反差() {
        val me = StoryCharacterRoleEntity(id = "r0", storyId = "s", roleName = "我", roleType = StoryRoleType.PROTAGONIST, isUserRole = true, intimatePersona = "原反差")
        val cfg = editConfig(me)
        assertFalse(cfg.nameEditable)
        assertEquals("这是你在故事里的角色，名字和定位在开书时定下；这里只改人设描述", cfg.nameLockedHint)
        assertFalse(cfg.typeEditable)
        assertFalse(cfg.showPersona)
        assertNull(cfg.onRemove)
        cfg.onSave("阿满", StoryRoleType.PROTAGONIST, " ", "新反差")
        assertNull(saved.single().roleDescription)
        assertEquals("原反差", saved.single().intimatePersona)
    }

    @Test fun 关联聊天角色_名锁定位开_有反差_移出调删除() {
        val lin = StoryCharacterRoleEntity(id = "r1", storyId = "s", roleName = "林夏", characterId = "c1")
        val cfg = editConfig(lin)
        assertFalse(cfg.nameEditable)
        assertEquals("名字跟着聊天角色本体，在这里改不了", cfg.nameLockedHint)
        assertTrue(cfg.typeEditable)
        assertTrue(cfg.showPersona)
        assertTrue(cfg.removeNeedsConfirm)
        cfg.onRemove!!()
        assertEquals(listOf("r1"), deleted)
    }

    @Test fun 本书专属_全开_反差去空白_全空白落null() {
        val own = StoryCharacterRoleEntity(id = "r2", storyId = "s", roleName = "阿澈")
        val cfg = editConfig(own)
        assertTrue(cfg.nameEditable)
        assertNull(cfg.nameLockedHint)
        cfg.onSave("阿澈", StoryRoleType.SUPPORTING, "邻居", " x ")
        assertEquals("x", saved.last().intimatePersona)
        cfg.onSave("阿澈", StoryRoleType.SUPPORTING, "邻居", "  ")
        assertNull(saved.last().intimatePersona)
    }

    @Test fun 书页新增_新建态配角_产出实体挂本书且非我非聊天角色() {
        val cfg = storyRoleAddConfig("s9", onDraftPersona = null) { saved += it }
        assertTrue(cfg.isNew)
        assertEquals(StoryRoleType.SUPPORTING, cfg.initialType)
        assertNull(cfg.onDraftPersona)
        cfg.onSave("小夏", StoryRoleType.ANTAGONIST, "", "")
        val e = saved.single()
        assertEquals("s9", e.storyId)
        assertFalse(e.isUserRole)
        assertNull(e.characterId)
        assertNull(e.roleDescription)
    }

    @Test fun 创建屏两构造器_无反差无起草_删除不确认_回调带下标() {
        val updates = mutableListOf<Pair<Int, CustomRoleDraft>>()
        val removes = mutableListOf<Int>()
        val adds = mutableListOf<CustomRoleDraft>()
        val edit = storyCustomRoleEditConfig(CustomRoleDraft("阿澈", StoryRoleType.SUPPORTING, "邻居"), 2, { i, d -> updates += i to d }, { removes += it })
        val add = storyCustomRoleAddConfig { adds += it }
        listOf(edit, add).forEach { cfg ->
            assertFalse(cfg.showPersona)
            assertNull(cfg.onDraftPersona)
            assertFalse(cfg.removeNeedsConfirm)
        }
        edit.onRemove!!()
        assertEquals(listOf(2), removes)
        edit.onSave("阿澈", StoryRoleType.PROTAGONIST, "改了", "忽略")
        assertEquals(listOf(2 to CustomRoleDraft("阿澈", StoryRoleType.PROTAGONIST, "改了")), updates)
        add.onSave("新人", StoryRoleType.SUPPORTING, "", "")
        assertEquals(listOf(CustomRoleDraft("新人", StoryRoleType.SUPPORTING, "")), adds)
    }

    private fun config(
        onSave: (String, String, String, String) -> Unit = { _, _, _, _ -> },
        onRemove: (() -> Unit)? = null,
        needsConfirm: Boolean = false,
        draft: (suspend (String, String) -> String?)? = null,
    ) = StoryRoleEditorConfig(
        initialName = "阿澈", initialType = StoryRoleType.SUPPORTING, initialDescription = "邻居", initialPersona = "",
        showPersona = true, onDraftPersona = draft, isNew = false, nameEditable = true, nameLockedHint = null, typeEditable = true,
        onRemove = onRemove, removeNeedsConfirm = needsConfirm, onSave = onSave,
    )

    @Test fun 态_空白名不可存_保存去空白并关弹层() {
        val calls = mutableListOf<String>()
        var dismissed = 0
        val st = StoryRoleEditorState("  ", StoryRoleType.SUPPORTING, "", "")
        assertFalse(st.canSave)
        st.name = " 阿澈 "
        assertTrue(st.canSave)
        st.save(config(onSave = { n, _, _, _ -> calls += n })) { dismissed++ }
        assertEquals(listOf("阿澈"), calls)
        assertEquals(1, dismissed)
    }

    @Test fun 态_移出_需确认只置标志_不需确认直接移出并关() {
        var removed = 0
        var dismissed = 0
        val st = StoryRoleEditorState("阿澈", StoryRoleType.SUPPORTING, "", "")
        st.requestRemove(config(onRemove = { removed++ }, needsConfirm = true)) { dismissed++ }
        assertTrue(st.confirmRemove)
        assertEquals(0 to 0, removed to dismissed)
        st.confirmRemoveAndClose(config(onRemove = { removed++ }, needsConfirm = true)) { dismissed++ }
        assertFalse(st.confirmRemove)
        assertEquals(1 to 1, removed to dismissed)
        st.requestRemove(config(onRemove = { removed++ }, needsConfirm = false)) { dismissed++ }
        assertFalse(st.confirmRemove)
        assertEquals(2 to 2, removed to dismissed)
    }

    @Test fun 态_起草成功写回_失败回调_无起草器无事发生() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val st = StoryRoleEditorState(" 阿澈 ", StoryRoleType.SUPPORTING, "邻居", "")
        val gate = CompletableDeferred<String?>()
        var askedWith: Pair<String, String>? = null
        st.startDraft(config(draft = { n, d -> askedWith = n to d; gate.await() }), scope) { error("不该失败") }
        scope.testScheduler.advanceUntilIdle()
        assertTrue(st.drafting)
        assertEquals("阿澈" to "邻居", askedWith)
        gate.complete("她人前冷，私下黏人")
        scope.testScheduler.advanceUntilIdle()
        assertEquals("她人前冷，私下黏人", st.persona)
        assertFalse(st.drafting)

        var failed = 0
        st.startDraft(config(draft = { _, _ -> null }), scope) { failed++ }
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, failed)
        assertFalse(st.drafting)

        st.startDraft(config(draft = null), scope) { failed++ }
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, failed)
        assertFalse(st.drafting)
    }

    @Test fun 态_动作吃调用时传入的config_不停在首份() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val st = StoryRoleEditorState("阿澈", StoryRoleType.SUPPORTING, "", "")
        val c1 = config(onSave = { n, _, _, _ -> first += n })
        val c2 = config(onSave = { n, _, _, _ -> second += n })
        st.save(c2) {}
        assertEquals(emptyList<String>(), first)
        assertEquals(listOf("阿澈"), second)
        st.save(c1) {}
        assertEquals(listOf("阿澈"), first)
    }
}
