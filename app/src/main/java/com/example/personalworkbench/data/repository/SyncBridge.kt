package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.local.entities.isDone
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.util.RecurrenceUtils
import kotlinx.coroutines.flow.first

/**
 * 「日程⇄待办」双向状态同步桥（V1.3.8）。
 *
 * 为什么独立成类：ScheduleRepository 与 TaskRepository 互相引用会形成构造环（手动 DI 的
 * `by lazy` 不支持递归）。把跨仓库的同步逻辑集中到本桥，它只持有**已构建完成**的两个
 * Repository 与设置项，因此不存在环。
 *
 * 同步语义（V1.4.4 起）：
 * - 日程↔待办 两条关联链路：
 *   1. 一次性日程：`schedules.task_id` 单列直连（V1.3.8 原有）；
 *   2. 重复日程实例：`tasks.schedule_id + tasks.due_date(=实例开始时间)` 定位（V1.4.4 新增），
 *      重复序列的**每一天**各有一条待办，勾选互相同步。
 * - 「自动同步」开关只控制**自动创建**待办；只要有链接存在，勾选完成始终双向同步。
 */
class SyncBridge(
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val settings: SettingsRepository
) {
    /** 日程侧被勾选/取消后，同步到关联待办 */
    suspend fun onScheduleToggled(schedule: ScheduleEntity, completed: Boolean) {
        // 优先旧链路：一次性日程的 task_id 直连
        schedule.taskId?.let {
            taskRepo.setStatusBySync(it, completed)
            return
        }
        // 兜底链路（V1.4.4）：按来源日程 + 实例时间定位（重复序列的某一天）
        taskRepo.getByScheduleOccurrence(schedule.id, schedule.startTime)
            ?.let { taskRepo.setStatusBySync(it.id, completed) }
    }

    /** 待办侧被勾选/取消后，同步到关联日程 */
    suspend fun onTaskToggled(taskId: Long, completed: Boolean) {
        // 优先旧链路：schedules.task_id 直连
        val linked = scheduleRepo.getByTaskId(taskId)
        if (linked != null) {
            scheduleRepo.setCompleted(linked, completed)
            return
        }
        // 兜底链路（V1.4.4）：待办记录了来源日程，则以该待办的到期日为实例时间勾选
        val task = taskRepo.getById(taskId) ?: return
        val scheduleId = task.scheduleId ?: return
        val master = scheduleRepo.getById(scheduleId) ?: return
        val occurrenceStart = task.dueDate ?: master.startTime
        val duration = (master.endTime - master.startTime).coerceAtLeast(0L)
        scheduleRepo.setCompleted(
            master.copy(startTime = occurrenceStart, endTime = occurrenceStart + duration),
            completed
        )
    }

    /**
     * 反向同步：把「当日」的日程生成关联待办（V1.4.0，自动触发，受开关控制）。
     */
    suspend fun syncTodaySchedulesToTasks(todayStart: Long, todayEnd: Long): Int {
        if (!settings.scheduleToTaskSync.first()) return 0
        return syncRangeToTasks(todayStart, todayEnd)
    }

    /**
     * 手动/自动共用的区间反向同步（V1.4.4 重写）。
     *
     * - **重复日程不再跳过**：按展开后的实例逐条建待办（每天一条，dueDate=实例开始时间），
     *   修复「当日（每天/每周等）日程刷新后不出现在待办」的问题；
     * - **完成状态跟随日程**：日程实例已勾完成（isDone）→ 新建待办直接是已完成，
     *   已有待办但未完成的也会被对齐为完成（修复「完成状态未同步」）；
     * - 去重：一次性日程按 `task_id` 链接；重复实例按 `(schedule_id, due_date)` 查重，可反复调用；
     * - 关联到已进回收站（DELETED）的待办视为不存在，重建新待办。
     *
     * @return 本次新创建的待办数量
     */
    suspend fun syncRangeToTasks(start: Long, end: Long): Int {
        val schedules = scheduleRepo.observeInRange(start, end).first()
        var created = 0
        for (s in schedules) {
            val done = s.isDone()
            val existing = linkedTask(s)
            if (existing != null) {
                // 完成态对齐：日程已勾完成而待办还没跟上（待办侧勾完成会同步写回日程，不会误伤）
                if (done && existing.status != TaskStatus.COMPLETED) {
                    taskRepo.setStatusBySync(existing.id, true)
                }
                continue
            }
            val taskId = taskRepo.create(
                title = s.title, dueDate = s.startTime,
                priority = Priority.MEDIUM, reminderOn = false,
                scheduleId = s.id, completed = done
            )
            // 一次性日程沿用旧链路回填 schedules.task_id；重复序列靠 schedule_id+due_date 定位
            if (!RecurrenceUtils.isRepeating(s)) {
                scheduleRepo.updateTaskLink(s.id, taskId)
            }
            created++
        }
        return created
    }

    /** 找到日程当前关联的、仍在使用中的待办（旧 task_id 链接优先，其次按实例定位） */
    private suspend fun linkedTask(s: ScheduleEntity): TaskEntity? {
        val byLink = s.taskId?.let { taskRepo.getById(it) }?.takeIf { it.status != TaskStatus.DELETED }
        return byLink ?: taskRepo.getByScheduleOccurrence(s.id, s.startTime)
    }
}
