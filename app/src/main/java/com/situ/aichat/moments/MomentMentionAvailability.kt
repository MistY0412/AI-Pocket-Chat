package com.situ.aichat.moments

import com.situ.aichat.data.local.dao.ConversationDao
import com.situ.aichat.offline.OfflineMeetingGate
import com.situ.aichat.prompt.schedule.CharacterSleepChecker
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 被提醒者此刻能不能来（乙卷选人弹层按它写「在睡觉 · 醒了再来看」/「正在和你见面 · 结束后来看」）。 */
enum class MentionAvailability { AVAILABLE, SLEEPING, IN_MEETING }

/**
 * 唯一判定口（J-6）：见面优先于睡觉（与 autoInteractWithPost 现有顺序一致）。
 * 互动（[MomentMentionInteractor]）与发布页 VM 共用，保证弹层里写的和实际行为永远一致
 * （朋友圈发布页重构·甲 §3.3.1·图纸 docs/handoff/2026-09-27-朋友圈发布页-甲-底子.md）。
 */
@Singleton
class MomentMentionAvailability @Inject constructor(
    private val sleepChecker: CharacterSleepChecker,
    private val conversationDao: ConversationDao,
) {
    suspend fun of(
        characterUuid: String,
        scheduleSystemEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): MentionAvailability = when {
        OfflineMeetingGate.characterInMeeting(conversationDao, characterUuid) -> MentionAvailability.IN_MEETING
        sleepChecker.isSleeping(characterUuid, scheduleSystemEnabled, nowMillis, zone) -> MentionAvailability.SLEEPING
        else -> MentionAvailability.AVAILABLE
    }
}
