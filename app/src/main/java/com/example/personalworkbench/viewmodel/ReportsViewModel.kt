package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.IssueStatus
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.LunarUtils
import com.example.personalworkbench.util.addMonths
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.daysInMonth
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatShortDate
import com.example.personalworkbench.util.formatMonth
import com.example.personalworkbench.util.monthStartMillis
import com.example.personalworkbench.util.weekStartMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

private const val DAY_MS = 86_400_000L

/** 报表周期 (PRD 4.8 周报/月报) */
enum class ReportPeriod(val label: String) { WEEK("周报"), MONTH("月报") }

/** 单个目标进度 */
data class GoalProgress(val name: String, val current: Double, val target: Double, val unit: String) {
    val percent: Float get() = if (target <= 0.0) 0f else (current / target).toFloat().coerceIn(0f, 1f)
}

/** 报表数据（周报/月报统一结构） */
data class ReportData(
    val period: ReportPeriod = ReportPeriod.WEEK,
    val start: Long = 0L,
    val end: Long = 0L,
    val title: String = "",
    val lunarRange: String = "",
    val highlights: List<String> = emptyList(),
    // 待办
    val taskCompleted: Int = 0,
    val taskCreated: Int = 0,
    val taskOverdue: Int = 0,
    val taskActive: Int = 0,
    val completionRate: Float = 0f,
    val prevCompletionRate: Float? = null,
    // 上一周期对比值（V1.5.3 报表下钻/环比箭头）：与本期同维度指标对照
    val prevTaskCompleted: Int = 0,
    val prevScheduleCount: Int = 0,
    val prevNoteCount: Int = 0,
    val dailyTasks: List<Int> = emptyList(),
    val dailyTaskLabels: List<String> = emptyList(),
    // 日程
    val scheduleCount: Int = 0,
    val scheduleAllDay: Int = 0,
    val dailySchedules: List<Int> = emptyList(),
    // 笔记
    val noteNew: Int = 0,
    val noteUpdated: Int = 0,
    // 目标
    val goals: List<GoalProgress> = emptyList(),
    // 工程
    val projectCount: Int = 0,
    val avgProjectProgress: Int = 0,
    val milestoneDone: Int = 0,
    val milestoneTotal: Int = 0,
    val issueOpen: Int = 0,
    val issueDone: Int = 0,
    // 工作日志（V1.3.7）：周期内按天记录的工作日志，自动汇总进报表
    val workLogs: List<WorkLogEntity> = emptyList()
) {
    val hasData: Boolean
        get() = taskCompleted > 0 || taskCreated > 0 || scheduleCount > 0 || noteNew > 0 ||
            goals.isNotEmpty() || projectCount > 0

    /** 最忙的一天（日程最多），无则 null */
    val busiestDay: Pair<String, Int>?
        get() {
            if (dailySchedules.isEmpty()) return null
            val max = dailySchedules.max()
            if (max <= 0) return null
            val idx = dailySchedules.indexOf(max)
            return (dailyTaskLabels.getOrNull(idx) ?: "") to max
        }
}

