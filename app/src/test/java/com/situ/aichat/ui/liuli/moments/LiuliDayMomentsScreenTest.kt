package com.situ.aichat.ui.liuli.moments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.situ.aichat.data.local.AppDatabase
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentLikeEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.ourdays.OurDayKey
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.moments.DayMomentsViewModel
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * T2-Y1–Y3（琉璃 2.0 卷六·二 §7·范式 `DayMomentsScreenTest`）：真组合跑琉璃「那天的动态」——标题、两组标签按空不发射、
 * 已加载且两组皆空出空态、非法日键不崩走空态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliDayMomentsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val momentRepo = MomentRepository(db.momentDao())
    private val characterRepo = mockk<CharacterRepository>()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayKey = OurDayKey.keyOf(LocalDate.of(2026, 9, 1))
    private val start = OurDayKey.dayBounds(dayKey, zone).first
    private val vms = mutableListOf<DayMomentsViewModel>()

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        db.close()
    }

    private fun seedPost(uuid: String, timestamp: Long) = runBlocking {
        db.momentDao().insertPost(
            MomentPostEntity(uuid = uuid, content = "内容 $uuid", timestamp = timestamp, authorTypeRaw = "character", characterUuid = "c1"),
        )
    }

    private fun seedUserLike(postUuid: String, timestamp: Long) = runBlocking {
        db.momentDao().insertLike(MomentLikeEntity(timestamp = timestamp, authorTypeRaw = "user", characterUuid = null, postUuid = postUuid))
    }

    private fun show(key: String = dayKey) {
        every { characterRepo.observeAll() } returns flowOf(listOf(CharacterEntity(uuid = "c1", name = "林晚", creationDate = 1)))
        val vm = DayMomentsViewModel(
            SavedStateHandle(mapOf(DayMomentsViewModel.ARG_CHARACTER_UUID to "c1", DayMomentsViewModel.ARG_DAY_KEY to key)),
            momentRepo, characterRepo, db.userProfileDao(),
        )
        vms += vm
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliDayMomentsScreen(onBack = {}, onOpenPost = {}, viewModel = vm)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun waitText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun Y1_两组都有两条标签都在且标题按日期拼() {
        seedPost("p-that-day", start + 3_600_000L)
        seedPost("p-earlier", start - 86_400_000L)
        seedUserLike("p-earlier", start + 7_200_000L)
        show()
        waitText("这一天发的")
        compose.onNodeWithText("9 月 1 日 · 朋友圈").assertExists()
        compose.onNodeWithText("更早发的 · 这一天有来往").assertExists()
    }

    @Test fun Y2_只有当天发的第二个标签不发射() {
        seedPost("p-that-day", start + 3_600_000L)
        show()
        waitText("这一天发的")
        compose.onNodeWithText("更早发的 · 这一天有来往").assertDoesNotExist()
    }

    @Test fun Y3_两组皆空出空态() {
        show()
        waitText("这一天的动态不在了")
        compose.onNodeWithText("这一天发的").assertDoesNotExist()
    }

    @Test fun Y3_非法日键走空态不崩() {
        show(key = "bad")
        waitText("这一天的动态不在了")
        compose.onNodeWithText("动态").assertExists()
    }
}
