package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.FileEntity
import com.example.personalworkbench.data.local.entities.FileCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long

    @Update
    suspend fun update(file: FileEntity)

    @Delete
    suspend fun delete(file: FileEntity)

    @Query("SELECT * FROM files ORDER BY created_at DESC")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE category = :category ORDER BY created_at DESC")
    fun observeByCategory(category: FileCategory): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE name LIKE :q OR tags LIKE :q ORDER BY created_at DESC")
    fun search(q: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getById(id: Long): FileEntity?
}
