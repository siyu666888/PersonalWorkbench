package com.example.personalworkbench

import android.app.Application
import android.util.Log
import androidx.room.InvalidationTracker
import com.example.personalworkbench.crash.CrashReporter
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.reminder.ReminderSync
import com.example.personalworkbench.util.BackupManager
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application 入口: 持有手动 DI 容器 (AppContainer)，并驱动本地提醒的同步闭环。
 *
 * 启动健壮性约定（V1.3.8 排障「打开就闪退」后加固）：
 * onCreate 里的**辅助组件**初始化一律容错 —— WorkManager 未就绪 / DataStore 读取异常等
 * 边缘情况只记日志并降级，绝不让「打开 App」这一步失败；
 * 唯一例外是 Room 数据库（核心数据源），它的失败由 fallbackToDestructiveMigration 兜底。
 */
class PersonalWorkbenchApp : Application() {

    val container by lazy { AppContainer(this) }

    /** 应用级协程作用域：只用于「设置开关 → 崩溃日志」这类长生命周期的收集。
     *  带兜底异常处理器：即使 DataStore 流异常也不会变成未捕获崩溃。 */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.w("PersonalWorkbench", "appScope 协程异常（已吞掉，不影响启动）", e)
        }
    )

    override fun onCreate() {
        super.onCreate()

        // 0) 崩溃日志：崩溃时先把堆栈写进 filesDir/crash，再交回系统默认处理，
        //    崩溃体验与系统一致，只是多留下一份可诊断的本地报告
        CrashReporter.install(this)
        CrashReporter.observeEnabled(appScope, container.settingsRepository.crashReportEnabled)

        // 1) 冷启动自愈 + 2) 每日周期重算：均走 WorkManager。
        //    正常路径下 androidx.startup 的 InitializationProvider 会在 Application.onCreate
        //    之前完成 WorkManager 初始化；但定制 ROM / 进程被异常杀死后重入 / WorkManager
        //    自身数据库损坏等边缘情况下 getInstance() 会抛 IllegalStateException，
        //    未捕获即表现为「安装后打开 App 就闪退」。本地提醒是辅助功能，不能阻塞启动。
        runCatching { ReminderSync.request(applicationContext) }
            .onFailure { Log.w("PersonalWorkbench", "WorkManager 冷启动重算初始化失败（已降级）", it) }
        runCatching { ReminderSync.scheduleDaily(applicationContext) }
            .onFailure { Log.w("PersonalWorkbench", "WorkManager 每日重算注册失败（已降级）", it) }

        // 3) 数据变更驱动：监听三张表，覆盖 UI / AI 助手 / 备份恢复等**所有**写入路径，
        //    不需要在每个 Repository 或 ViewModel 里手动调用
        container.database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("tasks", "schedules", "goals") {
                override fun onInvalidated(tables: Set<String>) {
                    // 提醒重算同样容错：数据写入路径不能反过来把写入方搞崩
                    runCatching { ReminderSync.request(applicationContext) }
                }
            }
        )

        // 4) 每日自动备份（V1.4.0 升级为可读 JSON 快照）：每天首次打开自动备份一次到
        //    Android/data/<包名>/files/backups/，文件名含日期，最多保留 7 天。
        //    异步执行、失败只记日志，绝不影响启动。
        appScope.launch {
            val today = dayStartMillis(System.currentTimeMillis())
            val settings = container.settingsRepository
            val enabled = settings.autoBackupEnabled.first()
            val last = settings.lastAutoBackup.first()
            if (enabled && last < today) {
                val ok = withContext(Dispatchers.IO) {
                    BackupManager.autoBackup(applicationContext, container.database.openHelper.writableDatabase, today)
                }
                if (ok) {
                    settings.setLastAutoBackup(today)
                    Log.i("PersonalWorkbench", "每日自动备份完成: ${BackupManager.autoBackupName(today)}")
                } else {
                    Log.w("PersonalWorkbench", "每日自动备份失败（已忽略，不影响启动）")
                }
            }
            // 4.5) 当日日程→待办 反向同步（V1.4.0）：仅把「今天」的日程补建到待办，
            // 解决「日程中的事项没有同步到待办中」；历史/未来日程不处理，避免污染待办。
            // 同样容错：失败只记日志，不影响启动。
            runCatching {
                withContext(Dispatchers.IO) {
                    container.syncBridge.syncTodaySchedulesToTasks(today, dayEndMillis(today))
                }
            }.onFailure { Log.w("PersonalWorkbench", "当日日程→待办同步失败（已忽略）", it) }
        }
    }
}
