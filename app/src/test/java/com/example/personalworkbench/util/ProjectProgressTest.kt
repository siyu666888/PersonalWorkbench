package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ProjectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 项目总进度「工期口径」自动计算 (V1.3.3) 的单元测试。
 * 时间轴全部用 epoch 毫秒手工构造，避免受时区/当前时间影响。
 */
class ProjectProgressTest {

    private val day = 86_400_000L
    private val start = 0L
    private val planEnd = 364 * day            // 总工期 364 天
    private val midPlan = 181 * day            // 年中里程碑的计划日期

    private fun project(
        startDate: Long? = start,
        planEndDate: Long? = planEnd,
        status: ProjectStatus = ProjectStatus.ONGOING,
        progress: Int = 0
    ) = ProjectEntity(name = "测试工程", startDate = startDate, planEndDate = planEndDate, status = status, progress = progress)

    private fun milestone(
        id: Long,
        planDate: Long? = midPlan,
        actualDate: Long? = null,
        status: ProjectMilestoneStatus = ProjectMilestoneStatus.COMPLETED
    ) = MilestoneEntity(id = id, projectId = 1L, name = "M$id", planDate = planDate, actualDate = actualDate, status = status)

    // ---- 无法按工期计算 → 返回 null（UI 回退手动口径） ----

    @Test fun `缺开工日期时无法计算`() {
        assertNull(projectDurationProgress(project(startDate = null), emptyList(), now = 100 * day))
    }

    @Test fun `缺竣工日期时无法计算`() {
        assertNull(projectDurationProgress(project(planEndDate = null), emptyList(), now = 100 * day))
    }

    @Test fun `工期非正时无法计算`() {
        assertNull(projectDurationProgress(project(planEndDate = start), emptyList(), now = 100 * day))
        assertNull(projectDurationProgress(project(planEndDate = start - day), emptyList(), now = 100 * day))
    }

    // ---- 纯日历工期 ----

    @Test fun `未开工时为0`() {
        val p = projectDurationProgress(project(), emptyList(), now = -10 * day)!!
        assertEquals(0, p.percent)
        assertEquals(0, p.elapsedDays)
    }

    @Test fun `工期过半时约为50`() {
        val p = projectDurationProgress(project(), emptyList(), now = 182 * day)!!
        assertEquals(50, p.percent)
        assertEquals(182, p.elapsedDays)
        assertEquals(364, p.totalDays)
    }

    @Test fun `超过竣工日期封顶100`() {
        val p = projectDurationProgress(project(), emptyList(), now = 500 * day)!!
        assertEquals(100, p.percent)
        assertEquals(364, p.elapsedDays)
    }

    // ---- 里程碑按期完成：总进度 = 已完成工期 / 总工期 ----

    @Test fun `里程碑按期完成时按计划日期计入已完成工期`() {
        // 今天才第 60 天，但里程碑已按期（实际完成日 = 计划日）干到第 181 天
        val p = projectDurationProgress(project(), listOf(milestone(1L)), now = 60 * day)!!
        assertEquals(181, p.elapsedDays)
        assertEquals(49, p.percent)   // 181/364 = 49.7% → 49
    }

    @Test fun `未完成的里程碑不计入`() {
        val p = projectDurationProgress(
            project(),
            listOf(milestone(1L, status = ProjectMilestoneStatus.IN_PROGRESS)),
            now = 60 * day
        )!!
        assertEquals(60, p.elapsedDays)
        assertEquals(16, p.percent)   // 60/364 = 16.4% → 16
    }

    @Test fun `提前完成按实际完成日计不虚报`() {
        val p = projectDurationProgress(
            project(),
            listOf(milestone(1L, planDate = midPlan, actualDate = 30 * day)),
            now = 60 * day
        )!!
        assertEquals(60, p.elapsedDays)   // 30 天干完，但今天已经第 60 天，取大者
    }

    @Test fun `滞后完成按实际完成日计如实反映超期`() {
        val p = projectDurationProgress(
            project(),
            listOf(milestone(1L, planDate = midPlan, actualDate = 300 * day)),
            now = 60 * day
        )!!
        assertEquals(300, p.elapsedDays)
        assertEquals(82, p.percent)   // 300/364 = 82.4% → 82
    }

