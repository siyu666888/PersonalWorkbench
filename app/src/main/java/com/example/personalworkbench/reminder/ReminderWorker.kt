package com.example.personalworkbench.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 提醒执行器：到点后把输入数据变成一条系统通知。
 *
 * 由 WorkManager 单次任务驱动。注意 WorkManager 的延迟**不是精确定时**：
 * 在 Doze / 应用待机等省电策略下可能晚几分钟触发，这是为避免申请
 * `SCHEDULE_EXACT_ALARM`（Google Play 受限权限）而主动接受的取舍。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_KEY) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "提醒"
        val content = inputData.getString(KEY_CONTENT).orEmpty()

        ReminderNotifier.notify(
            context = applicationContext,
            notificationId = ReminderScheduler.notificationId(key),
            title = title,
            content = content
        )
        return Result.success()
    }

    companion object {
        const val KEY_KEY = "reminder_key"
        const val KEY_TITLE = "reminder_title"
        const val KEY_CONTENT = "reminder_content"
    }
}
