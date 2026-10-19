package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.NoteDao
import com.example.personalworkbench.data.local.entities.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 笔记 Repository (PRD 4.3)
 * 自动保存: 编辑器内容变更后由 ViewModel 调用 save()，此处统一生成 preview 摘要(正文前 80 字)。
 */
class NoteRepository(private val dao: NoteDao) {

    fun observeAll() = dao.observeAll()
    fun observeRecent(limit: Int = 2) = dao.observeRecent(limit)
    fun search(q: String) = dao.search(q)
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun create(title: String, content: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title, content = content, preview = makePreview(content), createdAt = now, updatedAt = now))
    }

    suspend fun save(id: Long, title: String, content: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(title = title, content = content, preview = makePreview(content), updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    /** 置顶 / 取消置顶 (V1.3.5) */
    suspend fun setPinned(id: Long, pinned: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(pinned = pinned, updatedAt = System.currentTimeMillis())) }
    }

    /** 长按「上移」：在同置顶分组内把笔记与上一个交换顺序 (V1.3.5) */
    suspend fun moveUp(id: Long) = swapOrder(id, up = true)

    /** 长按「下移」：在同置顶分组内把笔记与下一个交换顺序 (V1.3.5) */
    suspend fun moveDown(id: Long) = swapOrder(id, up = false)

    private suspend fun swapOrder(id: Long, up: Boolean) {
        val list = dao.observeAll().first()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = if (up) idx - 1 else idx + 1
        if (target < 0 || target >= list.size) return
        val a = list[idx]
        val b = list[target]
        // 只允许在同分组（都置顶 / 都不置顶）内移动，跨分组交给置顶语义处理
        if (a.pinned != b.pinned) return
        // 重新按「交换后」的顺序赋唯一 order_index（列表长度 - 位置），保证可两两互换
        val reordered = list.toMutableList()
        reordered[idx] = b
        reordered[target] = a
        for (i in reordered.indices) {
            val n = reordered[i]
            val next = reordered.size - i
            if (n.orderIndex != next) dao.update(n.copy(orderIndex = next))
        }
    }

    /** 预览摘要: 去除简单 HTML 标签后取前 80 字 */
    private fun makePreview(content: String): String {
        val text = content.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (text.length <= 80) text else text.take(80) + "…"
    }
}
