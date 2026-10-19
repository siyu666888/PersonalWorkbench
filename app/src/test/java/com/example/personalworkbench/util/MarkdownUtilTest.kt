package com.example.personalworkbench.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MarkdownUtil 解析测试。
 * 重点覆盖 V1.4.2 启动闪退回归：图表标记正则 `{{chart:week}}` 在 Android ICU 引擎下
 * 必须能正常编译与解析，不能抛 PatternSyntaxException。
 */
class MarkdownUtilTest {

    /** 图表标记能被解析为 Chart 块，不触发类初始化异常（回归 V1.4.2 闪退） */
    @Test
    fun chartRegex_compilesAndParses() {
        val blocks = MarkdownUtil.parse("今日进展\n\n{{chart:week}}\n\n{{chart:month}}")
        val charts = blocks.filterIsInstance<MdBlock.Chart>()
        assertEquals(2, charts.size)
        assertEquals("week", charts[0].range)
        assertEquals("month", charts[1].range)
    }

    /** 标题与列表解析不受影响 */
    @Test
    fun headingsAndBullets() {
        val blocks = MarkdownUtil.parse("# 标题\n- 项目一\n- 项目二")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(2, blocks.count { it is MdBlock.Bullet })
    }

    /** stripMarkdown 去掉标记得到纯文本 */
    @Test
    fun stripRemovesMarkers() {
        val plain = MarkdownUtil.stripMarkdown("# 标题\n**粗体** 与 *斜体*\n- 列表项")
        assertTrue(plain.contains("标题"))
        assertTrue(plain.contains("粗体"))
        assertTrue(plain.contains("斜体"))
        assertTrue(plain.contains("列表项"))
        // 不应再残留 Markdown 标记
        assertTrue(!plain.contains("#"))
        assertTrue(!plain.contains("**"))
    }

    /** 行内粗体解析为带样式的 AnnotatedString */
    @Test
    fun inlineBold() {
        val s = MarkdownUtil.inline("这是 **重点** 内容")
        assertEquals("这是 重点 内容", s.text)
    }
}
