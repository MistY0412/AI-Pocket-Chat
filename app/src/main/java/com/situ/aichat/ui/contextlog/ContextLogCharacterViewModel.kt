package com.situ.aichat.ui.contextlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.dao.LogDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.ConversationRepository
import com.situ.aichat.data.repository.MessageRepository
import com.situ.aichat.diagnostics.LogMessageBrief
import com.situ.aichat.ui.contextlog.model.LogConversationChip
import com.situ.aichat.ui.contextlog.model.LogDaySection
import com.situ.aichat.ui.contextlog.model.LogTimelineScope
import com.situ.aichat.ui.contextlog.model.SYSTEM_KEY
import com.situ.aichat.ui.contextlog.model.anchorUuidsOf
import com.situ.aichat.ui.contextlog.model.buildCharacterTimeline
import com.situ.aichat.ui.contextlog.model.nameForKey
import com.situ.aichat.ui.contextlog.model.timelineScopeOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/** 角色页状态（四期·图纸四 §3.3）。 */
data class ContextLogCharacterUiState(
    val loaded: Boolean = false,
    val isSystem: Boolean = false,
    /** 系统键为空（界面用「系统任务」文案）。 */
    val name: String = "",
    /** ≥ 2 个才显示芯片行。 */
    val conversations: List<LogConversationChip> = emptyList(),
    val selectedConversation: String? = null,
    val sections: List<LogDaySection> = emptyList(),
)

/**
 * 角色页 VM（四期·图纸四 §3.3）：最近 500 条 + 角色 + 所选会话 → `mapLatest` 里读会话标题与消息轻投影（切会话 / 行变化会取消
 * 上一轮读库·E36）→ [buildCharacterUiState]。所选会话不进 SavedState（进程死亡回来默认最新会话·§3.11）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContextLogCharacterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    logDao: LogDao,
    characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val key: String = savedStateHandle.get<String>(ARG_KEY) ?: SYSTEM_KEY
    private val selectedConversation = MutableStateFlow<String?>(null)

    val state: StateFlow<ContextLogCharacterUiState> = combine(
        logDao.recent(ContextLogViewModel.RECENT_LIMIT),
        characterRepository.observeAll(),
        selectedConversation,
    ) { rows, characters, selected -> Triple(rows, characters, selected) }
        .mapLatest { (rows, characters, selected) ->
            val scope = timelineScopeOf(rows, characters, key, selected)
            val titles = scope.conversationUuids.associateWith { conversationRepository.get(it)?.title.orEmpty() }
            val selectedRows = scope.visible.filter { it.conversationUuid == scope.selected }
            val briefs = readLogBriefs(
                messageRepository, anchorUuidsOf(scope.visible), scope.selected,
                toMillis = selectedRows.maxOfOrNull { it.timestampMillis }?.plus(BRIEF_TAIL_MILLIS),
            )
            buildCharacterUiState(key, characters, scope, titles, briefs, System.currentTimeMillis(), ZoneId.systemDefault())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextLogCharacterUiState())

    fun selectConversation(uuid: String) { selectedConversation.value = uuid }

    companion object {
        const val ARG_KEY = "key"
    }
}

/** 消息轻投影第二步的上界余量：本轮各行最大时刻之后再多取 1 分钟（回复可能晚于调用落库）。 */
internal const val BRIEF_TAIL_MILLIS = 60_000L

/**
 * 两步消息轻投影（§3.3·角色页与条目页共用）：① 按锚点 uuid 取时刻；② 以①里最早时刻为起点、[toMillis] 为终点取 [conversationUuid]
 * 这段时间的消息（升序）。锚点为空 → 两步都跳过；①全查不到 / 没有会话 / 没有终点 → 只做①、返回空表。
 */
internal suspend fun readLogBriefs(
    messageRepository: MessageRepository,
    anchors: Collection<String>,
    conversationUuid: String?,
    toMillis: Long?,
): List<LogMessageBrief> {
    if (anchors.isEmpty()) return emptyList()
    val from = messageRepository.logBriefsByUuids(anchors.toList()).minOfOrNull { it.timestamp } ?: return emptyList()
    if (conversationUuid == null || toMillis == null) return emptyList()
    return messageRepository.logBriefsInRange(conversationUuid, from, toMillis)
}

/** 角色页纯装配：名字（系统键为空；uuid 查无回退行里的名字）+ 会话芯片 + 分节时间线。 */
internal fun buildCharacterUiState(
    key: String,
    characters: List<CharacterEntity>,
    scope: LogTimelineScope,
    titles: Map<String, String>,
    briefs: List<LogMessageBrief>,
    nowMillis: Long,
    zone: ZoneId,
): ContextLogCharacterUiState = ContextLogCharacterUiState(
    loaded = true,
    isSystem = scope.isSystem,
    name = if (scope.isSystem) "" else nameForKey(key, characters).ifEmpty { scope.visible.firstOrNull()?.characterName.orEmpty() },
    conversations = scope.conversationUuids.map { LogConversationChip(it, titles[it].orEmpty()) },
    selectedConversation = scope.selected,
    sections = buildCharacterTimeline(scope.visible, briefs, nowMillis, zone),
)
