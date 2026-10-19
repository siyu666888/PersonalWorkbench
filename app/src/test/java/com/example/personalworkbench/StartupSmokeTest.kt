package com.example.personalworkbench

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.local.Migrations
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.calendar.CalendarScreen
import com.example.personalworkbench.ui.home.HomeScreen
import com.example.personalworkbench.ui.settings.SettingsScreen
import com.example.personalworkbench.ui.todo.TodoScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.personalworkbench.util.dayStartMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.ui.LocalWindowSizeClass

/**
 * 启动冒烟测试（V1.3.8 排障「打开就闪退」）：
 * 1. Application.onCreate 完整执行（Robolectric 真实实例化 PersonalWorkbenchApp：
 *    Room 初始化 + 迁移 + WorkManager + 崩溃日志安装）
 * 2. 覆盖安装升级：用 schema 4.json 全量建 V4 数据库，再让 Room 打开触发 MIGRATION_4_5
 * 3. 五大主屏幕（首页/待办/日程/设置）Compose 组合不抛异常
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h640dp-xhdpi", application = StartupTestApp::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class StartupSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun container(): AppContainer =
        AppContainer(ApplicationProvider.getApplicationContext())

    /** 1. Application 启动路径（Room 初始化 + InvalidationTracker + 崩溃日志安装） */
    @Test
    fun application_onCreate_initializesWithoutCrash() {
        val app = ApplicationProvider.getApplicationContext<StartupTestApp>()
        app.container.settingsRepository.crashReportEnabled
        app.container.syncBridge
        app.container.database.openHelper.writableDatabase.use { }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * 真实 MainActivity 全链路启动（create/start/resume）：
     * 覆盖 NavHost + 首页渲染 + MainViewModel + 通知权限申请 + 备份提醒。
     * AppCompat 在 Robolectric 上的 abc_vector_test 兼容问题只在部分 SDK 出现，逐个 SDK 尝试。
     */
    @Test
    @Config(sdk = [33])
    fun mainActivity_fullStartup_sdk33_withoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create()
        shadowOf(Looper.getMainLooper()).idle()
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        controller.resume()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    @Config(sdk = [32])
    fun mainActivity_fullStartup_sdk32_withoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create()
        shadowOf(Looper.getMainLooper()).idle()
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        controller.resume()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    @Config(sdk = [29])
    fun mainActivity_fullStartup_sdk29_withoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create()
        shadowOf(Looper.getMainLooper()).idle()
        controller.start()
        shadowOf(Looper.getMainLooper()).idle()
        controller.resume()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 2. 覆盖安装升级：预置 version 4 全量数据库，再让 Room 打开触发 MIGRATION_4_5 + 全表 schema 校验 */
    @Test
    fun upgrade_v4_to_v5_migratesWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // 读取 schema 4.json，按其中的 createSql 原样建出「V1.3.7 留下的」version 4 数据库
        val schemaJson = javaClass.getResourceAsStream("/schemas/4.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("schema 4.json not found in test resources")
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            val entities = JSONObject(schemaJson)
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            raw.execSQL("PRAGMA user_version = 4")
        } finally {
            raw.close()
        }

        // Room 打开：执行 4→5 迁移并做全表 schema 校验
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            db.openHelper.writableDatabase.use { helper ->
                helper.query("SELECT task_id FROM schedules LIMIT 1").use { c ->
                    check(c.count >= 0) { "task_id 列不存在" }
                }
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /** 3. 首页组合 */
    @Test
    fun homeScreen_composes_withoutCrash() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalWindowSizeClass provides WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
            ) {
                HomeScreen(nav = rememberNavController(), container = container())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("今日焦点").assertExists()
    }

    /**
     * 3b. 全新安装首页全节点渲染（用户场景：首次安装 1.3.8，首次运行 V1.3.7 引入的全部首页代码）。
     * 快捷入口为两列分列网格（V1.6.0 恢复，非懒列表），一次性真实组合全部 8 个入口，
     * 覆盖「空数据库测不到、真机才触发」的布局路径。
     */
    @Test
    fun homeScreen_rendersAllQuickEntries_freshInstall() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalWindowSizeClass provides WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
            ) {
                HomeScreen(nav = rememberNavController(), container = container())
            }
        }
        composeRule.waitForIdle()
        // 快捷入口 8 项（V1.6.0 起为两列分列网格：4 行 × 2 列）
        listOf("待办", "笔记", "日程", "目标", "文件", "项目", "报表", "日志").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("今日焦点").assertExists()
        composeRule.onNodeWithText("快捷入口").assertExists()
        // 注：「快速添加」是 ExtendedFAB（Robolectric 合并树差异，需 useUnmergedNode）；
        // 「最近动态」位于快捷入口 340dp 之下，属懒加载屏幕外区域，真机同行为，均不在此断言
    }

    /** 4. 待办列表组合 */
    @Test
    fun todoScreen_composes_withoutCrash() {
        composeRule.setContent {
            TodoScreen(nav = rememberNavController(), container = container())
        }
        composeRule.waitForIdle()
    }

    /** 5. 日程页组合（V1.3.8 改动最大：图标切换器 + 年视图 + 周数标签） */
    @Test
    fun calendarScreen_composes_withoutCrash() {
        composeRule.setContent {
            CalendarScreen(nav = rememberNavController(), container = container())
        }
        composeRule.waitForIdle()
    }

    /** 6. 设置页组合（V1.3.8 新增：日程同步开关 + AI 配置卡片） */
    @Test
    fun settingsScreen_composes_withoutCrash() {
        composeRule.setContent {
            SettingsScreen(nav = rememberNavController(), container = container())
        }
        composeRule.waitForIdle()
    }
}

