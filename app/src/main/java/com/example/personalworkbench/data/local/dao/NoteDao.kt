package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY pinned DESC, order_index DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE title LIKE :q OR content LIKE :q ORDER BY pinned DESC, order_index DESC, updated_at DESC")
    fun search(q: String): Flow<List<NoteEntity>>

    /** 最近编辑的笔记 (首页「最近动态」) */
    @Query("SELECT * FROM notes ORDER BY pinned DESC, order_index DESC, updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>
}
