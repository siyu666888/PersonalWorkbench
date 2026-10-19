package com.example.personalworkbench.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalworkbench.PersonalWorkbenchApp

/**
 * 提醒重算器：读库 → 规划 → 与 WorkManager 已排程状态对齐。
 *
 * 三个触发时机共同保证「闭环」：
 * 1. 应用启动（冷启动自愈，例如恢复备份后、或用户清了 WorkManager 数据）
 * 2. 数据库变更（Room InvalidationTracker 监听 tasks / schedules / goals 三表）
 * 3. 每日周期任务（跨天后「今天」的区间变了，需要重新规划）
 */
class ReminderResyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PersonalWorkbenchApp)?.container
            ?: return Result.failure()
        val items = ReminderPlanner.plan(container)
        ReminderScheduler.sync(applicationContext, items)
        return Result.success()
    }

    companion object {
        /** 一次性重算（启动 / 数据变更） */
        const val NAME_ONESHOT = "reminder_resync"

        /** 每日周期重算 */
        const val NAME_PERIODIC = "reminder_resync_daily"
    }
}
