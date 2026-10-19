package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.data.repository.ScheduleRepository
import com.example.personalworkbench.data.repository.SyncBridge
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.addMonths
import com.example.personalworkbench.util.monthGrid
import com.example.personalworkbench.util.RecurrenceUtils
import com.example.personalworkbench.util.weekStartMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class CalendarViewMode { MONTH, WEEK, DAY, YEAR }

private const val DAY_MS = 86_400_000L

/**
 * 日程 ViewModel (PRD 4.4): 月/周/日/年视图切换，日程与待办联动展示。
 * 周期日程由 ScheduleRepository 展开，本层无需感知重复逻辑。
 * 「日程⇄待办」双向同步（V1.3.8）通过 syncBridge 在此处编排。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val settings: SettingsRepository,
    private val sync: SyncBridge
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(dayStartMillis(System.currentTimeMillis()))
    val selectedDate = _selectedDate.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode = _viewMode.asStateFlow()

    /**
     * 手动同步结果反馈（G-1 刷新按钮）。
     * 用带唯一 id 的数据类做 key，保证连续两次同步即便数量相同也会重新触发 UI 提示。
     */
    data class SyncFeedback(val count: Int, val id: Long = System.nanoTime())
    private val _syncFeedback = MutableStateFlow<SyncFeedback?>(null)
    val syncFeedback = _syncFeedback.asStateFlow()

    fun selectDate(d: Long) { _selectedDate.value = dayStartMillis(d) }
    fun setViewMode(m: CalendarViewMode) { _viewMode.value = m }
    fun goToday() { _selectedDate.value = dayStartMillis(System.currentTimeMillis()) }

    /** 上一周期（随视图粒度变化：年/月/周/日） */
    fun prev() {
        _selectedDate.value = when (_viewMode.value) {
            CalendarViewMode.YEAR -> addMonths(_selectedDate.value, -12)
            CalendarViewMode.MONTH -> addMonths(_selectedDate.value, -1)
            CalendarViewMode.WEEK -> _selectedDate.value - 7 * DAY_MS
            CalendarViewMode.DAY -> _selectedDate.value - DAY_MS
        }
    }

    /** 下一周期 */
    fun next() {
        _selectedDate.value = when (_viewMode.value) {
            CalendarViewMode.YEAR -> addMonths(_selectedDate.value, 12)
            CalendarViewMode.MONTH -> addMonths(_selectedDate.value, 1)
            CalendarViewMode.WEEK -> _selectedDate.value + 7 * DAY_MS
            CalendarViewMode.DAY -> _selectedDate.value + DAY_MS
        }
    }

    /** 当日日程（含重复实例） */
    val daySchedules: Flow<List<ScheduleEntity>> = _selectedDate.flatMapLatest { d ->
        scheduleRepo.observeToday(dayStartMillis(d), dayEndMillis(d))
    }

    /** 选中日期所在周（周一为首日）的日程 (PRD 4.4 周视图) */
    val weekSchedules: Flow<List<ScheduleEntity>> = _selectedDate.flatMapLatest { d ->
        val ws = weekStartMillis(d)
        scheduleRepo.observeInRange(ws, ws + 7L * 86_400_000L)
    }

    /** 月视图 6x7 网格内每天的日程数量（日期格打点），key = 当日 00:00 毫秒 */
    val monthScheduleDots: Flow<Map<Long, Int>> = _selectedDate.flatMapLatest { d ->
        val grid = monthGrid(d)
        val start = grid.first()
        val end = dayEndMillis(grid.last())
        scheduleRepo.observeInRange(start, end).map { list ->
            list.groupingBy { dayStartMillis(it.startTime) }.eachCount()
        }
    }

    /** 年视图：所选年份每一天（00:00）的日程数量打点，key = 当日 00:00 毫秒 */
    val yearScheduleDots: Flow<Map<Long, Int>> = _selectedDate.flatMapLatest { d ->
        val c = java.util.Calendar.getInstance().apply { timeInMillis = dayStartMillis(d) }
        val year = c.get(java.util.Calendar.YEAR)
        val start = java.util.Calendar.getInstance().apply {
            set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = java.util.Calendar.getInstance().apply {
            set(year, java.util.Calendar.DECEMBER, 31, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis + 86_399_000L
        scheduleRepo.observeInRange(start, end).map { list ->
            list.groupingBy { dayStartMillis(it.startTime) }.eachCount()
        }
    }

    fun createSchedule(
        title: String,
        startTime: Long,
        endTime: Long,
        allDay: Boolean,
        color: String = "primary",
        repeatRule: String? = null
    ) = viewModelScope.launch {
        scheduleRepo.create(title, startTime, endTime, allDay, 0, color, repeatRule)
        // 自动建待办（V1.4.4 统一走 SyncBridge）：开关开启且为当日日程时，
        // 把当天全部未同步日程（含重复序列的当日实例）补建为待办：
        // 一次性回填 task_id，重复实例按 (schedule_id, due_date) 去重，完成状态跟随日程。
        val isToday = dayStartMillis(startTime) == dayStartMillis(System.currentTimeMillis())
        if (isToday && settings.scheduleToTaskSync.first()) {
            sync.syncRangeToTasks(dayStartMillis(startTime), dayEndMillis(startTime))
        }
    }

    /** 编辑整条日程（含周期规则）；重复序列保留原起始日期（相位），只应用新的时间点 */
    fun updateSchedule(s: ScheduleEntity) = viewModelScope.launch {
        val master = scheduleRepo.getById(s.id)
        val target = if (master != null && RecurrenceUtils.isRepeating(master)) {
            val newStart = RecurrenceUtils.rebaseToSeriesDate(master.startTime, s.startTime, s.isAllDay)
            val duration = if (s.isAllDay) DAY_MS - 1000L else (s.endTime - s.startTime).coerceAtLeast(0L)
            master.copy(
                title = s.title, isAllDay = s.isAllDay, color = s.color, repeatRule = s.repeatRule,
                startTime = newStart, endTime = newStart + duration
            )
        } else s
        scheduleRepo.update(target)
        // 编辑后补齐当日的关联待办（V1.4.4 统一走 SyncBridge，含重复实例与完成态对齐）
        val isToday = dayStartMillis(target.startTime) == dayStartMillis(System.currentTimeMillis())
        if (isToday && settings.scheduleToTaskSync.first()) {
            sync.syncRangeToTasks(dayStartMillis(target.startTime), dayEndMillis(target.startTime))
        }
    }

    /**
     * 把「当日」日程反向同步成待办（V1.4.0）。
     * 仅当当前查看的日期就是「今天」才执行，天然满足「仅同步当日日程到待办」；
     * 重复日程 / 已关联待办的日程会被 [SyncBridge.syncTodaySchedulesToTasks] 内部跳过。
     * 可在进入日视图、或 App 启动时调用，用于把已有的当日日程补建到待办列表。
     */
    fun syncTodayToTasks() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (dayStartMillis(_selectedDate.value) != dayStartMillis(now)) return@launch
        sync.syncTodaySchedulesToTasks(dayStartMillis(now), dayEndMillis(now))
    }

    /**
     * 手动把日程反向同步成待办（G-1 刷新按钮）。
     * 同步范围**只限「今天 → 本周日」**（V1.4.5）：无论当前处于月/年/周/日哪个视图，
     * 都只把本周内、且从今天起尚未过期的日程补建为待办，避免月视图刷新时把整月
     * （含重复日程每天一条）一次性铺满待办列表。
     */
    fun syncVisibleToTasks() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val syncStart = dayStartMillis(now)
        val syncEnd = weekStartMillis(now) + 7L * DAY_MS - 1L
        _syncFeedback.value = SyncFeedback(sync.syncRangeToTasks(syncStart, syncEnd))
    }

    /** 删除整条日程 / 整个重复序列（同时清理其关联待办，V1.3.8） */
    fun delete(id: Long) = viewModelScope.launch {
        val s = scheduleRepo.getById(id)
        scheduleRepo.delete(id)
        s?.taskId?.let { taskRepo.softDelete(it) }
    }

    /** 仅删除重复序列中的某一次 */
    fun skipOccurrence(series: ScheduleEntity, occurrenceStart: Long) =
        viewModelScope.launch { scheduleRepo.skipOccurrence(series, occurrenceStart) }

    /**
     * 勾选 / 取消勾选日程完成。
     * 传入展开后的实例：非重复日程写 is_completed 列，重复日程写该实例的 done 标记。
     * 完成后经 syncBridge 把状态同步到关联待办（V1.3.8）。
     */
    fun setScheduleCompleted(instance: ScheduleEntity, completed: Boolean) = viewModelScope.launch {
        scheduleRepo.setCompleted(instance, completed)
        sync.onScheduleToggled(instance, completed)
    }

    fun toggleTask(id: Long) = viewModelScope.launch {
        val task = taskRepo.getById(id)
        val newCompleted = task?.status != TaskStatus.COMPLETED
        taskRepo.toggleComplete(id)
        sync.onTaskToggled(id, newCompleted)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CalendarViewModel(
                container.scheduleRepository,
                container.taskRepository,
                container.settingsRepository,
                container.syncBridge
            ) as T
    }
}
