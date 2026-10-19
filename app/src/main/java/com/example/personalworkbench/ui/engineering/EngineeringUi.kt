package com.example.personalworkbench.ui.engineering

import androidx.compose.ui.graphics.Color
import com.example.personalworkbench.data.local.entities.IssueSeverity
import com.example.personalworkbench.data.local.entities.IssueStatus
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ProjectStatus
import com.example.personalworkbench.data.local.entities.ResourceCategory
import com.example.personalworkbench.ui.theme.Danger
import com.example.personalworkbench.ui.theme.Info
import com.example.personalworkbench.ui.theme.Success
import com.example.personalworkbench.ui.theme.Warning

/** 项目状态文案 */
fun projectStatusLabel(s: ProjectStatus): String = when (s) {
    ProjectStatus.PLANNING -> "筹备中"
    ProjectStatus.ONGOING -> "进行中"
    ProjectStatus.COMPLETED -> "已完工"
    ProjectStatus.SUSPENDED -> "已停工"
}

/** 项目状态语义色 */
fun projectStatusColor(s: ProjectStatus): Color = when (s) {
    ProjectStatus.PLANNING -> Info
    ProjectStatus.ONGOING -> Success
    ProjectStatus.COMPLETED -> Success
    ProjectStatus.SUSPENDED -> Warning
}

/** 里程碑状态文案 */
fun milestoneStatusLabel(s: ProjectMilestoneStatus): String = when (s) {
    ProjectMilestoneStatus.NOT_STARTED -> "未开始"
    ProjectMilestoneStatus.IN_PROGRESS -> "进行中"
    ProjectMilestoneStatus.COMPLETED -> "已完成"
    ProjectMilestoneStatus.DELAYED -> "已延期"
}

/** 里程碑状态语义色 */
fun milestoneStatusColor(s: ProjectMilestoneStatus): Color = when (s) {
    ProjectMilestoneStatus.NOT_STARTED -> Info
    ProjectMilestoneStatus.IN_PROGRESS -> Warning
    ProjectMilestoneStatus.COMPLETED -> Success
    ProjectMilestoneStatus.DELAYED -> Danger
}

/** 里程碑完成度权重 (用于项目整体进度估算) */
fun milestoneWeightedProgress(list: List<MilestoneEntity>): Int {
    if (list.isEmpty()) return 0
    val totalWeight = list.sumOf { it.weight }.coerceAtLeast(1)
    val done = list.sumOf { m ->
        val ratio = when (m.status) {
            ProjectMilestoneStatus.COMPLETED -> 1.0
            ProjectMilestoneStatus.IN_PROGRESS -> 0.5
            else -> 0.0
        }
        m.weight * ratio
    }
    return (done / totalWeight * 100).toInt().coerceIn(0, 100)
}

/** 资源分类文案 */
fun resourceCategoryLabel(c: ResourceCategory): String = when (c) {
    ResourceCategory.PERSON -> "人员"
    ResourceCategory.MATERIAL -> "材料"
    ResourceCategory.EQUIPMENT -> "设备"
}

/** 问题严重度文案 */
fun issueSeverityLabel(s: IssueSeverity): String = when (s) {
    IssueSeverity.LOW -> "轻微"
    IssueSeverity.MEDIUM -> "一般"
    IssueSeverity.HIGH -> "严重"
    IssueSeverity.CRITICAL -> "紧急"
}

/** 问题严重度语义色 */
fun issueSeverityColor(s: IssueSeverity): Color = when (s) {
    IssueSeverity.LOW -> Info
    IssueSeverity.MEDIUM -> Warning
    IssueSeverity.HIGH -> Danger
    IssueSeverity.CRITICAL -> Danger
}

/** 问题状态文案 */
fun issueStatusLabel(s: IssueStatus): String = when (s) {
    IssueStatus.OPEN -> "待处理"
    IssueStatus.IN_PROGRESS -> "处理中"
    IssueStatus.RESOLVED -> "已解决"
    IssueStatus.CLOSED -> "已关闭"
}

/** 问题状态语义色 */
fun issueStatusColor(s: IssueStatus): Color = when (s) {
    IssueStatus.OPEN -> Warning
    IssueStatus.IN_PROGRESS -> Info
    IssueStatus.RESOLVED -> Success
    IssueStatus.CLOSED -> Success
}

/** 问题状态流转 (点击循环) */
fun nextIssueStatus(s: IssueStatus): IssueStatus = when (s) {
    IssueStatus.OPEN -> IssueStatus.IN_PROGRESS
    IssueStatus.IN_PROGRESS -> IssueStatus.RESOLVED
    IssueStatus.RESOLVED -> IssueStatus.CLOSED
    IssueStatus.CLOSED -> IssueStatus.OPEN
}
