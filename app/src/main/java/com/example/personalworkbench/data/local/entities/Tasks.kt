package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 优先级 (PRD 4.2) 高/中/低，对应视觉色: 高=红 中=橙 低=绿 */
enum class Priority { HIGH, MEDIUM, LOW }

/** 任务状态: ACTIVE 进行中 / COMPLETED 已完成 / DELETED 回收站(软删除) */
enum class TaskStatus { ACTIVE, COMPLETED, DELETED }

/**
 * 待办任务表 (PRD 5.3 tasks)
 * 子任务通过 parentId 关联父任务；删除进入回收站 (status=DELETED, deletedAt 用于 7 天自动清理)。
 */
@Entity(
    tableName = "tasks",
    indices = [Index("parent_id"), Index("status"), Index("due_date"), Index("schedule_id")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.ACTIVE,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "reminder_on") val reminderOn: Boolean = true,
    /**
     * 提醒提前量（分钟，V1.6.0 新增，见 Migrations 8→9）。
     * 语义与日程 reminder_minutes 对齐但按「天」粒度：截止日期是整天，
     * - null = 不提醒（reminderOn 置 false）；
     * - 0 = 截止当天 09:00（默认，兼容旧行为）；
     * - n = 截止当天 09:00 提前 n 分钟（UI 提供 1/2/3 天与 1 周选项）。
     * 旧任务该列为 null 时按 0 处理，保持「截止当天 9 点」原行为。
     */
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int? = null,
    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /**
     * 来源日程 id（V1.4.4 新增，见 Migrations 6→7）。
     * 与 [dueDate]（= 日程实例开始时间）共同定位「重复序列某一天的待办」：
     * 重复日程无法用 schedules.task_id 单列表达（一天一条待办），
     * 改为在待办侧记录来源日程，去重与双向完成同步都按 (schedule_id, due_date) 定位。
     */
    @ColumnInfo(name = "schedule_id") val scheduleId: Long? = null
)
