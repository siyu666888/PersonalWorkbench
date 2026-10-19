package com.example.personalworkbench

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.util.BackupManager
import com.example.personalworkbench.util.dayStartMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日自动备份测试（V1.4.0：可读 JSON 快照）：
 * 文件名含日期（.json）、当天重复备份覆盖、超过 7 天的旧备份被清理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 先建库，保证备份时有数据可导出
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
        db.openHelper.writableDatabase.use { }
        // 清掉历史备份，保证测试独立
        File(context.getExternalFilesDir(null), "backups").deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        File(context.getExternalFilesDir(null), "backups").deleteRecursively()
        context.getDatabasePath(AppDatabase.DATABASE_NAME).delete()
    }

    @Test
    fun autoBackup_createsJsonNamedWithDate() {
        val today = dayStartMillis(System.currentTimeMillis())
        assertTrue(BackupManager.autoBackup(context, db.openHelper.writableDatabase, today))

        val files = BackupManager.listAutoBackups(context)
        assertEquals(1, files.size)
        val expectedName = "workbench-backup-" +
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(today)) + ".json"
        assertEquals(expectedName, files[0].name)
        assertTrue("JSON 快照不应为空", files[0].length() > 0)
        assertTrue("快照应为合法 JSON 且含 _meta", org.json.JSONObject(files[0].readText()).has("_meta"))
    }

    @Test
    fun autoBackup_sameDay_overwritesInsteadOfDuplicating() {
        val today = dayStartMillis(System.currentTimeMillis())
        assertTrue(BackupManager.autoBackup(context, db.openHelper.writableDatabase, today))
        assertTrue(BackupManager.autoBackup(context, db.openHelper.writableDatabase, today))
        assertEquals("当天重复备份应覆盖而非堆积", 1, BackupManager.listAutoBackups(context).size)
    }

    @Test
    fun autoBackup_prunesOlderThanSevenDays() {
        val today = dayStartMillis(System.currentTimeMillis())
        val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)

        // 造 4 个历史备份：3/6/7/10 天前
        val old3 = File(dir, "workbench-backup-${fmt.format(Date(today - 3 * 86_400_000L))}.json").apply { writeText("x") }
        val old6 = File(dir, "workbench-backup-${fmt.format(Date(today - 6 * 86_400_000L))}.json").apply { writeText("x") }
        val old7 = File(dir, "workbench-backup-${fmt.format(Date(today - 7 * 86_400_000L))}.json").apply { writeText("x") }
        val old10 = File(dir, "workbench-backup-${fmt.format(Date(today - 10 * 86_400_000L))}.json").apply { writeText("x") }

        assertTrue(BackupManager.autoBackup(context, db.openHelper.writableDatabase, today))

        assertTrue("3 天前应保留", old3.exists())
        assertTrue("6 天前应保留", old6.exists())
        assertTrue("7 天前（临界）应保留", old7.exists())
        assertFalse("10 天前应被清理", old10.exists())
    }
}
