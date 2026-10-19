package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日程表 (PRD 5.3 schedules)
 * 有截止日期的待办会联动显示在日历上 (PRD 4.4)，由 UI 层查询 tasks 合并展示。
 */

/** 该日程实例是否已完成：合并「整条完成」与「重复序列中此实例已完成」两种来源 */
fun ScheduleEntity.isDone(): Boolean =
    isCompleted || com.example.personalworkbench.util.RecurrenceUtils
        .parse(repeatRule)?.doneDates?.contains(startTime) == true

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "is_all_day") val isAllDay: Boolean = false,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int? = 0,
    val color: String = "primary", // primary / tertiary / secondary / info / warning / success
    @ColumnInfo(name = "repeat_rule") val repeatRule: String? = null, // 不重复/每天/每周/每月/每年/自定义
    /**
     * 是否已完成（V1.3.3 新增，见 Migrations 1→2）。
     * 对**非重复**日程：勾选即整条完成。
     * 对**重复**日程：该列表示「整个系列已完成」；单个实例的完成状态存放在
     * repeatRule 的 `done=` 键里（见 [RecurrenceUtils.RepeatRule.doneDates]），
     * 两者由 [isDone] 统一合并，UI 不必关心区别。
     */
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    /**
     * 关联待办 id（V1.3.8 新增，见 Migrations 4→5）。
     * 当「日程自动同步待办」开关开启、且日程为一次性时，创建日程会同步建一条待办，
     * 并把该待办 id 记在这里；此后双方任意一侧勾选完成都会同步到另一侧。
     * 重复日程不做关联（无法用单条待办表达整个重复系列）。
     */
    @ColumnInfo(name = "task_id") val taskId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
