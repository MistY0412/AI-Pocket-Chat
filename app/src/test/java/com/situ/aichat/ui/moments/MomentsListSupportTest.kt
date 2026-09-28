package com.situ.aichat.ui.moments

import com.situ.aichat.data.local.entity.MomentLikeEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.model.MomentAuthorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-1（琉璃 2.0 卷六·二 §7）：列表共用件的两枚纯函数。期望值按规格独立反推：屏上判据只认字面 `"user"`；
 * 卡内判据 `fromRaw` 对未知 raw 回落 USER——两式对未知 raw 结论相反，这条就是「不许合并」的锁。
 */
class MomentsListSupportTest {

    private fun post(vararg likeRaws: String) = MomentPostWithRelations(
        post = MomentPostEntity(uuid = "p1", authorTypeRaw = "character", characterUuid = "c1"),
        comments = emptyList(),
        likes = likeRaws.map { MomentLikeEntity(authorTypeRaw = it, characterUuid = if (it == "character") "c1" else null, postUuid = "p1") },
    )

    @Test fun 用户点过赞() {
        assertTrue(momentUserLiked(post("character", "user")))
    }

    @Test fun 只有角色赞() {
        assertFalse(momentUserLiked(post("character", "character")))
    }

    @Test fun 未知raw的赞屏上判据不算用户而卡内判据算() {
        assertFalse(momentUserLiked(post("x")))
        assertTrue(MomentAuthorType.fromRaw("x") == MomentAuthorType.USER)
    }

    @Test fun 刷新结果四种() {
        val fmt = "%1\$d 条新动态"
        assertNull(momentsRefreshMessage(null, fmt, "暂无", "失败"))
        assertEquals("3 条新动态", momentsRefreshMessage(MomentsViewModel.RefreshOutcome.NewPosts(3), fmt, "暂无", "失败"))
        assertEquals("暂无", momentsRefreshMessage(MomentsViewModel.RefreshOutcome.NoNew, fmt, "暂无", "失败"))
        assertEquals("失败", momentsRefreshMessage(MomentsViewModel.RefreshOutcome.Failed, fmt, "暂无", "失败"))
    }
}
