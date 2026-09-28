package com.situ.aichat.ui.contextlog.model

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.diagnostics.LlmFailureClassifier
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.diagnostics.LogListRow
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.diagnostics.LogSource
import java.time.LocalDate
import java.time.ZoneId

data class LogConversationChip(val uuid: String, val title: String) // title 空 → 界面「未命名对话」

data class LogDaySection(val dayKind: LogDayKind, val date: LocalDate, val items: List<LogTimelineItem>)

sealed interface LogTimelineItem {
    val key: String
    val sortMillis: Long
}

/** 一轮卡：代表行 = 最后一条主调用（重试时即最后一次）。 */
data class LogTurnCard(
    override val key: String, override val sortMillis: Long,
    val openId: Long, val openFailed: Boolean,
    val mainSource: String, val quote: LogTurnQuoteText?, val replies: Int?,
    val tokens: Int, val tokensEstimated: Boolean, val cacheRatePercent: Int?, val durationMillis: Long?,
    val retries: Int, val failureKind: LlmFailureKind?, val kids: List<LogKid>,
) : LogTimelineItem

data class LogKid(val id: Long, val source: String, val timeMillis: Long, val tokens: Int, val tokensEstimated: Boolean, val failed: Boolean)

data class LogBackgroundRow(
    override val key: String, override val sortMillis: Long,
    val id: Long, val source: String, val tokens: Int, val tokensEstimated: Boolean, val failed: Boolean,
) : LogTimelineItem

/** 角色页第 1–3 步的结果（VM 据此决定读哪些会话标题与消息轻投影）。 */
data class LogTimelineScope(
    val isSystem: Boolean,
    val conversationUuids: List<String>,
    val selected: String?,
    val visible: List<LogListRow>,
)

private val ROW_ORDER = compareBy<LogListRow>({ it.timestampMillis }, { it.id })

/** §3.3 第 1–3 步：归属本键的行、会话（按各自最新行降序）、所选会话、可见行（无会话的行在每个会话视图里都在）。 */
fun timelineScopeOf(rows: List<LogListRow>, characters: List<CharacterEntity>, key: String, selectedConversation: String?): LogTimelineScope {
    val owned = rows.filter { ownerKeyOf(it, characters) == key }
    val conversations = owned.filter { it.conversationUuid != null }
        .groupBy { it.conversationUuid!! }
        .entries.sortedByDescending { e -> e.value.maxOf { it.timestampMillis } }
        .map { it.key }
    val selected = selectedConversation?.takeIf { it in conversations } ?: conversations.firstOrNull()
    val visible = if (conversations.isEmpty()) owned else owned.filter { it.conversationUuid == null || it.conversationUuid == selected }
    return LogTimelineScope(key == SYSTEM_KEY, conversations, selected, visible)
}

/** 一轮的锚点：代表行（最后一条主行）的锚点，没有则第一条主行的。 */
fun turnAnchorOf(mains: List<LogListRow>): String? = mains.lastOrNull()?.anchorMessageUuid ?: mains.firstOrNull()?.anchorMessageUuid

/** 消息轻投影第一步要查的 uuid：各轮锚点 ∪ 图片理解行的锚点。 */
fun anchorUuidsOf(visible: List<LogListRow>): Set<String> {
    val out = LinkedHashSet<String>()
    visible.filter { it.turnId != null }.groupBy { it.turnId }.values.forEach { group ->
        turnAnchorOf(group.filter { it.source in MAIN_SOURCES }.sortedWith(ROW_ORDER))?.let { out += it }
    }
    visible.filter { it.source == LogSource.IMAGE_UNDERSTANDING }.mapNotNullTo(out) { it.anchorMessageUuid }
    return out
}

/**
 * §3.3 第 4–8 步（锁定）：分轮、代表行、重试、引用与回复数、图片理解归轮、后台小方块、按本地日期分节（节与项都降序）。
 * [briefs] = 所选会话时刻升序的消息轻投影（没读 = 空表 → 引用全为 null）。
 */
