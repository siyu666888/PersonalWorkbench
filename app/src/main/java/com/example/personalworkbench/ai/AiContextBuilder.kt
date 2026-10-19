package com.example.personalworkbench.ai

import com.example.personalworkbench.data.repository.TaskFilter
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.weekStartMillis
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 本机数据上下文构建器（V1.4.1）
 *
 * 背景：此前调用云端大模型时，system 提示里只有「你是个人工作台助手」这句话，
 * **从未把用户本机的待办/日程/笔记/目标/工程/日志等数据告诉模型**，
 * 于是填了 API Key 之后模型只能凭空回答，与本地版的「查库作答」体验割裂。
 *
 * 本类把各板块数据聚合成一份**结构化、紧凑、可控体量**的文本快照，
 * 随每次提问一起发给模型，使其回答有据可依。
 *
 * 体量控制：
 *  - 每个板块最多 [MAX_PER_SECTION] 条，超出显示「…另有 N 条」；
 *  - 单条文本截断到 [MAX_ITEM_CHARS] 字；
 *  - 整体裁剪到 [MAX_TOTAL_CHARS] 字符，避免挤占对话预算。
 */
class AiContextBuilder(private val container: AppContainer) {

    /** 构建结果：文本快照 + 各板块条目数（供 UI 展示「已注入哪些数据」） */
    data class Snapshot(
        val text: String,
        val sections: List<Pair<String, Int>>
    ) {
        val totalItems: Int get() = sections.sumOf { it.second }
    }

