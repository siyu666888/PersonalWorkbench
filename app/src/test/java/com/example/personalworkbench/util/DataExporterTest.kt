package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalPeriod
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 数据导出渲染测试（V1.6.4 / A4）。
 *
 * 纯 JVM 测试：不依赖 Robolectric / Room / Context，
 * 因为 [DataExporter] 刻意不碰 Android API，只做「数据 → 字符串」。
 * 每固定时间戳，导出结果可逐字符断言。
 */
class DataExporterTest {

    // 固定时间：2026年3月5日 09:30（避免跨时区/跨年让断言变得不确定）
    private val base = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 5, 9, 30, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val later get() = base + 3_600_000L

    private val notes = listOf(
        NoteEntity(
            id = 1, title = "周会纪要", content = "讨论了「导出, 功能」\n重点：隐私",
            tags = listOf("工作", "会议"), preview = "讨论了", pinned = true,
            createdAt = base, updatedAt = later
        ),
        NoteEntity(id = 2, title = "", content = "", createdAt = base, updatedAt = base)
    )

    private val logs = listOf(
        WorkLogEntity(id = 1, date = base, content = "完成导出模块"),
        WorkLogEntity(id = 2, date = later, content = "写测试")
    )

    private val tasks = listOf(
        TaskEntity(
            id = 1, title = "补个说明", dueDate = later, priority = Priority.HIGH,
            tags = listOf("docs"), status = TaskStatus.ACTIVE
        ),
        TaskEntity(id = 2, title = "旧任务", priority = Priority.LOW, status = TaskStatus.COMPLETED, completedAt = later)
    )

    private val goals = listOf(
        GoalEntity(
            id = 1, name = "跑步", type = GoalType.NUMERIC, period = GoalPeriod.MONTH,
            targetValue = 100.0, currentValue = 40.0, unit = "公里", status = GoalStatus.ACTIVE
        ),
        // 第三个目标故意没有任何打卡：导出时也必须占一行（记录列留空），
        // 否则用户会发现「这个目标导出后凭空消失了」
        GoalEntity(id = 2, name = "早睡", type = GoalType.HABIT, unit = "次", status = GoalStatus.PAUSED),
        GoalEntity(id = 3, name = "存钱", type = GoalType.MILESTONE, unit = "元", status = GoalStatus.ACTIVE)
    )
    private val records = listOf(
        GoalRecordEntity(id = 1, goalId = 1, recordDate = base, value = 5.0, note = "夜跑"),
        GoalRecordEntity(id = 2, goalId = 1, recordDate = later, value = 3.5, isMakeup = true),
        GoalRecordEntity(id = 3, goalId = 2, recordDate = base, value = 1.0)
    )

    private val data get() = DataExporter.Data(notes, logs, tasks, goals, records)

    // -----------------------------------------------------------------------
    // Markdown
    // -----------------------------------------------------------------------

    @Test
    fun notesMarkdown_包含全部笔记标题与标签且空标题有兜底() {
        val md = DataExporter.render(data, DataExporter.Kind.NOTES, DataExporter.Format.MARKDOWN)
        assertTrue(md.contains("# ") && md.contains("笔记（2）"))
        assertTrue(md.contains("周会纪要"))
        assertTrue(md.contains("标签：工作、会议"))
        assertTrue(md.contains("置顶"))
        // 空标题不能导出成空行，否则用户找不到这条
        assertTrue(md.contains("（无标题）"))
        assertTrue(md.contains("（正文为空）"))
        // 日期含年份
        assertTrue(md.contains("2026年3月5日"))
    }

    @Test
    fun workLogsMarkdown_日期由近及远() {
        val md = DataExporter.render(data, DataExporter.Kind.WORK_LOGS, DataExporter.Format.MARKDOWN)
        val i09 = md.indexOf("完成导出模块")
        val i10 = md.indexOf("写测试")
        assertTrue(i09 >= 0 && i10 >= 0)
        // 后写的（时间更晚）排前面
        assertTrue("更新的一条应排在前面", i10 < i09)
    }

    @Test
    fun tasksMarkdown_按状态分组且用复选框标记() {
        val md = DataExporter.render(data, DataExporter.Kind.TASKS, DataExporter.Format.MARKDOWN)
        assertTrue(md.contains("- [ ] 补个说明"))
        assertTrue(md.contains("- [x] 旧任务"))
        assertTrue(md.contains("优先级：高"))
        // 已完成区块在后面
        assertTrue(md.indexOf("- [ ]") < md.indexOf("- [x]"))
    }

    @Test
    fun goalsMarkdown_含进度与打卡流水() {
        val md = DataExporter.render(data, DataExporter.Kind.GOALS, DataExporter.Format.MARKDOWN)
        assertTrue(md.contains("跑步"))
        assertTrue(md.contains("进度 40 / 100 公里"))
        assertTrue(md.contains("（补签）"))
        assertTrue(md.contains("备注：夜跑"))
        // 无打卡目标也保留一整行，只是没有流水
        assertTrue(md.contains("早睡"))
        assertFalse(md.contains("（暂无目标）"))
    }

