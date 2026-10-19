package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.ScheduleDao
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.util.RecurrenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * 日程 Repository (PRD 4.4)
 * - 待办联动: 带截止日期的任务由 UI 层查询 TaskRepository 合并到日历展示。
 * - 周期日程: repeatRule 存于 schedules.repeat_rule（kv 编码），此处负责按区间展开为实例，
 *   上层（首页/日历/日报/AI 助手）无需感知重复逻辑。
 */
class ScheduleRepository(private val dao: ScheduleDao) {

    /** 区间内日程（一次性 + 重复序列展开），按开始时间排序 */
    fun observeInRange(start: Long, end: Long): Flow<List<ScheduleEntity>> =
        combine(
            dao.observeNonRepeatingInRange(start, end),
            dao.observeRecurringUpTo(end)
        ) { oneTimed, recurring ->
            (oneTimed + RecurrenceUtils.expandAll(recurring, start, end)).sortedBy { it.startTime }
        }.flowOn(Dispatchers.Default)

    /** 当日日程 */
    fun observeToday(dayStart: Long, dayEnd: Long) = observeInRange(dayStart, dayEnd)

    fun observeAll() = dao.observeAll()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun getByTaskId(taskId: Long) = dao.getByTaskId(taskId)

    /** 创建日程后回填关联待办 id（V1.3.8） */
    suspend fun updateTaskLink(id: Long, taskId: Long) =
        dao.updateTaskLink(id, taskId, System.currentTimeMillis())

    suspend fun create(
        title: String,
        startTime: Long,
        endTime: Long,
        isAllDay: Boolean = false,
        reminderMinutes: Int? = 0,
        color: String = "primary",
        repeatRule: String? = null
    ): Long = dao.insert(
        ScheduleEntity(
            title = title, startTime = startTime, endTime = endTime,
            isAllDay = isAllDay, reminderMinutes = reminderMinutes,
            color = color, repeatRule = repeatRule
        )
    )

    /** 整条更新（含周期规则；编辑整个重复序列） */
    suspend fun update(schedule: ScheduleEntity) = dao.update(schedule.copy(updatedAt = System.currentTimeMillis()))

    /**
     * 勾选 / 取消勾选「已完成」。
     *
     * - 非重复日程：直接写 `is_completed` 列。
     * - 重复日程：改的是 `repeat_rule` 里的 `done=` 列表（按实例开始时间区分），
     *   因此勾选某一天不会影响序列里的其他天。
     *
     * 传入的 [schedule] 应为**展开后的实例**（其 startTime 即该次实例的时间）。
     */
    suspend fun setCompleted(schedule: ScheduleEntity, completed: Boolean) {
        val now = System.currentTimeMillis()
        val rule = RecurrenceUtils.parse(schedule.repeatRule)
        if (rule != null && rule.isRepeating) {
            val master = dao.getById(schedule.id) ?: return
            val current = RecurrenceUtils.parse(master.repeatRule) ?: rule
            val next = if (completed) {
                if (schedule.startTime in current.doneDates) return
                current.copy(doneDates = (current.doneDates + schedule.startTime).sorted())
            } else {
                current.copy(doneDates = current.doneDates - schedule.startTime)
            }
            dao.update(master.copy(repeatRule = RecurrenceUtils.encode(next), updatedAt = now))
        } else {
            if (schedule.isCompleted == completed) return
            dao.updateCompleted(schedule.id, completed, now)
        }
    }

    /** 删除整条日程 / 整个重复序列 */
    suspend fun delete(id: Long) = dao.getById(id)?.let { dao.delete(it) }

    /** 仅删除重复序列中的某一次（写入 exdates 排除该实例；必须基于库中主记录，避免相位被实例时间覆盖） */
    suspend fun skipOccurrence(series: ScheduleEntity, occurrenceStart: Long) {
        val master = dao.getById(series.id) ?: return
        val rule = RecurrenceUtils.parse(master.repeatRule) ?: return
        if (occurrenceStart in rule.exdates) return
        dao.update(
            master.copy(
                repeatRule = RecurrenceUtils.encode(rule.copy(exdates = rule.exdates + occurrenceStart)),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
