package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import java.util.Locale

/**
 * 数据导出：把本机数据渲染成「人能直接读、别的软件能直接打开」的 Markdown / CSV（V1.6.4 / A4）。
 *
 * 设计要点：
 * 1. **纯 Kotlin、零 Android 依赖**——不碰 Context / ContentResolver，因此可以直接跑 JVM 单测
 *    （见 `app/src/test/.../DataExporterTest`），不需要 Robolectric 起任何环境。
 *    写文件的部分放在 [ExportWriter] / BackupViewModel，保持这里可纯测。
 * 2. **CSV 一律带 UTF-8 BOM**（写入侧在 [BOM] 里，由调用方补在文件头）——Excel / WPS 在
 *    简体中文 Windows 上按 GBK 解析无 BOM 的 UTF-8，中文会变乱码，这是实测高频投诉点。
 * 3. **CSV 单元格一律转义**：包含逗号 / 引号 / 换行的正文会被双引号包裹，内部引号翻倍（`""`），
 *    否则一行 CSV 会被 Excel 切成多行/多列，数据就毁了。
 * 4. Markdown 用于「给人看 / 贴进其它笔记软件」，CSV 用于「给表格软件算」。两者互补。
 */
object DataExporter {

    /** 导出格式 */
    enum class Format(val ext: String, val mime: String) {
        MARKDOWN("md", "text/markdown"),
        CSV("csv", "text/csv")
    }

    /** 可导出的数据集（对应「数据导出」卡片里的行） */
    enum class Kind(val label: String, val slug: String) {
        NOTES("笔记", "notes"),
        WORK_LOGS("工作日志", "logs"),
        TASKS("待办", "tasks"),
        GOALS("目标与打卡", "goals"),
        ALL("全部数据", "all")
    }

    /** 一次导出的输入快照：按需填，未用到的部分留空即可 */
    data class Data(
        val notes: List<NoteEntity> = emptyList(),
        val logs: List<WorkLogEntity> = emptyList(),
        val tasks: List<TaskEntity> = emptyList(),
        val goals: List<GoalEntity> = emptyList(),
        /** 打卡流水（跨目标），导出时按 goal_id 归组 */
        val records: List<GoalRecordEntity> = emptyList()
    )

    /**
     * 统一渲染入口。
     * @param exportedAt 导出时刻，写进文件头便于日后区分版本。
     */
    fun render(data: Data, kind: Kind, format: Format, exportedAt: Long = System.currentTimeMillis()): String =
        when (kind) {
            Kind.ALL -> when (format) {
                // 全部数据：Markdown 串成一个文档；CSV 不适合多类合并，
                // 改为「逐块用标题行分隔」的单一 CSV（各块自带表头，导入 Excel 后可按需拆分）。
                Format.MARKDOWN -> buildString {
                    append(mdHead("全部数据导出", exportedAt, data.allCount()))
                    append(notesMarkdown(data.notes))
                    append(workLogsMarkdown(data.logs))
                    append(tasksMarkdown(data.tasks))
                    append(goalsMarkdown(data.goals, data.records))
                }
                Format.CSV -> buildString {
                    append(csvSection("笔记"))
                    append(notesCsv(data.notes))
                    append(csvSection("工作日志"))
                    append(workLogsCsv(data.logs))
                    append(csvSection("待办"))
                    append(tasksCsv(data.tasks))
                    append(csvSection("目标与打卡"))
                    append(goalsCsv(data.goals, data.records))
                }
            }
            Kind.NOTES -> if (format == Format.MARKDOWN) notesMarkdown(data.notes) else notesCsv(data.notes)
            Kind.WORK_LOGS -> if (format == Format.MARKDOWN) workLogsMarkdown(data.logs) else workLogsCsv(data.logs)
            Kind.TASKS -> if (format == Format.MARKDOWN) tasksMarkdown(data.tasks) else tasksCsv(data.tasks)
            Kind.GOALS -> if (format == Format.MARKDOWN) {
                goalsMarkdown(data.goals, data.records)
            } else {
                goalsCsv(data.goals, data.records)
            }
        }

