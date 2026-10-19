package com.example.personalworkbench

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * V1.6.1 两项修复的回归测试：
 * 1. 时间展示补齐年份（跨年后仍可区分）；
 * 2. 打卡记录排序：日期由近及远、同一天内从早到晚。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = StartupTestApp::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DateFormatAndRecordOrderTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 本地时区下的 <年,月,日,时,分> -> 毫秒 */
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ---- 1. 时间格式：必须含年份 ----

    @Test
    fun formatDate_默认格式包含年份() {
        val s = formatDate(ms(2024, 5, 1, 10, 30))
        assertTrue("实际：$s，应包含四位年份", Regex("\\d{4}年\\d{1,2}月\\d{1,2}日").matches(s))
        assertEquals("2024年5月1日", s)
    }

    @Test
    fun formatDateTime_默认格式包含年份与时刻() {
        assertEquals("2024年5月1日 10:30", formatDateTime(ms(2024, 5, 1, 10, 30)))
    }

    @Test
    fun 跨年同月同日_展示结果不同() {
        val y2024 = formatDateTime(ms(2024, 5, 1, 10, 30))
        val y2025 = formatDateTime(ms(2025, 5, 1, 10, 30))
        assertTrue("跨年后两条记录的展示必须可区分，实际均为：$y2024", y2024 != y2025)
    }

    // ---- 2. 打卡记录排序 ----

    @Test
    fun 打卡记录_跨天由近及远_同天从早到晚() = runBlocking {
        val dao = db.goalDao()
        dao.insert(GoalEntity(id = 1, name = "阅读", type = GoalType.NUMERIC))

        // 故意乱序写入：涵盖「跨天」与「同一天多次打卡」两种情形
        dao.insertRecord(GoalRecordEntity(goalId = 1, recordDate = ms(2026, 1, 5, 18, 20), value = 1.0))
        dao.insertRecord(GoalRecordEntity(goalId = 1, recordDate = ms(2026, 1, 8, 9, 0), value = 1.0))
        dao.insertRecord(GoalRecordEntity(goalId = 1, recordDate = ms(2026, 1, 8, 21, 30), value = 1.0))
        dao.insertRecord(GoalRecordEntity(goalId = 1, recordDate = ms(2026, 1, 8, 13, 15), value = 1.0))
        dao.insertRecord(GoalRecordEntity(goalId = 1, recordDate = ms(2025, 12, 31, 8, 0), value = 1.0))

        val expected = listOf(
            "2026年1月8日 09:00", // 最近一天，最早的时刻排在同日首位
            "2026年1月8日 13:15",
            "2026年1月8日 21:30",
            "2026年1月5日 18:20",
            "2025年12月31日 08:00" // 跨年：更早在后
        )
        assertEquals(expected, dao.observeRecords(1).first().map { formatDateTime(it.recordDate) })
    }

    @Test
    fun 新增记录后_列表按新顺序刷新() = runBlocking {
        val dao = db.goalDao()
        dao.insert(GoalEntity(id = 2, name = "运动", type = GoalType.HABIT))
        dao.insertRecord(GoalRecordEntity(goalId = 2, recordDate = ms(2026, 3, 10, 7, 0), value = 1.0))

        val before = dao.observeRecords(2).first().map { formatDateTime(it.recordDate) }
        assertEquals(listOf("2026年3月10日 07:00"), before)

        // 同一天再打一次卡（时刻更晚）→ 应排在当天已有记录之后
        dao.insertRecord(GoalRecordEntity(goalId = 2, recordDate = ms(2026, 3, 10, 20, 45), value = 1.0))
        val sameDay = dao.observeRecords(2).first().map { formatDateTime(it.recordDate) }
        assertEquals(listOf("2026年3月10日 07:00", "2026年3月10日 20:45"), sameDay)

        // 更早的一天前补打卡 → 应排在两者之后
        dao.insertRecord(GoalRecordEntity(goalId = 2, recordDate = ms(2026, 3, 9, 22, 0), value = 1.0, isMakeup = true))
        val after = dao.observeRecords(2).first().map { formatDateTime(it.recordDate) }
        assertEquals(
            listOf("2026年3月10日 07:00", "2026年3月10日 20:45", "2026年3月9日 22:00"),
            after
        )
    }

    @Test
    fun getRecordsInRange_同样保证日期倒序_同日升序() = runBlocking {
        val dao = db.goalDao()
        dao.insert(GoalEntity(id = 3, name = "冥想", type = GoalType.HABIT))
        val day = ms(2026, 6, 1)
        dao.insertRecord(GoalRecordEntity(goalId = 3, recordDate = day + 15 * 3600_000L, value = 1.0))
        dao.insertRecord(GoalRecordEntity(goalId = 3, recordDate = day + 8 * 3600_000L, value = 1.0))

        val list = dao.getRecordsInRange(3, day, day + 86_399_000L)
        assertEquals(
            listOf("2026年6月1日 08:00", "2026年6月1日 15:00"),
            list.map { formatDateTime(it.recordDate) }
        )
    }
}