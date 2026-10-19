package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WorkLogEntity): Long

    @Query("SELECT * FROM work_logs WHERE log_date = :date LIMIT 1")
    suspend fun getByDate(date: Long): WorkLogEntity?

    @Query("SELECT * FROM work_logs WHERE log_date = :date LIMIT 1")
    fun observeByDate(date: Long): Flow<WorkLogEntity?>

    @Query("SELECT * FROM work_logs WHERE log_date >= :start AND log_date <= :end ORDER BY log_date ASC")
    fun observeInRange(start: Long, end: Long): Flow<List<WorkLogEntity>>

    /** 全部日志，按日期倒序（日志列表页用，V1.4.1） */
    @Query("SELECT * FROM work_logs ORDER BY log_date DESC")
    fun observeAll(): Flow<List<WorkLogEntity>>

    /** 关键词搜索日志正文（V1.4.1） */
    @Query("SELECT * FROM work_logs WHERE content LIKE '%' || :q || '%' ORDER BY log_date DESC")
    fun search(q: String): Flow<List<WorkLogEntity>>

    @Query("DELETE FROM work_logs WHERE log_date = :date")
    suspend fun deleteByDate(date: Long)
}