    /** UTF-8 BOM：CSV 落盘时写在文件头，保证 Excel / WPS 打开不是乱码（Windows 中文环境刚需） */
    const val BOM = "\uFEFF"

    /** 建议文件名：`workbench-<模块>-<yyyyMMdd-HHmm>.<ext>` */
    fun suggestedFileName(kind: Kind, format: Format, ts: Long = System.currentTimeMillis()): String {
        val stamp = formatDate(ts, "yyyyMMdd-HHmm")
        return "workbench-${kind.slug}-$stamp.${format.ext}"
    }

    /**
     * 落盘用的最终字节：CSV 在头部补 [BOM]，Markdown 原样输出。
     * 单独抽出来的原因：单元测试可以直接断言 BOM 存在与否，不必真的写文件。
     */
    fun bytesFor(content: String, format: Format): ByteArray =
        (if (format == Format.CSV) BOM + content else content).toByteArray(Charsets.UTF_8)

    private fun Data.allCount(): Int = notes.size + logs.size + tasks.size + goals.size

    // -----------------------------------------------------------------------
    // 通用片段
    // -----------------------------------------------------------------------

    private fun mdHead(title: String, exportedAt: Long, count: Int): String = buildString {
        append("# $title\n\n")
        append("> 导出时间：${formatDateTime(exportedAt)}\n")
        append("> 条目合计：$count\n\n")
    }

    /** CSV 里的小节标题（作为一行带 # 前缀的注释，Excel 会原样显示） */
    private fun csvSection(title: String): String = "## $title\n"

    /** 没有数据时的占位提示，避免导出空文件让用户怀疑「是不是坏了」 */
    private fun emptyTip(what: String): String = "_（暂无$what）_\n\n"

    // -----------------------------------------------------------------------
    // 笔记
    // -----------------------------------------------------------------------

    fun notesMarkdown(notes: List<NoteEntity>): String = buildString {
        append("## 笔记（${notes.size}）\n\n")
        if (notes.isEmpty()) { append(emptyTip("笔记")); return@buildString }
        notes.forEach { n ->
            append("### ${escapeMdHeading(n.title.ifBlank { "（无标题）" })}\n\n")
            val meta = buildList {
                if (n.pinned) add("置顶")
                if (n.tags.isNotEmpty()) add("标签：${n.tags.joinToString("、")}")
                add("创建：${formatDateTime(n.createdAt)}")
                add("更新：${formatDateTime(n.updatedAt)}")
            }
            append("> ${meta.joinToString("　·　")}\n\n")
            append(n.content.trim().ifBlank { "（正文为空）" }).append("\n\n")
            append("---\n\n")
        }
    }

    fun notesCsv(notes: List<NoteEntity>): String = csvTable(
        listOf("标题", "标签", "置顶", "摘要", "正文", "创建时间", "更新时间"),
        notes.map { n ->
            listOf(
                n.title, n.tags.joinToString("、"), if (n.pinned) "是" else "否", n.preview,
                n.content, formatDateTime(n.createdAt), formatDateTime(n.updatedAt)
            )
        }
    )

    // -----------------------------------------------------------------------
    // 工作日志
    // -----------------------------------------------------------------------

    fun workLogsMarkdown(logs: List<WorkLogEntity>): String = buildString {
        append("## 工作日志（${logs.size}）\n\n")
        if (logs.isEmpty()) { append(emptyTip("工作日志")); return@buildString }
        // 日期由近及远：日志是「今天写了什么」的流水，最新的在最上面最好找
        logs.sortedByDescending { it.date }.forEach { l ->
            append("### ${formatDate(l.date)}\n\n")
            append(l.content.trim().ifBlank { "（正文为空）" }).append("\n\n")
        }
    }