    suspend fun build(now: Long = System.currentTimeMillis()): Snapshot {
        val sections = mutableListOf<Pair<String, String>>()
        val counts = mutableListOf<Pair<String, Int>>()

        suspend fun add(name: String, block: suspend () -> List<String>) {
            val items = runCatching { block() }.getOrElse { emptyList() }
            if (items.isEmpty()) return
            counts += name to items.size
            val body = items.take(MAX_PER_SECTION).joinToString("\n") { "· $it" }
            val more = if (items.size > MAX_PER_SECTION) "\n· …另有 ${items.size - MAX_PER_SECTION} 条" else ""
            sections += name to (body + more)
        }

        val dayStart = dayStartMillis(now)
        val dayEnd = dayEndMillis(now)
        val weekStart = weekStartMillis(now)
        val weekEnd = dayEndMillis(weekStart + 7L * 86_400_000L - 1)

        // ---- 今日待办 ----
        add("今日待办") {
            container.taskRepository.observeToday(dayStart, dayEnd).first()
                .sortedBy { it.dueDate ?: Long.MAX_VALUE }
                .map { t ->
                    buildString {
                        append(t.title.clip(48))
                        t.dueDate?.let { append("（${hm(it)}）") }
                        append("［${prio(t.priority.name)}］")
                        if (t.tags.isNotEmpty()) append(" #${t.tags.joinToString("#")}")
                    }
                }
        }

        // ---- 逾期任务 ----
        add("逾期任务") {
            container.taskRepository.observeOverdue(now).first()
                .map { "${t2(it.title.clip(48))}（原定 ${md(it.dueDate ?: 0)}，已逾期 ${daysOverdue(it.dueDate ?: now, now)} 天）" }
        }

        // ---- 本周日程 ----
        add("本周日程") {
            container.scheduleRepository.observeInRange(weekStart, weekEnd).first()
                .sortedBy { it.startTime }
                .map { s ->
                    buildString {
                        append(md(s.startTime)).append(' ').append(hm(s.startTime)).append(' ').append(s.title.clip(48))
                        if (s.isAllDay) append("［全天］")
                        com.example.personalworkbench.util.RecurrenceUtils
                            .describe(com.example.personalworkbench.util.RecurrenceUtils.parse(s.repeatRule))
                            ?.let { append("（$it）") }
                        if (s.isCompleted) append("［已完成］")
                    }
                }
        }

        // ---- 进行中待办概览（非今日） ----
        add("进行中待办") {
            container.taskRepository.observeByFilter(TaskFilter.ACTIVE).first()
                .filter { it.parentId == null }
                .sortedBy { it.priority.ordinal }
                .take(MAX_PER_SECTION * 2)
                .map { t ->
                    buildString {
                        append(t.title.clip(48)).append("［${prio(t.priority.name)}］")
                        t.dueDate?.let { append(" 截止 ${md(it)}") }
                    }
                }
        }

        // ---- 最近笔记 ----
        add("最近笔记") {
            container.noteRepository.observeRecent(6).first()
                .map { n -> "${n.title.ifBlank { "无标题" }.clip(40)}：${n.preview.clip(60)}" }
        }

        // ---- 进行中目标 ----
        add("进行中目标") {
            container.goalRepository.observeActive().first()
                .map { g ->
                    buildString {
                        append(g.name.clip(40))
                        append(" ${container.goalRepository.progressPercent(g)}%")
                        if (g.type == com.example.personalworkbench.data.local.entities.GoalType.HABIT && g.streak > 0) {
                            append("（连续 ${g.streak} 天）")
                        }
                        if (g.targetValue > 0) append(" ${fmtNum(g.currentValue)}/${fmtNum(g.targetValue)}${g.unit}")
                    }
                }
        }

        // ---- 工程项目 ----
        add("工程项目") {
            container.engineeringRepository.observeActiveProjects().first()
                .map { p ->
                    buildString {
                        append(p.name.clip(40))
                        if (!p.manager.isNullOrBlank()) append("（负责人 ${p.manager}）")
                        append(" 进度 ${p.progress}%")
                        p.planEndDate?.let { append(" 计划竣工 ${md(it)}") }
                    }
                }
        }

        // ---- 最近工作日志（近 7 天） ----
        add("最近工作日志") {
            container.workLogRepository.observeRange(dayStartMillis(now - 6L * 86_400_000L), dayEnd).first()
                .map { l -> "${md(l.date)}：${stripMd(l.content).clip(120)}" }
        }

        // ---- 文件概况 ----
        runCatching {
            val files = container.fileRepository.observeAll().first()
            if (files.isNotEmpty()) {
                counts += "文件" to files.size
                val byCat = files.groupingBy { it.category.name }.eachCount()
                sections += "文件" to (byCat.entries.joinToString("；") { "${catCn(it.key)} ${it.value} 个" } + "。")
            }
        }

        // ---- 组装 ----
        if (sections.isEmpty()) {
            return Snapshot("（本机暂无任何业务数据）", emptyList())
        }

        val header = buildString {
            append("【个人工作台 · 本机数据快照】\n")
            append("统计时间：${fullDate(now)}　覆盖板块：")
            append(counts.joinToString("、") { "${it.first}${it.second}" })
            append("\n以下内容为用户本机数据，可据其回答；数据缺失时明确说明，不要编造。\n")
        }

        val body = sections.joinToString("\n\n") { (name, text) -> "【${name}】\n$text" }
        val full = header + "\n" + body
        val trimmed = if (full.length <= MAX_TOTAL_CHARS) full
        else full.take(MAX_TOTAL_CHARS) + "\n…（上下文过长已截断）"

        return Snapshot(trimmed, counts)
    }

    // ---------------- 工具 ----------------

    private fun String.clip(n: Int): String = if (length <= n) this else take(n) + "…"

    /** 去掉 Markdown 标记，让日志摘要更干净地进入上下文 */
    private fun stripMd(s: String): String = s
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("\\*\\*|__|`"), "")
        .replace(Regex("(?m)^>\\s?"), "")
        .replace(Regex("\\n{2,}"), " / ")

    private fun prio(p: String) = when (p) { "HIGH" -> "高"; "MEDIUM" -> "中"; "LOW" -> "低"; else -> p }
    private fun catCn(c: String) = when (c) {
        "IMAGE" -> "图片"; "DOC" -> "文档"; "PDF" -> "PDF"; "AUDIO" -> "音频"; else -> "其他"
    }

    private fun fmtNum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.CHINA, "%.1f", v)

    private fun daysOverdue(due: Long, now: Long): Long = ((now - due) / 86_400_000L).coerceAtLeast(0)

    private fun hm(ts: Long): String =
        if (ts <= 0) "" else SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
    private fun md(ts: Long): String =
        if (ts <= 0) "" else SimpleDateFormat("M/d", Locale.CHINA).format(Date(ts))
    private fun fullDate(ts: Long): String =
        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(ts))

    private fun t2(s: String) = s

    companion object {
        const val MAX_PER_SECTION = 10
        const val MAX_ITEM_CHARS = 120
        const val MAX_TOTAL_CHARS = 6000
    }
}
