package com.example.personalworkbench.reminder

/**
 * 一条待排程的本地提醒。
 *
 * @param key 稳定唯一键，同时用作 WorkManager unique work 名与通知 ID 的来源。
 *   待办/目标用 `task_<id>` / `goal_<id>`（同一条记录只有一次提醒）；
 *   周期性日程用 `sched_<id>_<实例开始时间>`（每个实例独立一次）。
 * @param triggerAt 触发时刻（epoch millis）。规划器只产出**严格大于当前时间**的条目。
 * @param title 通知标题
 * @param content 通知正文
 */
data class ReminderItem(
    val key: String,
    val triggerAt: Long,
    val title: String,
    val content: String
)
