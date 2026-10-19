package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.FileDao
import com.example.personalworkbench.data.local.entities.FileCategory
import com.example.personalworkbench.data.local.entities.FileEntity
import kotlinx.coroutines.flow.Flow

/**
 * 文件 Repository (PRD 4.5)
 * 文件实际存储于 APP 私有目录，数据库仅记录元信息。
 * 图片去重(MD5)与压缩在 ViewModel/工具层处理，此处负责持久化记录。
 */
class FileRepository(private val dao: FileDao) {

    fun observeAll() = dao.observeAll()
    fun observeByCategory(category: FileCategory) = dao.observeByCategory(category)
    fun search(q: String) = dao.search(q)
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun add(
        name: String,
        path: String,
        mimeType: String,
        size: Long,
        category: FileCategory
    ): Long = dao.insert(
        FileEntity(name = name, path = path, mimeType = mimeType, size = size, category = category)
    )

    suspend fun update(file: FileEntity) = dao.update(file)
    suspend fun delete(id: Long) = dao.getById(id)?.let { dao.delete(it) }
}
