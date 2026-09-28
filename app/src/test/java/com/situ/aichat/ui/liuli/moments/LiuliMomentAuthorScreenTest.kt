package com.situ.aichat.ui.liuli.moments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.situ.aichat.data.local.AppDatabase
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.moments.MomentAuthorViewModel
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T2-A1–A3（琉璃 2.0 卷六·二 §7·范式 `DayMomentsScreenTest`）：内存库手搭 [MomentAuthorViewModel]，真组合跑琉璃
 * 「我的动态 / 某人的动态」——标题、头部计数、正文上屏、点正文进详情、已加载无帖出空态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliMomentAuthorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val momentRepo = MomentRepository(db.momentDao())
    private val characterRepo = mockk<CharacterRepository>()
    private val vms = mutableListOf<MomentAuthorViewModel>()
    private val opened = mutableListOf<String>()

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        db.close()
    }

    private fun seed(uuid: String, content: String, authorRaw: String, characterUuid: String?, timestamp: Long) = runBlocking {
        db.momentDao().insertPost(
            MomentPostEntity(uuid = uuid, content = content, timestamp = timestamp, authorTypeRaw = authorRaw, characterUuid = characterUuid),
        )
    }

    private fun show(characterUuid: String?) {
        every { characterRepo.observeAll() } returns flowOf(listOf(CharacterEntity(uuid = "c1", name = "小满", creationDate = 1)))
        val args = if (characterUuid != null) mapOf(MomentAuthorViewModel.ARG_CHARACTER_UUID to characterUuid) else emptyMap()
        val vm = MomentAuthorViewModel(SavedStateHandle(args), momentRepo, characterRepo, db.userProfileDao())
        vms += vm
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliMomentAuthorScreen(onBack = {}, onOpenPost = { opened += it }, viewModel = vm)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun waitText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun A1_我的动态两条() {
        val now = System.currentTimeMillis()
        seed("u1", "下班啦风好大", "user", null, now - 3_600_000)
        seed("u2", "终于交稿了", "user", null, now - 7_200_000)
        show(characterUuid = null)
        waitText("下班啦风好大")
        compose.onNodeWithText("我的动态").assertExists()
        waitText("2 条动态")
        compose.onNodeWithText("终于交稿了", useUnmergedTree = true).assertExists()
    }

    @Test fun A2_角色页名字在大标题与头部且点正文进详情() {
        seed("x1", "今天的晚霞像橘子汽水", "character", "c1", System.currentTimeMillis() - 3_600_000)
        show(characterUuid = "c1")
        waitText("今天的晚霞像橘子汽水")
        assertTrue(compose.onAllNodesWithText("小满", useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("今天的晚霞像橘子汽水", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("x1"), opened)
    }

    @Test fun A3_角色无帖出空态() {
        show(characterUuid = "c1")
        waitText("还没有动态")
        compose.onNodeWithText("还没有动态").assertExists()
    }
}
