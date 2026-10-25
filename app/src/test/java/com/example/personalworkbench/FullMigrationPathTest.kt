package com.example.personalworkbench

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.local.Migrations
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库全链路迁移测试（技术债 D2）。
 *
 * 背景：DB 当前为 version 10、共 10 个迁移（1→2 … 9→10），
 * 但此前只有 StartupSmokeTest 覆盖 4→5 一条路径。老用户从任意历史版本
 * 覆盖安装升级时若某个迁移写错，就是**启动即崩溃**的致命缺陷。
 *
 * 做法：用 app/schemas 下各版本的 schema JSON 原样建出「那个版本留下的数据库」，
 * 再让 Room 带着 Migrations.ALL 打开——Room 会依次执行途中所有迁移，
 * 并在结束时做全表 schema 校验（列缺失 / 类型不符 / 索引缺失都会抛异常）。
 *
 * 两个关键细节（都是踩坑换来的）：
 * 1. **建旧库必须走 [FrameworkSQLiteOpenHelperFactory]，不能用 `SQLiteDatabase.openOrCreateDatabase`。**
 *    后者在 Robolectric 下与 Room 使用的连接不一定落在同一个句柄/文件上，
 *    结果 Room 看到的是「空库」→ 走 onCreate 建出全新 v10 → 测试假绿（迁移一条都没跑）。
 *    本类靠 [MARKER_TABLE] 的存在性守住这一点：旧库里写入的行若消失，说明根本没做原地升级。
 * 2. 断言版本号用 [AppDatabase] 的当前版本常量而非硬编码数字，升级时只改一处。
 *
 * 覆盖路径：2→10、3→10、…、9→10（共 8 条）。
 * 注：无 1.json（V1.0 时期未开启 exportSchema），1→2 无法用此法回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = StartupTestApp::class)
class FullMigrationPathTest {

    private val targetVersion = 10

    @Test
    fun upgrade_from_v2_to_latest() = assertUpgradeOk(2)

    @Test
    fun upgrade_from_v3_to_latest() = assertUpgradeOk(3)

    @Test
    fun upgrade_from_v4_to_latest() = assertUpgradeOk(4)

    @Test
    fun upgrade_from_v5_to_latest() = assertUpgradeOk(5)

    @Test
    fun upgrade_from_v6_to_latest() = assertUpgradeOk(6)

    @Test
    fun upgrade_from_v7_to_latest() = assertUpgradeOk(7)

    @Test
    fun upgrade_from_v8_to_latest() = assertUpgradeOk(8)

    @Test
    fun upgrade_from_v9_to_latest_onlyOneStep() = assertUpgradeOk(9)

    /**
     * 用 schema [fromVersion].json 建库 → Room 打开触发全链路迁移 → 校验结果。
     */
    private fun assertUpgradeOk(fromVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v$fromVersion.db"
        context.deleteDatabase(dbName)

        createDatabaseAtVersion(context, dbName, fromVersion)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            // writableDatabase 会真正跑迁移；迁移写错会直接抛 IllegalStateException
            val helper = db.openHelper.writableDatabase

            // 0) 先证明「升级」确实发生在旧库上：旧库里预置的标记行必须还在。
            //    若 Room 因为路径/句柄不一致而新建了空库，这行会消失——测试必须红。
            assertEquals(
                "旧库预置数据丢失，说明本次走的是「新建」而不是「原地升级」，测试无效",
                1, queryInt(helper, "SELECT COUNT(*) FROM $MARKER_TABLE")
            )

            // 1) 版本号必须落在当前版本
            val version = helper.query("PRAGMA user_version").use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
            assertEquals("从 v$fromVersion 升级后应为 version $targetVersion", targetVersion, version)

            // 2) 抽查核心表仍在（迁移若误删表会在这里暴露）
            val tables = queryStrings(helper, "SELECT name FROM sqlite_master WHERE type = 'table'")
            listOf("tasks", "schedules", "notes", "goals", "goal_records").forEach { t ->
                assertTrue("缺少表 $t", tables.contains(t))
            }

            // 3) 抽查后期迁移新增的关键列确实建出来了
            //    tasks.schedule_id（日程待办同步）、schedules.task_id（反向关联）、
            //    goal_records.note（打卡备注，8→9 新增）
            assertColumnExists(helper, "tasks", "schedule_id")
            assertColumnExists(helper, "schedules", "task_id")
            assertColumnExists(helper, "goal_records", "note")

            // 4) work_logs 的 log_date 唯一索引必须存在（V1.6.5 修复的迁移/实体不一致）
            val names = mutableListOf<String>()
            helper.query("PRAGMA index_list(work_logs)").use { c ->
                val idx = c.getColumnIndex("name")
                while (c.moveToNext()) names.add(c.getString(idx))
            }
            assertTrue("work_logs 缺少 log_date 唯一索引：$names", names.contains("index_work_logs_log_date"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    /**
     * 用 Room 同一个 SQLite 实现（[FrameworkSQLiteOpenHelperFactory]）建出指定版本的库，
     * 并顺手写入 [MARKER_TABLE] 一行作为「老库身份证」。
     */
    private fun createDatabaseAtVersion(context: Context, dbName: String, version: Int) {
        val schemaJson = javaClass.getResourceAsStream("/schemas/$version.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("schema $version.json not found in test resources")
        val root = JSONObject(schemaJson).getJSONObject("database")
        val entities = root.getJSONArray("entities")

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        // createSql 里是 ${'$'}{TABLE_NAME} 占位
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        e.optJSONArray("indices")?.let { arr ->
                            for (j in 0 until arr.length()) {
                                db.execSQL(
                                    arr.getJSONObject(j).getString("createSql")
                                        .replace("\${TABLE_NAME}", table)
                                )
                            }
                        }
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS $MARKER_TABLE (id INTEGER NOT NULL PRIMARY KEY)"
                    )
                    db.execSQL("INSERT INTO $MARKER_TABLE (id) VALUES (42)")
                }

                // 只用来造旧库，升级交给 Room
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            helper.writableDatabase // 触发 onCreate + 写入 user_version
        } finally {
            helper.close()
        }
    }

    private fun queryStrings(db: SupportSQLiteDatabase, sql: String): List<String> {
        val out = mutableListOf<String>()
        db.query(sql).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    private fun queryInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }

    private fun assertColumnExists(db: SupportSQLiteDatabase, table: String, column: String) {
        val cols = mutableListOf<String>()
        db.query("PRAGMA table_info($table)").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) cols.add(c.getString(idx))
        }
        assertTrue("表 $table 缺少列 $column（实际：$cols）", cols.contains(column))
    }

    private companion object {
        const val MARKER_TABLE = "__migration_marker"
    }
}
