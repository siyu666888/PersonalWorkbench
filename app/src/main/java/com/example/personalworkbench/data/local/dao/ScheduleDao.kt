package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    /**
     * 只更新完成状态一列（非重复日程用）。
     * 与 projects.progress 同理：避免整行 @Update 把并发修改的其他字段回滚。
     */
    @Query("UPDATE schedules SET is_completed = :completed, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: Long, completed: Boolean, updatedAt: Long)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntity?

    /** 通过关联待办 id 反查日程（用于「待办→日程」状态同步，V1.3.8） */
    @Query("SELECT * FROM schedules WHERE task_id = :taskId LIMIT 1")
    suspend fun getByTaskId(taskId: Long): ScheduleEntity?

    /** 仅写入关联待办 id（创建日程后回填，避免整行 @Update 覆盖并发修改） */
    @Query("UPDATE schedules SET task_id = :taskId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTaskLink(id: Long, taskId: Long, updatedAt: Long)

    /** 某日期范围内的一次性日程（不含重复序列，重复序列由仓库层展开，避免重复） */
    @Query("SELECT * FROM schedules WHERE (repeat_rule IS NULL OR repeat_rule = '' OR repeat_rule = '不重复') AND start_time BETWEEN :start AND :end ORDER BY start_time ASC")
    fun observeNonRepeatingInRange(start: Long, end: Long): Flow<List<ScheduleEntity>>

    /** 起始时间不晚于 end 的全部重复序列（用于按区间展开实例） */
    @Query("SELECT * FROM schedules WHERE repeat_rule IS NOT NULL AND repeat_rule != '' AND repeat_rule != '不重复' AND start_time <= :end ORDER BY start_time ASC")
    fun observeRecurringUpTo(end: Long): Flow<List<ScheduleEntity>>

    /** 某日期范围内的日程 (月/周/日视图) —— 仅一次性日程，重复序列请用上面的组合查询 */
    @Query("SELECT * FROM schedules WHERE start_time BETWEEN :start AND :end ORDER BY start_time ASC")
    fun observeInRange(start: Long, end: Long): Flow<List<ScheduleEntity>>

    /** 当日日程 (首页今日焦点) —— 仅一次性日程 */
    @Query("SELECT * FROM schedules WHERE start_time BETWEEN :dayStart AND :dayEnd ORDER BY start_time ASC")
    fun observeToday(dayStart: Long, dayEnd: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY start_time ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>
}
