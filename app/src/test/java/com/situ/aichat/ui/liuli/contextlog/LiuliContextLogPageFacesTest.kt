package com.situ.aichat.ui.liuli.contextlog

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T2-14（四期·图纸四 §3.1·范式 `LiuliDiaryPageFacesTest`）：日志七个新选脸包装的**签名不漂移**——
 * 包装参数个数 = 暖陶屏参数个数 − 1 个 VM 默认形参（包装漏一个回调 = 「点了没反应」的静默事故，编译拦不住）。
 * 整屏两脸分派落在 `hiltViewModel()` 上，Robolectric 起不来，实际分派留装机。
 */
class LiuliContextLogPageFacesTest {

    /** 数一个 @Composable 函数在合成器参数之前的声明参数个数。 */
    private fun declaredParams(className: String, method: String): Int {
        val clazz = Class.forName(className)
        val fn = clazz.declaredMethods.firstOrNull { it.name == method } ?: error("$className 里找不到 $method")
        val composer = fn.parameterTypes.indexOfFirst { it.name == "androidx.compose.runtime.Composer" }
        return if (composer >= 0) composer else fn.parameterTypes.size
    }

    private fun check(warmFile: String, warm: String, expectedWarm: Int) {
        assertEquals("暖陶 $warm 形参数（含 VM）", expectedWarm, declaredParams("com.situ.aichat.ui.contextlog.$warmFile", warm))
        assertEquals("Skinned$warm = 暖陶 − VM", expectedWarm - 1, declaredParams(FACES, "Skinned$warm"))
    }

    @Test fun 首页() = check("ContextLogHomeScreenKt", "ContextLogHomeScreen", 5)
    @Test fun 角色页() = check("ContextLogCharacterScreenKt", "ContextLogCharacterScreen", 3)
    @Test fun 一轮详情() = check("ContextLogEntryScreenKt", "ContextLogEntryScreen", 6)
    @Test fun 上下文地图() = check("ContextLogMapScreenKt", "ContextLogMapScreen", 3)
    @Test fun 实际发送() = check("ContextLogSentScreenKt", "ContextLogSentScreen", 4)
    @Test fun 失败详情() = check("ContextLogFailureScreenKt", "ContextLogFailureScreen", 3)
    @Test fun 回复全文() = check("ContextLogReplyScreenKt", "ContextLogReplyScreen", 2)

    @Test
    fun 琉璃屏与暖陶屏同形参() {
        for ((liuliFile, name, n) in listOf(
            Triple("LiuliContextLogHomeScreenKt", "LiuliContextLogHomeScreen", 5),
            Triple("LiuliContextLogCharacterScreenKt", "LiuliContextLogCharacterScreen", 3),
            Triple("LiuliContextLogEntryScreenKt", "LiuliContextLogEntryScreen", 6),
            Triple("LiuliContextLogMapScreenKt", "LiuliContextLogMapScreen", 3),
            Triple("LiuliContextLogSentScreenKt", "LiuliContextLogSentScreen", 4),
            Triple("LiuliContextLogFailureScreenKt", "LiuliContextLogFailureScreen", 3),
            Triple("LiuliContextLogReplyScreenKt", "LiuliContextLogReplyScreen", 2),
        )) {
            assertEquals(name, n, declaredParams("com.situ.aichat.ui.liuli.contextlog.$liuliFile", name))
        }
    }

    private companion object {
        const val FACES = "com.situ.aichat.ui.liuli.contextlog.LiuliContextLogFacesKt"
    }
}