fun buildCharacterTimeline(visible: List<LogListRow>, briefs: List<LogMessageBrief>, nowMillis: Long, zone: ZoneId): List<LogDaySection> {
    val cards = ArrayList<LogTurnCard>()
    val cardUserUuids = ArrayList<Set<String>>() // 与 cards 同序：每张卡的本轮用户消息 uuid 集
    val cardKids = ArrayList<MutableList<LogListRow>>()
    val background = ArrayList<LogListRow>()

    visible.filter { it.turnId != null }.groupBy { it.turnId!! }.forEach { (turnId, group) ->
        val mains = group.filter { it.source in MAIN_SOURCES }.sortedWith(ROW_ORDER)
        if (mains.isEmpty()) {
            background += group
            return@forEach
        }
        val userMessages = turnAnchorOf(mains)?.let { LogTurnQuote.turnUserMessages(briefs, it, mains.first().timestampMillis) }.orEmpty()
        cards += turnCard("t:$turnId", mains, LogTurnQuote.quoteOf(userMessages), userMessages.lastOrNull()?.let { LogTurnQuote.replyCount(briefs, it.messageUUID) })
        cardUserUuids.add(userMessages.map { it.messageUUID }.toSet())
        cardKids.add(group.filter { it.source !in MAIN_SOURCES }.toMutableList())
    }
    visible.filter { it.turnId == null }.sortedWith(ROW_ORDER).forEach { row ->
        when {
            row.source in MAIN_SOURCES -> {
                cards += turnCard("r:${row.id}", listOf(row), quote = null, replies = null)
                cardUserUuids.add(emptySet())
                cardKids.add(mutableListOf())
            }
            row.source == LogSource.IMAGE_UNDERSTANDING -> {
                // 挂进最早带上这张图的那一轮（复核 R1：连着失败的几轮用户消息集会重叠，取最早 = 图第一次被发出去的那轮）。
                val at = row.anchorMessageUuid?.let { anchor ->
                    cardUserUuids.indices.filter { anchor in cardUserUuids[it] }.minByOrNull { cards[it].sortMillis }
                } ?: -1
                if (at >= 0) cardKids[at] += row else background += row
            }
            else -> background += row
        }
    }

    val items = ArrayList<LogTimelineItem>()
    cards.forEachIndexed { i, card -> items += card.copy(kids = cardKids[i].sortedWith(ROW_ORDER).map(::kidOf)) }
    background.forEach { items += LogBackgroundRow("b:${it.id}", it.timestampMillis, it.id, it.source, tokensOf(it), it.isTokenEstimated, !it.isSuccess) }
    return items.sortedByDescending { it.sortMillis }
        .groupBy { LogFormat.localDate(it.sortMillis, zone) }
        .entries.sortedByDescending { it.key }
        .map { (date, dayItems) -> LogDaySection(LogFormat.dayKind(dayItems.first().sortMillis, nowMillis, zone), date, dayItems) }
}

private fun turnCard(key: String, mains: List<LogListRow>, quote: LogTurnQuoteText?, replies: Int?): LogTurnCard {
    val rep = mains.last()
    return LogTurnCard(
        key = key,
        sortMillis = mains.first().timestampMillis,
        openId = rep.id,
        openFailed = !rep.isSuccess,
        mainSource = rep.source,
        quote = quote,
        replies = replies,
        tokens = tokensOf(rep),
        tokensEstimated = rep.isTokenEstimated,
        cacheRatePercent = LogFormat.cacheRate(rep.cacheHitTokens.toLong(), rep.cacheMissTokens.toLong()),
        durationMillis = rep.durationMillis,
        retries = mains.size - 1,
        failureKind = LlmFailureClassifier.kindOfRow(rep.isSuccess, rep.failureKind, rep.errorMessage),
        kids = emptyList(),
    )
}

private fun kidOf(row: LogListRow) = LogKid(row.id, row.source, row.timestampMillis, tokensOf(row), row.isTokenEstimated, !row.isSuccess)

private fun tokensOf(row: LogListRow): Int = row.promptTokens + row.completionTokens
