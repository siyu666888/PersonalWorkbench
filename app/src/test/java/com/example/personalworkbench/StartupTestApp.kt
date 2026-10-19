package com.example.personalworkbench

import android.app.Application
import androidx.room.InvalidationTracker
import com.example.personalworkbench.crash.CrashReporter
import com.example.personalworkbench.di.AppContainer

/**
 * 测试专用 Application：与 [PersonalWorkbenchApp] 执行相同的启动路径
 * （崩溃日志安装 + Room 初始化 + InvalidationTracker 注册），
 * 唯一区别是跳过 ReminderSync —— Robolectric 的 Application.onCreate
 * 先于 ContentProvider 安装执行，androidx.startup 驱动的 WorkManagerInitializer
 * 此时尚未运行（真实设备无此问题，ContentProvider 始终先于 Application.onCreate）。
 */
class StartupTestApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container.database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("tasks", "schedules", "goals") {
                override fun onInvalidated(tables: Set<String>) {}
            }
        )
    }
}
