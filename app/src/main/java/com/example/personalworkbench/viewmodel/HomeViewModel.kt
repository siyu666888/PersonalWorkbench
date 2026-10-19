package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.repository.EngineeringRepository
import com.example.personalworkbench.data.repository.FileRepository
import com.example.personalworkbench.data.repository.GoalRepository
import com.example.personalworkbench.data.repository.NoteRepository
import com.example.personalworkbench.data.repository.ScheduleRepository
import com.example.personalworkbench.data.repository.TaskFilter
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 「最近动态」条目类型 */
enum class ActivityType { TASK, SCHEDULE, NOTE, GOAL }

/**
 * 首页「最近动态」聚合条目。
 * 由待办 / 日程 / 笔记 / 目标四条 **实时** Room 流合并而成，
 * 任一模块新增/修改/完成都会即时反映到首页，无需手动刷新。
 */
data class RecentActivity(
    val id: String,
    val type: ActivityType,
    val refId: Long,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

/**
 * 工作台首页 ViewModel (PRD 4.1): 今日待办概览 + 今日日程 + 最近动态 + 未完成任务数。
 *
 * 实时性设计：
 * - 所有业务数据都直接来自 Room 的 `Flow`，数据库一变就重新发射，UI 自动重组；
 * - [dayTick] 是「今日」区间的种子：除 [refresh]（点击主页 Tab / 页面 resume）外，
 *   还有一个 30 秒心跳在跨天时自动推进，保证「今日待办/今日日程」不会停在昨天；
 * - [recentActivity] 把四类数据合并成一条按时间倒序的动态流。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val taskRepo: TaskRepository = container.taskRepository
    private val scheduleRepo: ScheduleRepository = container.scheduleRepository
    private val noteRepo: NoteRepository = container.noteRepository
    private val goalRepo: GoalRepository = container.goalRepository
    private val fileRepo: FileRepository = container.fileRepository
    private val engineeringRepo: EngineeringRepository = container.engineeringRepository

    /** 触发「今日」相关数据重算的时间戳种子 */
    private val dayTick = MutableStateFlow(System.currentTimeMillis())

    /** 刷新首页（重算今日日期区间，跨天/点击主页 Tab 时调用） */
    fun refresh() { dayTick.value = System.currentTimeMillis() }

    init {
        // 心跳：每 30 秒检查一次是否跨天，跨天则自动切换「今日」区间（不依赖用户手动刷新）
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val now = System.currentTimeMillis()
                if (dayStartMillis(now) != dayStartMillis(dayTick.value)) dayTick.value = now
            }
        }
    }

    val todayTasks: Flow<List<TaskEntity>> =
        dayTick.flatMapLatest { ts -> taskRepo.observeToday(dayStartMillis(ts), dayEndMillis(ts)) }
    val todaySchedules: Flow<List<ScheduleEntity>> =
        dayTick.flatMapLatest { ts -> scheduleRepo.observeToday(dayStartMillis(ts), dayEndMillis(ts)) }
    val overdueTasks: Flow<List<TaskEntity>> = taskRepo.observeOverdue()
    val activeGoals: Flow<List<GoalEntity>> = goalRepo.observeActive()

    /**
     * 最近动态：把「待办 / 日程 / 笔记 / 目标」合并为一条时间倒序流。
     * 全部来自 Room 实时流，任意一处写入都会触发重新发射 → 首页立即刷新。
     */
    val recentActivity: Flow<List<RecentActivity>> = combine(
        taskRepo.observeAllTasks(),
        scheduleRepo.observeAll(),
        noteRepo.observeAll(),
        goalRepo.observeActive()
    ) { tasks, schedules, notes, goals ->
        val now = System.currentTimeMillis()
        val recentWindow = now - 30L * 86_400_000L // 只展示近 30 天内的动态

        val fromTasks = tasks.mapNotNull { t ->
            val done = t.status == TaskStatus.COMPLETED && t.completedAt != null
            val stamp = (t.completedAt ?: t.updatedAt).coerceAtLeast(t.createdAt)
            if (stamp < recentWindow) return@mapNotNull null
            RecentActivity(
                id = "task-${t.id}",
                type = ActivityType.TASK,
                refId = t.id,
                title = if (done) "完成待办「${t.title}」" else "待办「${t.title}」有更新",
                subtitle = "待办 · ${formatDate(stamp)}",
                timestamp = stamp
            )
        }

        val fromSchedules = schedules.mapNotNull { s ->
            val stamp = s.updatedAt.coerceAtLeast(s.createdAt)
            if (stamp < recentWindow) return@mapNotNull null
            RecentActivity(
                id = "schedule-${s.id}",
                type = ActivityType.SCHEDULE,
                refId = s.id,
                title = "日程「${s.title}」",
                subtitle = (if (s.isAllDay) "全天日程 · " else "日程 · ${formatTime(s.startTime)} · ") + formatDate(s.startTime),
                timestamp = stamp
            )
        }

        val fromNotes = notes.mapNotNull { n ->
            val stamp = n.updatedAt.coerceAtLeast(n.createdAt)
            if (stamp < recentWindow) return@mapNotNull null
            RecentActivity(
                id = "note-${n.id}",
                type = ActivityType.NOTE,
                refId = n.id,
                title = n.title.ifBlank { "无标题笔记" },
                subtitle = "最近笔记 · ${formatDate(stamp)}",
                timestamp = stamp
            )
        }

        val fromGoals = goals.mapNotNull { g ->
            val stamp = g.updatedAt.coerceAtLeast(g.createdAt)
            if (stamp < recentWindow) return@mapNotNull null
            val progress = if (g.targetValue > 0) "${g.currentValue.toInt()}/${g.targetValue.toInt()} ${g.unit}" else ""
            RecentActivity(
                id = "goal-${g.id}",
                type = ActivityType.GOAL,
                refId = g.id,
                title = g.name,
                subtitle = if (progress.isBlank()) "目标进度 · ${formatDate(stamp)}" else "目标进度 · $progress",
                timestamp = stamp
            )
        }

        sortRecentActivities(fromTasks + fromSchedules + fromNotes + fromGoals)
            .take(8)
    }

    /**
     * 把聚合后的动态按时间倒序排列（最新动态按日期排序，V1.4.0）。
     * 抽成纯函数便于单测，排序稳定（同时间戳保持原有顺序）。
     */
    companion object {
        internal fun sortRecentActivities(items: List<RecentActivity>): List<RecentActivity> =
            items.sortedByDescending { it.timestamp }
    }

    /** 兼容旧调用：最近编辑的笔记（首页「最近动态」已改为统一动态流，保留给其他页面） */
    val recentNotes: Flow<List<NoteEntity>> = noteRepo.observeRecent(2)

    // ---- 快捷入口数量提示 ----
    val todoCount: Flow<Int> = taskRepo.observeByFilter(TaskFilter.ACTIVE).map { it.size }
    val scheduleCount: Flow<Int> = dayTick.flatMapLatest { ts -> scheduleRepo.observeToday(dayStartMillis(ts), dayEndMillis(ts)) }.map { it.size }
    val noteCount: Flow<Int> = noteRepo.observeAll().map { it.size }
    val goalCount: Flow<Int> = goalRepo.observeActive().map { it.size }
    val fileCount: Flow<Int> = fileRepo.observeAll().map { it.size }
    val projectCount: Flow<Int> = engineeringRepo.observeActiveProjects().map { it.size }

    fun toggleTask(id: Long) {
        viewModelScope.launch { taskRepo.toggleComplete(id) }
    }

    fun createQuickTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepo.create(title = title) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }
}