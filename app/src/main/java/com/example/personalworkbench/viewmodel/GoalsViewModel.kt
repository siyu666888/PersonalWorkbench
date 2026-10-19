package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneStatus
import com.example.personalworkbench.data.local.entities.GoalPeriod
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.repository.GoalRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 目标 ViewModel (PRD 4.6): 三类型目标创建、进度记录、里程碑维护。
 */
class GoalsViewModel(private val repo: GoalRepository) : ViewModel() {

    val activeGoals: Flow<List<GoalEntity>> = repo.observeActive()
    val completedGoals: Flow<List<GoalEntity>> = repo.observeCompleted()

    fun createGoal(
        name: String,
        type: GoalType,
        period: GoalPeriod,
        targetValue: Double = 0.0,
        unit: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        frequency: String? = null,
        remindTime: String? = null,
        imagePath: String? = null
    ) = viewModelScope.launch {
        repo.createGoal(name, type, period, startDate, endDate, targetValue, unit, frequency, remindTime, imagePath)
    }

    fun recordProgress(id: Long, value: Double = 1.0, isMakeup: Boolean = false) =
        viewModelScope.launch { repo.recordProgress(id, value, isMakeup) }

    fun completeGoal(id: Long) = viewModelScope.launch { repo.completeGoal(id) }
    fun reactivate(id: Long) = viewModelScope.launch { repo.reactivate(id) }
    fun pauseGoal(id: Long) = viewModelScope.launch { repo.pauseGoal(id) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GoalsViewModel(container.goalRepository) as T
    }
}

/**
 * 目标详情 ViewModel: 单目标 + 记录 + 里程碑。
 */
class GoalDetailViewModel(private val repo: GoalRepository, private val goalId: Long) : ViewModel() {
    val goal: Flow<GoalEntity?> = repo.observeActive().let { flow -> kotlinx.coroutines.flow.combine(flow, repo.observeCompleted()) { a, c -> (a + c).firstOrNull { it.id == goalId } }
    }
    val records: Flow<List<GoalRecordEntity>> = repo.observeRecords(goalId)
    val milestones: Flow<List<GoalMilestoneEntity>> = repo.observeMilestones(goalId)

    fun recordProgress(value: Double = 1.0, isMakeup: Boolean = false) = viewModelScope.launch { repo.recordProgress(goalId, value, isMakeup) }
    fun addMilestone(name: String, planDate: Long? = null) = viewModelScope.launch {
        // orderIndex 取当前最大值 +1，保证新增里程碑排在最末
        val maxOrder = repo.getMilestones(goalId).maxOfOrNull { it.orderIndex } ?: 0
        repo.addMilestone(goalId, name, planDate, maxOrder + 1)
    }

    /** 修改里程碑（名称 / 计划日期 / 顺序）—— V1.3.4 前只能新增和勾选，无法改 */
    fun updateMilestone(m: GoalMilestoneEntity) = viewModelScope.launch { repo.updateMilestone(m) }

    /** 删除里程碑 */
    fun deleteMilestone(m: GoalMilestoneEntity) = viewModelScope.launch { repo.deleteMilestone(m) }

    /** 勾选推进里程碑状态：未开始 → 进行中 → 已完成 → 未开始 */
    fun toggleMilestone(m: GoalMilestoneEntity) = viewModelScope.launch {
        val next = when (m.status) {
            GoalMilestoneStatus.PENDING -> GoalMilestoneStatus.IN_PROGRESS
            GoalMilestoneStatus.IN_PROGRESS -> GoalMilestoneStatus.COMPLETED
            GoalMilestoneStatus.COMPLETED -> GoalMilestoneStatus.PENDING
        }
        repo.updateMilestone(m.copy(status = next))
    }

    /**
     * 里程碑型目标的整体进度：由子里程碑完成数驱动。
     * 同时 [GoalRepository.syncMilestoneProgress] 会把同一结果写回 goal.currentValue/targetValue，
     * 保证目标列表页与详情页口径一致（V1.3.4 前这里恒为 0%）。
     */
    val milestoneProgress: Flow<Int> = milestones.map { list ->
        if (list.isEmpty()) 0
        else (list.count { it.status == GoalMilestoneStatus.COMPLETED } * 100 / list.size).coerceIn(0, 100)
    }

    fun completeGoal() = viewModelScope.launch { repo.completeGoal(goalId) }

    /** 设置/更换/移除目标封面图（V1.4.2） */
    fun setImage(path: String?) = viewModelScope.launch { repo.updateImage(goalId, path) }

    class Factory(private val container: AppContainer, private val goalId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GoalDetailViewModel(container.goalRepository, goalId) as T
    }
}
