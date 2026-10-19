package com.example.personalworkbench.desktop.data

import com.example.personalworkbench.desktop.core.Goal
import com.example.personalworkbench.desktop.core.GoalMilestoneStatus
import com.example.personalworkbench.desktop.core.Milestone
import com.example.personalworkbench.desktop.core.Note
import com.example.personalworkbench.desktop.core.Project
import com.example.personalworkbench.desktop.core.ProjectMilestone
import com.example.personalworkbench.desktop.core.Schedule
import com.example.personalworkbench.desktop.core.Task
import com.example.personalworkbench.desktop.core.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 全部业务数据的 JSON 快照（对应 Android 版 Room 全库） */
@Serializable
data class AppData(
    val tasks: List<Task> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val notes: List<Note> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val projects: List<Project> = emptyList(),
    val projectMilestones: List<ProjectMilestone> = emptyList(),
    val nextId: Long = 1L
)

/** 桌面端本地设置 */
@Serializable
data class DesktopSettings(
    val darkTheme: Boolean = false,
    val accentIndex: Int = 0,
    val scheduleToTaskSync: Boolean = true
)

/**
 * 桌面版数据仓库：JSON 文件持久化 + StateFlow。
 * 文件位置：<用户目录>/.personalworkbench/{data.json, settings.json}
 */
