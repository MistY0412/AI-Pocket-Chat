package com.situ.aichat.prompt

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.data.local.entity.OurDayEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.diagnostics.CacheBreakAnalyzer
import com.situ.aichat.diagnostics.CacheBreakKind
import com.situ.aichat.diagnostics.LogRequestShape
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * T2-7（时间感知四期·图纸三 §3.5 / E30）：真 [PromptBuilder.buildMessagesWithSegments] 产出的分段指纹。
 * - 两个自定义模块（前置 / 后置）内容在测试里写死 → 指纹 = `fingerprintOf(该字面量)`，与实现完全独立；
 * - 其余前置 / 后置段：指纹必须等于「实际发出的 system 消息里、段落边界起、长度 = charCount 的那一截」的指纹（真在发送内容里）；
 * - 省钱挪位段：指纹 = 挪位块那条消息全文的指纹；「对话历史」段不带指纹。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class PromptBuilderSegmentFingerprintTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private val now = LocalDateTime.of(2026, 9, 28, 21, 40).atZone(zone).toInstant()
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 9, 28, h, m).atZone(zone).toInstant().toEpochMilli()

    private val prefixCustom = "【前置自定义】这段内容用来钉指纹。"
    private val suffixCustom = "【后置自定义】后置区也要有指纹。"

    private fun settings(saver: Boolean): AppSettings {
        val modules = PromptModuleService.defaultModules() + listOf(
            PromptModule(id = "CUSTOM-PRE", name = "自定义前置", content = prefixCustom, sortOrder = 1, position = PromptModulePosition.PREFIX),
            PromptModule(id = "CUSTOM-SUF", name = "自定义后置", content = suffixCustom, sortOrder = 999, position = PromptModulePosition.SUFFIX),
        )
        return AppSettings(cacheSaverEnabled = saver, promptModulesJSON = PromptModuleService.encodeModules(modules))
    }

    private val turn1 = listOf(
        MessageEntity(messageUUID = "a1", conversationUuid = "conv1", roleRaw = "assistant", content = "在干嘛呀", timestamp = at(21, 30)),
        MessageEntity(messageUUID = "u1", conversationUuid = "conv1", roleRaw = "user", content = "刚到家", timestamp = at(21, 39)),
    )

    /** 下一轮：多了一问一答（时间都在 [now] 之前，两轮同一时刻构建——只比结构，不让「X 分钟前」这类随时间变的字混进来）。 */
    private val turn2 = turn1 + listOf(
        MessageEntity(messageUUID = "a2", conversationUuid = "conv1", roleRaw = "assistant", content = "累不累", timestamp = at(21, 39)),
        MessageEntity(messageUUID = "u2", conversationUuid = "conv1", roleRaw = "user", content = "还好，想你了", timestamp = at(21, 40)),
    )

    private fun build(saver: Boolean, history: List<MessageEntity> = turn1) = PromptBuilder.buildMessagesWithSegments(
        character = CharacterEntity(uuid = "c1", name = "夏晴子", creationDate = 0L),
        conversation = null,
        sortedMessages = history,
        userProfile = UserProfileEntity(nickname = "阿远"),
        appSettings = settings(saver),
        strings = PromptStrings(RuntimeEnvironment.getApplication()),
        retrievedMemorySnippets = listOf("[2026-06-14 22:33] 夏晴子：好想看看"),
        ourDays = listOf(OurDayEntity(uuid = "d1", characterUuid = "c1", dayKey = "2025-09-28", factLine = "第一次一起看了海。", createdAtMillis = 0L, updatedAtMillis = 0L)),
        now = now,
    )

    /** [text] 里从开头或某个 "\n\n" 之后起、长度 [len] 的各截的指纹集合。 */
    private fun paragraphAlignedFingerprints(text: String, len: Int): Set<String> {
        val starts = mutableListOf(0)
        var i = text.indexOf("\n\n")
        while (i >= 0) {
            starts += i + 2
            i = text.indexOf("\n\n", i + 1)
        }
        return starts.filter { it + len <= text.length }.map { ContextSegment.fingerprintOf(text.substring(it, it + len)) }.toSet()
    }

    @Test
    fun customModules_fingerprintOfTheirLiteralContent() {
        val segs = build(saver = false).segments
        assertEquals(ContextSegment.fingerprintOf(prefixCustom), segs.single { it.name == "自定义前置" }.fingerprint)
        assertEquals(ContextSegment.POSITION_PREFIX, segs.single { it.name == "自定义前置" }.position)
        assertEquals(ContextSegment.fingerprintOf(suffixCustom), segs.single { it.name == "自定义后置" }.fingerprint)
        assertEquals(ContextSegment.POSITION_SUFFIX, segs.single { it.name == "自定义后置" }.position)
    }

    @Test
    fun everyPrefixAndSuffixSegment_fingerprintIsAPieceOfWhatWasSent_historyHasNone() {
        val result = build(saver = false)
        val system = result.messages.first().content.orEmpty()
        val suffixTexts = result.messages.drop(1).filter { it.role == "system" }.map { it.content.orEmpty() }
        val prefix = result.segments.filter { it.position == ContextSegment.POSITION_PREFIX }
        val suffix = result.segments.filter { it.position == ContextSegment.POSITION_SUFFIX }
        assertTrue(prefix.size >= 3)
        assertTrue(suffix.size >= 3)
        for (s in prefix) {
            assertNotNull(s.name, s.fingerprint)
            assertTrue("前置段「${s.name}」的指纹不在系统提示词里", s.fingerprint in paragraphAlignedFingerprints(system, s.charCount))
        }
        for (s in suffix) {
            assertNotNull(s.name, s.fingerprint)
            assertTrue("后置段「${s.name}」的指纹不在后置消息里", suffixTexts.any { s.fingerprint in paragraphAlignedFingerprints(it, s.charCount) })
        }
        val history = result.segments.single { it.name == "对话历史" }
        assertEquals(ContextSegment.POSITION_HISTORY, history.position)
        assertNull("「对话历史」段不加指纹", history.fingerprint)
    }

    @Test
    fun saverBlockSegment_fingerprintOfMovedMessage() {
        val result = build(saver = true)
        val saved = result.segments.single { it.name == "每轮会变的内容（省钱模式）" }
        val lastUser = result.messages.indexOfLast { it.role == "user" }
        val moved = result.messages[lastUser - 1]
        assertEquals("system", moved.role)
        // 复核 R1：与请求形状里这条消息的指纹同源（缓存断点按它认挪位块）
        assertEquals(LogRequestShape.of(result.messages).fingerprints[lastUser - 1], saved.fingerprint)
    }

    /**
     * 复核 R1 🔴-1：缓存断点靠「挪位段指纹 == 请求形状里那条消息的指纹」认出挪位块——两边必须同源。
     * 真 [PromptBuilder] 连着两轮：省钱模式开 → 断在上一轮的挪位块（SAVED_BLOCK·正常）；关 → 只在末尾（TAIL）。
     * 修前两边一个算「块文字」一个算「整条消息」，永远对不上，开着省钱模式每轮都被误报成「中间的消息变了」。
     */
    @Test
    fun R1_真提示词连续两轮_开省钱模式断在挪位块_关着只在末尾() {
        val on1 = build(saver = true)
        val on2 = build(saver = true, history = turn2)
        val blockAt = on1.messages.indexOfLast { it.role == "user" } - 1
        assertEquals("system", on1.messages[blockAt].role)
        val on = CacheBreakAnalyzer.analyze(LogRequestShape.of(on2.messages), on2.segments, LogRequestShape.of(on1.messages), on1.segments)
        assertEquals(CacheBreakKind.SAVED_BLOCK, on.kind)
        assertEquals(blockAt, on.messageIndex)

        val off1 = build(saver = false)
        val off2 = build(saver = false, history = turn2)
        val off = CacheBreakAnalyzer.analyze(LogRequestShape.of(off2.messages), off2.segments, LogRequestShape.of(off1.messages), off1.segments)
        assertEquals(CacheBreakKind.TAIL, off.kind)
    }

    private fun atDay(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 9, day, h, m).atZone(zone).toInstant().toEpochMilli()

    /**
     * 复核 R1 🔴-2：历史跨天的长聊，最早一条被挤出窗口时，历史开头的「起始时间锚」写的是第一条的时刻、跟着一起变——
     * 必须判「最早的聊天被挤出窗口」（HISTORY_WINDOW_SLID），不能判成「时间标记变了」。开关省钱模式都一样。
     */
    @Test
    fun R1_跨天长聊窗口前滑_起始锚跟着变_判窗口滑动() {
        val a0 = MessageEntity(messageUUID = "a0", conversationUuid = "conv1", roleRaw = "assistant", content = "晚安呀", timestamp = atDay(26, 23, 0))
        val u0 = MessageEntity(messageUUID = "u0", conversationUuid = "conv1", roleRaw = "user", content = "早呀", timestamp = atDay(27, 8, 0))
        for (saver in listOf(false, true)) {
            val prev = build(saver, listOf(a0, u0) + turn1)
            val cur = build(saver, listOf(u0) + turn2)
            val r = CacheBreakAnalyzer.analyze(LogRequestShape.of(cur.messages), cur.segments, LogRequestShape.of(prev.messages), prev.segments)
            assertEquals("saver=$saver", CacheBreakKind.HISTORY_WINDOW_SLID, r.kind)
        }
    }

    @Test
    fun oldSegmentsJson_withoutFingerprint_decodes() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false; isLenient = true }
        val old = "[{\"name\":\"对话历史\",\"charCount\":7,\"estimatedTokens\":7,\"position\":\"history\"}," +
            "{\"name\":\"人设\",\"systemModuleType\":\"characterIdentity\",\"charCount\":3,\"estimatedTokens\":3,\"position\":\"prefix\"}]"
        val decoded = json.decodeFromString(ListSerializer(ContextSegment.serializer()), old)
        assertEquals(listOf(null, null), decoded.map { it.fingerprint })
        assertEquals(listOf("对话历史", "人设"), decoded.map { it.name })
    }
}
