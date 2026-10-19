package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.EngineeringDao
import com.example.personalworkbench.data.local.entities.DailyReportEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ResourceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工程管理 Repository (PRD 4.7) —— V1.5 (P1)
 * 数据层完整，UI 按需迭代。彻底删除项目时级联删除关联数据(里程碑/台账/问题/日报)。
 */
class EngineeringRepository(private val dao: EngineeringDao) {

    fun observeActiveProjects(): Flow<List<ProjectEntity>> = dao.observeActive()
    fun observeArchivedProjects(): Flow<List<ProjectEntity>> = dao.observeArchived()
    fun observeProject(id: Long): Flow<ProjectEntity?> = dao.observeProject(id)
    suspend fun getProject(id: Long) = dao.getProject(id)

    suspend fun createProject(
        name: String, code: String? = null, client: String? = null, contractor: String? = null,
        startDate: Long? = null, planEndDate: Long? = null, manager: String? = null
    ): Long = dao.insertProject(
        ProjectEntity(name = name, code = code, client = client, contractor = contractor,
            startDate = startDate, planEndDate = planEndDate, manager = manager)
    )

    /** 更新项目：自动刷新 updated_at（此前直接 @Update 整行，updated_at 永远停留在创建时间，
     *  导致按更新时间排序的列表排序不动，看起来像"数据没变"） */
    suspend fun updateProject(p: ProjectEntity) =
        dao.updateProject(p.copy(updatedAt = System.currentTimeMillis()))

    /** 只更新进度列，避免整行覆盖导致其他字段的并发修改被回滚 */
    suspend fun updateProgress(id: Long, progress: Int) =
        dao.updateProgress(id, progress.coerceIn(0, 100), System.currentTimeMillis())

    /** 工期口径的自动校准写入：只改 progress，不动 updated_at（见 Dao 注释） */
    suspend fun autoSyncProgress(id: Long, progress: Int) =
        dao.updateProgressOnly(id, progress.coerceIn(0, 100))

    suspend fun archiveProject(id: Long) = dao.getProject(id)?.let {
        dao.updateProject(it.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }
    suspend fun restoreProject(id: Long) = dao.getProject(id)?.let {
        dao.updateProject(it.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun permanentlyDeleteProject(id: Long) {
        // 级联删除关联数据 (PRD 4.7): 里程碑/资源/问题/日报 + 项目主体
        dao.deleteMilestonesByProject(id)
        dao.deleteResourcesByProject(id)
        dao.deleteIssuesByProject(id)
        dao.deleteReportsByProject(id)
        dao.deleteProjectById(id)
    }

    fun observeMilestones(projectId: Long): Flow<List<MilestoneEntity>> = dao.observeMilestones(projectId)
    fun observeAllMilestones(): Flow<List<MilestoneEntity>> = dao.observeAllMilestones()
    suspend fun upsertMilestone(m: MilestoneEntity) = dao.insertMilestone(m).let { if (m.id == 0L) it else dao.updateMilestone(m) }
    suspend fun updateMilestone(m: MilestoneEntity) = dao.updateMilestone(m)
    suspend fun deleteMilestone(id: Long) = dao.deleteMilestoneById(id)

    fun observeResources(projectId: Long): Flow<List<ResourceEntity>> = dao.observeResources(projectId)
    suspend fun upsertResource(r: ResourceEntity) = dao.insertResource(r).let { if (r.id == 0L) it else dao.updateResource(r) }
    suspend fun deleteResource(id: Long) = dao.deleteResourceById(id)

    fun observeIssues(projectId: Long): Flow<List<IssueEntity>> = dao.observeIssues(projectId)
    suspend fun upsertIssue(i: IssueEntity) = dao.insertIssue(i)
    suspend fun deleteIssue(id: Long) = dao.deleteIssueById(id)

    fun observeReports(projectId: Long): Flow<List<DailyReportEntity>> = dao.observeReports(projectId)
    suspend fun upsertReport(r: DailyReportEntity) = dao.insertReport(r)
    suspend fun deleteReport(id: Long) = dao.deleteReportById(id)
}
