package com.example.personalworkbench.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personalworkbench.data.local.entities.DailyReportEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EngineeringDao {

    // ---- projects ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(p: ProjectEntity): Long

    @Update
    suspend fun updateProject(p: ProjectEntity)

    /** 单独更新进度：只写 progress 一列，避免用整行快照覆盖造成「刚改的状态被回滚」，
     *  同时刷新 updated_at，让列表按更新时间排序的查询能感知到变化。 */
    @Query("UPDATE projects SET progress = :progress, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, updatedAt: Long)

    /** 自动校准专用：只写 progress 一列且**不**刷新 updated_at，
     *  避免「按工期自动校准」产生的进度漂移打乱列表的「最近更新」排序。 */
    @Query("UPDATE projects SET progress = :progress WHERE id = :id")
    suspend fun updateProgressOnly(id: Long, progress: Int)

    @Delete
    suspend fun deleteProject(p: ProjectEntity)

    @Query("SELECT * FROM projects WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE is_archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    // 级联删除 (PRD 4.7): 删除项目同时清空关联数据
    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("DELETE FROM milestones WHERE project_id = :projectId")
    suspend fun deleteMilestonesByProject(projectId: Long)

    @Query("DELETE FROM resources WHERE project_id = :projectId")
    suspend fun deleteResourcesByProject(projectId: Long)

    @Query("DELETE FROM issues WHERE project_id = :projectId")
    suspend fun deleteIssuesByProject(projectId: Long)

    @Query("DELETE FROM daily_reports WHERE project_id = :projectId")
    suspend fun deleteReportsByProject(projectId: Long)

    // ---- milestones ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(m: MilestoneEntity): Long

    @Update
    suspend fun updateMilestone(m: MilestoneEntity)

    @Query("SELECT * FROM milestones WHERE project_id = :projectId ORDER BY plan_date ASC")
    fun observeMilestones(projectId: Long): Flow<List<MilestoneEntity>>

    /** 全部项目的里程碑（列表页做总进度自动校准用） */
    @Query("SELECT * FROM milestones ORDER BY project_id ASC, plan_date ASC")
    fun observeAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteMilestoneById(id: Long)

    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun deleteResourceById(id: Long)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun deleteIssueById(id: Long)

    @Query("DELETE FROM daily_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    // ---- resources ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(r: ResourceEntity): Long

    @Update
    suspend fun updateResource(r: ResourceEntity)

    @Query("SELECT * FROM resources WHERE project_id = :projectId ORDER BY category ASC, name ASC")
    fun observeResources(projectId: Long): Flow<List<ResourceEntity>>

    // ---- issues ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(i: IssueEntity): Long

    @Update
    suspend fun updateIssue(i: IssueEntity)

    @Query("SELECT * FROM issues WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeIssues(projectId: Long): Flow<List<IssueEntity>>

    // ---- daily_reports ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(r: DailyReportEntity): Long

    @Update
    suspend fun updateReport(r: DailyReportEntity)

    @Query("SELECT * FROM daily_reports WHERE project_id = :projectId ORDER BY report_date DESC")
    fun observeReports(projectId: Long): Flow<List<DailyReportEntity>>
}
