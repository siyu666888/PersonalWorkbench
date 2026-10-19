package com.example.personalworkbench.desktop.core

import kotlinx.serialization.Serializable

/**
 * 桌面版数据模型（对应 Android 版 Room 实体，去掉注解，加 kotlinx.serialization）。
 * 字段名与 Android 版保持一致，便于直接移植 RecurrenceUtils / ProjectProgress。
 */

/** 优先级 (PRD 4.2) 高/中/低，对应视觉色: 高=红 中=橙 低=绿 */
@Serializable
enum class Priority { HIGH, MEDIUM, LOW }

/** 任务状态: ACTIVE 进行中 / COMPLETED 已完成 / DELETED 回收站(软删除) */
@Serializable
enum class TaskStatus { ACTIVE, COMPLETED, DELETED }

@Serializable
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.ACTIVE,
    val parentId: Long? = null,
    val reminderOn: Boolean = true,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null
)

@Serializable
data class Schedule(
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val reminderMinutes: Int? = 0,
    val color: String = "primary",
    val repeatRule: String? = null,
    val isCompleted: Boolean = false,
    val taskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 该日程实例是否已完成：合并「整条完成」与「重复序列中此实例已完成」两种来源 */
fun Schedule.isDone(): Boolean =
    isCompleted || RecurrenceUtils.parse(repeatRule)?.doneDates?.contains(startTime) == true

@Serializable
data class Note(
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val preview: String = "",
    val pinned: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 目标类型 (PRD 4.6) */
@Serializable
enum class GoalType { NUMERIC, HABIT, MILESTONE }

@Serializable
enum class GoalPeriod { YEAR, QUARTER, MONTH, CUSTOM }

@Serializable
enum class GoalStatus { ACTIVE, COMPLETED, PAUSED }

@Serializable
enum class GoalMilestoneStatus { PENDING, IN_PROGRESS, COMPLETED }

@Serializable
data class Goal(
    val id: Long = 0,
    val name: String,
    val type: GoalType,
    val period: GoalPeriod = GoalPeriod.CUSTOM,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val targetValue: Double = 0.0,
    val currentValue: Double = 0.0,
    val unit: String = "",
    val remindTime: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val frequency: String? = null,
    val streak: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Serializable
data class Milestone(
    val id: Long = 0,
    val goalId: Long,
    val name: String,
    val planDate: Long? = null,
    val actualDate: Long? = null,
    val status: GoalMilestoneStatus = GoalMilestoneStatus.PENDING,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 工程（简化版：桌面版保留项目/里程碑主链路） */
@Serializable
enum class ProjectStatus { PLANNING, ONGOING, COMPLETED, SUSPENDED }

@Serializable
data class Project(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ONGOING,
    val startDate: Long? = null,
    val planEndDate: Long? = null,
    val actualEndDate: Long? = null,
    val progress: Int = 0,
    val progressAuto: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ProjectMilestone(
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val planDate: Long? = null,
    val actualDate: Long? = null,
    val status: GoalMilestoneStatus = GoalMilestoneStatus.PENDING,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
