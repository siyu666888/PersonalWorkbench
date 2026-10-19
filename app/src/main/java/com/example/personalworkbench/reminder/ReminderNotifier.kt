package com.example.personalworkbench.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.personalworkbench.MainActivity
import com.example.personalworkbench.R

/**
 * 本地提醒通知的构建与发送。
 *
 * 调度侧由 WorkManager（[ReminderWorker]）驱动，本类只负责「把一条提醒变成一条系统通知」。
 * 原 ReminderReceiver（AlarmManager 方案）因从未被任何代码调度而废弃删除。
 */
object ReminderNotifier {

    const val CHANNEL_ID = "reminder_channel"

    /** 发送一条提醒通知；[notificationId] 需稳定，同一条提醒重复发送时覆盖上一条 */
    fun notify(context: Context, notificationId: Int, title: String, content: String) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, notificationId, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "待办、日程、目标的本地提醒"
        }
        manager.createNotificationChannel(channel)
    }
}
