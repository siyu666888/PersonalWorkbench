package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.util.dayStartMillis
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** 按来源日程 + 实例时间定位待办（重复序列每天一条，V1.4.4） */
    @Query("SELECT * FROM tasks WHERE schedule_id = :scheduleId AND due_date = :dueDate AND status != 'DELETED' LIMIT 1")
    suspend fun getByScheduleOccurrence(scheduleId: Long, dueDate: Long): TaskEntity?

    /** 活跃(非回收站)任务流，供列表观察 */
    @Query("SELECT * FROM tasks WHERE status != 'DELETED' AND parent_id IS NULL ORDER BY order_index ASC, due_date ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    /** 全部非回收站任务 (含子任务)，用于在列表中按父任务折叠展示子任务 */
    @Query("SELECT * FROM tasks WHERE status != 'DELETED' ORDER BY parent_id IS NULL DESC, order_index ASC, due_date ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** 按状态过滤 (进行中/已完成) */
    @Query("SELECT * FROM tasks WHERE status = :status AND parent_id IS NULL ORDER BY due_date ASC")
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    /** 逾期任务 (到期日当天不算逾期，跨过当天即 due_date 早于今天 00:00、且未完成才算；V1.4.5 修复「今日待办误标逾期」) */
    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' AND due_date IS NOT NULL AND due_date < :todayStart AND parent_id IS NULL ORDER BY due_date ASC")
    fun observeOverdue(todayStart: Long): Flow<List<TaskEntity>>

    /** 今日焦点：今天到期 **或** 今天新建（未设截止日期）的进行中任务
     *  说明: 早前只按 due_date 过滤，导致「今天新建但没设截止日期」的待办不会出现在今日焦点里，
     *  看起来像新增后不同步；这里补上 created_at 分支。 */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'ACTIVE' AND parent_id IS NULL
        AND (
            (due_date IS NOT NULL AND due_date BETWEEN :dayStart AND :dayEnd)
            OR (due_date IS NULL AND created_at BETWEEN :dayStart AND :dayEnd)
        )
        ORDER BY CASE WHEN due_date IS NULL THEN :dayEnd ELSE due_date END ASC, order_index ASC
    """)
    fun observeToday(dayStart: Long, dayEnd: Long): Flow<List<TaskEntity>>

    /** 时间范围内到期(进行中)任务，用于周视图 (PRD 4.4) */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'ACTIVE' AND parent_id IS NULL
        AND (due_date IS NOT NULL AND due_date BETWEEN :start AND :end)
        ORDER BY due_date ASC
    """)
    fun observeInRange(start: Long, end: Long): Flow<List<TaskEntity>>

    /** 子任务 */
    @Query("SELECT * FROM tasks WHERE parent_id = :parentId ORDER BY order_index ASC")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    /** 搜索 (标题/描述) */
    @Query("SELECT * FROM tasks WHERE status != 'DELETED' AND (title LIKE :q OR description LIKE :q) ORDER BY updated_at DESC")
    fun search(q: String): Flow<List<TaskEntity>>

    /** 回收站 (软删除) */
    @Query("SELECT * FROM tasks WHERE status = 'DELETED' ORDER BY deleted_at DESC")
    fun observeRecycleBin(): Flow<List<TaskEntity>>

    /** 软删除 -> 进入回收站 */
    @Query("UPDATE tasks SET status = 'DELETED', deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    /** 恢复 (回收站 -> 活跃) */
    @Query("UPDATE tasks SET status = 'ACTIVE', deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    /** 级联彻底删除 (含子任务) */
    @Transaction
    suspend fun permanentlyDelete(id: Long) {
        deleteSubtasksOf(id)
        hardDelete(id)
    }

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM tasks WHERE parent_id = :parentId")
    suspend fun deleteSubtasksOf(parentId: Long)

    /** 7 天到期自动清理：删除 deleted_at 早于 threshold 的记录 */
    @Query("DELETE FROM tasks WHERE status = 'DELETED' AND deleted_at < :threshold")
    suspend fun purgeExpired(threshold: Long)
}
