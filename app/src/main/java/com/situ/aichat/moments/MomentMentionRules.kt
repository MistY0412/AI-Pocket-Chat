package com.situ.aichat.moments

import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.model.mentionedCharacterUuids

/** 「提醒谁看」的三条纯规则（朋友圈发布页重构·甲 §3.2·J-1 / J-10）。 */
internal object MomentMentionRules {

    /** 欠评论的被提醒者：帖上 @ 的（按帖上顺序·去重）、仍存在、不是帖主、不在 [commentedUuids] 里。非用户帖 → 空。 */
    fun owedMentions(
        post: MomentPostEntity,
        allCharacters: List<CharacterEntity>,
        commentedUuids: Set<String>,
    ): List<CharacterEntity> {
        if (post.authorTypeRaw != MomentAuthorType.USER.raw) return emptyList()
        val byUuid = allCharacters.associateBy { it.uuid }
        return post.mentionedCharacterUuids.distinct()
            .filter { it != post.characterUuid && it !in commentedUuids }
            .mapNotNull { byUuid[it] }
    }

    /** 圈子 / 详情「提醒了 …」那一行的名字（按帖上顺序·去重·跳过已删与空名）；空表 = 不显示那一行（乙卷用）。 */
    fun displayNames(post: MomentPostEntity, characters: Map<String, CharacterEntity>): List<String> =
        post.mentionedCharacterUuids.distinct().mapNotNull { characters[it]?.name?.takeIf { n -> n.isNotBlank() } }

    /** 发布前清洗：去重（保首次出现的顺序）、去掉已不存在的角色。 */
    fun sanitize(mentions: List<String>, existingUuids: Set<String>): List<String> =
        mentions.distinct().filter { it in existingUuids }
}
