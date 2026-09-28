package com.situ.aichat.ui.contextlog.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T2-16（四期·图纸四 §4.9·E32）：两份 strings.xml 的 `clog_` 键一一对应、占位符（序号 + 类型）一致、没有落单的 `%`。
 * 直接解析源文件（不经资源编译），英文缺键 / 占位符错位在这里先红。
 */
class ClogStringsParityTest {

    private fun resFile(dir: String): File =
        listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.exists() }

    private fun clogStrings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(dir))
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            val name = e.getAttribute("name")
            if (name.startsWith("clog_")) out[name] = e.textContent
        }
        return out
    }

    private val placeholder = Regex("""%(\d+)\$([sd])""")

    private fun placeholders(value: String): List<String> = placeholder.findAll(value).map { it.value }.sorted().toList()

    @Test
    fun sameKeysBothLocales() {
        val en = clogStrings("values")
        val zh = clogStrings("values-zh-rCN")
        assertEquals("图纸四 §4.9 共 229 个键 + 图纸五 §4.9 新增 31 个 − 删 3 个（clog_sent_tab_* / clog_context_full）", 257, zh.size)
        assertEquals(zh.keys, en.keys)
    }

    @Test
    fun placeholdersMatch_noStrayPercent() {
        val en = clogStrings("values")
        val zh = clogStrings("values-zh-rCN")
        for ((key, zhValue) in zh) {
            val enValue = en.getValue(key)
            assertEquals("$key 占位符", placeholders(zhValue), placeholders(enValue))
            for (v in listOf(zhValue, enValue)) {
                val rest = v.replace("%%", "").replace(placeholder, "")
                assertTrue("$key 有落单的 %：$v", !rest.contains('%'))
            }
        }
    }

    @Test
    fun edgeSpacesEscaped() {
        val en = clogStrings("values")
        val sp = "\\" + "u0020" // XML 里原样写的反斜杠 u0020（资源编译时才变成空格）
        assertEquals("$sp·$sp%1${'$'}s", en["clog_alert_kind"])
        assertEquals(",$sp", en["clog_source_sep"])
        assertTrue(en.getValue("clog_cut_prev").endsWith(sp))
    }
}
