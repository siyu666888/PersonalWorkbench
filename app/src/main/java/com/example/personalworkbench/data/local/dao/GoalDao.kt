package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneEntity
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE status != 'COMPLETED' ORDER BY updated_at DESC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<GoalEntity>>

    // ---- goal_records ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GoalRecordEntity): Long

    /**
     * 打卡记录流水：日期由近及远（跨天后仍能看出先后），**同一天内按打卡时刻从早到晚**。
     *
     * 注意 `record_date` 存的是完整时间戳（含时分秒），不是当日零点：
     * 只写 `ORDER BY record_date DESC` 会让同一天的多条变成「晚的在前」，
     * 与需求相反。故先用 `date(...,'localtime')` 取到日历日做降序，
     * 再在同一日内以原始时间戳升序，得到「近 → 远、同日早 → 晚」。
     * 统计类消费方（连续天数/汇总/maxByOrNull）均为顺序无关，可安全改序。
     */
    @Query(
        """
        SELECT * FROM goal_records WHERE goal_id = :goalId
        ORDER BY date(record_date / 1000, 'unixepoch', 'localtime') DESC, record_date ASC
        """
    )
    fun observeRecords(goalId: Long): Flow<List<GoalRecordEntity>>

    /** 统计用途：按时间正序（便于连续天数回溯） */
    @Query("SELECT * FROM goal_records WHERE goal_id = :goalId ORDER BY record_date ASC")
    suspend fun getRecords(goalId: Long): List<GoalRecordEntity>

    @Query(
        """
        SELECT * FROM goal_records WHERE goal_id = :goalId AND record_date BETWEEN :start AND :end
        ORDER BY date(record_date / 1000, 'unixepoch', 'localtime') DESC, record_date ASC
        """
    )
    suspend fun getRecordsInRange(goalId: Long, start: Long, end: Long): List<GoalRecordEntity>

    // ---- goal_milestones ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(m: GoalMilestoneEntity): Long

    @Update
    suspend fun updateMilestone(m: GoalMilestoneEntity)

    @Delete
    suspend fun deleteMilestone(m: GoalMilestoneEntity)

    @Query("SELECT * FROM goal_milestones WHERE goal_id = :goalId ORDER BY order_index ASC")
    fun observeMilestones(goalId: Long): Flow<List<GoalMilestoneEntity>>

    @Query("SELECT * FROM goal_milestones WHERE goal_id = :goalId ORDER BY order_index ASC")
    suspend fun getMilestones(goalId: Long): List<GoalMilestoneEntity>
}
