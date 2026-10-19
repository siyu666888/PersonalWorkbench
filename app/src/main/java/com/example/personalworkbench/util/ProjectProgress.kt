package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ProjectStatus

/**
 * 项目总进度的「工期口径」自动计算 (V1.3.3)
 *
 * 公式：**总进度 = 已完成工期 / 总工期**
 *  - 总工期     = planEndDate - startDate
 *  - 已完成工期 = max(今日 - startDate, 已完工里程碑的实际完成日 - startDate)
 *
 * 即「日历时间已经过去多少」与「已完工里程碑证明工程已经推进到哪个时间点」两者取大：
 *  - 里程碑**按期**完成时，实际完成日 == 计划日，总进度正好等于「已完成的计划工期 / 总工期」；
 *  - 里程碑**提前**完成时按实际完成日计（不虚报）；
 *  - 里程碑**滞后**完成时按实际完成日计（超期部分如实反映）；
 *  - 没有任何完工里程碑时，进度就是纯日历工期进度。
 *
 * 缺少 startDate / planEndDate（或工期非正）时无法按工期计算，返回 null，
 * 由调用方退回原来的手动口径（滑杆）。
 */

const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

/** 工期口径的进度计算结果 */
data class ProjectDurationProgress(
    val percent: Int,      // 0-100，总进度
    val elapsedDays: Int,  // 已完成工期（天，四舍五入）
    val totalDays: Int     // 总工期（天，四舍五入）
)

/** 把毫秒 duration 折算成天数（四舍五入） */
private fun toDays(millis: Long): Int = ((millis + DAY_MILLIS / 2) / DAY_MILLIS).toInt()

/**
 * 计算项目的工期口径进度。无法计算时返回 null。
 *
 * @param now 当前时间，测试可注入
 */
fun projectDurationProgress(
    project: ProjectEntity,
    milestones: List<MilestoneEntity>,
    now: Long = System.currentTimeMillis()
): ProjectDurationProgress? {
    val start = project.startDate ?: return null
    val end = project.planEndDate ?: return null
    val total = end - start
    if (total <= 0L) return null

    var elapsed = now - start
    for (m in milestones) {
        if (m.status != ProjectMilestoneStatus.COMPLETED) continue
        // 已完工里程碑证明「到这一天为止的工期已经完成」；缺实际完成日时退回计划日期
        val done = m.actualDate ?: m.planDate ?: continue
        if (done > start) elapsed = maxOf(elapsed, done - start)
    }
    // 项目整体已完工：直接拉满，避免 actualEndDate 与 planEndDate 的误差让进度卡在 99%
    if (project.status == ProjectStatus.COMPLETED) elapsed = total

    val clamped = elapsed.coerceIn(0L, total)
    val percent = (clamped.toDouble() / total.toDouble() * 100.0).toInt().coerceIn(0, 100)
    return ProjectDurationProgress(percent, toDays(clamped), toDays(total))
}

/**
 * 自动校准决策：这一次要不要把 project.progress 写成自动值。
 */
sealed interface ProgressSyncDecision {
    /** 缺日期，无法按工期计算，保持现状（手动口径） */
    data object NotComputable : ProgressSyncDecision
    /** 无需写库：要么已经是自动值，要么用户手动改过且输入未变（尊重手动） */
    data class UpToDate(val percent: Int) : ProgressSyncDecision
    /** 需要把进度写为 percent */
    data class ShouldWrite(val percent: Int) : ProgressSyncDecision
}

/**
 * 工期进度自动校准器（有状态，按 projectId 分别记住输入指纹）。
 *
 * 语义：
 *  - 输入指纹**变化**（跨天 / 改开工竣工日期 / 里程碑增删或状态、日期变化 / 项目状态变化）
 *    → 按工期重新校准，此时即使用户手动拖动过也会被校准值覆盖——这正是「总进度要跟着实际进展自动调整」；
 *  - 输入指纹**未变**而库里的进度与自动值不一致 → 判定为用户手动微调，**尊重手动**，不覆盖，
 *    否则用户一松手进度就被弹回自动值，滑杆等于不可用。
 *
 * 一个实例可同时服务单个项目（详情页）和项目列表（列表页按 projectId 各自记账）。
 */
class ProjectProgressCalibrator {

    private val signatures = HashMap<Long, String>()

    fun decide(
        project: ProjectEntity,
        milestones: List<MilestoneEntity>,
        now: Long = System.currentTimeMillis()
    ): ProgressSyncDecision {
        val auto = projectDurationProgress(project, milestones, now) ?: run {
            signatures.remove(project.id)
            return ProgressSyncDecision.NotComputable
        }
        val sig = signature(project, milestones, now)
        val changed = signatures.put(project.id, sig) != sig
        return when {
            // 输入没变且库里已经是自动值 → 无事可做
            !changed && project.progress == auto.percent -> ProgressSyncDecision.UpToDate(auto.percent)
            // 输入没变但值不一样 → 用户手动拖动过，尊重手动
            !changed -> ProgressSyncDecision.UpToDate(project.progress)
            // 输入变了 → 重新校准
            project.progress == auto.percent -> ProgressSyncDecision.UpToDate(auto.percent)
            else -> ProgressSyncDecision.ShouldWrite(auto.percent)
        }
    }

    /** 输入指纹：项目日期与状态 + 当天 + 每个里程碑的状态/计划日/实际完成日 */
    private fun signature(project: ProjectEntity, milestones: List<MilestoneEntity>, now: Long): String =
        listOf(
            project.startDate?.toString() ?: "-",
            project.planEndDate?.toString() ?: "-",
            project.status.name,
            dayStartMillis(now).toString(),
            milestones
                .sortedBy { it.id }
                .joinToString("|") { "${it.id}:${it.status.name}:${it.planDate ?: "-"}:${it.actualDate ?: "-"}" }
        ).joinToString("#")

    /** 忘记某个项目的指纹（项目被删除时调用，避免 Map 无限增长） */
    fun forget(projectId: Long) {
        signatures.remove(projectId)
    }
}
