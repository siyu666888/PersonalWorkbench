package com.example.personalworkbench.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 提醒同步的触发入口（非挂起，供 Application 与 InvalidationTracker 直接调用）。
 *
 * 数据变更可能高频连续到达（例如批量导入、恢复备份），这里做 **1.5s 防抖**：
 * 同一窗口内的多次变更只提交一次重算，避免反复取消/重建 WorkManager 内部数据库。
 */
object ReminderSync {

    private const val DEBOUNCE_MS = 1_500L

    private val lock = Any()
    private var lastRequestAt = 0L

    /** 请求一次重算（有防抖） */
    fun request(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastRequestAt < DEBOUNCE_MS) return
            lastRequestAt = now
        }
        // 注意：不要打 ReminderScheduler.TAG_ALL —— 那个 tag 专用于「提醒本身」，
        // 重算器若也带上会被 sync() 当成待对齐的提醒条目。
        val request = OneTimeWorkRequestBuilder<ReminderResyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderResyncWorker.NAME_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 注册每日周期重算（KEEP：已注册过就不重复注册） */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderResyncWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderResyncWorker.NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
