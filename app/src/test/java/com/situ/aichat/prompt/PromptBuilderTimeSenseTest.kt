package com.situ.aichat.prompt

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.ConversationEntity
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.data.model.AppSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * T2-2（时间感知四期·图纸一 §3.5 ⑤·E20 / E23 / E24 / E25）：真 [PromptBuilder.buildMessages] 里的现在卡。
 * now 钉 2026-09-26 21:40（沪·中秋假期第 2 天）；角色 18:40 说话、用户 21:39 回（间隔 2 小时 59 分 → 约 3 小时）。
 * 期望按 §3.5 规格手算；锁定文本重新打字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class PromptBuilderTimeSenseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private val now: Instant = LocalDateTime.of(2026, 9, 26, 21, 40).atZone(zone).toInstant()
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 9, 26, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun history(offline: Boolean = false) = listOf(
        MessageEntity(messageUUID = "a1", conversationUuid = "conv1", roleRaw = "assistant", content = "晚点聊~", timestamp = at(18, 40),
            isOfflineMode = offline, offlineSessionId = if (offline) "os1" else null),
        MessageEntity(messageUUID = "u1", conversationUuid = "conv1", roleRaw = "user", content = "我回来了", timestamp = at(21, 39),
            isOfflineMode = offline, offlineSessionId = if (offline) "os1" else null),
    )

    private fun systemText(
        scene: PromptScene = PromptScene.ONLINE_CHAT,
        delayed: Boolean = false,
        conversation: ConversationEntity? = null,
        offline: Boolean = false,
    ): String = PromptBuilder.buildMessages(
        character = CharacterEntity(uuid = "c1", name = "小雨", creationDate = 0L),
        conversation = conversation,
        sortedMessages = history(offline),
        userProfile = null,
        appSettings = AppSettings(),
        strings = PromptStrings(RuntimeEnvironment.getApplication()),
        now = now,
        scene = scene,
        delayedGeneration = delayed,
    ).filter { it.role == "system" }.joinToString("\n\n") { it.content.orEmpty() }

    @Test
    fun `在线聊天_现在卡含日子行_新间隔行_两句附言_不含旧行`() {
        val text = systemText()
        val expectedCard = "<time_context>\n现在：2026-09-26 周六 21:40（晚上）\n今天：中秋节假期第 2 天（9月25日–9月27日）\n" +
            "这条消息距离上条过去了约 3 小时\n</time_context>\n" +
            "↑ 以上是此刻的真实时间，以它为准。几分钟到半小时的停顿是正常聊天节奏，一般不用特意提。节日、生日这类日子自然提一次就够，别每条都提。" +
            "这几个小时你在过自己的生活，现在才重新拿起手机。前面聊的还算近，接得上就自然接。" +
            "前面聊到的长期、持续的事，本来就会延续——具体哪些还算数、此刻你是什么状态，你按现在的时间自己判断。"
        assertTrue("现在卡整张按规格：\n$text", text.contains(expectedCard))
        assertFalse(text.contains("隔了约"))
        assertFalse(text.contains("这次从"))
        assertFalse(text.contains("聊到现在"))
    }

    @Test
    fun `E23_语音通话_无新行_旧间隔行去才`() {
        val text = systemText(scene = PromptScene.VOICE_CALL)
        assertTrue(text, text.contains("对方隔了约 3 小时回你"))
        assertFalse(text.contains("才回你"))
        assertFalse(text.contains("这条消息距离上条"))
        assertFalse("通话不接日子行", text.contains("今天：中秋节假期"))
    }

    @Test
    fun `E24_延迟补生成_无新行_中性间隔行逐字不变`() {
        val text = systemText(delayed = true)
        assertTrue(text, text.contains("\n距离你上条回复：约 3 小时\n</time_context>"))
        assertFalse(text.contains("这条消息距离上条"))
        assertFalse(text.contains("今天：中秋节假期"))
        assertFalse(text.contains("几分钟到半小时的停顿"))
    }

    @Test
    fun `E25_线下见面_现在卡只有时刻事实`() {
        val conv = ConversationEntity(
            uuid = "conv1", title = "t", characterUuid = "c1", creationDate = 0L,
            isInOfflineMode = true, currentOfflineSessionId = "os1",
        )
        val text = systemText(scene = PromptScene.OFFLINE_MEETING, conversation = conv, offline = true)
        assertTrue(text, text.contains("<time_context>\n现在：2026-09-26 周六 21:40（晚上）\n</time_context>\n↑ 以上是此刻的真实时间，以它为准。"))
        assertFalse(text.contains("这条消息距离上条"))
        assertFalse(text.contains("今天：中秋节假期"))
        assertFalse(text.contains("几分钟到半小时的停顿"))
    }
}
