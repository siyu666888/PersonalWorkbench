package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.WorkLogDao
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工作日志 Repository (V1.3.7)
 * 保存按天聚合：date 唯一，存在则覆盖内容；内容清空则删除该日记录，避免残留空日志。
 */
class WorkLogRepository(private val dao: WorkLogDao) {

    fun observeByDate(date: Long): Flow<WorkLogEntity?> = dao.observeByDate(date)
    fun observeRange(start: Long, end: Long): Flow<List<WorkLogEntity>> = dao.observeInRange(start, end)

    /** 全部日志（倒序），供日志列表页浏览/阅读（V1.4.1） */
    fun observeAll(): Flow<List<WorkLogEntity>> = dao.observeAll()

    /** 关键词搜索（V1.4.1） */
    fun search(q: String): Flow<List<WorkLogEntity>> =
        if (q.isBlank()) dao.observeAll() else dao.search(q.trim())

    suspend fun save(date: Long, content: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getByDate(date)
        if (content.isBlank()) {
            existing?.let { dao.deleteByDate(date) }
            return
        }
        if (existing != null) {
            dao.insert(existing.copy(content = content, updatedAt = now))
        } else {
            dao.insert(WorkLogEntity(date = date, content = content, createdAt = now, updatedAt = now))
        }
    }

    suspend fun delete(date: Long) = dao.deleteByDate(date)
}
