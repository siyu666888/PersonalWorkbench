package com.example.personalworkbench.reminder

import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.data.local.entities.GoalMilestoneStatus
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.util.RecurrenceUtils
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * 提醒规划器：从 Room 当前数据推导「未来应当触发哪些提醒」。
 *
 * 设计要点：
 * - **无状态、可重算**。任何时候调用都能得到完整的目标集合，因此冷启动、数据变更、
 *   跨天都能用同一套逻辑自愈，不需要记录「上次算到哪」。
 * - **只规划未来**。已过触发时刻的条目直接丢弃，天然避免补发历史通知。
 * - **不碰 UI、不写库**，只读 Repository，便于单测。
 *
 * 触发规则（与既有数据字段语义对齐，未新增数据库列）：
 * - 待办：`reminder_on = true` 且有截止日期（截止日期按整天存储）→ 当天 **09:00** 提醒。
 * - 日程：`reminder_minutes` 非空即开启，`0` 表示准点、`n` 表示提前 n 分钟；
 *   周期性日程按区间展开，每个实例独立提醒。
 * - 目标：`remind_time`（HH:mm）非空且进行中 → **每天**到点提醒。
 * - 里程碑（V1.5.0）：`plan_date` 非空且未完成 → 计划日 **09:00** 提醒（「单项目提醒」即此规则，
 *   不新增字段：详情页铃铛一键设置/取消，取消时清空 plan_date）。
 */
object ReminderPlanner {

    /** 待办截止日是整天（本地零点），提醒定在当天上午 9 点 */
    private const val TASK_REMIND_HOUR = 9

    /** 周期性日程向后展开的天数上限，避免无界增长 */
    private const val SCHEDULE_HORIZON_DAYS = 90

    suspend fun plan(container: AppContainer, now: Long = System.currentTimeMillis()): List<ReminderItem> {
        val items = mutableListOf<ReminderItem>()
        items += planTasks(container, now)
        items += planSchedules(container, now)
        items += planGoals(container, now)
        items += planMilestones(container, now)
        return items
    }

    // ---------------------------------------------------------------- 待办

    private suspend fun planTasks(container: AppContainer, now: Long): List<ReminderItem> {
        val tasks = container.taskRepository.observeAllTasks().first()
        return tasks.asSequence()
            .filter { it.status == TaskStatus.ACTIVE && it.reminderOn && it.dueDate != null }
            .mapNotNull { task ->
                val dueDate = task.dueDate ?: return@mapNotNull null
                // V1.6.0：reminderMinutes 可空；null/0 = 截止当天 09:00（旧行为），n = 提前 n 分钟
                val offsetMinutes = (task.reminderMinutes ?: 0).coerceAtLeast(0)
                val triggerAt = atHourOfDay(dueDate, TASK_REMIND_HOUR) - offsetMinutes * 60_000L
                if (triggerAt <= now) return@mapNotNull null
                ReminderItem(
                    key = "task_${task.id}",
                    triggerAt = triggerAt,
                    title = task.title,
                    content = taskReminderText(offsetMinutes)
                )
            }
            .toList()
    }

    /** 待办提醒文案（V1.6.0）：默认「今天截止」；有提前量时说明提前多久 */
    private fun taskReminderText(offsetMinutes: Int): String = when {
        offsetMinutes <= 0 -> "今天截止，别忘了处理"
        offsetMinutes < 60 -> "$offsetMinutes 分钟后截止，别忘了处理"
        offsetMinutes % 60 == 0 -> "${offsetMinutes / 60} 小时后截止，别忘了处理"
        else -> "${offsetMinutes / 60} 小时 ${offsetMinutes % 60} 分钟后截止，别忘了处理"
    }

    // ---------------------------------------------------------------- 日程

    private suspend fun planSchedules(container: AppContainer, now: Long): List<ReminderItem> {
        val horizonEnd = now + SCHEDULE_HORIZON_DAYS * 24L * 3600 * 1000
        val all = container.scheduleRepository.observeAll().first()

        return all.asSequence()
            .filter { it.reminderMinutes != null }
            .flatMap { schedule ->
                val offset = (schedule.reminderMinutes ?: 0).coerceAtLeast(0) * 60_000L
                val instances: List<Long> =
                    if (RecurrenceUtils.isRepeating(schedule)) {
                        RecurrenceUtils
                            .expandAll(listOf(schedule), now, horizonEnd)
                            .map { it.startTime }
                    } else {
                        listOf(schedule.startTime)
                    }
                instances.asSequence()
                    .map { it - offset }
                    .filter { it > now }
                    .map { triggerAt ->
                        ReminderItem(
                            key = "sched_${schedule.id}_$triggerAt",
                            triggerAt = triggerAt,
                            title = schedule.title,
                            content = reminderText(schedule.reminderMinutes ?: 0)
                        )
                    }
            }
            .toList()
    }

    private fun reminderText(minutes: Int): String = when {
        minutes <= 0 -> "即将开始"
        minutes < 60 -> "$minutes 分钟后开始"
        minutes % 60 == 0 -> "${minutes / 60} 小时后开始"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟后开始"
    }

    // ---------------------------------------------------------------- 目标

    private suspend fun planGoals(container: AppContainer, now: Long): List<ReminderItem> {
        val goals = container.goalRepository.observeActive().first()
        return goals.asSequence()
            .filter { it.status == GoalStatus.ACTIVE && !it.remindTime.isNullOrBlank() }
            .mapNotNull { goal ->
                val (hour, minute) = parseHhMm(goal.remindTime) ?: return@mapNotNull null
                val triggerAt = nextDailyTrigger(now, hour, minute)
                ReminderItem(
                    key = "goal_${goal.id}",
                    triggerAt = triggerAt,
                    title = goal.name,
                    content = "今天的份还没完成，去打个卡吧"
                )
            }
            .toList()
    }

    // ---------------------------------------------------------------- 里程碑

    /** 里程碑单项目提醒（V1.5.0）：有计划日期且未完成的里程碑，计划日 09:00 提醒一次 */
    private suspend fun planMilestones(container: AppContainer, now: Long): List<ReminderItem> {
        val goals = container.goalRepository.observeActive().first()
        val items = mutableListOf<ReminderItem>()
        goals.asSequence()
            .filter { it.status == GoalStatus.ACTIVE }
            .forEach { goal ->
                container.goalRepository.getMilestones(goal.id).asSequence()
                    .filter { it.planDate != null && it.status != GoalMilestoneStatus.COMPLETED }
                    .forEach { m ->
                        val triggerAt = atHourOfDay(m.planDate!!, TASK_REMIND_HOUR)
                        if (triggerAt > now) {
                            items += ReminderItem(
                                key = "ms_${m.id}",
                                triggerAt = triggerAt,
                                title = "里程碑提醒",
                                content = "「${goal.name}」的里程碑「${m.name}」计划今天完成"
                            )
                        }
                    }
            }
        return items
    }

    /** 解析 "HH:mm"；非法格式返回 null（该目标不提醒，不影响其他数据） */
    internal fun parseHhMm(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    /** 今天 hour:minute 已过则顺延到明天，实现「每天」提醒 */
    internal fun nextDailyTrigger(now: Long, hour: Int, minute: Int): Long {
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return if (today.timeInMillis > now) {
            today.timeInMillis
        } else {
            // 用 Calendar 加一天，避免跨夏令时固定 +24h 产生偏移
            today.add(Calendar.DAY_OF_MONTH, 1)
            today.timeInMillis
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 取 dayStart 当天的 hour 点（本地时区，分钟及以下归零） */
    internal fun atHourOfDay(dayStart: Long, hour: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