    fun workLogsCsv(logs: List<WorkLogEntity>): String = csvTable(
        listOf("日期", "正文", "更新时间"),
        logs.sortedByDescending { it.date }.map { l ->
            listOf(formatDate(l.date), l.content, formatDateTime(l.updatedAt))
        }
    )

    // -----------------------------------------------------------------------
    // 待办
    // -----------------------------------------------------------------------

    fun tasksMarkdown(tasks: List<TaskEntity>): String = buildString {
        val done = tasks.filter { it.status == TaskStatus.COMPLETED }
        val todo = tasks.filter { it.status != TaskStatus.COMPLETED }
        append("## 待办（进行中 ${todo.size} · 已完成 ${done.size}）\n\n")
        if (tasks.isEmpty()) { append(emptyTip("待办")); return@buildString }

        append("### 进行中\n\n")
        if (todo.isEmpty()) append(emptyTip("进行中的待办")) else todo.forEach { append(taskLine(it)) }
        append("\n### 已完成\n\n")
        if (done.isEmpty()) append(emptyTip("已完成的待办")) else done.forEach { append(taskLine(it)) }
    }

    private fun taskLine(t: TaskEntity): String {
        val box = if (t.status == TaskStatus.COMPLETED) "x" else " "
        val bits = buildList {
            add("优先级：${t.priority.label()}")
            t.dueDate?.let { add("截止：${formatDate(it)}") }
            if (t.tags.isNotEmpty()) add("标签：${t.tags.joinToString("、")}")
            t.completedAt?.let { add("完成于：${formatDateTime(it)}") }
        }
        return "- [$box] ${t.title}" + (if (bits.isNotEmpty()) "　（${bits.joinToString("　·　")}）" else "") + "\n"
    }

    fun tasksCsv(tasks: List<TaskEntity>): String = csvTable(
        listOf("标题", "状态", "优先级", "截止日期", "标签", "描述", "创建时间", "完成时间"),
        tasks.map { t ->
            listOf(
                t.title,
                when (t.status) {
                    TaskStatus.ACTIVE -> "进行中"
                    TaskStatus.COMPLETED -> "已完成"
                    TaskStatus.DELETED -> "回收站"
                },
                t.priority.label(),
                t.dueDate?.let { formatDate(it) } ?: "",
                t.tags.joinToString("、"),
                t.description.orEmpty(),
                formatDateTime(t.createdAt),
                t.completedAt?.let { formatDateTime(it) } ?: ""
            )
        }
    )

    // -----------------------------------------------------------------------
    // 目标与打卡
    // -----------------------------------------------------------------------

    fun goalsMarkdown(goals: List<GoalEntity>, records: List<GoalRecordEntity>): String = buildString {
        append("## 目标与打卡（目标 ${goals.size} · 打卡 ${records.size}）\n\n")
        if (goals.isEmpty()) { append(emptyTip("目标")); return@buildString }
        val byGoal = records.groupBy { it.goalId }
        goals.forEach { g ->
            val rs = (byGoal[g.id] ?: emptyList()).sortedByDescending { it.recordDate }
            append("### ${g.name}\n\n")
            append(
                "> ${g.type.label()}　·　${g.status.label()}　·　" +
                    "进度 ${g.currentValue.formatNum()} / ${g.targetValue.formatNum()} ${g.unit}"
            )
            if (g.endDate != null) append("　·　截止：${formatDate(g.endDate)}")
            append("\n\n")
            if (rs.isEmpty()) {
                append(emptyTip("打卡记录"))
            } else {
                rs.forEach { r ->
                    val note = r.note?.takeIf { it.isNotBlank() }?.let { "　备注：$it" }.orEmpty()
                    append("- ${formatDateTime(r.recordDate)}　${r.value.formatNum()} ${g.unit}")
                    if (r.isMakeup) append("　（补签）")
                    append(note).append("\n")
                }
                append("\n")
            }
        }
    }

