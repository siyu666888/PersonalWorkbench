package com.example.personalworkbench.reminder

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提醒规划器中纯时间逻辑的单测。
 * 这部分最容易因时区 / 跨天 / 边界值出错，且不依赖 Android 框架，值得固定行为。
 */
class ReminderPlannerTest {

    // ------------------------------------------------------------ parseHhMm

    @Test
    fun `parse valid hh mm`() {
        assertEquals(8 to 30, ReminderPlanner.parseHhMm("08:30"))
        assertEquals(0 to 0, ReminderPlanner.parseHhMm("00:00"))
        assertEquals(23 to 59, ReminderPlanner.parseHhMm("23:59"))
        assertEquals(7 to 5, ReminderPlanner.parseHhMm(" 7 : 5 "))
    }

    @Test
    fun `parse invalid hh mm returns null`() {
        assertNull(ReminderPlanner.parseHhMm(null))
        assertNull(ReminderPlanner.parseHhMm(""))
        assertNull(ReminderPlanner.parseHhMm("   "))
        assertNull(ReminderPlanner.parseHhMm("24:00"))   // 小时越界
        assertNull(ReminderPlanner.parseHhMm("12:60"))   // 分钟越界
        assertNull(ReminderPlanner.parseHhMm("12:30:00")) // 三段
        assertNull(ReminderPlanner.parseHhMm("abc"))
    }

    // ------------------------------------------------------------ atHourOfDay

    @Test
    fun `atHourOfDay returns same day at given hour`() {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val at9 = ReminderPlanner.atHourOfDay(dayStart, 9)

        assertEquals(9 * 3600_000L, at9 - dayStart)
        val cal = Calendar.getInstance().apply { timeInMillis = at9 }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    // ------------------------------------------------------ nextDailyTrigger

    @Test
    fun `nextDailyTrigger today when not yet passed`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val trigger = ReminderPlanner.nextDailyTrigger(now, 9, 0)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("早上 6 点应排在当天 9 点", expected, trigger)
        assertTrue(trigger > now)
    }

    @Test
    fun `nextDailyTrigger tomorrow when already passed`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val trigger = ReminderPlanner.nextDailyTrigger(now, 9, 0)

        val expected = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("晚上 21 点应顺延到次日 9 点", expected, trigger)
        assertTrue(trigger > now)
    }

    @Test
    fun `nextDailyTrigger exactly at target goes to next day`() {
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val trigger = ReminderPlanner.nextDailyTrigger(target, 9, 0)

        assertTrue("恰好到点应排到下一天，避免同一天重复触发", trigger > target)
    }
}
