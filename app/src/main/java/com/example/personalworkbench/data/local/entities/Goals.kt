package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 目标类型 (PRD 4.6) */
enum class GoalType { NUMERIC, HABIT, MILESTONE }

/** 目标周期类型 */
enum class GoalPeriod { YEAR, QUARTER, MONTH, CUSTOM }

/** 目标状态: ACTIVE 进行中 / COMPLETED 已完成(归档成就墙) / PAUSED 暂停 */
enum class GoalStatus { ACTIVE, COMPLETED, PAUSED }

/** 子里程碑状态 (goal_milestones) */
enum class GoalMilestoneStatus { PENDING, IN_PROGRESS, COMPLETED }

/**
 * 目标表 (PRD 5.3 goals)
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val period: GoalPeriod = GoalPeriod.CUSTOM,
    @ColumnInfo(name = "start_date") val startDate: Long? = null,
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    @ColumnInfo(name = "target_value") val targetValue: Double = 0.0,
    @ColumnInfo(name = "current_value") val currentValue: Double = 0.0,
    val unit: String = "",
    @ColumnInfo(name = "remind_time") val remindTime: String? = null, // HH:mm
    val status: GoalStatus = GoalStatus.ACTIVE,
    @ColumnInfo(name = "frequency") val frequency: String? = null, // 习惯型频率: 每天/每周N次/每月N次
    @ColumnInfo(name = "streak") val streak: Int = 0, // 习惯型连续天数
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "image_path") val imagePath: String? = null // 自定义封面/配图（应用私有目录绝对路径）
)

/**
 * 目标记录表 (PRD 5.3 goal_records) — 数值型每次记录 / 习惯型每次打卡
 */
@Entity(
    tableName = "goal_records",
    indices = [Index("goal_id")]
)
data class GoalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "record_date") val recordDate: Long = System.currentTimeMillis(),
    val value: Double = 1.0,
    val note: String? = null,
    @ColumnInfo(name = "is_makeup") val isMakeup: Boolean = false, // 补签标记
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * 子里程碑子表 (PRD 4.6) — 独立表，不使用 JSON
 */
@Entity(
    tableName = "goal_milestones",
    indices = [Index("goal_id")]
)
data class GoalMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val name: String,
    @ColumnInfo(name = "plan_date") val planDate: Long? = null,
    @ColumnInfo(name = "actual_date") val actualDate: Long? = null,
    val status: GoalMilestoneStatus = GoalMilestoneStatus.PENDING,
    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
