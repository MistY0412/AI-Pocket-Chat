package com.situ.aichat.data.local

import androidx.room.Room
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.ConversationEntity
import com.situ.aichat.data.local.entity.LogEntryEntity
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.diagnostics.LogMessageBrief
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T2-5（时间感知四期·图纸四 §3.10 / §3.4·Robolectric + 真 in-memory Room·照 DayMomentsDaoTest）：
 * 消息轻投影两条查询（按 uuid / 按会话时间闭区间升序）+ `previousComparable` 带出上一条的时刻。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogDaoBriefsTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.characterDao().upsert(CharacterEntity(uuid = "char-1", name = "林晚", creationDate = 0L))
        db.conversationDao().upsert(ConversationEntity(uuid = "conv-1", title = "日常", characterUuid = "char-1", creationDate = 0L))
        db.conversationDao().upsert(ConversationEntity(uuid = "conv-2", title = "旅行", characterUuid = "char-1", creationDate = 0L))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun message(uuid: String, ts: Long, conversation: String = "conv-1", role: String = "user", content: String = "话$uuid") =
        db.messageDao().upsert(MessageEntity(messageUUID = uuid, conversationUuid = conversation, roleRaw = role, content = content, timestamp = ts))

    @Test
    fun logBriefsByUuids_projectsColumns_andSkipsMissing() = runBlocking {
        db.messageDao().upsert(
            MessageEntity(
                messageUUID = "m-img", conversationUuid = "conv-1", roleRaw = "user", content = "[图片]", timestamp = 100,
                imageRelativePath = "content_images/a.jpg", messageKindRaw = "plain_text",
            ),
        )
        db.messageDao().upsert(
            MessageEntity(messageUUID = "m-voice", conversationUuid = "conv-1", roleRaw = "user", content = "语音转写", timestamp = 200, isVoiceMessage = true),
        )
        val got = db.messageDao().logBriefsByUuids(listOf("m-img", "m-voice", "gone")).sortedBy { it.timestamp }
        assertEquals(
            listOf(
                LogMessageBrief("m-img", "conv-1", "user", "[图片]", 100, "content_images/a.jpg", false, "plain_text"),
                LogMessageBrief("m-voice", "conv-1", "user", "语音转写", 200, null, true, "plain_text"),
            ),
            got,
        )
    }

    @Test
    fun logBriefsInRange_closedInterval_ascending_sameConversationOnly() = runBlocking {
        message("c", 300)
        message("a", 100)
        message("b", 200, role = "assistant")
        message("d", 301)
        message("early", 99)
        message("other", 200, conversation = "conv-2")
        val got = db.messageDao().logBriefsInRange("conv-1", 100, 300)
        assertEquals("闭区间：100 与 300 都在，99 / 301 不在；别的会话不在；时间升序", listOf("a", "b", "c"), got.map { it.messageUUID })
        assertEquals(listOf("user", "assistant", "user"), got.map { it.roleRaw })
    }

    @Test
    fun previousComparable_carriesTimestampMillis() = runBlocking {
        val dao = db.logDao()
        val a = dao.insert(LogEntryEntity(timestampMillis = 1_000, source = "chat", conversationUuid = "conv-1", shapeJson = "A"))
        val b = dao.insert(LogEntryEntity(timestampMillis = 2_000, source = "chat", conversationUuid = "conv-1", shapeJson = "B"))
        val prev = dao.previousComparable("conv-1", "chat", 2_000, b)!!
        assertEquals(a, prev.id)
        assertEquals(1_000L, prev.timestampMillis)
        assertEquals("A", prev.shapeJson)
    }
}
