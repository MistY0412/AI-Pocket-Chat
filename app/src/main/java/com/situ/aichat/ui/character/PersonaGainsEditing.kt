package com.situ.aichat.ui.character

import com.situ.aichat.data.model.CustomGain
import com.situ.aichat.data.model.PersonaGains
import com.situ.aichat.data.model.PersonaVocab
import java.util.UUID

/** 三档次序恒为 不吃这套 / 正常 / 很敏感（= 档位整数 0/1/2 的自然序）。 */
internal val PERSONA_GAIN_LEVELS = listOf(PersonaVocab.LEVEL_NUMB, PersonaVocab.LEVEL_NORMAL, PersonaVocab.LEVEL_SENSITIVE)

/** 摘要行：系统项里「与常人不同」的条数。 */
internal fun PersonaGains.differingCount(): Int = system.count { it.value != PersonaVocab.LEVEL_NORMAL }

/** 「展开其余 N 项」的 N：系统项里「正常」（含缺席）的条数。 */
internal fun PersonaGains.normalSystemCount(): Int =
    PersonaVocab.GAIN_KEYS.count { system[it] == null || system[it] == PersonaVocab.LEVEL_NORMAL }

/** 护栏 3：专属项满 [PersonaGains.MAX_CUSTOM] 条。 */
internal fun PersonaGains.isCustomFull(): Boolean = custom.size >= PersonaGains.MAX_CUSTOM

internal fun PersonaGains.withCustomLevel(id: String, level: Int): PersonaGains =
    copy(custom = custom.map { if (it.id == id) it.copy(level = level) else it })

internal fun PersonaGains.withoutCustom(id: String): PersonaGains = copy(custom = custom.filterNot { it.id == id })

/** 回到「正常」= 从 map 里摘掉（缺席即 1·Y-7）。 */
internal fun PersonaGains.withSystemLevel(key: String, level: Int): PersonaGains {
    val next = system.toMutableMap()
    if (level == PersonaVocab.LEVEL_NORMAL) next.remove(key) else next[key] = level
    return copy(system = next)
}

/** D-10：新项默认「很敏感」、来源 = 手写。 */
internal fun PersonaGains.withNewCustom(label: String, id: String = UUID.randomUUID().toString()): PersonaGains =
    copy(
        custom = custom + CustomGain(
            id = id,
            label = label.trim(),
            level = PersonaVocab.LEVEL_SENSITIVE,
            origin = CustomGain.ORIGIN_MANUAL,
        ),
    )

/** 护栏：输入上限 12 字——**超出不接收**（而不是接收后截断）。 */
internal fun acceptsCustomGainDraft(next: String): Boolean = next.length <= CustomGain.MAX_LABEL_LENGTH

/** 查重口径：27 项标签 ∪ 已有专属标签，去空白 + 全小写比较；命中返回被撞的那个原标签。 */
internal fun customGainDuplicateOf(draft: String, systemLabels: List<String>, custom: List<CustomGain>): String? =
    (systemLabels + custom.map { it.label }).associateBy { it.trim().lowercase() }[draft.trim().lowercase()]

internal fun customGainSubmittable(draft: String, duplicateOf: String?, full: Boolean): Boolean =
    draft.isNotBlank() && duplicateOf == null && !full

/** D-4：默认只展开档位 ≠「正常」的项；[showAll] 时全显。 */
internal fun PersonaGains.visibleSystemKeys(group: PersonaVocab.GainGroup, showAll: Boolean): List<String> =
    group.keys.filter { showAll || (system[it] ?: PersonaVocab.LEVEL_NORMAL) != PersonaVocab.LEVEL_NORMAL }
