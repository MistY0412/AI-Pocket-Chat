package com.situ.aichat.notification

import androidx.test.core.app.ApplicationProvider
import com.situ.aichat.data.local.dao.NotificationDeliveryDao
import com.situ.aichat.data.local.entity.NotificationDeliveryRecordEntity
import com.situ.aichat.testutil.PrefsThreadRecordingContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 启动主线程读盘清零 ③（图纸 docs/handoff/2026-09-28-启动主线程读盘清零.md）：「待物化标记」排干挪到 IO 线程，
 * 物化结果一点不变。真路径 = 真 [StreakNotificationBridgeService] + 真 [PendingDeliveryStore]（Robolectric SharedPreferences）；
 * 调用方线程 = 测试线程（生产里是主线程协程：回前台 viewModelScope / 点击通知 lifecycleScope）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreakNotificationBridgeDrainThreadTest {

    private val context = PrefsThreadRecordingContext(ApplicationProvider.getApplicationContext())
    private val now = 1_800_000_000_000L
    private val dao: NotificationDeliveryDao = mockk(relaxed = true)

    private val marker = PendingDeliveryStore.PendingDelivery(
        deliveryIdentifier = "d-1", characterId = "A", category = "random", conversationUuid = "conv-A",
        notificationBody = "早安呀", requestIdentifier = "req-1", scheduledAt = now - 60_000L, deliveredAt = now,
    )

    /** 调度时就建好的台账（6.1e）：尚未投递、正文是兜底文案。 */
    private val scheduledRecord = NotificationDeliveryRecordEntity(
        id = "rec-1", characterId = "A", category = "random", deliveryIdentifier = "d-1",
        requestIdentifier = "req-1", conversationUuid = "conv-A", notificationBody = "兜底文案",
        windowId = "w", windowStartMinute = 0, windowEndMinute = 10, scheduledAt = now - 60_000L,
    )

    @Before
    fun setUp() {
        PendingDeliveryStore.drainAll(context)
        coEvery { dao.getByDeliveryIdentifier("d-1") } returns scheduledRecord
        coEvery { dao.pendingForMaterialization() } returns emptyList()
    }

    private fun bridge() = StreakNotificationBridgeService(
        context = context, messageRepository = mockk(relaxed = true), conversationRepository = mockk(relaxed = true),
        characterRepository = mockk(relaxed = true), deliveryDao = dao,
        activeConversationStore = mockk(relaxed = true), navigator = mockk(relaxed = true),
    )

    /**
     * 跑 [block]（在调用方线程上），断它期间读过标记存储的线程里没有调用方线程（= 没在主线程读盘），
     * 且确实读过（正向证据，防「根本没走到排干」的假绿）。只看 [block] 期间的新记录：准备数据时在测试线程上的读写不算。
     */
    private fun assertDrainsOffCaller(block: suspend () -> Unit) = runBlocking {
        val caller = Thread.currentThread()
        val before = context.threadsFor(PREFS_NAME).size
        block()
        val touched = context.threadsFor(PREFS_NAME).drop(before)
        assertTrue("排干时应当读过标记存储", touched.isNotEmpty())
        assertTrue("标记存储不许在调用方线程上读：$touched", touched.none { it === caller })
    }

    @Test
    fun 回前台物化_标记在IO线程排干_台账照常回填() {
        PendingDeliveryStore.appendDelivered(context, marker)

        assertDrainsOffCaller { bridge().materializeDeliveredNotifications() }

        // 行为不变：按 deliveryIdentifier 找到调度台账 → 回填投递时间 + 回灌到点现写的正文（notification-1）。
        coVerify(exactly = 1) { dao.update(scheduledRecord.copy(deliveredAt = now, notificationBody = "早安呀")) }
        assertEquals("标记已排干", emptyList<PendingDeliveryStore.PendingDelivery>(), PendingDeliveryStore.drainAll(context))
    }

    @Test
    fun 点击通知_同样在IO线程排干() {
        PendingDeliveryStore.appendDelivered(context, marker)

        assertDrainsOffCaller {
            bridge().materializeFromClick(
                NotificationClickPayload(
                    deliveryIdentifier = null, conversationUuid = null, characterId = null,
                    notificationBody = "早安呀", category = "random", requestKey = null, scheduledAt = now,
                ),
            )
        }

        coVerify(exactly = 1) { dao.update(scheduledRecord.copy(deliveredAt = now, notificationBody = "早安呀")) }
    }

    private companion object {
        /** 与 [PendingDeliveryStore] 的文件名同值（它是 private，这里照抄；改名会让本测「确实读过」那条先红）。 */
        const val PREFS_NAME = "notif_pending_delivery"
    }
}
