package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.ai.AiContextBuilder
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.weekStartMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * AI 智能问答：默认本地版（基于设备内数据做意图识别与结构化应答）；
 * 若用户在设置中填入云端大模型 API Key，则改用 OpenAI 兼容接口（V1.3.8），
 * 调用失败自动回退本地问答。
 */
enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val ts: Long
)

class AiAssistantViewModel(private val container: AppContainer) : ViewModel() {

    private val idGen = AtomicLong(1)
    private fun nextId() = idGen.getAndIncrement()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = nextId(),
                role = ChatRole.ASSISTANT,
                text = "你好，我是你的个人工作台 AI 助手 🤖\n可以问我：今天有什么待办、哪些任务逾期了、本周有哪些日程、我的目标进度、进行中的工程项目等。\n（在「我的 - AI 智能助手」中填入 API Key 可切换为云端免费大模型回答）",
                ts = System.currentTimeMillis()
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    /**
     * 上下文状态（V1.4.1）：展示「本机数据已注入哪些板块、是否开启」，
     * 让用户明确知道云端模型回答是**基于本机数据**的。
     */
    data class ContextState(
        val enabled: Boolean = true,
        val loading: Boolean = false,
        val sections: List<Pair<String, Int>> = emptyList(),
        val charCount: Int = 0,
        val hasApiKey: Boolean = false
    ) {
        val totalItems: Int get() = sections.sumOf { it.second }
        val summary: String
            get() = when {
                !enabled -> "上下文已关闭，模型看不到本机数据"
                loading -> "正在汇总本机数据…"
                sections.isEmpty() -> "本机暂无业务数据"
                else -> "已注入 ${sections.size} 个板块 / $totalItems 条（$charCount 字符）"
            }
    }

    private val _context = MutableStateFlow(ContextState())
    val context: StateFlow<ContextState> = _context.asStateFlow()

    private val contextBuilder = AiContextBuilder(container)

    /** 重新汇总本机数据（进入聊天页 / 用户手动刷新时调用） */
    fun refreshContext() {
        viewModelScope.launch {
            val enabled = container.settingsRepository.aiLocalContext.first()
            val hasKey = container.settingsRepository.aiApiKey.first().isNotBlank()
            _context.value = _context.value.copy(enabled = enabled, hasApiKey = hasKey, loading = enabled)
            if (!enabled) {
                _context.value = _context.value.copy(loading = false, sections = emptyList(), charCount = 0)
                return@launch
            }
            val snapshot = runCatching { contextBuilder.build() }.getOrNull()
            _context.value = ContextState(
                enabled = true,
                loading = false,
                sections = snapshot?.sections ?: emptyList(),
                charCount = snapshot?.text?.length ?: 0,
                hasApiKey = hasKey
            )
        }
    }

    /** 预设快捷问题 (空闲时展示) */
    val suggestions = listOf(
        "今天有什么待办？",
        "哪些任务逾期了？",
        "本周有哪些日程？",
        "我的目标进度怎么样？",
        "进行中的工程项目有哪些？"
    )

    fun send(text: String) {
        val q = text.trim()
        if (q.isBlank()) return
        _messages.value = _messages.value + ChatMessage(nextId(), ChatRole.USER, q, System.currentTimeMillis())
        viewModelScope.launch {
            _typing.value = true
            val config = container.settingsRepository.aiConfig.first()
            val useContext = container.settingsRepository.aiLocalContext.first()
            val reply = if (config.key.isNotBlank()) {
                // 先汇总本机数据，再连同对话一起发给云端模型
                val snapshot = if (useContext) runCatching { contextBuilder.build() }.getOrNull() else null
                _context.value = ContextState(
                    enabled = useContext,
                    loading = false,
                    sections = snapshot?.sections ?: emptyList(),
                    charCount = snapshot?.text?.length ?: 0,
                    hasApiKey = true
                )
                try {
                    chatWithLLM(q, config, snapshot?.text)
                } catch (e: Exception) {
                    "（云端模型调用失败，已回退本地问答：${e.message ?: "网络错误"}）\n\n" + answer(q)
                }
            } else {
                refreshContext()
                answer(q)
            }
            delay(350) // 轻微「思考」停顿, 提升真实感
            _typing.value = false
            _messages.value = _messages.value + ChatMessage(nextId(), ChatRole.ASSISTANT, reply, System.currentTimeMillis())
        }
    }

