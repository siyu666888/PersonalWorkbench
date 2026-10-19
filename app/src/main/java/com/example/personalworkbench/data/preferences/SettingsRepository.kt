package com.example.personalworkbench.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户偏好设置 (PRD 5.1 DataStore Preferences)
 * 涵盖: 主题模式、强调色、应用锁、备份提醒频率计数。
 * 崩溃日志上报默认关闭 (PRD 6.3)，需用户在设置页手动开启。
 *
 * 注: 采用手动 DI (AppContainer)，不引入 Hilt，降低构建复杂度 (PRD 5.1「Hilt 可选」)。
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    companion object {
        private val THEME_MODE = intPreferencesKey("theme_mode")        // 0 SYSTEM / 1 LIGHT / 2 DARK
        private val ACCENT_INDEX = intPreferencesKey("accent_index")   // 0-4 预设强调色
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val CRASH_REPORT_ENABLED = booleanPreferencesKey("crash_report_enabled")
        private val LAUNCH_COUNT = intPreferencesKey("launch_count")
        private val LAST_BACKUP_REMINDER = longPreferencesKey("last_backup_reminder")
        private val NOTIFY_PERMISSION_ASKED = booleanPreferencesKey("notify_perm_asked")
        /** 「日程⇄待办」双向同步开关（V1.3.8），默认关闭 */
        private val SCHEDULE_TO_TASK_SYNC = booleanPreferencesKey("schedule_to_task_sync")
        /** 每日自动备份开关（V1.3.9），默认开启：每天首次打开自动备份一次 */
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        /** 上次自动备份日期（当日 00:00 毫秒，0 表示从未备份） */
        private val LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup")
        /** AI 智能助手：云端大模型配置（V1.3.8，OpenAI 兼容接口） */
        private val AI_API_BASE = stringPreferencesKey("ai_api_base")
        private val AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val AI_MODEL = stringPreferencesKey("ai_model")
        private val AI_LOCAL_CONTEXT = booleanPreferencesKey("ai_local_context")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { p ->
        when (p[THEME_MODE] ?: 0) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
    }
    // 默认强调色改为「品牌绿」(2)，与全局规范「品牌绿为主色调」一致；已手动选择过强调色的用户不受影响
    val accentIndex: Flow<Int> = dataStore.data.map { p -> p[ACCENT_INDEX] ?: 2 }
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { p -> p[APP_LOCK_ENABLED] ?: false }
    /** 崩溃日志本地落盘开关：默认开启（只写本机、不上传），用户可在设置页关闭 */
    val crashReportEnabled: Flow<Boolean> = dataStore.data.map { p -> p[CRASH_REPORT_ENABLED] ?: true }
    val launchCount: Flow<Int> = dataStore.data.map { p -> p[LAUNCH_COUNT] ?: 0 }
    val notifyPermissionAsked: Flow<Boolean> = dataStore.data.map { p -> p[NOTIFY_PERMISSION_ASKED] ?: false }
    /** 上次弹出备份提醒的时间戳 (0 表示从未提醒) */
    val lastBackupReminder: Flow<Long> = dataStore.data.map { p -> p[LAST_BACKUP_REMINDER] ?: 0L }
    /** 「日程⇄待办」双向同步开关 */
    val scheduleToTaskSync: Flow<Boolean> = dataStore.data.map { p -> p[SCHEDULE_TO_TASK_SYNC] ?: false }
    /** 每日自动备份开关（默认开启） */
    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { p -> p[AUTO_BACKUP_ENABLED] ?: true }
    /** 上次自动备份日期（当日 00:00 毫秒，0 表示从未备份） */
    val lastAutoBackup: Flow<Long> = dataStore.data.map { p -> p[LAST_AUTO_BACKUP] ?: 0L }
    /** AI 助手：API Base 地址（默认硅基流动 OpenAI 兼容端点） */
    val aiApiBase: Flow<String> = dataStore.data.map { p -> p[AI_API_BASE] ?: "https://api.siliconflow.cn/v1" }
    /** AI 助手：API Key（为空表示使用本地问答） */
    val aiApiKey: Flow<String> = dataStore.data.map { p -> p[AI_API_KEY] ?: "" }
    /** AI 助手：模型名（默认硅基流动免费模型 Qwen2.5-7B-Instruct） */
    val aiModel: Flow<String> = dataStore.data.map { p -> p[AI_MODEL] ?: "Qwen/Qwen2.5-7B-Instruct" }

    /** AI 助手：是否把本机各板块数据作为上下文发给云端模型（V1.4.1，默认开启） */
    val aiLocalContext: Flow<Boolean> = dataStore.data.map { p -> p[AI_LOCAL_CONTEXT] ?: true }

    /** AI 助手：合并配置，便于一次性读取 */
    data class AiConfig(val base: String, val key: String, val model: String)
    val aiConfig: Flow<AiConfig> = dataStore.data.map {
        AiConfig(
            base = it[AI_API_BASE] ?: "https://api.siliconflow.cn/v1",
            key = it[AI_API_KEY] ?: "",
            model = it[AI_MODEL] ?: "Qwen/Qwen2.5-7B-Instruct"
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.ordinal }
    suspend fun setAccentIndex(index: Int) = dataStore.edit { it[ACCENT_INDEX] = index }
    suspend fun setAppLockEnabled(enabled: Boolean) = dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    suspend fun setCrashReportEnabled(enabled: Boolean) = dataStore.edit { it[CRASH_REPORT_ENABLED] = enabled }
    suspend fun setNotifyPermissionAsked(asked: Boolean) = dataStore.edit { it[NOTIFY_PERMISSION_ASKED] = asked }
    suspend fun setScheduleToTaskSync(enabled: Boolean) = dataStore.edit { it[SCHEDULE_TO_TASK_SYNC] = enabled }
    suspend fun setAutoBackupEnabled(enabled: Boolean) = dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }
    suspend fun setLastAutoBackup(date: Long) = dataStore.edit { it[LAST_AUTO_BACKUP] = date }
    suspend fun setAiApiBase(base: String) = dataStore.edit { it[AI_API_BASE] = base }
    suspend fun setAiApiKey(key: String) = dataStore.edit { it[AI_API_KEY] = key }
    suspend fun setAiModel(model: String) = dataStore.edit { it[AI_MODEL] = model }
    suspend fun setAiLocalContext(enabled: Boolean) = dataStore.edit { it[AI_LOCAL_CONTEXT] = enabled }

    /** 启动计数 +1，返回最新值 (用于第 3 次提醒, PRD 7.1) */
    suspend fun incrementLaunchCount(): Int {
        var latest = 0
        dataStore.edit { p ->
            latest = (p[LAUNCH_COUNT] ?: 0) + 1
            p[LAUNCH_COUNT] = latest
        }
        return latest
    }

    suspend fun setLastBackupReminder(time: Long) = dataStore.edit { it[LAST_BACKUP_REMINDER] = time }
}
