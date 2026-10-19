package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.repository.SyncBridge
import com.example.personalworkbench.data.repository.TaskFilter
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.dayStartMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 待办 ViewModel (PRD 4.2): 筛选(全部/进行中/已完成/逾期)、搜索、创建、完成、软删除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(private val repo: TaskRepository, private val sync: SyncBridge) : ViewModel() {

    // 默认展示「进行中」（用户需求 V1.6.0）：打开待办即聚焦未完成事项，其余靠筛选 chips 切换
    private val _filter = MutableStateFlow(TaskFilter.ACTIVE)
    val filter = _filter.asStateFlow()

    /** 关键词搜索（V1.5.0）：非空时覆盖筛选，跨全部非回收站任务（含已完成）按标题/描述检索历史待办 */
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /**
     * 高级日期筛选（V1.5.1）：按「到期日 / 创建日 / 完成日」+ 起止范围（单端或区间）过滤，
     * 与关键词 AND 组合，作用于状态筛选/搜索结果之上。
     * 因 [TaskRepository.search] 仅匹配标题/描述且不含日期字段，这里在内存中对结果二次过滤，
     * 数据集为个人工作台量级，无需新增 DAO 查询。
     */
    private val _dateFilter = MutableStateFlow(DateFilter())
    val dateFilter = _dateFilter.asStateFlow()

    val tasks: Flow<List<TaskEntity>> = combine(_filter, _query, _dateFilter) { f, q, d -> Triple(f, q, d) }
        .flatMapLatest { (f, q, d) ->
            val base: Flow<List<TaskEntity>> = if (q.isBlank()) repo.observeByFilter(f) else repo.search("%$q%")
            if (d.isNone()) base else base.map { list -> list.filter { d.matches(it) } }
        }

    /** 父任务 id -> 子任务列表，供列表折叠展示 */
    val subtasksByParent: StateFlow<Map<Long, List<TaskEntity>>> = repo.observeAllTasks()
        .map { list -> list.filter { it.parentId != null }.groupBy { it.parentId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFilter(f: TaskFilter) { _filter.value = f }

    fun setQuery(q: String) { _query.value = q }

    /** 高级日期筛选（V1.5.1）：设置日期字段 + 起止范围；传 DateFilter() 即清除 */
    fun setDateFilter(d: DateFilter) { _dateFilter.value = d }

    fun createTask(
        title: String,
        description: String? = null,
        dueDate: Long? = null,
        priority: Priority = Priority.MEDIUM,
        tags: List<String> = emptyList(),
        reminderMinutes: Int? = null
    ) = viewModelScope.launch {
        // reminderMinutes == null 表示「不提醒」：与旧语义对齐需同时关闭 reminderOn
        val remind = reminderMinutes != null
        repo.create(
            title = title, description = description, dueDate = dueDate, priority = priority, tags = tags,
            reminderOn = remind, reminderMinutes = reminderMinutes
        )
    }

    fun toggle(id: Long) = viewModelScope.launch {
        val task = repo.getById(id)
        val newCompleted = task?.status != TaskStatus.COMPLETED
        repo.toggleComplete(id)
        sync.onTaskToggled(id, newCompleted)
    }

    fun softDelete(id: Long) = viewModelScope.launch { repo.softDelete(id) }

    fun restore(id: Long) = viewModelScope.launch { repo.restore(id) }

    fun permanentlyDelete(id: Long) = viewModelScope.launch { repo.permanentlyDelete(id) }

    fun purgeExpired() = viewModelScope.launch {
        repo.purgeExpired(System.currentTimeMillis() - 7L * 86_400_000L)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodoViewModel(container.taskRepository, container.syncBridge) as T
    }
}

/** 高级日期筛选字段（V1.5.1）：到期日 / 创建日 / 完成日 */
enum class DateField { DUE, CREATED, COMPLETED }

/**
 * 高级日期筛选条件（V1.5.1）：字段 + 起止范围（按天粒度，单端或区间皆可）。
 * - [field] 取任务哪个时间字段比对；
 * - [start]/[end] 为某天 00:00 的毫秒值，闭区间 [start 当天, end 当天 23:59:59.999]；
 * - 两值皆空表示未启用筛选（[isNone]）。
 */
data class DateFilter(
    val field: DateField = DateField.DUE,
    val start: Long? = null,
    val end: Long? = null
) {
    fun isNone() = start == null && end == null

    /** 任务是否满足该日期范围（字段为空值则视为不匹配，如「完成日」筛选下未完成任务不会命中） */
    fun matches(task: TaskEntity): Boolean {
        val v = when (field) {
            DateField.DUE -> task.dueDate
            DateField.CREATED -> task.createdAt
            DateField.COMPLETED -> task.completedAt
        } ?: return false
        val lo = start?.let { dayStartMillis(it) } ?: Long.MIN_VALUE
        val hi = end?.let { dayStartMillis(it) + DAY_MS - 1 } ?: Long.MAX_VALUE
        return v in lo..hi
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