    @Test
    fun allMarkdown_四个板块都在() {
        val md = DataExporter.render(data, DataExporter.Kind.ALL, DataExporter.Format.MARKDOWN, base)
        assertTrue(md.contains("导出时间：2026年3月5日 09:30"))
        listOf("笔记（2）", "工作日志（2）", "待办（进行中", "目标与打卡").forEach {
            assertTrue("缺少板块：$it", md.contains(it))
        }
    }

    // -----------------------------------------------------------------------
    // CSV
    // -----------------------------------------------------------------------

    @Test
    fun csv_逗号与换行被引号转义且不破坏列数() {
        val csv = DataExporter.render(data, DataExporter.Kind.NOTES, DataExporter.Format.CSV)
        val rows = splitCsv(csv)
        assertEquals("表头 + 2 条笔记", 3, rows.size)
        rows.forEach { row -> assertEquals("列数应与表头一致", 7, row.size) }
        // 正文里的逗号和换行必须原样留在同一个单元格里（未被切成多行/多列）
        assertEquals("讨论了「导出, 功能」\n重点：隐私", rows[1][4])
        assertTrue(csv.contains("\"讨论了「导出, 功能」\n重点：隐私\""))
    }

    @Test
    fun csv_引号翻倍转义() {
        val nasty = DataExporter.Data(
            notes = listOf(NoteEntity(id = 1, title = "含\"引号\"和,逗号", content = "前后空格 ", createdAt = base))
        )
        val csv = DataExporter.render(nasty, DataExporter.Kind.NOTES, DataExporter.Format.CSV)
        assertTrue(csv.contains("\"含\"\"引号\"\"和,逗号\""))
        // 首尾有空格也要包起来，避免 Excel 自动 trim 造成误解
        assertTrue(csv.contains("\"前后空格 \""))
        // 用真正的解析器验证：带引号的标题应还原出原始字符串
        assertEquals("含\"引号\"和,逗号", splitCsv(csv)[1][0])
    }

    @Test
    fun csv_每条记录一行且空表仍保留表头() {
        val csv = DataExporter.render(
            DataExporter.Data(logs = logs), DataExporter.Kind.WORK_LOGS, DataExporter.Format.CSV
        )
        val rows = splitCsv(csv)
        assertEquals("表头 + 2 条", 3, rows.size)
        assertTrue(rows[0] == listOf("日期", "正文", "更新时间"))

        val empty = DataExporter.render(
            DataExporter.Data(), DataExporter.Kind.WORK_LOGS, DataExporter.Format.CSV
        )
        assertEquals("空表仍写入表头一行", 1, splitCsv(empty).size)
    }

    @Test
    fun goalsCsv_无打卡的目标也占一行() {
        val csv = DataExporter.render(
            DataExporter.Data(goals = goals, records = records),
            DataExporter.Kind.GOALS, DataExporter.Format.CSV
        )
        val rows = splitCsv(csv)
        assertEquals("表头 + 2 条跑步 + 1 条早睡 + 1 条无打卡目标", 5, rows.size)
        assertEquals("目标", rows[0].first())
        rows.forEach { assertEquals("每行 10 列", 10, it.size) }
        assertTrue(csv.contains("跑步,数值型,进行中,公里,100,40,"))
        assertTrue("实际CSV:\n$csv", csv.contains("2026年3月5日 10:30,3.5,补签,"))        // 无打卡目标：前 6 列有值，后 4 列为空
        val emptyRow = rows.last { it.first() == "存钱" }
        assertEquals(listOf("存钱", "里程碑", "进行中", "元", "0", "0", "", "", "", ""), emptyRow)
    }

    @Test
    fun csv_落盘带UTF8_BOM而Markdown不带() {
        val csv = DataExporter.bytesFor("a,b\n", DataExporter.Format.CSV)
        assertEquals(0xEF.toByte(), csv[0])
        assertEquals(0xBB.toByte(), csv[1])
        assertEquals(0xBF.toByte(), csv[2])

        val md = DataExporter.bytesFor("# a\n", DataExporter.Format.MARKDOWN)
        assertEquals('#'.code.toByte(), md[0])
    }

    @Test
    fun 文件名按模块与格式区分() {
        val name = DataExporter.suggestedFileName(DataExporter.Kind.WORK_LOGS, DataExporter.Format.CSV, base)
        assertEquals("workbench-logs-20260305-0930.csv", name)
        assertTrue(
            DataExporter.suggestedFileName(DataExporter.Kind.ALL, DataExporter.Format.MARKDOWN, base)
                .endsWith(".md")
        )
    }

    /**
     * 一个够用的 RFC4180 解析器：这里刻意**不用** DataExporter 自己的代码，
     * 而是模拟「别人的表格软件」去读我们导出的文件——包括被引号包住的行内换行。
     */
    private fun splitCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cur.append('"'); i++ // "" 表示一个字面量引号
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells.add(cur.toString()); cur.setLength(0) }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\n') {
                        cells.add(cur.toString()); cur.setLength(0)
                        rows.add(cells.toList()); cells.clear()
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty() || cells.isNotEmpty()) {
            cells.add(cur.toString()); rows.add(cells.toList())
        }
        return rows.filter { it != listOf("") }
    }
}
