package com.example.personalworkbench.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * 提醒调度入口：把 [ReminderPlanner] 产出的目标集合与 WorkManager 中已排定的工作做**增量对齐**。
 *
 * 对齐策略（用 tag 携带触发时刻，避免无脑全量重建）：
 * - 目标集合中不存在的工作 → `cancelUniqueWork` 取消（记录被删/完成/改期/已触发）
 * - 新增、或触发时刻发生变化 → `REPLACE` 重新排程
 * - 完全一致 → 不动，避免每次数据变更都重建全部任务
 */
object ReminderScheduler {

    /** 所有提醒工作统一打这个 tag，便于一次性取回 */
    const val TAG_ALL = "reminder"

    /** 通知 ID 必须是 Int，用 key 的稳定哈希派生 */
    fun notificationId(key: String): Int = key.hashCode()

    fun uniqueName(key: String): String = "reminder_$key"

    /** tag 中不允许有空格等特殊字符，key 只包含 [a-zA-Z0-9_] 与 '@' */
    private fun keyTag(key: String) = "k:$key"
    private fun timeTag(triggerAt: Long) = "t:$triggerAt"

    suspend fun sync(context: Context, items: List<ReminderItem>) {
        val wm = WorkManager.getInstance(context)
        val desired = items.associateBy { it.key }

        val existing = runCatching { wm.getWorkInfosByTag(TAG_ALL).get(10, TimeUnit.SECONDS) }
            .getOrDefault(emptyList())

        val seen = mutableSetOf<String>()
        for (info in existing) {
            // 只关心「还没结束」的；SUCCEEDED / FAILED / CANCELLED 视为不存在
            val state = info.state
            if (state == WorkInfo.State.SUCCEEDED ||
                state == WorkInfo.State.FAILED ||
                state == WorkInfo.State.CANCELLED
            ) continue
            val key = info.tags.firstOrNull { it.startsWith("k:") }?.removePrefix("k:") ?: continue
            seen += key
            val target = desired[key]
            if (target == null) {
                // 记录已删除 / 已完成 / 已改期 / 已触发
                wm.cancelUniqueWork(uniqueName(key))
                continue
            }
            val alreadyScheduled = info.tags.contains(timeTag(target.triggerAt))
            if (!alreadyScheduled) {
                enqueue(wm, target)
            }
        }

        // 全新条目
        for (item in desired.values) {
            if (item.key !in seen) enqueue(wm, item)
        }
    }

    private fun enqueue(wm: WorkManager, item: ReminderItem) {
        val delay = (item.triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_KEY to item.key,
                    ReminderWorker.KEY_TITLE to item.title,
                    ReminderWorker.KEY_CONTENT to item.content
                )
            )
            .addTag(TAG_ALL)
            .addTag(keyTag(item.key))
            .addTag(timeTag(item.triggerAt))
            .build()

        wm.enqueueUniqueWork(uniqueName(item.key), ExistingWorkPolicy.REPLACE, request)
    }
}
