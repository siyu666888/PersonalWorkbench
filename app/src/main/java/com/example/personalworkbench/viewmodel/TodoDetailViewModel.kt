package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.repository.SyncBridge
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 待办详情 ViewModel (PRD 4.2): 编辑字段、子任务增删与完成、软删除。
 * 「日程⇄待办」同步（V1.3.8）通过 syncBridge 在勾选完成时反向同步到关联日程。
 */
class TodoDetailViewModel(private val repo: TaskRepository, private val sync: SyncBridge, private val taskId: Long) : ViewModel() {
    private val _task = MutableStateFlow<TaskEntity?>(null)
    val task = _task.asStateFlow()

    val subtasks: Flow<List<TaskEntity>> = repo.observeSubtasks(taskId)

    init { reload() }

    private fun reload() = viewModelScope.launch { _task.value = repo.getById(taskId) }

    fun save(title: String, description: String?, dueDate: Long?, priority: Priority, reminderMinutes: Int?) {
        val cur = _task.value ?: return
        viewModelScope.launch {
            // reminderMinutes == null 表示不提醒；与旧语义对齐需同步 reminderOn
            repo.update(
                cur.copy(
                    title = title, description = description, dueDate = dueDate, priority = priority,
                    reminderOn = reminderMinutes != null, reminderMinutes = reminderMinutes
                )
            )
            reload()
        }
    }

    fun toggleComplete() = viewModelScope.launch {
        val task = repo.getById(taskId)
        val newCompleted = task?.status != TaskStatus.COMPLETED
        repo.toggleComplete(taskId)
        sync.onTaskToggled(taskId, newCompleted)
        reload()
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.create(title = title, parentId = taskId); }
    }

    fun toggleSubtask(id: Long) = viewModelScope.launch { repo.toggleComplete(id) }

    fun delete() = viewModelScope.launch { repo.softDelete(taskId) }

    class Factory(private val container: AppContainer, private val taskId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodoDetailViewModel(container.taskRepository, container.syncBridge, taskId) as T
    }
}
