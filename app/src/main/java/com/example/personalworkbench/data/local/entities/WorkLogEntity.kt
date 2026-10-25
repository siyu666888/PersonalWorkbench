package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 工作日志表 (V1.3.7)：按天记录工作日志，每日期望一条（log_date 唯一）。
 * 周报/月报自动按周期聚合这些日志，形成「工作日志」汇总区块。
 */
@Entity(
    tableName = "work_logs",
    // 每天只应有一条日志：唯一索引在 MIGRATION_3_4 里就建了，但实体长期没声明，
    // 导致「迁移后的库」与「Room 期望的库」不一致——V1.6.5 补声明（见 MIGRATION_9_10）。
    indices = [Index(value = ["log_date"], unique = true)]
)
data class WorkLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "log_date") val date: Long = 0L,
    // 注意：**不要**在这里声明 defaultValue。
    // Room 只比较「实体声明了默认值」的那一侧；一旦声明 DEFAULT ''，
    // 从 v4 起新建的库（当年实体没声明默认值）就全都对不上，升级即崩溃。
    val content: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
