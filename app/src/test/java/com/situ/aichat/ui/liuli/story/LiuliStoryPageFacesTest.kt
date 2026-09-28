package com.situ.aichat.ui.liuli.story

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T2-S1（琉璃 2.0 卷六·三·上 §7·范式 `LiuliMomentsPageFacesTest`）：故事八屏选脸包装的**签名不漂移**——
 * 包装参数个数 = 暖陶屏参数个数 − 1 个 VM 默认形参（包装漏一个回调 = 「点了没反应」的静默事故，编译拦不住）。
 */
class LiuliStoryPageFacesTest {

    /** 数一个 @Composable 函数在合成器参数之前的声明参数个数。 */
    private fun declaredParams(className: String, method: String): Int {
        val clazz = Class.forName(className)
        val fn = clazz.declaredMethods.firstOrNull { it.name == method } ?: error("$className 里找不到 $method")
        val composer = fn.parameterTypes.indexOfFirst { it.name == "androidx.compose.runtime.Composer" }
        return if (composer >= 0) composer else fn.parameterTypes.size
    }

    private fun check(warmFile: String, screen: String, expected: Int) {
        assertEquals("暖陶 $screen = $expected 个回调 + VM", expected + 1, declaredParams("$WARM.${warmFile}Kt", screen))
        assertEquals("选脸包装 Skinned$screen", expected, declaredParams(FACES, "Skinned$screen"))
    }

    @Test fun 书架() = check("StoryBookshelfScreen", "StoryBookshelfScreen", 7)

    @Test fun 结局档案() = check("StoryArchiveDetailScreen", "StoryArchiveDetailScreen", 1)

    @Test fun 全部结局() = check("StoryArchiveAllScreen", "StoryArchiveAllScreen", 2)

    @Test fun 章节() = check("StoryChapterListScreen", "StoryChapterListScreen", 4)

    @Test fun 模板墙() = check("StoryTemplateWallScreen", "StoryTemplateWallScreen", 3)

    @Test fun 字段编辑() = check("StoryFieldEditorScreen", "StoryFieldEditorScreen", 1)

    @Test fun 书页() = check("StoryBookHubScreen", "StoryBookHubScreen", 5)

    @Test fun 创建() = check("StoryCreationScreen", "StoryCreationScreen", 2)

    /** 卷六·三·下甲：阅读器包装 = 暖陶 − 1（VM）= 3。 */
    @Test fun 阅读器() = check("StoryReaderScreen", "StoryReaderScreen", 3)

    private companion object {
        const val FACES = "com.situ.aichat.ui.liuli.story.LiuliStoryFacesKt"
        const val WARM = "com.situ.aichat.ui.story"
    }
}
