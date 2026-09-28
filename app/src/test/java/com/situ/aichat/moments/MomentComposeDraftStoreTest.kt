package com.situ.aichat.moments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1-4（朋友圈发布页重构·甲 §7·J-7）：发布页草稿单份存取。prefs 名 `moment_compose_draft`、键 `draft` 为图纸 §9 ① 锁定文本，
 * 测试直接按字面量读底层 SharedPreferences 核对「键已删」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentComposeDraftStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs() = context.getSharedPreferences("moment_compose_draft", Context.MODE_PRIVATE)

    @Before fun setUp() = MomentComposeDraftStore.clear(context)
    @After fun tearDown() = MomentComposeDraftStore.clear(context)

    @Test
    fun 存取往返相等() {
        val draft = MomentComposeDraft("今天的风", listOf("/a.jpg", "/b.jpg"), listOf("c2", "c1"))
        MomentComposeDraftStore.save(context, draft)
        assertTrue(prefs().contains("draft"))
        assertEquals(draft, MomentComposeDraftStore.load(context))
    }

    @Test
    fun 空草稿存了等于清掉() {
        MomentComposeDraftStore.save(context, MomentComposeDraft("有字"))
        assertTrue(prefs().contains("draft"))
        MomentComposeDraftStore.save(context, MomentComposeDraft())
        assertNull(MomentComposeDraftStore.load(context))
        assertFalse("空草稿 save 后键已删", prefs().contains("draft"))
    }

    @Test
    fun 只有空白正文也算空() {
        assertTrue(MomentComposeDraft(" \n\t ").isEmpty)
        assertFalse(MomentComposeDraft("", images = listOf("/a.jpg")).isEmpty)
        assertFalse(MomentComposeDraft("", mentions = listOf("c1")).isEmpty)
        MomentComposeDraftStore.save(context, MomentComposeDraft("   "))
        assertFalse(prefs().contains("draft"))
        assertNull(MomentComposeDraftStore.load(context))
    }

    @Test
    fun 坏数据读出null() {
        prefs().edit().putString("draft", "{不是 json").commit()
        assertNull(MomentComposeDraftStore.load(context))
    }

    @Test
    fun 清掉后读出null() {
        MomentComposeDraftStore.save(context, MomentComposeDraft("有字"))
        MomentComposeDraftStore.clear(context)
        assertNull(MomentComposeDraftStore.load(context))
        assertFalse(prefs().contains("draft"))
    }
}
