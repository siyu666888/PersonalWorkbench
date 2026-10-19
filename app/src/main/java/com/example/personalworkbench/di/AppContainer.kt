package com.example.personalworkbench.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.local.Migrations
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.data.repository.EngineeringRepository
import com.example.personalworkbench.data.repository.FileRepository
import com.example.personalworkbench.data.repository.GoalRepository
import com.example.personalworkbench.data.repository.NoteRepository
import com.example.personalworkbench.data.repository.ScheduleRepository
import com.example.personalworkbench.data.repository.TagRepository
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.data.repository.WorkLogRepository
import com.example.personalworkbench.data.repository.SyncBridge

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 手动依赖注入容器 (PRD 5.1「Hilt 可选 / 手动 DI」)。
 * 作为 Single Source of Truth 的 Room 数据库与各 Repository 统一在此构建。
 */
class AppContainer(private val context: Context) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // 显式注册迁移；fallback 仅作为「遇到未知未来版本」的最后兜底
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()
    }

    private val settingsDataStore: DataStore<Preferences> by lazy { appContext.dataStore }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(database.scheduleDao()) }
    val fileRepository: FileRepository by lazy { FileRepository(database.fileDao()) }
    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao()) }
    val goalRepository: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val engineeringRepository: EngineeringRepository by lazy { EngineeringRepository(database.engineeringDao()) }
    val workLogRepository: WorkLogRepository by lazy { WorkLogRepository(database.workLogDao()) }

    /** 日程⇄待办 双向同步桥（V1.3.8）：聚合两个已构建的 Repository 与设置项，无构造环 */
    val syncBridge: SyncBridge by lazy {
        SyncBridge(scheduleRepository, taskRepository, settingsRepository)
    }
}
