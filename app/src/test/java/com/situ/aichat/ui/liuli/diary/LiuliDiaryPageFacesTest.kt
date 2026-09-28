package com.situ.aichat.ui.liuli.diary

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T2-F1（琉璃 2.0 卷六·一 §3.15·范式 `LiuliSecondaryFacesTest`）：日记三屏选脸包装的**签名不漂移**——
 * 包装参数个数 = 暖陶屏参数个数 − 1 个 VM 默认形参（包装漏一个回调 = 「点了没反应」的静默事故，编译拦不住）。
 * 整屏两脸分派落在 `hiltViewModel()` 上，Robolectric 起不来，实际分派留装机（T4-5–T4-7）。
 */
class LiuliDiaryPageFacesTest {

    /** 数一个 @Composable 函数在合成器参数之前的声明参数个数。 */
    private fun declaredParams(className: String, method: String): Int {
        val clazz = Class.forName(className)
        val fn = clazz.declaredMethods.firstOrNull { it.name == method } ?: error("$className 里找不到 $method")
        val composer = fn.parameterTypes.indexOfFirst { it.name == "androidx.compose.runtime.Composer" }
        return if (composer >= 0) composer else fn.parameterTypes.size
    }

    @Test fun 日记本包装与暖陶同签名() {
        assertEquals("暖陶日记本 = onBack / onCompose / onOpenEntry + VM", 4, declaredParams(WARM_LIST, "DiaryListScreen"))
        assertEquals(3, declaredParams(FACES, "SkinnedDiaryListScreen"))
    }

    @Test fun 日记详情包装与暖陶同签名() {
        assertEquals("暖陶详情 = onBack / onEdit + VM", 3, declaredParams(WARM_DETAIL, "DiaryDetailScreen"))
        assertEquals(2, declaredParams(FACES, "SkinnedDiaryDetailScreen"))
    }

    @Test fun 写日记包装与暖陶同签名() {
        assertEquals("暖陶写日记 = onClose / onNavigateToApiConfig + VM", 3, declaredParams(WARM_COMPOSE, "ComposeDiaryScreen"))
        assertEquals(2, declaredParams(FACES, "SkinnedComposeDiaryScreen"))
    }

    private companion object {
        const val FACES = "com.situ.aichat.ui.liuli.diary.LiuliDiaryFacesKt"
        const val WARM_LIST = "com.situ.aichat.ui.diary.DiaryListScreenKt"
        const val WARM_DETAIL = "com.situ.aichat.ui.diary.DiaryDetailScreenKt"
        const val WARM_COMPOSE = "com.situ.aichat.ui.diary.ComposeDiaryScreenKt"
    }
}