class Store(private val dir: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataFile = File(dir, "data.json")
    private val settingsFile = File(dir, "settings.json")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val writeMutex = Mutex()

    private val _data: MutableStateFlow<AppData>
    val data: StateFlow<AppData>

    private val _settings: MutableStateFlow<DesktopSettings>
    val settings: StateFlow<DesktopSettings>

    init {
        dir.mkdirs()
        _data = MutableStateFlow(loadData())
        data = _data.asStateFlow()
        _settings = MutableStateFlow(loadSettings())
        settings = _settings.asStateFlow()
    }

    // ---------------- 加载 ----------------

    private fun loadData(): AppData =
        if (dataFile.exists()) {
            runCatching { json.decodeFromString<AppData>(dataFile.readText()) }.getOrElse { AppData() }
        } else AppData()

    private fun loadSettings(): DesktopSettings =
        if (settingsFile.exists()) {
            runCatching { json.decodeFromString<DesktopSettings>(settingsFile.readText()) }.getOrElse { DesktopSettings() }
        } else DesktopSettings()

    private fun persistAsync() {
        scope.launch {
            writeMutex.withLock {
                runCatching {
                    dataFile.writeText(json.encodeToString(_data.value))
                    settingsFile.writeText(json.encodeToString(_settings.value))
                }
            }
        }
    }

    /** 立即落盘（退出前调用，确保不丢数据） */
    suspend fun flush() = writeMutex.withLock {
        runCatching {
            dataFile.writeText(json.encodeToString(_data.value))
            settingsFile.writeText(json.encodeToString(_settings.value))
        }
    }

    // ---------------- 设置 ----------------

    fun setDarkTheme(dark: Boolean) { _settings.value = _settings.value.copy(darkTheme = dark); persistAsync() }
    fun setAccent(index: Int) { _settings.value = _settings.value.copy(accentIndex = index); persistAsync() }
    fun setScheduleToTaskSync(enabled: Boolean) { _settings.value = _settings.value.copy(scheduleToTaskSync = enabled); persistAsync() }

    // ---------------- 待办 ----------------

    fun addTask(task: Task): Task {
        val id = _data.value.nextId
        val added = task.copy(id = id, createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(tasks = _data.value.tasks + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun updateTask(task: Task) {
        _data.value = _data.value.copy(tasks = _data.value.tasks.map { if (it.id == task.id) task.copy(updatedAt = now()) else it })
        persistAsync()
    }

    /** 勾选/取消完成；同时把关联日程同步为同一状态（Android 版 SyncBridge 行为） */
    fun toggleTaskComplete(id: Long) {
        val cur = _data.value
        val task = cur.tasks.firstOrNull { it.id == id } ?: return
        val completing = task.status != TaskStatus.COMPLETED
        val updated = task.copy(
            status = if (completing) TaskStatus.COMPLETED else TaskStatus.ACTIVE,
            completedAt = if (completing) now() else null,
            updatedAt = now()
        )
        val schedules = cur.schedules.map { s ->
            if (s.taskId == id) s.copy(isCompleted = completing, updatedAt = now()) else s
        }
        _data.value = cur.copy(tasks = cur.tasks.map { if (it.id == id) updated else it }, schedules = schedules)
        persistAsync()
    }

    fun deleteTask(id: Long) {
        val cur = _data.value
        _data.value = cur.copy(
            tasks = cur.tasks.filter { it.id != id && it.parentId != id },
            schedules = cur.schedules.filter { it.taskId != id }
        )
        persistAsync()
    }

    // ---------------- 日程 ----------------

    fun addSchedule(schedule: Schedule, syncToTask: Boolean = false): Schedule {
        val cur = _data.value
        val id = cur.nextId
        var added = schedule.copy(id = id, createdAt = now(), updatedAt = now())
        var tasks = cur.tasks
        var nextId = id + 1
        // 新建当日一次性日程 → 自动建关联待办（对应 Android 版 SyncBridge.syncTodaySchedulesToTasks）
        val today = now()
        if (syncToTask &&
            _settings.value.scheduleToTaskSync &&
            added.taskId == null &&
            com.example.personalworkbench.desktop.core.RecurrenceUtils.parse(added.repeatRule) == null &&
            dayStartOf(added.startTime) == dayStartOf(today)
        ) {
            val task = Task(
                id = nextId,
                title = added.title,
                dueDate = added.startTime,
                priority = com.example.personalworkbench.desktop.core.Priority.MEDIUM,
                reminderOn = false,
                createdAt = today,
                updatedAt = today
            )
            tasks = tasks + task
            nextId++
            added = added.copy(taskId = task.id)
        }
        _data.value = cur.copy(schedules = cur.schedules + added, tasks = tasks, nextId = nextId)
        persistAsync()
        return added
    }

    fun updateSchedule(schedule: Schedule) {
        _data.value = _data.value.copy(schedules = _data.value.schedules.map { if (it.id == schedule.id) schedule.copy(updatedAt = now()) else it })
        persistAsync()
    }

    fun deleteSchedule(id: Long) {
        val cur = _data.value
        val s = cur.schedules.firstOrNull { it.id == id }
        _data.value = cur.copy(schedules = cur.schedules.filter { it.id != id }, tasks = if (s?.taskId != null) cur.tasks.filter { it.id != s.taskId } else cur.tasks)
        persistAsync()
    }

    /** 勾选/取消完成某日程实例（重复序列把 startTime 记入 doneDates） */
    fun toggleScheduleComplete(id: Long, instanceStart: Long) {
        val cur = _data.value
        val s = cur.schedules.firstOrNull { it.id == id } ?: return
        val rule = com.example.personalworkbench.desktop.core.RecurrenceUtils.parse(s.repeatRule)
        val schedules = cur.schedules.map { item ->
            if (item.id != id) item else {
                if (rule == null) {
                    val completing = !item.isCompleted
                    item.copy(isCompleted = completing, updatedAt = now())
                } else {
                    val done = rule.doneDates.toMutableList()
                    if (instanceStart in done) done.remove(instanceStart) else done.add(instanceStart)
                    item.copy(
                        repeatRule = com.example.personalworkbench.desktop.core.RecurrenceUtils.encode(rule.copy(doneDates = done)),
                        updatedAt = now()
                    )
                }
            }
        }
        // 关联待办同步（仅一次性日程）
        val tasks = if (rule == null && s.taskId != null) {
            val completing = !s.isCompleted
            cur.tasks.map { t ->
                if (t.id == s.taskId) t.copy(
                    status = if (completing) TaskStatus.COMPLETED else TaskStatus.ACTIVE,
                    completedAt = if (completing) now() else null,
                    updatedAt = now()
                ) else t
            }
        } else cur.tasks
        _data.value = cur.copy(schedules = schedules, tasks = tasks)
        persistAsync()
    }

    // ---------------- 笔记 ----------------

    fun addNote(note: Note): Note {
        val id = _data.value.nextId
        val added = note.copy(id = id, createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(notes = _data.value.notes + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun updateNote(note: Note) {
        _data.value = _data.value.copy(notes = _data.value.notes.map { if (it.id == note.id) note.copy(updatedAt = now()) else it })
        persistAsync()
    }

    fun deleteNote(id: Long) {
        _data.value = _data.value.copy(notes = _data.value.notes.filter { it.id != id })
        persistAsync()
    }

    fun toggleNotePinned(id: Long) {
        _data.value = _data.value.copy(notes = _data.value.notes.map { if (it.id == id) it.copy(pinned = !it.pinned, updatedAt = now()) else it })
        persistAsync()
    }

    // ---------------- 目标 / 里程碑 ----------------

    fun addGoal(goal: Goal): Goal {
        val id = _data.value.nextId
        val added = goal.copy(id = id, createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(goals = _data.value.goals + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun updateGoal(goal: Goal) {
        _data.value = _data.value.copy(goals = _data.value.goals.map { if (it.id == goal.id) goal.copy(updatedAt = now()) else it })
        persistAsync()
    }

    fun deleteGoal(id: Long) {
        _data.value = _data.value.copy(
            goals = _data.value.goals.filter { it.id != id },
            milestones = _data.value.milestones.filter { it.goalId != id }
        )
        persistAsync()
    }

    fun addMilestone(m: Milestone): Milestone {
        val id = _data.value.nextId
        val added = m.copy(id = id, createdAt = now())
        _data.value = _data.value.copy(milestones = _data.value.milestones + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun toggleMilestone(id: Long) {
        _data.value = _data.value.copy(milestones = _data.value.milestones.map {
            if (it.id != id) it else it.copy(
                status = if (it.status == GoalMilestoneStatus.COMPLETED) GoalMilestoneStatus.PENDING else GoalMilestoneStatus.COMPLETED,
                actualDate = if (it.status == GoalMilestoneStatus.COMPLETED) null else now()
            )
        })
        persistAsync()
    }

    // ---------------- 工程 ----------------

    fun addProject(p: Project): Project {
        val id = _data.value.nextId
        val added = p.copy(id = id, createdAt = now(), updatedAt = now())
        _data.value = _data.value.copy(projects = _data.value.projects + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun updateProject(p: Project) {
        _data.value = _data.value.copy(projects = _data.value.projects.map { if (it.id == p.id) p.copy(updatedAt = now()) else it })
        persistAsync()
    }

    fun deleteProject(id: Long) {
        _data.value = _data.value.copy(
            projects = _data.value.projects.filter { it.id != id },
            projectMilestones = _data.value.projectMilestones.filter { it.projectId != id }
        )
        persistAsync()
    }

    fun addProjectMilestone(m: ProjectMilestone): ProjectMilestone {
        val id = _data.value.nextId
        val added = m.copy(id = id, createdAt = now())
        _data.value = _data.value.copy(projectMilestones = _data.value.projectMilestones + added, nextId = id + 1)
        persistAsync()
        return added
    }

    fun toggleProjectMilestone(id: Long) {
        _data.value = _data.value.copy(projectMilestones = _data.value.projectMilestones.map {
            if (it.id != id) it else it.copy(
                status = if (it.status == GoalMilestoneStatus.COMPLETED) GoalMilestoneStatus.PENDING else GoalMilestoneStatus.COMPLETED,
                actualDate = if (it.status == GoalMilestoneStatus.COMPLETED) null else now()
            )
        })
        persistAsync()
    }

    /** 工程进度按工期口径自动校准后写回 */
    fun applyProjectProgress(projectId: Long, percent: Int) {
        _data.value = _data.value.copy(projects = _data.value.projects.map {
            if (it.id == projectId) it.copy(progress = percent, updatedAt = now()) else it
        })
        persistAsync()
    }

    // ---------------- 备份 / 恢复 ----------------

    fun exportTo(file: File): Result<Unit> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(_data.value))
    }

    fun importFrom(file: File): Result<Unit> = runCatching {
        val imported = json.decodeFromString<AppData>(file.readText())
        _data.value = imported
        persistAsync()
    }

    fun clearAll() {
        _data.value = AppData()
        persistAsync()
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun dayStartOf(ts: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
