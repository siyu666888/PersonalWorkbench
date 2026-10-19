package com.example.personalworkbench.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 笔记表 (PRD 5.3 notes)
 * content 存储 HTML/Markdown 正文；preview 取正文前 80 字摘要；
 * 图片以相对路径存储于 app 私有目录 /files/notes/images/ (PRD 4.2 图片存储机制)。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val preview: String = "",
    /** 置顶：列表中置顶笔记排在最前并高亮 (V1.3.5) */
    @ColumnInfo(name = "pinned", defaultValue = "0") val pinned: Boolean = false,
    /** 手动排序位：同分组内 order_index 越大越靠前 (V1.3.5 长按「移动」使用) */
    @ColumnInfo(name = "order_index", defaultValue = "0") val orderIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
