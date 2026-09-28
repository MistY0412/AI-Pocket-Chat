package com.situ.aichat.data.repository

import com.situ.aichat.data.local.dao.OpenLoopDao
import com.situ.aichat.data.local.entity.OpenLoopEntity
import com.situ.aichat.data.local.entity.OpenLoopType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T2-4（时间感知四期·图纸一 §3.6·E34）：「惦记的事」口径 [OpenLoopRepository.openLoopsForChat] 去掉她自己的打算，
 * 其余三类原样（顺序不变）；扫描口径 [OpenLoopRepository.openLoopsForCharacter] 仍是全量。
 */
class OpenLoopRepositoryChatFilterTest {

    private fun loop(id: String, type: String) = OpenLoopEntity(
        uuid = id, conversationUuid = "conv", characterUuid = "c1", content = id, typeRaw = type, createdAt = 1L,
    )

    @Test
    fun `openLoopsForChat 滤掉 plan_char_openLoopsForCharacter 仍全量`() = runBlocking {
        val dao = mockk<OpenLoopDao>()
        val all = listOf(
            loop("a", OpenLoopType.USER_EVENT),
            loop("b", "plan_char"),
            loop("c", OpenLoopType.PROMISE_CHAR),
            loop("d", OpenLoopType.OPEN_TOPIC),
        )
        coEvery { dao.openByCharacter("c1") } returns all
        val repo = OpenLoopRepository(dao)
        assertEquals(listOf("a", "c", "d"), repo.openLoopsForChat("c1").map { it.uuid })
        assertEquals(all, repo.openLoopsForCharacter("c1"))
    }
}