    @Test fun `缺实际完成日时退回计划日期`() {
        val withPlan = projectDurationProgress(project(), listOf(milestone(1L, actualDate = null)), now = 60 * day)!!
        val withActual = projectDurationProgress(project(), listOf(milestone(1L, actualDate = midPlan)), now = 60 * day)!!
        assertEquals(withActual.elapsedDays, withPlan.elapsedDays)
        assertEquals(withActual.percent, withPlan.percent)
    }

    @Test fun `项目整体完工直接拉满`() {
        val p = projectDurationProgress(project(status = ProjectStatus.COMPLETED), emptyList(), now = 10 * day)!!
        assertEquals(100, p.percent)
        assertEquals(364, p.elapsedDays)
    }

    @Test fun `多个完工里程碑取最远的一个`() {
        val p = projectDurationProgress(
            project(),
            listOf(
                milestone(1L, planDate = 30 * day),
                milestone(2L, planDate = 200 * day),
                milestone(3L, planDate = 100 * day, status = ProjectMilestoneStatus.IN_PROGRESS)
            ),
            now = 60 * day
        )!!
        assertEquals(200, p.elapsedDays)
        assertEquals(54, p.percent)   // 200/364 = 54.9% → 54
    }

    // ---- 自动校准状态机 ----

    @Test fun `校准器首次发射即写入`() {
        val c = ProjectProgressCalibrator()
        val p = project(progress = 0)
        val d = c.decide(p, listOf(milestone(1L)), now = 60 * day)
        assertTrue(d is ProgressSyncDecision.ShouldWrite)
        assertEquals(49, (d as ProgressSyncDecision.ShouldWrite).percent)
    }

    @Test fun `校准器已是自动值时不再写库`() {
        val c = ProjectProgressCalibrator()
        val p = project(progress = 49)
        val d = c.decide(p, listOf(milestone(1L)), now = 60 * day)
        assertTrue(d is ProgressSyncDecision.UpToDate)
    }

    @Test fun `输入未变时尊重用户手动微调`() {
        val c = ProjectProgressCalibrator()
        c.decide(project(progress = 0), listOf(milestone(1L)), now = 60 * day)   // 首次：写入 49
        // 用户把滑杆拖到 80
        val d = c.decide(project(progress = 80), listOf(milestone(1L)), now = 60 * day)
        assertTrue(d is ProgressSyncDecision.UpToDate)
        assertEquals(80, (d as ProgressSyncDecision.UpToDate).percent)
    }

    @Test fun `里程碑完成后重新校准覆盖手动值`() {
        val c = ProjectProgressCalibrator()
        c.decide(project(progress = 0), listOf(milestone(1L)), now = 60 * day)
        c.decide(project(progress = 80), listOf(milestone(1L)), now = 60 * day)  // 手动 80，被尊重
        // 又干完一个更远的里程碑 → 输入指纹变化 → 重新校准
        val d = c.decide(project(progress = 80), listOf(milestone(1L), milestone(2L, planDate = 300 * day)), now = 60 * day)
        assertTrue(d is ProgressSyncDecision.ShouldWrite)
        assertEquals(82, (d as ProgressSyncDecision.ShouldWrite).percent)
    }

    @Test fun `跨天自动重新校准`() {
        val c = ProjectProgressCalibrator()
        c.decide(project(progress = 16), emptyList(), now = 60 * day)            // 16%，已同步
        assertTrue(c.decide(project(progress = 16), emptyList(), now = 60 * day) is ProgressSyncDecision.UpToDate)
        val next = c.decide(project(progress = 16), emptyList(), now = 100 * day) // 过了一天 → 27%
        assertTrue(next is ProgressSyncDecision.ShouldWrite)
        assertEquals(27, (next as ProgressSyncDecision.ShouldWrite).percent)
    }

    @Test fun `缺日期时退回手动口径`() {
        val c = ProjectProgressCalibrator()
        val d = c.decide(project(startDate = null, progress = 30), emptyList(), now = 60 * day)
        assertTrue(d is ProgressSyncDecision.NotComputable)
    }
}
