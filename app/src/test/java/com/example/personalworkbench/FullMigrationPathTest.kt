package com.example.personalworkbench

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
 * 背景：DB 当前为 version 9、共 8 个迁移（1→2 … 8→9），
 * 但此前只有 StartupSmokeTest 覆盖 4→5 一条路径。老用户从任意历史版本
 * 覆盖安装升级时若某个迁移写错，就是**启动即崩溃**的致命缺陷。
 *
 * 做法：用 app/schemas 下各版本的 schema JSON 原样建出「那个版本留下的数据库」，
 * 再让 Room 带着 Migrations.ALL 打开——Room 会依次执行途中所有迁移，
 * 并在结束时做全表 schema 校验（列缺失 / 类型不符 / 索引缺失都会抛异常）。
 *
 * 覆盖路径：2→9、3→9、…、8→9（共 7 条）。
 * 注：无 1.json（V1.0 时期未开启 exportSchema），1→2 无法用此法回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = StartupTestApp::class)
class FullMigrationPathTest {

    /** 各版本升级到 v9 均应成功，且落在 version 9 */
    @Test
    fun upgrade_from_v2_to_v9() = assertUpgradeOk(2)

    @Test
    fun upgrade_from_v3_to_v9() = assertUpgradeOk(3)

    @Test
    fun upgrade_from_v4_to_v9() = assertUpgradeOk(4)

    @Test
    fun upgrade_from_v5_to_v9() = assertUpgradeOk(5)

    @Test
    fun upgrade_from_v6_to_v9() = assertUpgradeOk(6)

    @Test
    fun upgrade_from_v7_to_v9() = assertUpgradeOk(7)

    /** 最近一次迁移 8→9：单独保留，便于失败时一眼看出是哪一步 */
    @Test
    fun upgrade_from_v8_to_v9_onlyOneStep() = assertUpgradeOk(8)

    /**
     * 用 schema [fromVersion].json 建库 → Room 打开触发全链路迁移 → 校验结果。
     */
    private fun assertUpgradeOk(fromVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v$fromVersion.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        createDatabaseAtVersion(dbFile, fromVersion)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            // writableDatabase 会真正跑迁移；迁移写错会直接抛 IllegalStateException
            val helper = db.openHelper.writableDatabase

            // 1) 版本号必须落在 9
            val version = helper.query("PRAGMA user_version").use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
            assertEquals("从 v$fromVersion 升级后应为 version 9", 9, version)

            // 2) 抽查核心表仍在（迁移若误删表会在这里暴露）
            val tables = queryStrings(helper, "SELECT name FROM sqlite_master WHERE type = 'table'")
            listOf("tasks", "schedules", "notes", "goals", "goal_records").forEach { t ->
                assertTrue("缺少表 $t", tables.contains(t))
            }

            // 3) 抽查后期迁移新增的关键列确实建出来了
            //    tasks.schedule_id（日程⇄待办同步）、schedules.task_id（反向关联）、
            //    goal_records.note（打卡备注，8→9 新增）
            assertColumnExists(helper, "tasks", "schedule_id")
            assertColumnExists(helper, "schedules", "task_id")
            assertColumnExists(helper, "goal_records", "note")
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /** 按 schema JSON 原样建出指定版本的数据库（含索引，否则 Room 校验会报索引缺失） */
    private fun createDatabaseAtVersion(dbFile: java.io.File, version: Int) {
        val schemaJson = javaClass.getResourceAsStream("/schemas/$version.json")
            ?.use { it.readBytes().decodeToString() }
            ?: error("schema $version.json not found in test resources")

        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            val root = JSONObject(schemaJson).getJSONObject("database")
            val entities = root.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        raw.execSQL(
                            arr.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                        )
                    }
                }
            }
            raw.execSQL("PRAGMA user_version = $version")
        } finally {
            raw.close()
        }
    }

    private fun queryStrings(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): List<String> {
        val out = mutableListOf<String>()
        db.query(sql).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    private fun assertColumnExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String
    ) {
        val cols = mutableListOf<String>()
        db.query("PRAGMA table_info($table)").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) cols.add(c.getString(idx))
        }
        assertTrue("表 $table 缺少列 $column（实际：$cols）", cols.contains(column))
    }
}
