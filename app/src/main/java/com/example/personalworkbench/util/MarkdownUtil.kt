package com.example.personalworkbench.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * 轻量 Markdown 解析（V1.4.1 · 工作日志富文本）
 *
 * 不引三方库，自行实现日志场景需要的子集：
 *  标题 `# / ## / ###`、无序列表 `- / *`、有序列表 `1.`、引用 `>`、
 *  分隔线 `---`、代码块 ``` ``` ```、以及行内 `**粗体**` `*斜体*` `~~删除线~~` `` `代码` ``。
 *  另外支持图表标记 `{{chart:week}}` / `{{chart:month}}`，由预览层渲染为真实统计图。
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val ordered: Boolean, val index: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Divider(val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Chart(val range: String) : MdBlock
}

object MarkdownUtil {

    private val CHART_RE = Regex("^\\{\\{\\s*chart\\s*:\\s*([a-zA-Z]+)\\s*\\}\\}$")

    /** 去掉 Markdown 标记，得到纯文本（列表摘要、AI 上下文、日志卡片预览用） */
    fun stripMarkdown(src: String): String {
        if (src.isBlank()) return ""
        val out = StringBuilder()
        var inCode = false
        src.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            if (line.trim().startsWith("```")) { inCode = !inCode; return@forEach }
            if (inCode) { out.append(line.trim()).append(' '); return@forEach }
            val t = line.trim()
            if (t.isEmpty()) { out.append('\n'); return@forEach }
            out.append(
                t.replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^>\\s?"), "")
                    .replace(Regex("^[-*]\\s+"), "· ")
                    .replace(Regex("^\\d+\\.\\s+"), "")
                    .replace("**", "").replace("~~", "")
                    .replace(Regex("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)"), "$1")
                    .replace(Regex("(?<!_)_([^_]+)_(?!_)"), "$1")
                    .replace("`", "")
            )
            out.append('\n')
        }
        return out.toString().replace(Regex("\n{2,}"), "\n").trim()
    }

    /** 解析为块序列 */
    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = src.lines()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val t = raw.trim()

            if (t.startsWith("```")) {
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++ // 跳过结束围栏
                blocks += MdBlock.CodeBlock(code.toString().trimEnd())
                continue
            }
            if (t.isEmpty()) { i++; continue }

            CHART_RE.find(t)?.let { m ->
                blocks += MdBlock.Chart(m.groupValues[1].lowercase()); i++; return@let
            }

            if (t == "---" || t == "***" || t == "___") {
                blocks += MdBlock.Divider(t); i++; continue
            }

            // 表格：当前行与下一行均以 | 开头
            if (t.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().startsWith("|")) {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                    // 跳过 |---|--- 分隔行
                    if (!cells.all { it.replace("-", "").replace(":", "").isBlank() }) rows += cells
                    i++
                }
                blocks += MdBlock.Table(rows)
                continue
            }

            val heading = Regex("^(#{1,6})\\s+(.*)$").find(t)
            if (heading != null) {
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()); i++; continue
            }
            if (t.startsWith(">")) {
                blocks += MdBlock.Quote(t.removePrefix(">").trim()); i++; continue
            }
            val bullet = Regex("^[-*+]\\s+(.*)$").find(t)
            if (bullet != null) {
                blocks += MdBlock.Bullet(bullet.groupValues[1], ordered = false, index = 0); i++; continue
            }
            val ordered = Regex("^(\\d+)[.)]\\s+(.*)$").find(t)
            if (ordered != null) {
                blocks += MdBlock.Bullet(ordered.groupValues[2], ordered = true, index = ordered.groupValues[1].toInt()); i++; continue
            }

            blocks += MdBlock.Paragraph(t)
            i++
        }
        return blocks
    }

    /** 行内样式解析为 AnnotatedString */
    fun inline(text: String, codeBg: androidx.compose.ui.graphics.Color? = null): AnnotatedString =
        buildAnnotatedString {
            var i = 0
            val n = text.length
            fun pushPlain(s: String) { if (s.isNotEmpty()) append(s) }
            while (i < n) {
                val c = text[i]
                // 行内代码
                if (c == '`') {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, end))
                        }
                        if (codeBg != null) {
                            addStyle(SpanStyle(background = codeBg), i, end + 1)
                        }
                        i = end + 1
                        continue
                    }
                }
                // 粗体
                if (c == '*' && i + 1 < n && text[i + 1] == '*') {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                        continue
                    }
                }
                // 删除线
                if (c == '~' && i + 1 < n && text[i + 1] == '~') {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                        continue
                    }
                }
                // 斜体
                if (c == '*' || c == '_') {
                    val end = text.indexOf(c, i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                        continue
                    }
                }
                append(c)
                i++
            }
        }

    private inline fun AnnotatedString.Builder.pushStyle(
        style: SpanStyle,
        block: AnnotatedString.Builder.() -> Unit
    ) {
        val start = length
        block()
        addStyle(style, start, length)
    }
}

/** 便捷入口：去掉 Markdown 标记得到纯文本 */
fun stripMarkdown(src: String): String = MarkdownUtil.stripMarkdown(src)
