package com.example.personalworkbench

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.util.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 可读 JSON 快照导出/恢复测试（V1.4.0）：验证快照能完整还原数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupJsonTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
        db.openHelper.writableDatabase.use { }
    }

    @After
    fun tearDown() {
        db.close()
        context.getDatabasePath(AppDatabase.DATABASE_NAME).delete()
    }

    @Test
    fun snapshotAndRestore_roundTripsTasks() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insert(TaskEntity(title = "备份还原测试", priority = Priority.HIGH))
        assertTrue("插入应返回有效 id", id > 0)

        val json = BackupManager.snapshotJson(db.openHelper.writableDatabase)

        // 清空后从快照恢复
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks")
        assertEquals(0, dao.observeAll().first().size)

        BackupManager.restoreFromJson(db.openHelper.writableDatabase, json)
        val restored = dao.observeAll().first()
        assertEquals(1, restored.size)
        assertEquals("备份还原测试", restored[0].title)
        assertEquals(Priority.HIGH, restored[0].priority)
    }

    @Test
    fun snapshot_includesMetaData() {
        val json = BackupManager.snapshotJson(db.openHelper.writableDatabase)
        assertTrue("快照应含 _meta 字段", JSONObject(json).has("_meta"))
    }
}