/**
 * 统计报表 ViewModel (PRD 4.8)：按周/月聚合待办完成、日程分布、笔记、目标、工程进度，
 * 并与上一周期对比完成率；同时给出周期内的农历/节气/节日亮点。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val container: AppContainer) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.WEEK)
    val period: StateFlow<ReportPeriod> = _period

    private val _anchor = MutableStateFlow(dayStartMillis(System.currentTimeMillis()))
    val anchor: StateFlow<Long> = _anchor

    fun setPeriod(p: ReportPeriod) {
        if (_period.value == p) return
        _period.value = p
        // 切换周期时以当前时间为锚点，避免"周锚点取月"造成空档
        _anchor.value = dayStartMillis(System.currentTimeMillis())
    }

    fun prev() {
        _anchor.value = if (_period.value == ReportPeriod.WEEK) _anchor.value - 7 * DAY_MS
        else addMonths(_anchor.value, -1)
    }

    fun next() {
        _anchor.value = if (_period.value == ReportPeriod.WEEK) _anchor.value + 7 * DAY_MS
        else addMonths(_anchor.value, 1)
    }

    fun goCurrent() { _anchor.value = dayStartMillis(System.currentTimeMillis()) }

    val report: StateFlow<ReportData> = combine(_period, _anchor) { p, a -> p to a }
        .flatMapLatest { (period, anchor) ->
            val range = rangeOf(period, anchor)
            val prevRange = rangeOf(period, prevAnchor(period, anchor))

            val tasks = container.taskRepository.observeAllTasks()
            val notes = container.noteRepository.observeAll()
            val goals = container.goalRepository.observeActive()
            val projects = container.engineeringRepository.observeActiveProjects()
            val schedules = container.scheduleRepository.observeInRange(range.first, range.second)
            val prevSchedules = container.scheduleRepository.observeInRange(prevRange.first, prevRange.second)
            val logsFlow = container.workLogRepository.observeRange(range.first, range.second)

            // 工程子表依赖 projects，需 flatMapLatest 展开后再展平
            val milestones = projects.flatMapLatest { list ->
                if (list.isEmpty()) flowOf(emptyList())
                else combine(list.map { container.engineeringRepository.observeMilestones(it.id) }) { it.toList().flatten() }
            }
            val issues = projects.flatMapLatest { list ->
                if (list.isEmpty()) flowOf(emptyList())
                else combine(list.map { container.engineeringRepository.observeIssues(it.id) }) { it.toList().flatten() }
            }

            // kotlinx combine 单次最多 5 个流，这里分三组再合并
            val coreFlow = combine(tasks, notes, goals) { t, n, g -> Triple(t, n, g) }
            val schedFlow = combine(projects, schedules, prevSchedules) { p, s, ps -> Triple(p, s, ps) }
            val engFlow = combine(milestones, issues) { m, i -> m to i }

            combine(coreFlow, schedFlow, engFlow, logsFlow) { core, sched, eng, logs ->
                buildReport(
                    period = period,
                    range = range,
                    prevRange = prevRange,
                    tasks = core.first,
                    notes = core.second,
                    goals = core.third,
                    projects = sched.first,
                    schedules = sched.second,
                    prevSchedules = sched.third,
                    milestones = eng.first,
                    issues = eng.second,
                    workLogs = logs
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportData())

    private fun prevAnchor(period: ReportPeriod, anchor: Long): Long =
        if (period == ReportPeriod.WEEK) anchor - 7 * DAY_MS else addMonths(anchor, -1)

    private fun rangeOf(period: ReportPeriod, anchor: Long): Pair<Long, Long> =
        if (period == ReportPeriod.WEEK) {
            val s = weekStartMillis(anchor)
            s to dayEndMillis(s + 6 * DAY_MS)
        } else {
            val s = monthStartMillis(anchor)
            s to dayEndMillis(s + (daysInMonth(s) - 1) * DAY_MS)
        }

    // ---------- 聚合 ----------

    @Suppress("UNUSED_PARAMETER")
    private fun buildReport(
        period: ReportPeriod,
        range: Pair<Long, Long>,
        prevRange: Pair<Long, Long>,
        tasks: List<TaskEntity>,
        notes: List<NoteEntity>,
        goals: List<GoalEntity>,
        projects: List<ProjectEntity>,
        schedules: List<ScheduleEntity>,
        prevSchedules: List<ScheduleEntity>,
        milestones: List<MilestoneEntity>,
        issues: List<IssueEntity>,
        workLogs: List<WorkLogEntity>
    ): ReportData {
        val (start, end) = range
        val (prevStart, prevEnd) = prevRange

        // ---- 待办 ----
        val completed = tasks.count { it.status == TaskStatus.COMPLETED && it.completedAt != null && it.completedAt in start..end }
        val created = tasks.count { it.createdAt in start..end }
        val involved = completed + created
        val rate = if (involved > 0) completed.toFloat() / involved else 0f

        val prevCompleted = tasks.count { it.status == TaskStatus.COMPLETED && it.completedAt != null && it.completedAt in prevStart..prevEnd }
        val prevCreated = tasks.count { it.createdAt in prevStart..prevEnd }
        val prevInvolved = prevCompleted + prevCreated
        val prevRate = if (prevInvolved > 0) prevCompleted.toFloat() / prevInvolved else null

        val now = System.currentTimeMillis()
        val overdue = tasks.count { it.status == TaskStatus.ACTIVE && it.dueDate != null && it.dueDate < dayStartMillis(now) }
        val activeCount = tasks.count { it.status == TaskStatus.ACTIVE }

        // 每日桶
        val dayCount = ((end - start) / DAY_MS).toInt() + 1
        val dayStarts = (0 until dayCount).map { dayStartMillis(start + it * DAY_MS) }
        val dailyTasks = dayStarts.map { d ->
            val de = dayEndMillis(d)
            tasks.count { it.status == TaskStatus.COMPLETED && it.completedAt != null && it.completedAt in d..de }
        }
        // X 轴标签：柱子密集处放不下完整年月日会重叠（每月最多只画 ~7 个标签且按柱居中），
        // 故此处保留紧凑短格式；年份由本期报告标题带你（见下方 title），不存在跨年歧义。
        val dailyLabels = dayStarts.map { formatShortDate(it) }
        val dailySchedules = dayStarts.map { d ->
            val de = dayEndMillis(d)
            schedules.count { it.startTime in d..de }
        }

        // ---- 目标 ----
        val goalList = goals.filter { it.targetValue > 0.0 }
            .map { GoalProgress(it.name, it.currentValue, it.targetValue, it.unit) }

        // ---- 工程 ----
        val avgProgress = if (projects.isEmpty()) 0 else projects.sumOf { it.progress } / projects.size
        val msDone = milestones.count { it.status == ProjectMilestoneStatus.COMPLETED }
        val issueOpen = issues.count { it.status == IssueStatus.OPEN || it.status == IssueStatus.IN_PROGRESS }
        val issueDone = issues.count { it.status == IssueStatus.RESOLVED || it.status == IssueStatus.CLOSED }

        // ---- 农历/节气/节日亮点 ----
        val highlights = LinkedHashSet<String>()
        var cursor = start
        var guard = 0
        while (cursor <= end && guard++ < 40) {
            val lunar = LunarUtils.of(cursor)
            lunar.festival?.let { highlights += it }
            lunar.solarTerm?.let { highlights += it }
            cursor = dayStartMillis(cursor + DAY_MS)
        }
        val lunarRange = run {
            val a = LunarUtils.of(start)
            val b = LunarUtils.of(end)
            "农历${a.monthDayCn} ~ ${b.monthDayCn}"
        }

        return ReportData(
            period = period,
            start = start,
            end = end,
            title = if (period == ReportPeriod.WEEK) {
                // 跨年周：两端都带年份；同年：年份只出现一次，避免标题过长
                val sameYear = java.util.Calendar.getInstance().apply { timeInMillis = start }
                    .get(java.util.Calendar.YEAR) ==
                    java.util.Calendar.getInstance().apply { timeInMillis = end }
                        .get(java.util.Calendar.YEAR)
                if (sameYear) "${formatDate(start)} - ${formatDate(end, "M月d日")}"
                else "${formatDate(start)} - ${formatDate(end)}"
            } else formatMonth(start),
            lunarRange = lunarRange,
            highlights = highlights.toList(),
            taskCompleted = completed,
            taskCreated = created,
            taskOverdue = overdue,
            taskActive = activeCount,
            completionRate = rate,
            prevCompletionRate = prevRate,
            prevTaskCompleted = prevCompleted,
            prevScheduleCount = prevSchedules.size,
            prevNoteCount = notes.count { it.createdAt in prevStart..prevEnd },
            dailyTasks = dailyTasks,
            dailyTaskLabels = dailyLabels,
            scheduleCount = schedules.size,
            scheduleAllDay = schedules.count { it.isAllDay },
            dailySchedules = dailySchedules,
            noteNew = notes.count { it.createdAt in start..end },
            noteUpdated = notes.count { it.updatedAt in start..end && it.createdAt !in start..end },
            goals = goalList,
            projectCount = projects.size,
            avgProjectProgress = avgProgress,
            milestoneDone = msDone,
            milestoneTotal = milestones.size,
            issueOpen = issueOpen,
            issueDone = issueDone,
            workLogs = workLogs
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportsViewModel(container) as T
    }
}
