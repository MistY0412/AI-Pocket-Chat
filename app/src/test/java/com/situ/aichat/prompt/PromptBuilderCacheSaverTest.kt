package com.situ.aichat.prompt

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.ConversationEntity
import com.situ.aichat.data.local.entity.MessageEntity
import com.situ.aichat.data.local.entity.OurDayEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.data.model.AppSettings
import com.situ.aichat.data.model.MomentChatContext
import com.situ.aichat.data.remote.llm.ChatMessageDto
import com.situ.aichat.worldbook.WorldInfoActivationResult
import com.situ.aichat.worldbook.WorldInfoDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
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
 * T2-1（时间感知四期·图纸二 §3.2 · E1–E7 / E14 / E16）：真 [PromptBuilder.buildMessages] 里的省钱模式挪位。
 * now 钉 2026-09-28 21:40（沪）；召回 4 条（角色 / 用户 / 见面 / 去年跨行）+ 那年今日一天 + 朋友圈。
 * 挪位块全文按 §3.2 规格手算（锁定文本重新打字），不回读实现产物。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class PromptBuilderCacheSaverTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private val now: Instant = LocalDateTime.of(2026, 9, 28, 21, 40).atZone(zone).toInstant()
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 9, 28, h, m).atZone(zone).toInstant().toEpochMilli()

    private val character = CharacterEntity(uuid = "c1", name = "夏晴子", creationDate = 0L)
    private val profile = UserProfileEntity(nickname = "阿远")

    private val snippets = listOf(
        "[2026-06-14 22:33] 夏晴子：好想看看",
        "[2026-06-14 22:31] 阿远：小时候…",
        "[2026-06-20 20:00 · 线下见面] 夏晴子：到了吗",
        "[2025-12-01 09:15] 阿远：下雪了\n好冷",
    )
    private val ourDays = listOf(
        OurDayEntity(uuid = "d1", characterUuid = "c1", dayKey = "2025-09-28", factLine = "夏晴子和阿远第一次一起看了海。", createdAtMillis = 0L, updatedAtMillis = 0L),
    )
    private val moments = MomentChatContext(characterPostsSummary = "- 9月27日 20:10 发了晚霞照片", userPostsSummary = "")
    private val worldInfo = WorldInfoActivationResult(
        before = "【世界书前】雾港是座海边小城。",
        after = "【世界书后】这里常年起雾。",
        suffix = "",
        atDepth = emptyList(),
        newTimedStates = emptyList(),
        expiredTimedStates = emptyList(),
        diagnostics = WorldInfoDiagnostics(emptyList(), emptyList(), emptyList(), 0, 0),
    )

    // MARK: - 手算期望（§3.2 ① 叙述句 + ② 段头引言 + ③ 块间 "\n\n"）

    private val memSection = "## 与当前话题相关的历史对话片段\n" +
        "以下是从历史对话中检索到的、与当前话题最相关的对话片段：\n" +
        "6月14日，你说过：「好想看看」\n" +
        "6月14日，阿远说过：「小时候…」\n" +
        "[2026-06-20 20:00 · 线下见面] 夏晴子：到了吗\n" +
        "2025年12月1日，阿远说过：「下雪了\n好冷」"
    private val ourDaysBlock = "[我们的日子 · 按日期翻到的记录]\n" +
        "这是夏晴子和阿远当天的记录。同一天若与记忆里的概括有出入，以这里为准。\n" +
        "一年前的今天：[2025-09-28 周日] 夏晴子和阿远第一次一起看了海。"
    private val wiBefore = "【世界书前】雾港是座海边小城。"
    private val wiAfter = "【世界书后】这里常年起雾。"
    private val divider = "【时间 · 今天 21:39 · 距离上条消息过去了约 9 分钟】"

    private fun history(offline: Boolean = false) = listOf(
        MessageEntity(messageUUID = "a1", conversationUuid = "conv1", roleRaw = "assistant", content = "在干嘛呀", timestamp = at(21, 30),
            isOfflineMode = offline, offlineSessionId = if (offline) "os1" else null),
        MessageEntity(messageUUID = "u1", conversationUuid = "conv1", roleRaw = "user", content = "刚到家", timestamp = at(21, 39),
            isOfflineMode = offline, offlineSessionId = if (offline) "os1" else null),
    )

    private fun settings(saver: Boolean, edit: ((PromptModule) -> PromptModule)? = null): AppSettings {
        val base = AppSettings(cacheSaverEnabled = saver)
        if (edit == null) return base
        return base.copy(promptModulesJSON = PromptModuleService.encodeModules(PromptModuleService.defaultModules().map(edit)))
    }

    private fun build(
        appSettings: AppSettings,
        snippets: List<String> = this.snippets,
        ourDays: List<OurDayEntity> = this.ourDays,
        worldInfo: WorldInfoActivationResult? = null,
        scene: PromptScene = PromptScene.ONLINE_CHAT,
        conversation: ConversationEntity? = null,
        offline: Boolean = false,
    ): PromptBuilder.PromptBuildResult = PromptBuilder.buildMessagesWithSegments(
        character = character,
        conversation = conversation,
        sortedMessages = history(offline),
        userProfile = profile,
        appSettings = appSettings,
        strings = PromptStrings(RuntimeEnvironment.getApplication()),
        momentChatContext = moments,
        retrievedMemorySnippets = snippets,
        ourDays = ourDays,
        worldInfo = worldInfo,
        scene = scene,
        now = now,
    )

    private fun List<ChatMessageDto>.lastUserIndex(): Int = indexOfLast { it.role == "user" }
    private fun List<ChatMessageDto>.movedBlock(): ChatMessageDto = this[lastUserIndex() - 1]
    private fun List<ChatMessageDto>.all(): String = joinToString("\n\n") { it.content.orEmpty() }

    // MARK: - E1 关着

    @Test
    fun `E1_关着_与默认设置逐字节同_原格式留在系统提示词`() {
        val off = build(settings(saver = false), worldInfo = worldInfo)
        val def = build(AppSettings(), worldInfo = worldInfo)
        assertEquals(def.messages, off.messages)
        assertEquals(def.segments, off.segments)

        val sys = off.messages.first().content.orEmpty()
        assertEquals("system", off.messages.first().role)
        assertTrue(sys.contains("## 与当前话题相关的历史对话片段\n以下是从历史对话中检索到的、与当前话题最相关的对话片段：\n[2026-06-14 22:33] 夏晴子：好想看看"))
        assertTrue(sys.contains("[我们的日子 · 按日期翻到的记录]"))
        assertTrue(sys.contains(wiBefore) && sys.contains(wiAfter))
        assertTrue(sys.contains("当前时刻：2026-09-28 21:40。以下是你和阿远最近 7 天在朋友圈的互动记录，"))
        assertFalse(off.messages.all().contains("说过：「"))
        assertFalse(off.segments.any { it.name == "每轮会变的内容（省钱模式）" })
        // 关着时最新用户消息前紧挨的是时间标记（9 分钟停顿 ≥ 5 分钟），不是挪位块。
        assertEquals(divider, off.messages[off.messages.lastUserIndex() - 1].content)
    }

    // MARK: - E2 典型在线聊天

    @Test
    fun `E2_开着_召回与日子挪到最新用户消息前_朋友圈行只写日期`() {
        val msgs = build(settings(saver = true)).messages
        val moved = msgs.movedBlock()
        assertEquals("system", moved.role)
        assertEquals(memSection + "\n\n" + ourDaysBlock, moved.content)
        assertEquals("刚到家", msgs[msgs.lastUserIndex()].content)
        assertEquals("时间标记在挪位块之前（§0.2-4 实测形状）", divider, msgs[msgs.lastUserIndex() - 2].content)

        val sys = msgs.first().content.orEmpty()
        assertFalse(sys.contains("## 与当前话题相关的历史对话片段"))
        assertFalse(sys.contains("[我们的日子"))
        assertTrue(msgs.all().contains("当前日期：2026-09-28。以下是你和阿远最近 7 天在朋友圈的互动记录，"))
        assertFalse(msgs.all().contains("当前时刻：2026-09-28 21:40。"))
        assertEquals("召回段全表只出现一次", 1, msgs.count { it.content.orEmpty().contains("## 与当前话题相关的历史对话片段") })
        assertFalse("原始方括号片段不再出现", msgs.all().contains("[2026-06-14 22:33] 夏晴子："))
    }

    @Test
    fun `E2_开着_只挪不删_其余消息与关着时一致`() {
        val off = build(settings(saver = false)).messages
        val on = build(settings(saver = true)).messages
        assertEquals("恰好多一条", off.size + 1, on.size)
        val onWithoutMoved = on.toMutableList().apply { removeAt(on.lastUserIndex() - 1) }
        // 除系统提示词（挪走两块 + 朋友圈行）外其余逐条相同。
        assertEquals(off.drop(1), onWithoutMoved.drop(1))
        val offSys = off.first().content.orEmpty()
        // 本例角色记忆只有召回一层 → 挪走后整个模块（含「[夏晴子的记忆]」标题）为空、不进系统提示词。
        val expectedOnSys = offSys
            .replace(
                "\n\n[夏晴子的记忆]\n## 与当前话题相关的历史对话片段\n以下是从历史对话中检索到的、与当前话题最相关的对话片段：\n" +
                    snippets.joinToString("\n"),
                "",
            )
            .replace("\n\n$ourDaysBlock", "")
            .replace("当前时刻：2026-09-28 21:40。", "当前日期：2026-09-28。")
        assertEquals(expectedOnSys, onWithoutMoved.first().content)
    }

    // MARK: - E3 / E4 世界书

    @Test
    fun `E3_开着_世界书前后锚进挪位块开头`() {
        val msgs = build(settings(saver = true), worldInfo = worldInfo).messages
        assertEquals("$wiBefore\n\n$wiAfter\n\n$memSection\n\n$ourDaysBlock", msgs.movedBlock().content)
        val sys = msgs.first().content.orEmpty()
        assertFalse(sys.contains("【世界书前】"))
        assertFalse(sys.contains("【世界书后】"))
    }

    @Test
    fun `E4_开着_身份模块关闭_前锚最前后锚最后`() {
        val appSettings = settings(saver = true) {
            if (it.systemModuleType == SystemModuleType.CHARACTER_IDENTITY) it.copy(isEnabled = false) else it
        }
        val msgs = build(appSettings, worldInfo = worldInfo).messages
        assertEquals("$wiBefore\n\n$memSection\n\n$ourDaysBlock\n\n$wiAfter", msgs.movedBlock().content)
        assertFalse(msgs.first().content.orEmpty().contains("【世界书"))
    }

    // MARK: - E5 / E6 用户挪过 / 关掉角色记忆

    @Test
    fun `E5_开着_角色记忆在后置区_召回留原位不改叙述句`() {
        val appSettings = settings(saver = true) {
            if (it.systemModuleType == SystemModuleType.CHARACTER_MEMORY) it.copy(position = PromptModulePosition.SUFFIX) else it
        }
        val msgs = build(appSettings).messages
        assertEquals("只挪了日子", ourDaysBlock, msgs.movedBlock().content)
        val afterUser = msgs.drop(msgs.lastUserIndex() + 1).all()
        assertTrue(afterUser.contains("[2026-06-14 22:33] 夏晴子：好想看看"))
        assertFalse(msgs.all().contains("说过：「"))
    }

    @Test
    fun `E6_开着_角色记忆关闭_召回不出现`() {
        val appSettings = settings(saver = true) {
            if (it.systemModuleType == SystemModuleType.CHARACTER_MEMORY) it.copy(isEnabled = false) else it
        }
        val msgs = build(appSettings).messages
        assertEquals(ourDaysBlock, msgs.movedBlock().content)
        assertFalse(msgs.all().contains("好想看看"))
        assertFalse(msgs.all().contains("## 与当前话题相关的历史对话片段"))
    }

    // MARK: - E7 无可挪

    @Test
    fun `E7_开着_无可挪内容_不插system_只有朋友圈行变化`() {
        val off = build(settings(saver = false), snippets = emptyList(), ourDays = emptyList())
        val on = build(settings(saver = true), snippets = emptyList(), ourDays = emptyList())
        assertEquals(off.messages.size, on.messages.size)
        assertEquals(divider, on.messages[on.messages.lastUserIndex() - 1].content)
        val restored = on.messages.map {
            it.copy(content = it.content?.replace("当前日期：2026-09-28。", "当前时刻：2026-09-28 21:40。"))
        }
        assertEquals(off.messages, restored)
        assertTrue(on.messages.all().contains("当前日期：2026-09-28。"))
        assertEquals(off.segments.map { it.name }, on.segments.map { it.name })
    }

    // MARK: - 复核 R1 🔵：挪位块顺序 = 关着时的出现顺序（§9-4 机制锁·非默认模块序）

    @Test
    fun `R1_我们的日子排到身份前面_挪位块顺序跟着关着时的出现顺序`() {
        val ourDaysFirst: (PromptModule) -> PromptModule = {
            if (it.systemModuleType == SystemModuleType.OUR_DAYS) it.copy(sortOrder = -1) else it
        }
        val offSys = build(settings(saver = false, edit = ourDaysFirst), worldInfo = worldInfo).messages.first().content.orEmpty()
        val offOrder = listOf("[我们的日子", wiBefore, wiAfter, "## 与当前话题相关的历史对话片段").map { offSys.indexOf(it) }
        assertTrue("前提：关着时的出现顺序 = 日子 → 前锚 → 后锚 → 召回", offOrder.all { it >= 0 } && offOrder == offOrder.sorted())

        val on = build(settings(saver = true, edit = ourDaysFirst), worldInfo = worldInfo).messages
        assertEquals("$ourDaysBlock\n\n$wiBefore\n\n$wiAfter\n\n$memSection", on.movedBlock().content)
    }

    // MARK: - 复核 R1 🟡-1（§11 TODO-1）：前置区只剩可挪内容 + 后置区全关

    @Test
    fun `TODO1_开着_前置区只剩可挪内容且后置区全关_不走最小兜底_挪位块照插`() {
        val onlyMemory: (PromptModule) -> PromptModule = {
            if (it.systemModuleType == SystemModuleType.CHARACTER_MEMORY) it else it.copy(isEnabled = false)
        }
        val fallbackHead = "You are “夏晴子”"
        val off = build(settings(saver = false, edit = onlyMemory), worldInfo = worldInfo).messages
        assertFalse("关着：前置区有召回 + 世界书，本就不走兜底", off.all().contains(fallbackHead))
        assertTrue(off.first().content.orEmpty().contains("[2026-06-14 22:33] 夏晴子：好想看看"))

        val on = build(settings(saver = true, edit = onlyMemory), worldInfo = worldInfo).messages
        assertFalse("开着：内容只是挪走了，不能因此掉进最小兜底", on.all().contains(fallbackHead))
        assertEquals("身份模块缺席：前锚最前、后锚最后（同 E4）", "$wiBefore\n\n$memSection\n\n$wiAfter", on.movedBlock().content)
        assertTrue("系统提示词为空时不发空 system", on.none { it.role == "system" && it.content.isNullOrEmpty() })
    }

    // MARK: - E14 分段

    @Test
    fun `E14_分段表_前置_对话历史_挪位段_后置`() {
        val on = build(settings(saver = true))
        val block = memSection + "\n\n" + ourDaysBlock
        val names = on.segments.map { it.name }
        val historyAt = names.indexOf("对话历史")
        assertTrue(historyAt > 0)
        val saved = on.segments[historyAt + 1]
        assertEquals(ContextSegment("每轮会变的内容（省钱模式）", null, block.length, TokenEstimator.estimate(block), "history", fingerprint = ContextSegment.fingerprintOf("system\u0000" + block + "\u0000\u0000\u0000")), saved)
        assertTrue(on.segments.take(historyAt).all { it.position == "prefix" })
        assertTrue(on.segments.drop(historyAt + 2).isNotEmpty())
        assertTrue(on.segments.drop(historyAt + 2).all { it.position == "suffix" })

        val off = build(settings(saver = false))
        assertFalse(off.segments.any { it.name == "每轮会变的内容（省钱模式）" })
        assertEquals(1, off.segments.count { it.position == "history" })
    }

    // MARK: - E16 语音通话 / 线下见面

    @Test
    fun `E16_语音通话_同样挪位`() {
        val msgs = build(settings(saver = true), scene = PromptScene.VOICE_CALL).messages
        assertEquals(memSection + "\n\n" + ourDaysBlock, msgs.movedBlock().content)
        assertFalse(msgs.first().content.orEmpty().contains("## 与当前话题相关的历史对话片段"))
    }

    @Test
    fun `E16_线下见面_同样挪位_日子模块线下不启用`() {
        val conv = ConversationEntity(
            uuid = "conv1", title = "t", characterUuid = "c1", creationDate = 0L,
            isInOfflineMode = true, currentOfflineSessionId = "os1",
        )
        val msgs = build(settings(saver = true), scene = PromptScene.OFFLINE_MEETING, conversation = conv, offline = true).messages
        assertEquals(memSection, msgs.movedBlock().content)
        assertFalse(msgs.first().content.orEmpty().contains("## 与当前话题相关的历史对话片段"))
        assertFalse(msgs.all().contains("[我们的日子"))
    }
}
