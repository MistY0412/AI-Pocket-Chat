package com.situ.aichat.ui.moments

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.data.local.dao.UserProfileDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.moments.MomentComposeDraft
import com.situ.aichat.moments.MomentComposeDraftStore
import com.situ.aichat.moments.MomentInteractionService
import com.situ.aichat.moments.MomentMentionAvailability
import com.situ.aichat.stt.VoiceMessageRecorder
import com.situ.aichat.util.StringListJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * T2-5（朋友圈发布页重构·甲 §7）：发布页大脑——草稿自动存 / 恢复 / 保留 / 不保留 / 清空、图片归属、换位、提醒清洗、
 * 可用性刷新、离开即保留（V-1）。真 [MomentComposeDraftStore]，图文件建在 `filesDir` 下，主线程循环驱动防抖。
 * **断言一律用图纸字面量（500 / 300 / 600 毫秒、9 张），不读实现常量。**
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeMomentViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val momentRepo = mockk<MomentRepository>(relaxed = true)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val interactionService = mockk<MomentInteractionService>(relaxed = true)
    private val userProfileDao = mockk<UserProfileDao>()
    private val characterRepo = mockk<CharacterRepository>()
    private val availability = mockk<MomentMentionAvailability>()
    private val vms = mutableListOf<ComposeMomentViewModel>()

    private fun ch(uuid: String) = CharacterEntity(uuid = uuid, name = "名$uuid", creationDate = 0L)

    @Before
    fun setUp() {
        every { userProfileDao.observe() } returns flowOf(null)
        characters(ch("c1"), ch("c2"))
        coEvery { settingsRepo.getAppSettings() } returns AppSettings(momentCommentDelay = 3)
        MomentComposeDraftStore.clear(context)
    }

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        idle()
        MomentComposeDraftStore.clear(context)
    }

    private fun characters(vararg list: CharacterEntity) {
        every { characterRepo.observeAll() } returns flowOf(list.toList())
        coEvery { characterRepo.getAll() } returns list.toList()
    }

    private fun newVm() = ComposeMomentViewModel(
        context = context, momentRepo = momentRepo, settingsRepo = settingsRepo,
        interactionService = interactionService, userProfileDao = userProfileDao, characterRepo = characterRepo,
        mentionAvailabilityChecker = availability, voiceRecorder = VoiceMessageRecorder(), sttEngine = mockk(relaxed = true),
    )

    private fun vm() = newVm().also { vms += it }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun img(name: String) = File(context.filesDir, name).apply { writeText("jpg") }.absolutePath

    private fun rawDraftKeyExists() =
        context.getSharedPreferences("moment_compose_draft", Context.MODE_PRIVATE).contains("draft")

    /** a（E14 / E26）：发布——提醒已清洗、图按换位后顺序、存储清、没发出去的图删盘、排互动 1 次。 */
    @Test
    fun a_发布_清洗提醒_图按新顺序_清草稿_删未发的图() {
        val f1 = img("a1.jpg"); val f2 = img("a2.jpg"); val f3 = img("a3.jpg")
        MomentComposeDraftStore.save(context, MomentComposeDraft("今天", listOf(f1, f2, f3), listOf("c2", "gone", "c1")))
        val vm = vm()
        vm.moveImage(0, 2) // → f2, f3, f1
        vm.removeImage(f3) // → f2, f1
        val saved = slot<MomentPostEntity>()
        coEvery { momentRepo.upsert(capture(saved)) } returns Unit
        var done = 0
        vm.publish { done++ }
        idle()

        assertEquals(1, done)
        assertEquals(StringListJson.encode(listOf("c2", "c1")), saved.captured.mentionedCharacterUuidsJson)
        assertEquals(StringListJson.encode(listOf(f2, f1)), saved.captured.imagePathsJson)
        assertEquals("今天", saved.captured.content)
        assertNull(MomentComposeDraftStore.load(context))
        assertFalse(rawDraftKeyExists())
        assertFalse("移除过的图删盘", File(f3).exists())
        assertTrue(File(f1).exists() && File(f2).exists())
        verify(exactly = 1) { interactionService.scheduleAIInteraction(saved.captured.uuid, 3) }
    }

    /** a′（E32）：首装冷启——无草稿无角色，初值为空、发布照常（提醒列为空串）。 */
    @Test
    fun a2_无草稿无角色_初值为空_发布照常() {
        characters()
        val vm = vm()
        assertEquals(ComposeMomentState(), vm.state.value)
        assertTrue(vm.characters.value.isEmpty())
        val saved = slot<MomentPostEntity>()
        coEvery { momentRepo.upsert(capture(saved)) } returns Unit
        vm.setContent("你好")
        var done = false
        vm.publish { done = true }
        idle()

        assertTrue(done)
        assertEquals("", saved.captured.mentionedCharacterUuidsJson)
        assertEquals("", saved.captured.imagePathsJson)
    }

    /** b（E20）：打字后 300ms 未存、600ms 已存（防抖 500ms）。 */
    @Test
    fun b_打字防抖500毫秒自动存() {
        val vm = vm()
        vm.setContent("草稿")
        idleFor(300)
        assertNull(MomentComposeDraftStore.load(context))
        idleFor(300)
        assertEquals("草稿", MomentComposeDraftStore.load(context)?.content)
    }

    /** c（E21）：进页有草稿、其中一张图已不在 → 恢复正文 / 提醒（去重）/ 存在的图，restoredFromDraft = true。 */
    @Test
    fun c_恢复草稿_剔除不在的图() {
        val f1 = img("c1.jpg")
        val gone = File(context.filesDir, "c-gone.jpg").absolutePath
        MomentComposeDraftStore.save(context, MomentComposeDraft("接着写", listOf(gone, f1), listOf("c1", "c1", "c2")))
        val s = vm().state.value

        assertEquals("接着写", s.content)
        assertEquals(listOf(f1), s.images)
        assertEquals(listOf("c1", "c2"), s.mentions)
        assertTrue(s.restoredFromDraft)
    }

    /** c′（E22）：草稿只剩已消失的图（正文空白、无提醒）→ 当作没有草稿，存储被清。 */
    @Test
    fun c2_只剩消失的图_当没草稿() {
        val gone = File(context.filesDir, "c2-gone.jpg").absolutePath
        MomentComposeDraftStore.save(context, MomentComposeDraft("  ", listOf(gone), emptyList()))
        assertTrue(rawDraftKeyExists())
        val vm = vm()

        assertEquals(ComposeMomentState(), vm.state.value)
        assertFalse(rawDraftKeyExists())
    }

    /** d（E23）：保留——移除过的图删盘、留下的保留、存储有草稿（且不等防抖）。 */
    @Test
    fun d_保留草稿_删移除过的图() {
        val f1 = img("d1.jpg"); val f2 = img("d2.jpg")
        MomentComposeDraftStore.save(context, MomentComposeDraft("写了一半", listOf(f1, f2)))
        val vm = vm()
        vm.removeImage(f2)
        vm.keepDraft()

        assertFalse(File(f2).exists())
        assertTrue(File(f1).exists())
        assertEquals(MomentComposeDraft("写了一半", listOf(f1)), MomentComposeDraftStore.load(context))
    }

    /** e（E24）：不保留——草稿里的图（含恢复来的）全删、存储清；之后再写再 keepDraft 也不写。 */
    @Test
    fun e_不保留_全删且之后不再存() {
        val f1 = img("e1.jpg")
        MomentComposeDraftStore.save(context, MomentComposeDraft("算了", listOf(f1)))
        val vm = vm()
        vm.discard()
        assertFalse(File(f1).exists())
        assertNull(MomentComposeDraftStore.load(context))

        vm.setContent("又写了几个字")
        idleFor(600)
        vm.keepDraft()
        assertNull(MomentComposeDraftStore.load(context))
        assertFalse(rawDraftKeyExists())
    }

    /** f（E25）：清空——全删、存储清、页面继续可写，再打字又会自动存。 */
    @Test
    fun f_清空后继续写照常自动存() {
        val f1 = img("f1.jpg")
        MomentComposeDraftStore.save(context, MomentComposeDraft("旧的", listOf(f1), listOf("c1")))
        val vm = vm()
        vm.clearAll()
        assertFalse(File(f1).exists())
        assertNull(MomentComposeDraftStore.load(context))
        assertEquals(ComposeMomentState(), vm.state.value)

        vm.setContent("新的")
        idleFor(600)
        assertEquals(MomentComposeDraft("新的"), MomentComposeDraftStore.load(context))
    }

    /** g（E27）：换位——正常移位；越界 / 同位不动。 */
    @Test
    fun g_换位与越界同位不动() {
        val f1 = img("g1.jpg"); val f2 = img("g2.jpg"); val f3 = img("g3.jpg")
        MomentComposeDraftStore.save(context, MomentComposeDraft("", listOf(f1, f2, f3)))
        val vm = vm()
        vm.moveImage(0, 2)
        assertEquals(listOf(f2, f3, f1), vm.state.value.images)
        vm.moveImage(-1, 0)
        vm.moveImage(0, 3)
        vm.moveImage(1, 1)
        assertEquals(listOf(f2, f3, f1), vm.state.value.images)
    }

    /** h（E28）：只有提醒、没字没图 → 有未保存改动，但发布仍禁（正文空）；提醒可点选 / 取消 / 移除。 */
    @Test
    fun h_只有提醒_有改动但不能发() {
        val vm = vm()
        vm.toggleMention("c1")
        vm.toggleMention("c2")
        vm.toggleMention("c1") // 再点 = 取消
        assertEquals(listOf("c2"), vm.state.value.mentions)
        vm.toggleMention("c1") // 再选 = 追加到末尾
        assertEquals(listOf("c2", "c1"), vm.state.value.mentions)
        vm.removeMention("zz") // 不在则不动
        vm.removeMention("c2")
        assertEquals(listOf("c1"), vm.state.value.mentions)
        assertTrue(vm.hasUnsavedChanges)

        var done = false
        vm.publish { done = true }
        idle()
        assertFalse(done)
        coVerify(exactly = 0) { momentRepo.upsert(any()) }
    }

    /** i（E29）：刷新可用性 = 逐角色走唯一判定口，按日程开关与当前时刻问。 */
    @Test
    fun i_刷新可用性走唯一判定口() {
        coEvery { settingsRepo.getAppSettings() } returns AppSettings(scheduleSystemEnabled = true)
        coEvery { availability.of("c1", true, any(), any()) } returns MentionAvailability.SLEEPING
        coEvery { availability.of("c2", true, any(), any()) } returns MentionAvailability.IN_MEETING
        val vm = vm()
        assertTrue(vm.mentionAvailability.value.isEmpty())
        vm.refreshMentionAvailability()
        idle()

        assertEquals(
            mapOf("c1" to MentionAvailability.SLEEPING, "c2" to MentionAvailability.IN_MEETING),
            vm.mentionAvailability.value,
        )
    }

    /** V-1：离开页面（VM 被清理）= 立即保留草稿，不等防抖。 */
    @Test
    fun v1_离开页面即保留草稿() {
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = newVm() as T
            },
        )[ComposeMomentViewModel::class.java]
        vm.setContent("没发完")
        vm.toggleMention("c1")
        store.clear()

        assertEquals(MomentComposeDraft("没发完", emptyList(), listOf("c1")), MomentComposeDraftStore.load(context))
    }

    /** 发布成功后离开：不回写草稿（finished 拦住 onCleared 的保留）。 */
    @Test
    fun 发布成功后离开_不回写草稿() {
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = newVm() as T
            },
        )[ComposeMomentViewModel::class.java]
        vm.setContent("发出去了")
        vm.publish {}
        idle()
        store.clear()
        idleFor(600)

        assertNull(MomentComposeDraftStore.load(context))
    }
}