    /**
     * 目标 + 打卡的**扁平连表** CSV：每条打卡一行，前面带上目标冗余列。
     * 未打卡过的目标也会占一行（记录列留空），避免导出后「目标凭空消失」。
     */
    fun goalsCsv(goals: List<GoalEntity>, records: List<GoalRecordEntity>): String {
        val byGoal = records.groupBy { it.goalId }
        val rows = ArrayList<List<String>>()
        goals.forEach { g ->
            val rs = (byGoal[g.id] ?: emptyList()).sortedByDescending { it.recordDate }
            val head = listOf(
                g.name, g.type.label(), g.status.label(), g.unit,
                g.targetValue.formatNum(), g.currentValue.formatNum()
            )
            if (rs.isEmpty()) {
                rows.add(head + listOf("", "", "", ""))
            } else {
                rs.forEach { r ->
                    rows.add(
                        head + listOf(
                            formatDateTime(r.recordDate),
                            r.value.formatNum(),
                            if (r.isMakeup) "补签" else "正常",
                            r.note.orEmpty()
                        )
                    )
                }
            }
        }
        return csvTable(
            listOf("目标", "类型", "状态", "单位", "目标值", "当前值", "打卡时间", "本次数值", "打卡类型", "备注"),
            rows
        )
    }

    // -----------------------------------------------------------------------
    // CSV 基础设施
    // -----------------------------------------------------------------------

    /** 渲染一张表：表头 + 若干数据行；空表也保留表头（便于用户确认导出成功） */
    fun csvTable(header: List<String>, rows: List<List<String>>): String = buildString {
        append(csvRow(header))
        rows.forEach { append(csvRow(it)) }
    }

    /** 单行 CSV（末尾 \n） */
    fun csvRow(cells: List<String>): String = cells.joinToString(",") { it.csvCell() } + "\n"

    /**
     * CSV 单元格转义：含 `,` `"` `\r` `\n` 或首尾空格时整体加引号，
     * 内部 `"` 写成 `""`（RFC 4180）。否则正文里的逗号会把一行切成两列。
     */
    private fun String.csvCell(): String {
        val needQuote = any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            (isNotEmpty() && (first().isWhitespace() || last().isWhitespace()))
        val body = replace("\"", "\"\"")
        return if (needQuote) "\"$body\"" else body
    }

    /** Markdown 标题里的 `#` 会被当成层级符号，标题内的换行也要压平 */
    private fun escapeMdHeading(s: String): String = s.replace("\n", " ").replace("#", "\\#")

    // -----------------------------------------------------------------------
    // 展示辅助
    // -----------------------------------------------------------------------

    fun Priority.label(): String = when (this) {
        Priority.HIGH -> "高"
        Priority.MEDIUM -> "中"
        Priority.LOW -> "低"
    }

    fun GoalType.label(): String = when (this) {
        GoalType.NUMERIC -> "数值型"
        GoalType.HABIT -> "习惯型"
        GoalType.MILESTONE -> "里程碑"
    }

    fun GoalStatus.label(): String = when (this) {
        GoalStatus.ACTIVE -> "进行中"
        GoalStatus.COMPLETED -> "已完成"
        GoalStatus.PAUSED -> "已暂停"
    }

    /**
     * 数值去掉无意义的小数尾巴（1.0 → "1"，1.25 → "1.25"，3.50 → "3.5"）。
     * 必须写死 [Locale.CHINA]：`"%.2f".format()` 走系统默认 locale，
     * 在部分欧洲 locale 会输出 "1,25"——那是 CSV 的分隔符，一行数据会被硬切成两列。
     * 实现思路：先按两位小数定格式再裁掉多余的 0。
     */
    private fun Double.formatNum(): String {
        if (this % 1.0 == 0.0) return this.toLong().toString()
        return String.format(Locale.CHINA, "%.2f", this).trimEnd('0').trimEnd('.')
    }
}
