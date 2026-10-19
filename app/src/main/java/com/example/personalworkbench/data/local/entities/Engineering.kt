package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 工程管理模块数据实体 (PRD 4.7 / 5.3) —— V1.5 (P1)
 * 数据层完整定义，UI 按需迭代。
 */

enum class ProjectStatus { PLANNING, ONGOING, COMPLETED, SUSPENDED }
enum class ProjectMilestoneStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, DELAYED }
enum class ResourceCategory { PERSON, MATERIAL, EQUIPMENT }
enum class IssueSeverity { LOW, MEDIUM, HIGH, CRITICAL }
enum class IssueStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String? = null,
    val client: String? = null,        // 甲方
    val contractor: String? = null,    // 总包方
    @ColumnInfo(name = "start_date") val startDate: Long? = null,
    @ColumnInfo(name = "plan_end_date") val planEndDate: Long? = null,
    @ColumnInfo(name = "actual_end_date") val actualEndDate: Long? = null,
    val manager: String? = null,       // 项目经理
    val status: ProjectStatus = ProjectStatus.PLANNING,
    val progress: Int = 0,             // 整体完成度 0-100
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "milestones",
    indices = [Index("project_id")]
)
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val name: String,
    @ColumnInfo(name = "plan_date") val planDate: Long? = null,
    @ColumnInfo(name = "actual_date") val actualDate: Long? = null,
    val responsible: String? = null,
    val dependencies: List<String> = emptyList(), // 前置依赖 JSON
    val status: ProjectMilestoneStatus = ProjectMilestoneStatus.NOT_STARTED,
    val weight: Int = 1,               // 权重，用于计算项目整体进度
    val note: String? = null
)

@Entity(
    tableName = "resources",
    indices = [Index("project_id")]
)
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val category: ResourceCategory,
    val name: String,
    val spec: String? = null,         // 规格/工种
    val quantity: Double = 0.0,
    val unit: String? = null,
    val status: String? = null,
    val supplier: String? = null,      // 材料供应商
    val contact: String? = null,       // 人员联系方式
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "issues",
    indices = [Index("project_id")]
)
data class IssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "milestone_id") val milestoneId: Long? = null,
    val title: String,
    val description: String? = null,
    val images: List<String> = emptyList(), // 图片路径 JSON
    val location: String? = null,      // GPS 坐标
    val reporter: String? = null,
    val assignee: String? = null,
    val severity: IssueSeverity = IssueSeverity.MEDIUM,
    val status: IssueStatus = IssueStatus.OPEN,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null
)

@Entity(
    tableName = "daily_reports",
    indices = [Index("project_id")]
)
data class DailyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "report_date") val reportDate: Long = System.currentTimeMillis(),
    val weather: String? = null,
    val attendance: Int = 0,
    @ColumnInfo(name = "equipment_records") val equipmentRecords: String? = null, // 机械台班 JSON
    @ColumnInfo(name = "material_records") val materialRecords: String? = null,   // 材料进场 JSON
    @ColumnInfo(name = "safety_records") val safetyRecords: String? = null,       // 安全巡查 JSON
    val progress: String? = null,
    @ColumnInfo(name = "plan_tomorrow") val planTomorrow: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
