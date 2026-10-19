package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 文件分类 (UI规范 8.2) */
enum class FileCategory { IMAGE, DOC, PDF, AUDIO, OTHER }

/**
 * 文件关联的业务对象类型（V1.5.6 模块七：文件 ⇄ 项目/日志 数据联动）。
 * 存库时用 name 小写字符串，见 FileEntity.relatedType。
 */
enum class FileRelation(val key: String, val label: String) {
    PROJECT("project", "项目"), LOG("log", "日志")
}

/**
 * 文件记录表 (PRD 5.3 files)
 * 文件实际存储于 APP 私有目录；数据库仅保存路径/名称/大小/类型/标签。
 * ⚠️ 卸载 APP 将丢失全部文件 (PRD 4.5)。
 */
@Entity(
    tableName = "files",
    indices = [Index("category")]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val size: Long,
    val category: FileCategory = FileCategory.OTHER,
    val tags: List<String> = emptyList(),
    /** 关联类型（project/log），null 表示未关联 */
    @ColumnInfo(name = "related_type") val relatedType: String? = null,
    /** 关联对象 id（配合 relatedType 定位项目/日志） */
    @ColumnInfo(name = "related_id") val relatedId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
