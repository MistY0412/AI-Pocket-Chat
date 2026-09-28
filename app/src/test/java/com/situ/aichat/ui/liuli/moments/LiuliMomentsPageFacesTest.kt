package com.situ.aichat.ui.liuli.moments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T2-F1（琉璃 2.0 卷六·二 §3.8·范式 `LiuliDiaryPageFacesTest`）：朋友圈六屏选脸包装的**签名不漂移**——
 * 包装参数个数 = 暖陶屏参数个数 − 1 个 VM 默认形参（包装漏一个回调 = 「点了没反应」的静默事故，编译拦不住）。
 * 整屏两脸分派落在 `hiltViewModel()` 上，Robolectric 起不来，实际分派留装机（T4-3–T4-5）。
 */
class LiuliMomentsPageFacesTest {

    /** 数一个 @Composable 函数在合成器参数之前的声明参数个数。 */
    private fun declaredParams(className: String, method: String): Int {
        val clazz = Class.forName(className)
        val fn = clazz.declaredMethods.firstOrNull { it.name == method } ?: error("$className 里找不到 $method")
        val composer = fn.parameterTypes.indexOfFirst { it.name == "androidx.compose.runtime.Composer" }
        return if (composer >= 0) composer else fn.parameterTypes.size
    }

    @Test fun 圈子() {
        assertEquals("暖陶圈子 = onBack / onCompose / onOpenPost / onOpenNotifications / onOpenCharacterMoments + VM", 6, declaredParams("$WARM.MomentsListScreenKt", "MomentsListScreen"))
        assertEquals(5, declaredParams(FACES, "SkinnedMomentsListScreen"))
    }

    @Test fun 某人与我的动态() {
        assertEquals("暖陶作者页 = onBack / onOpenPost + VM", 3, declaredParams("$WARM.MomentAuthorScreenKt", "MomentAuthorScreen"))
        assertEquals(2, declaredParams(FACES, "SkinnedMomentAuthorScreen"))
    }

    @Test fun 那天的动态() {
        assertEquals("暖陶那天页 = onBack / onOpenPost + VM", 3, declaredParams("$WARM.DayMomentsScreenKt", "DayMomentsScreen"))
        assertEquals(2, declaredParams(FACES, "SkinnedDayMomentsScreen"))
    }

    @Test fun 动态详情() {
        assertEquals("暖陶详情 = onBack + VM", 2, declaredParams("$WARM.MomentDetailScreenKt", "MomentDetailScreen"))
        assertEquals(1, declaredParams(FACES, "SkinnedMomentDetailScreen"))
    }

    @Test fun 发动态() {
        assertEquals("暖陶发动态 = onClose + VM", 2, declaredParams("$WARM.ComposeMomentScreenKt", "ComposeMomentScreen"))
        assertEquals(1, declaredParams(FACES, "SkinnedComposeMomentScreen"))
    }

    @Test fun 消息() {
        assertEquals("暖陶消息 = onBack / onOpenPost + VM", 3, declaredParams("$WARM.MomentNotificationListScreenKt", "MomentNotificationListScreen"))
        assertEquals(2, declaredParams(FACES, "SkinnedMomentNotificationListScreen"))
    }

    private companion object {
        const val FACES = "com.situ.aichat.ui.liuli.moments.LiuliMomentsFacesKt"
        const val WARM = "com.situ.aichat.ui.moments"
    }
}
