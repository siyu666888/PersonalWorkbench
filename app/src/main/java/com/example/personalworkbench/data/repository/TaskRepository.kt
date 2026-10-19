package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.TaskDao
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 待办 Repository (PRD 4.2)
 * 业务规则: 子任务全部完成 -> 父任务自动标记完成；删除父任务级联子任务；回收站 7 天自动清理。
 */
class TaskRepository(private val dao: TaskDao) {

    fun observeByFilter(filter: TaskFilter): Flow<List<TaskEntity>> = when (filter) {
        TaskFilter.ALL -> dao.observeActive()
        TaskFilter.ACTIVE -> dao.observeByStatus(TaskStatus.ACTIVE)
        TaskFilter.DONE -> dao.observeByStatus(TaskStatus.COMPLETED)
        TaskFilter.OVERDUE -> observeOverdue()
    }

    fun observeToday(dayStart: Long, dayEnd: Long) = dao.observeToday(dayStart, dayEnd)
    fun observeInRange(start: Long, end: Long) = dao.observeInRange(start, end)
    fun observeOverdue(now: Long = System.currentTimeMillis()) = dao.observeOverdue(dayStartMillis(now))
    fun observeSubtasks(parentId: Long) = dao.observeSubtasks(parentId)
    fun observeAllTasks() = dao.observeAll()
    fun observeRecycleBin() = dao.observeRecycleBin()
    fun search(q: String) = dao.search(q)

    suspend fun getById(id: Long) = dao.getById(id)

    /** 按来源日程 + 实例时间定位待办（重复序列每天一条，V1.4.4） */
    suspend fun getByScheduleOccurrence(scheduleId: Long, dueDate: Long) =
        dao.getByScheduleOccurrence(scheduleId, dueDate)

    suspend fun create(
        title: String,
        description: String? = null,
        dueDate: Long? = null,
        priority: Priority = Priority.MEDIUM,
        tags: List<String> = emptyList(),
        parentId: Long? = null,
        reminderOn: Boolean = true,
        reminderMinutes: Int? = null,
        scheduleId: Long? = null,
        completed: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            TaskEntity(
                title = title, description = description, dueDate = dueDate,
                priority = priority, tags = tags, parentId = parentId,
                reminderOn = reminderOn, reminderMinutes = reminderMinutes,
                createdAt = now, updatedAt = now,
                status = if (completed) TaskStatus.COMPLETED else TaskStatus.ACTIVE,
                completedAt = if (completed) now else null,
                scheduleId = scheduleId
            )
        )
    }

    suspend fun update(task: TaskEntity) = dao.update(task.copy(updatedAt = System.currentTimeMillis()))

    /** 切换完成状态 (带划线动画在 UI 层) */
    suspend fun toggleComplete(id: Long) {
        val task = dao.getById(id) ?: return
        val isDone = task.status == TaskStatus.COMPLETED
        val newStatus = if (isDone) TaskStatus.ACTIVE else TaskStatus.COMPLETED
        dao.update(
            task.copy(
                status = newStatus,
                completedAt = if (isDone) null else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        // 若为子任务，检查父任务是否可自动完成
        task.parentId?.let { pid -> maybeAutoCompleteParent(pid) }
    }

    /**
     * 由「日程↔待办」同步触发：直接把任务置为指定完成状态（V1.3.8）。
     * 与 [toggleComplete] 不同，这里以目标状态为准，且完成时仍触发父任务自动完成检查。
     */
    suspend fun setStatusBySync(id: Long, completed: Boolean) {
        val task = dao.getById(id) ?: return
        if ((task.status == TaskStatus.COMPLETED) == completed) return
        dao.update(
            task.copy(
                status = if (completed) TaskStatus.COMPLETED else TaskStatus.ACTIVE,
                completedAt = if (completed) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
        task.parentId?.let { pid -> maybeAutoCompleteParent(pid) }
    }

    /** 子任务全部完成 -> 父任务自动标记完成 (PRD 4.2) */
    private suspend fun maybeAutoCompleteParent(parentId: Long) {
        val subs = dao.observeSubtasks(parentId).first()
        if (subs.isEmpty()) return
        val allDone = subs.all { it.status == TaskStatus.COMPLETED }
        val parent = dao.getById(parentId) ?: return
        if (allDone && parent.status != TaskStatus.COMPLETED) {
            dao.update(parent.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        } else if (!allDone && parent.status == TaskStatus.COMPLETED) {
            dao.update(parent.copy(status = TaskStatus.ACTIVE, completedAt = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /** 软删除 (进入回收站)，级联子任务 */
    suspend fun softDelete(id: Long) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
        val subs = dao.observeSubtasks(id).first()
        subs.forEach { dao.softDelete(it.id, now) }
    }

    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun permanentlyDelete(id: Long) = dao.permanentlyDelete(id)
    suspend fun purgeExpired(threshold: Long) = dao.purgeExpired(threshold)

    /** 计算今日/逾期数量 (首页徽标) */
    suspend fun countActive(now: Long = System.currentTimeMillis()): Int =
        dao.observeActive().first().count { it.status == TaskStatus.ACTIVE }

    suspend fun countOverdue(now: Long = System.currentTimeMillis()): Int =
        dao.observeOverdue(dayStartMillis(now)).first().size
}

enum class TaskFilter { ALL, ACTIVE, DONE, OVERDUE }