/**
 * 低版本 Android 兼容性冒烟（V1.3.8 排障）：
 * minSdk 26（Android 8.0）。部分厂商/旧系统在旧 API 上的行为差异可能引发启动崩溃，
 * 这里在 SDK 26/29 上重复四大屏幕组合验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "zh-rCN-w360dp-h640dp-xhdpi", application = StartupTestApp::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class StartupSmokeLegacySdkTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun container(): AppContainer =
        AppContainer(ApplicationProvider.getApplicationContext())

    @Test
    fun homeScreen_composes_onSdk26_withoutCrash() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalWindowSizeClass provides WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
            ) {
                HomeScreen(nav = rememberNavController(), container = container())
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun calendarScreen_composes_onSdk26_withoutCrash() {
        composeRule.setContent {
            CalendarScreen(nav = rememberNavController(), container = container())
        }
        composeRule.waitForIdle()
    }

    @Test
    fun settingsScreen_composes_onSdk26_withoutCrash() {
        composeRule.setContent {
            SettingsScreen(nav = rememberNavController(), container = container())
        }
        composeRule.waitForIdle()
    }
}

/**
 * 真实数据场景冒烟（V1.3.8 排障）：
 * 预置 version 4 数据库 + 各表真实数据行，走完整 4→5 迁移后查询主要 Flow，
 * 覆盖「空数据库测不到、用户设备上才触发」的路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h640dp-xhdpi", application = StartupTestApp::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class StartupSmokeWithDataTest {

    @Test
    fun upgrade_withRealData_thenQueryAllFlows_withoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val schemaJson = javaClass.getResourceAsStream("/schemas/4.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("schema 4.json not found")
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val now = System.currentTimeMillis()
        try {
            val entities = JSONObject(schemaJson)
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            // 预置真实数据（V1.3.7 用户库的典型行）
            raw.execSQL(
                "INSERT INTO tasks (title, description, due_date, priority, tags, status, reminder_on, order_index, created_at, updated_at) " +
                    "VALUES ('写周报', NULL, $now, 'HIGH', '', 'ACTIVE', 0, 0, $now, $now)"
            )
            raw.execSQL(
                "INSERT INTO schedules (title, start_time, end_time, is_all_day, reminder_minutes, color, repeat_rule, is_completed, created_at, updated_at) " +
                    "VALUES ('项目例会', $now, ${now + 3600000}, 0, 0, 'primary', NULL, 0, $now, $now)"
            )
            raw.execSQL(
                "INSERT INTO notes (title, content, tags, preview, pinned, order_index, created_at, updated_at) " +
                    "VALUES ('会议纪要', '本周进展…', '', '本周进展…', 0, 0, $now, $now)"
            )
            raw.execSQL("PRAGMA user_version = 4")
        } finally {
            raw.close()
        }

        // 走完整升级 + 查询首页/日程/AI 用到的全部 Flow
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            db.openHelper.writableDatabase.use { }
            kotlinx.coroutines.runBlocking {
                db.taskDao().let { dao ->
                    dao.observeToday(dayStartMillis(now), dayStartMillis(now) + 86_400_000L).first()
                }
                db.scheduleDao().let { dao ->
                    dao.observeToday(dayStartMillis(now), dayStartMillis(now) + 86_400_000L).first()
                    dao.observeAll().first()
                    // V1.3.8 新增查询：在迁移后的列上执行
                    dao.getByTaskId(999L)
                }
                db.noteDao().observeAll().first()
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
