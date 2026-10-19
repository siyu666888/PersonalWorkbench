package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.TagDao
import com.example.personalworkbench.data.local.entities.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(private val dao: TagDao) {
    fun observeAll(): Flow<List<TagEntity>> = dao.observeAll()
    suspend fun create(name: String, color: String = "primary") = dao.insert(TagEntity(name = name, color = color))
    suspend fun delete(tag: TagEntity) = dao.delete(tag)
}
