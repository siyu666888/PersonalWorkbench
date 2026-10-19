package com.example.personalworkbench.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.personalworkbench.data.local.dao.EngineeringDao
import com.example.personalworkbench.data.local.dao.FileDao
import com.example.personalworkbench.data.local.dao.GoalDao
import com.example.personalworkbench.data.local.dao.NoteDao
import com.example.personalworkbench.data.local.dao.ScheduleDao
import com.example.personalworkbench.data.local.dao.TagDao
import com.example.personalworkbench.data.local.dao.TaskDao
import com.example.personalworkbench.data.local.dao.WorkLogDao
import com.example.personalworkbench.data.local.entities.DailyReportEntity
import com.example.personalworkbench.data.local.entities.FileEntity
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneEntity
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ResourceEntity
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TagEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.WorkLogEntity

/**
 * 本地 Room 数据库 —— Offline-First 唯一数据源 (PRD 5.2)
 * 升级路径通过 Migration 管理 (PRD 7.1)：每个版本定义明确迁移，未定义方向默认清空重建(开发期)。
 *
 * V1.3.3 起开启 `exportSchema`，schema JSON 落在 `app/schemas/`，
 * 便于用 `Room.databaseBuilder(...).addMigrations(...)` 前先校验迁移正确性，
 * 也让自动生成的 Migration 测试可编写。
 */
@Database(
    entities = [
        TaskEntity::class, NoteEntity::class, ScheduleEntity::class, FileEntity::class,
        TagEntity::class, GoalEntity::class, GoalRecordEntity::class, GoalMilestoneEntity::class,
        ProjectEntity::class, MilestoneEntity::class, ResourceEntity::class,
        IssueEntity::class, DailyReportEntity::class, WorkLogEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun fileDao(): FileDao
    abstract fun tagDao(): TagDao
    abstract fun goalDao(): GoalDao
    abstract fun engineeringDao(): EngineeringDao
    abstract fun workLogDao(): WorkLogDao

    companion object {
        const val DATABASE_NAME = "personal_workbench.db"
    }
}
