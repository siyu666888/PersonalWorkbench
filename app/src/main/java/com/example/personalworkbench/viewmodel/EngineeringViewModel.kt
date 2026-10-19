package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.DailyReportEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ResourceEntity
import com.example.personalworkbench.data.repository.EngineeringRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.ProgressSyncDecision
import com.example.personalworkbench.util.ProjectDurationProgress
import com.example.personalworkbench.util.ProjectProgressCalibrator
import com.example.personalworkbench.util.projectDurationProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 工程管理 - 项目列表 ViewModel (PRD 4.7) —— V1.5 (P1)
 * 负责「进行中 / 已归档」项目列表、新建项目、归档与永久删除。
 */
class EngineeringViewModel(private val repo: EngineeringRepository) : ViewModel() {

    val activeProjects: Flow<List<ProjectEntity>> = repo.observeActiveProjects()
    val archivedProjects: Flow<List<ProjectEntity>> = repo.observeArchivedProjects()

    // 总进度自动校准：项目日期 / 里程碑 / 跨天发生变化时，把 progress 重算为「已完成工期/总工期」
    private val calibrator = ProjectProgressCalibrator()

    init {
        combine(activeProjects, archivedProjects, repo.observeAllMilestones()) { active, archived, ms ->
            (active + archived) to ms.groupBy { it.projectId }
        }.onEach { (projects, byProject) ->
            projects.forEach { p ->
                // decide 有状态（会记录输入指纹），每个项目每次发射只能调一次
                when (val d = calibrator.decide(p, byProject[p.id].orEmpty())) {
                    is ProgressSyncDecision.ShouldWrite -> repo.autoSyncProgress(p.id, d.percent)
                    else -> Unit
                }
            }
        }.launchIn(viewModelScope)
    }

    fun createProject(
        name: String,
        code: String? = null,
        client: String? = null,
        contractor: String? = null,
        startDate: Long? = null,
        planEndDate: Long? = null,
        manager: String? = null
    ) = viewModelScope.launch {
        repo.createProject(name, code, client, contractor, startDate, planEndDate, manager)
    }

    fun archiveProject(id: Long) = viewModelScope.launch { repo.archiveProject(id) }
    fun restoreProject(id: Long) = viewModelScope.launch { repo.restoreProject(id) }
    fun permanentlyDeleteProject(id: Long) = viewModelScope.launch {
        repo.permanentlyDeleteProject(id); calibrator.forget(id)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EngineeringViewModel(container.engineeringRepository) as T
    }
}

/**
 * 工程管理 - 项目详情 ViewModel (PRD 4.7)
 * 单项目 + 里程碑 / 资源 / 问题 / 施工日报 四类子数据 + 全部 CRUD。
 */
class ProjectDetailViewModel(
    private val repo: EngineeringRepository,
    private val projectId: Long
) : ViewModel() {

    val project: Flow<ProjectEntity?> = repo.observeProject(projectId)
    val milestones: Flow<List<MilestoneEntity>> = repo.observeMilestones(projectId)
    val resources: Flow<List<ResourceEntity>> = repo.observeResources(projectId)
    val issues: Flow<List<IssueEntity>> = repo.observeIssues(projectId)
    val reports: Flow<List<DailyReportEntity>> = repo.observeReports(projectId)

    /** 工期口径的进度明细（已完成工期/总工期）；缺日期时为 null，UI 回退手动口径 */
    val durationProgress: Flow<ProjectDurationProgress?> =
        combine(project, milestones) { p, ms -> p?.let { projectDurationProgress(it, ms) } }

    // 总进度自动校准：里程碑完成 / 改日期 / 跨天 时把 progress 重算为「已完成工期/总工期」。
    // 输入不变时尊重用户在滑杆上的手动微调（见 ProjectProgressCalibrator 语义）。
    private val calibrator = ProjectProgressCalibrator()

    init {
        combine(project, milestones) { p, ms -> p to ms }
            .onEach { (p, ms) ->
                if (p == null) return@onEach
                when (val d = calibrator.decide(p, ms)) {
                    is ProgressSyncDecision.ShouldWrite -> repo.autoSyncProgress(p.id, d.percent)
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)
    }

    // ---- 项目主体 ----
    fun updateProject(entity: ProjectEntity) = viewModelScope.launch { repo.updateProject(entity) }
    /** 进度专用通道：只写 progress 一列，拖动滑杆时不会把刚改的状态/信息回滚 */
    fun updateProgress(progress: Int) = viewModelScope.launch { repo.updateProgress(projectId, progress) }
    fun archive() = viewModelScope.launch { repo.archiveProject(projectId) }
    fun restore() = viewModelScope.launch { repo.restoreProject(projectId) }
    fun delete() = viewModelScope.launch {
        repo.permanentlyDeleteProject(projectId); calibrator.forget(projectId)
    }

    // ---- 里程碑 ----
    fun upsertMilestone(m: MilestoneEntity) = viewModelScope.launch { repo.upsertMilestone(m) }
    fun deleteMilestone(id: Long) = viewModelScope.launch { repo.deleteMilestone(id) }

    // ---- 资源 ----
    fun upsertResource(r: ResourceEntity) = viewModelScope.launch { repo.upsertResource(r) }
    fun deleteResource(id: Long) = viewModelScope.launch { repo.deleteResource(id) }

    // ---- 问题 ----
    fun upsertIssue(i: IssueEntity) = viewModelScope.launch { repo.upsertIssue(i) }
    fun deleteIssue(id: Long) = viewModelScope.launch { repo.deleteIssue(id) }

    // ---- 施工日报 ----
    fun upsertReport(r: DailyReportEntity) = viewModelScope.launch { repo.upsertReport(r) }
    fun deleteReport(id: Long) = viewModelScope.launch { repo.deleteReport(id) }

    class Factory(private val container: AppContainer, private val projectId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectDetailViewModel(container.engineeringRepository, projectId) as T
    }
}
