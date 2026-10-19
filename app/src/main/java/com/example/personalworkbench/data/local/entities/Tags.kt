package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 标签所属模块 (PRD 5.3 tags 复用) */
enum class ModuleType { TODO, NOTE, FILE }

/**
 * 标签表 (复用) (PRD 5.3 tags)
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "primary",
    @ColumnInfo(name = "module_type") val moduleType: ModuleType = ModuleType.TODO
)