    /**
     * 调用 OpenAI 兼容 /chat/completions（V1.3.8）。
     * 使用 HttpURLConnection（框架内置，免引入额外依赖），在 IO 线程执行。
     *
     * [localContext] 为本机各板块数据快照（V1.4.1）：拼进 system 消息，
     * 让云端模型也能基于用户真实数据回答，而不是凭空作答。
     */
    private suspend fun chatWithLLM(
        q: String,
        config: com.example.personalworkbench.data.preferences.SettingsRepository.AiConfig,
        localContext: String? = null
    ): String =
        withContext(Dispatchers.IO) {
            val base = config.base.trim().trimEnd('/')
            val url = URL("$base/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${config.key}")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            val systemPrompt = buildString {
                append("你是个人工作台的 AI 助手，帮助用户查询与管理本机的待办、日程、笔记、目标、工程项目、工作日志等信息。请用简体中文、简洁友好地回答。")
                if (!localContext.isNullOrBlank()) {
                    append("\n\n用户已授权你参考以下**本机数据快照**作答，请严格基于其中的内容回答，")
                    append("涉及具体条目时引用快照里的标题/数值；快照中没有的信息要明确说明「本机数据里没有」，不要编造。\n\n")
                    append(localContext)
                } else {
                    append("\n（当前未开启本机数据上下文，只能进行通用问答。）")
                }
            }
            val messagesJson = buildString {
                append("""{"role":"system","content":${JSONObject.quote(systemPrompt)}}""")
                // 只带最近若干轮，避免上下文无限增长
                _messages.value.takeLast(12).forEach { m ->
                    val role = if (m.role == ChatRole.USER) "user" else "assistant"
                    append(""",{"role":"$role","content":${JSONObject.quote(m.text)}}""")
                }
            }
            val body = """{"model":${JSONObject.quote(config.model)},"messages":[$messagesJson],"temperature":0.7,"stream":false}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw RuntimeException("HTTP $code: ${err.take(200)}")
            }
            val resp = conn.inputStream.bufferedReader().readText()
            val choices = JSONObject(resp).optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) throw RuntimeException("空响应")
            choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
        }

    private suspend fun answer(q: String): String {
        val lower = q.lowercase()
        val now = System.currentTimeMillis()
        val dayStart = dayStartMillis(now)
        val dayEnd = dayStart + 86_400_000L
        val weekStart = weekStartMillis(now)
        val weekEnd = weekStart + 7L * 86_400_000L

        val sections = mutableListOf<String>()

        if (lower.contains("逾期") || lower.contains("过期") || lower.contains("拖欠")) {
            sections += sectionOverdue()
        }
        if (lower.contains("今天") || lower.contains("今日")) {
            sections += sectionToday(dayStart, dayEnd)
        }
        if (lower.contains("本周") || lower.contains("这周") || lower.contains("周计划") || lower.contains("未来七天") || lower.contains("七天")) {
            sections += sectionWeek(weekStart, weekEnd)
        }
        if (lower.contains("日程") || lower.contains("安排") || lower.contains("会议") || lower.contains("日历")) {
            if (sections.none { it.startsWith("📅") }) sections += sectionWeek(weekStart, weekEnd)
        }
        if (lower.contains("待办") || lower.contains("任务") || lower.contains("要做") || lower.contains("todo") || lower.contains("to do")) {
            if (sections.none { it.startsWith("✅") }) sections += sectionTasks()
        }
        if (lower.contains("笔记") || lower.contains("记录") || lower.contains("文章") || lower.contains("想法")) {
            sections += sectionNotes()
        }
        if (lower.contains("日志") || lower.contains("周报") || lower.contains("月报") || lower.contains("汇报") || lower.contains("总结")) {
            sections += sectionLogs()
        }
        if (lower.contains("目标") || lower.contains("习惯") || lower.contains("打卡") || lower.contains("goal")) {
            sections += sectionGoals()
        }
        if (lower.contains("项目") || lower.contains("工程") || lower.contains("工地")) {
            sections += sectionProjects()
        }

        // 问候 / 帮助 优先于兜底
        if (lower.contains("你好") || lower.contains("您好") || lower.contains("hi") || lower.contains("hello") || lower.contains("在吗") || lower.contains("你是")) {
            return "你好 👋 我是个人工作台 AI 助手，所有回答都基于你本机的数据（不上传云端）。\n你可以问我：今日待办、逾期任务、本周日程、目标进度或工程项目。"
        }
        if (lower.contains("帮助") || lower.contains("能做什么") || lower.contains("怎么用") || lower.contains("功能") || lower.contains("会什么")) {
            return helpText()
        }

        if (sections.isNotEmpty()) return sections.joinToString("\n\n")

        // 兜底
        return "抱歉，我还不能理解这个问题 🤔\n目前我可以帮你查询本机数据，例如：\n" +
                "• 今天 / 本周有哪些待办与日程\n• 哪些任务已逾期\n• 目标与工程项目的进展\n试试上面的快捷问题，或换个说法～"
    }

    private fun helpText(): String = buildString {
        appendLine("我可以帮你快速掌握本机数据 🤖")
        appendLine("• 「今天有什么待办」→ 今日任务与截止时间")
        appendLine("• 「哪些任务逾期了」→ 逾期清单")
        appendLine("• 「本周有哪些日程」→ 未来 7 天安排")
        appendLine("• 「我的目标进度」→ 各目标完成度")
        appendLine("• 「进行中的工程项目」→ 项目与状态")
        appendLine("\n所有数据均本地存储，离线也能用。")
    }

    private suspend fun sectionToday(dayStart: Long, dayEnd: Long): String {
        val tasks = container.taskRepository.observeToday(dayStart, dayEnd).first()
        val schedules = container.scheduleRepository.observeToday(dayStart, dayEnd).first()
        return buildString {
            appendLine("📌 今日待办（${tasks.size}）")
            if (tasks.isEmpty()) appendLine("• 今天没有待办，安排得很轻松～")
            else tasks.take(6).forEach {
                val time = if (it.dueDate != null) formatHM(it.dueDate) else ""
                appendLine("• ${it.title}${if (time.isNotEmpty()) "（$time）" else ""}")
            }
            if (schedules.isNotEmpty()) {
                appendLine("\n🗓️ 今日日程（${schedules.size}）")
                schedules.take(6).forEach { appendLine("• ${formatHM(it.startTime)} ${it.title}") }
            }
        }.trimEnd()
    }

    private suspend fun sectionOverdue(): String {
        val overdue = container.taskRepository.observeOverdue(System.currentTimeMillis()).first()
        return buildString {
            appendLine("⚠️ 逾期任务（${overdue.size}）")
            if (overdue.isEmpty()) appendLine("• 太棒了，没有逾期任务！")
            else overdue.take(8).forEach { appendLine("• ${it.title}") }
        }.trimEnd()
    }

    private suspend fun sectionWeek(weekStart: Long, weekEnd: Long): String {
        val schedules = container.scheduleRepository.observeInRange(weekStart, weekEnd).first()
            .sortedBy { it.startTime }
        val tasks = container.taskRepository.observeInRange(weekStart, weekEnd).first()
        return buildString {
            appendLine("📅 本周日程（${schedules.size}）")
            if (schedules.isEmpty()) appendLine("• 本周暂无日程安排")
            else schedules.take(8).forEach {
                appendLine("• ${formatMD(it.startTime)} ${formatHM(it.startTime)} ${it.title}")
            }
            if (tasks.isNotEmpty()) appendLine("\n✅ 本周截止任务（${tasks.size}）")
            tasks.take(6).forEach { appendLine("• ${formatMD(it.dueDate ?: 0)} ${it.title}") }
        }.trimEnd()
    }

    private suspend fun sectionTasks(): String {
        val active = container.taskRepository.observeByFilter(com.example.personalworkbench.data.repository.TaskFilter.ACTIVE).first()
        return buildString {
            appendLine("✅ 进行中待办（${active.size}）")
            if (active.isEmpty()) appendLine("• 暂无进行中的任务")
            else active.take(8).forEach { appendLine("• ${it.title}") }
        }.trimEnd()
    }

    private suspend fun sectionNotes(): String {
        val notes = container.noteRepository.observeRecent(3).first()
        return buildString {
            appendLine("📝 最近笔记（${notes.size}）")
            if (notes.isEmpty()) appendLine("• 还没有笔记")
            else notes.forEach { appendLine("• ${it.title.ifBlank { "无标题笔记" }}") }
        }.trimEnd()
    }

    /** 最近 7 天工作日志（V1.4.1：补齐「日志」板块的本地问答能力） */
    private suspend fun sectionLogs(): String {
        val now = System.currentTimeMillis()
        val logs = container.workLogRepository
            .observeRange(dayStartMillis(now - 6L * 86_400_000L), dayStartMillis(now) + 86_400_000L - 1)
            .first()
            .sortedByDescending { it.date }
        return buildString {
            appendLine("📔 最近工作日志（${logs.size}）")
            if (logs.isEmpty()) {
                appendLine("• 近 7 天还没有写过工作日志")
            } else {
                logs.forEach { l ->
                    val firstLine = l.content.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.removePrefix("#")
                        ?.take(40)
                        .orEmpty()
                    appendLine("• ${formatMD(l.date)} ${firstLine.ifBlank { "（无内容）" }}")
                }
            }
        }.trimEnd()
    }

    private suspend fun sectionGoals(): String {
        val goals = container.goalRepository.observeActive().first()
        return buildString {
            appendLine("🎯 进行中的目标（${goals.size}）")
            if (goals.isEmpty()) appendLine("• 暂无进行中的目标")
            else goals.take(6).forEach {
                val pct = container.goalRepository.progressPercent(it)
                appendLine("• ${it.name} — $pct%")
            }
        }.trimEnd()
    }

    private suspend fun sectionProjects(): String {
        val projects = container.engineeringRepository.observeActiveProjects().first()
        return buildString {
            appendLine("🏗️ 进行中的工程项目（${projects.size}）")
            if (projects.isEmpty()) appendLine("• 暂无进行中的项目")
            else projects.take(6).forEach { appendLine("• ${it.name}${if (!it.manager.isNullOrBlank()) "（${it.manager}）" else ""}") }
        }.trimEnd()
    }

    private fun formatHM(ts: Long): String =
        if (ts <= 0) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(ts))
    private fun formatMD(ts: Long): String =
        if (ts <= 0) "" else java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(ts))

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AiAssistantViewModel(container) as T
    }
}
