package com.example.personalworkbench.data.repository

import com.example.personalworkbench.data.local.dao.GoalDao
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneStatus
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.local.entities.GoalPeriod
import com.example.personalworkbench.util.dayStartMillis
import kotlinx.coroutines.flow.Flow

/**
 * 目标 Repository (PRD 4.6)
 * 数值型: 累加记录值自动计算完成百分比；
 * 习惯型: 按天打卡，连续天数统计，断签清零(支持每月≤3次补签)；
 * 里程碑型: 子里程碑独立表，全部完成推进整体进度。
 */
class GoalRepository(private val dao: GoalDao) {

    fun observeActive() = dao.observeActive()
    fun observeCompleted() = dao.observeCompleted()
    fun observeRecords(goalId: Long) = dao.observeRecords(goalId)
    fun observeMilestones(goalId: Long) = dao.observeMilestones(goalId)
    suspend fun getMilestones(goalId: Long) = dao.getMilestones(goalId)
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun createGoal(
        name: String,
        type: GoalType,
        period: GoalPeriod,
        startDate: Long? = null,
        endDate: Long? = null,
        targetValue: Double = 0.0,
        unit: String = "",
        frequency: String? = null,
        remindTime: String? = null,
        imagePath: String? = null
    ): Long = dao.insert(
        GoalEntity(
            name = name, type = type, period = period, startDate = startDate,
            endDate = endDate, targetValue = targetValue, unit = unit,
            frequency = frequency, remindTime = remindTime, imagePath = imagePath
        )
    )

    /** 记录进度 (数值型=累加值; 习惯型=打卡当天) */
    suspend fun recordProgress(goalId: Long, value: Double = 1.0, isMakeup: Boolean = false) {
        val goal = dao.getById(goalId) ?: return
        when (goal.type) {
            GoalType.NUMERIC -> {
                val newValue = (goal.currentValue + value).coerceAtMost(goal.targetValue)
                val done = newValue >= goal.targetValue
                dao.update(
                    goal.copy(
                        currentValue = newValue, updatedAt = System.currentTimeMillis(),
                        status = if (done) GoalStatus.COMPLETED else goal.status,
                        completedAt = if (done) System.currentTimeMillis() else goal.completedAt
                    )
                )
                dao.insertRecord(GoalRecordEntity(goalId = goalId, value = value, isMakeup = isMakeup))
            }
            GoalType.HABIT -> {
                val today = dayStartMillis(System.currentTimeMillis())
                if (!isMakeup) {
                    val already = dao.getRecords(goalId).any {
                        !it.isMakeup && dayStartMillis(it.recordDate) == today
                    }
                    if (already) return
                }
                val streak = computeStreak(goalId)
                dao.update(goal.copy(currentValue = goal.currentValue + 1, streak = streak, updatedAt = System.currentTimeMillis()))
                dao.insertRecord(GoalRecordEntity(goalId = goalId, value = 1.0, isMakeup = isMakeup))
            }
            GoalType.MILESTONE -> {
                dao.insertRecord(GoalRecordEntity(goalId = goalId, value = value, isMakeup = isMakeup))
            }
        }
    }

    /** 习惯连续天数: 从今天往前回溯连续有打卡记录的天数 */
    private suspend fun computeStreak(goalId: Long): Int {
        val daySet = dao.getRecords(goalId)
            .filter { !it.isMakeup }
            .map { dayStartMillis(it.recordDate) }
            .toSet()
        var streak = 0
        var cursor = dayStartMillis(System.currentTimeMillis())
        while (cursor in daySet) {
            streak++
            cursor -= 86_400_000L
        }
        return streak
    }

    suspend fun addMilestone(goalId: Long, name: String, planDate: Long? = null, orderIndex: Int = 0): Long =
        dao.insertMilestone(GoalMilestoneEntity(goalId = goalId, name = name, planDate = planDate, orderIndex = orderIndex))
            .also { syncMilestoneProgress(goalId) }

    suspend fun updateMilestone(m: GoalMilestoneEntity) {
        val updated = if (m.status == GoalMilestoneStatus.COMPLETED) {
            m.copy(actualDate = m.actualDate ?: System.currentTimeMillis())
        } else m
        dao.updateMilestone(updated)
        syncMilestoneProgress(m.goalId)
    }

    suspend fun deleteMilestone(m: GoalMilestoneEntity) {
        dao.deleteMilestone(m)
        syncMilestoneProgress(m.goalId)
    }

    /**
     * 里程碑型目标：把「子里程碑完成数/总数」同步到 goal.currentValue / targetValue。
     * V1.3.4 前这两个字段恒为 0，导致目标列表与详情页的进度对里程碑型目标永远显示 0%，
     * 「子里程碑 2/5」只是行小字、看不出整体推进。
     */
    private suspend fun syncMilestoneProgress(goalId: Long) {
        val goal = dao.getById(goalId) ?: return
        if (goal.type != GoalType.MILESTONE) return
        val ms = dao.getMilestones(goalId)
        val target = ms.size.toDouble()
        val done = ms.count { it.status == GoalMilestoneStatus.COMPLETED }.toDouble()
        if (goal.targetValue != target || goal.currentValue != done) {
            dao.update(goal.copy(targetValue = target, currentValue = done, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun completeGoal(id: Long) {
        val g = dao.getById(id) ?: return
        dao.update(g.copy(status = GoalStatus.COMPLETED, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun pauseGoal(id: Long) {
        val g = dao.getById(id) ?: return
        dao.update(g.copy(status = GoalStatus.PAUSED, updatedAt = System.currentTimeMillis()))
    }

    suspend fun reactivate(id: Long) {
        val g = dao.getById(id) ?: return
        if (g.currentValue < g.targetValue) {
            dao.update(g.copy(status = GoalStatus.ACTIVE, completedAt = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /** 更新目标封面/配图（V1.4.2）：传 null 表示移除图片 */
    suspend fun updateImage(goalId: Long, imagePath: String?) {
        val g = dao.getById(goalId) ?: return
        // 若旧图存在且要换成新图/移除，先删除旧文件释放空间
        if (g.imagePath != null && g.imagePath != imagePath) {
            com.example.personalworkbench.util.deleteGoalImage(g.imagePath)
        }
        dao.update(g.copy(imagePath = imagePath, updatedAt = System.currentTimeMillis()))
    }

    fun progressPercent(goal: GoalEntity): Int =
        if (goal.targetValue <= 0.0) 0 else
            ((goal.currentValue / goal.targetValue) * 100).toInt().coerceIn(0, 100)
}
