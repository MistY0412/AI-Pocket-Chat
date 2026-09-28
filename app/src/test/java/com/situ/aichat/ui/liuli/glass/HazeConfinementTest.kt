package com.situ.aichat.ui.liuli.glass

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1-7：机检「Haze 只许在 `ui/liuli/glass/` 里调用」（铁律 #1 唯一例外的围栏·琉璃 2.0 卷一图纸 §2.3 / §7 T1-7·E19）。
 *
 * 扫主源码与 debug 源码（存在才扫）下全部 `.kt`：含 `dev.chrisbanes.haze` 的文件集合必须**非空**（防扫描根错位
 * 假绿——根找错了会扫到零个文件、断言恒真），且每一个都住在 glass 包目录里。
 */
class HazeConfinementTest {

    @Test fun hazeIsImportedOnlyInsideLiuliGlassPackage() {
        val roots = listOf(File("src/main/java"), File("src/debug/java")).filter { it.isDirectory }
        val hazeFiles = roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.readText().contains("dev.chrisbanes.haze") }
                .toList()
        }
        assertTrue("没扫到任何用 Haze 的文件——扫描根错位？（工作目录 ${File(".").absolutePath}）", hazeFiles.isNotEmpty())
        val outside = hazeFiles.filterNot { it.invariantSeparatorsPath.contains("/com/situ/aichat/ui/liuli/glass/") }
        assertTrue(
            "Haze 越界（只许 ui/liuli/glass/ 调用）：${outside.joinToString { it.invariantSeparatorsPath }}",
            outside.isEmpty(),
        )
    }
}
