package com.situ.aichat.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 卷四复核 R1（装机 O-7）：[WallpaperStore.purgeOrphans] 走真文件——编辑页裁好、还没保存的新壁纸（刚写盘、数据库无引用）
 * 在回前台维护里**活下来**；放满一天的孤儿照删；被引用的老文件永不删。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperStorePurgeTest {

    @Test fun purge_keepsFreshUnsavedWallpaper_deletesStaleOrphan_keepsReferenced() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val fresh = File(dir, "fresh.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 60_000L) }
        val stale = File(dir, "stale.jpg").apply { writeBytes(byteArrayOf(2)); setLastModified(now - WallpaperStore.ORPHAN_MIN_AGE_MS - 60_000L) }
        val inUse = File(dir, "inuse.jpg").apply { writeBytes(byteArrayOf(3)); setLastModified(now - 3 * WallpaperStore.ORPHAN_MIN_AGE_MS) }

        val deleted = WallpaperStore.purgeOrphans(context, setOf(inUse.absolutePath))

        assertEquals(1, deleted)
        assertTrue("编辑中未保存的新壁纸不能被删", fresh.exists())
        assertFalse("放满一天的孤儿照删", stale.exists())
        assertTrue("在用壁纸永不删", inUse.exists())
    }
}
