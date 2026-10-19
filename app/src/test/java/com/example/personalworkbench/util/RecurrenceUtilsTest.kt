package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.ScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceUtilsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dayStart(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dayEnd(y: Int, m: Int, d: Int): Long = dayStart(y, m, d) + 86_399_000L

    private fun sched(start: Long, rule: String?): ScheduleEntity =
        ScheduleEntity(title = "测试", startTime = start, endTime = start + 3_600_000L, repeatRule = rule)

    @Test
    fun `编解码往返`() {
        val rule = RepeatRule(
            freq = RepeatFreq.WEEKLY, interval = 2, weekdays = listOf(1, 3, 5),
            count = 10, until = at(2026, 12, 31), exdates = listOf(1000L, 2000L)
        )
        val encoded = RecurrenceUtils.encode(rule)
        val parsed = RecurrenceUtils.parse(encoded)
        assertEquals(rule.freq, parsed?.freq)
        assertEquals(2, parsed?.interval)
        assertEquals(listOf(1, 3, 5), parsed?.weekdays)
        assertEquals(10, parsed?.count)
        assertEquals(listOf(1000L, 2000L), parsed?.exdates)
        assertNull(RecurrenceUtils.encode(RepeatRule(RepeatFreq.NONE)))
        assertNull(RecurrenceUtils.parse(null))
        assertNull(RecurrenceUtils.parse(""))
    }

    @Test
    fun `每天展开到一周`() {
        val s = sched(at(2026, 10, 14), "freq=D;interval=1")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 14), dayEnd(2026, 10, 20))
        assertEquals(7, got.size)
        assertEquals(at(2026, 10, 14), got.first().startTime)
        assertEquals(at(2026, 10, 20), got.last().startTime)
    }

    @Test
    fun `每两天展开`() {
        val s = sched(at(2026, 10, 14), "freq=D;interval=2")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 14), dayEnd(2026, 10, 20))
        assertEquals(4, got.size) // 14/16/18/20
        assertEquals(at(2026, 10, 18), got[2].startTime)
    }

    @Test
    fun `每周一三五展开`() {
        // 2026-10-14 是周三
        val s = sched(at(2026, 10, 14), "freq=W;interval=1;byday=1,3,5")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 14), dayEnd(2026, 10, 20))
        assertEquals(3, got.size) // 10/14(三) 10/16(五) 10/19(一)
        assertEquals(at(2026, 10, 14), got[0].startTime)
        assertEquals(at(2026, 10, 16), got[1].startTime)
        assertEquals(at(2026, 10, 19), got[2].startTime)
    }

    @Test
    fun `每月重复越过小月`() {
        val s = sched(at(2026, 1, 31), "freq=M;interval=1")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 2, 1), dayEnd(2026, 2, 28))
        assertEquals(1, got.size)
        assertEquals(at(2026, 2, 28), got[0].startTime) // 2 月无 31 日，收敛到月末
    }

    @Test
    fun `每年重复`() {
        val s = sched(at(2026, 10, 14), "freq=Y;interval=1")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 1), dayEnd(2027, 12, 31))
        assertEquals(2, got.size)
        assertEquals(at(2026, 10, 14), got[0].startTime)
        assertEquals(at(2027, 10, 14), got[1].startTime)
    }

    @Test
    fun `次数上限`() {
        val s = sched(at(2026, 10, 14), "freq=D;interval=1;count=3")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 1), dayEnd(2026, 10, 31))
        assertEquals(3, got.size) // 14/15/16
        assertEquals(at(2026, 10, 16), got.last().startTime)
    }

    @Test
    fun `快速跳过仍保持次数语义`() {
        // 每天重复、共 200 次，从 1/1 起；6 月 1 日应为第 152 次
        val s = sched(at(2026, 1, 1), "freq=D;interval=1;count=200")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 6, 1), dayEnd(2026, 6, 3))
        assertEquals(3, got.size)
        assertEquals(at(2026, 6, 1), got[0].startTime)
        // count=100 时限不到 6 月
        val s2 = sched(at(2026, 1, 1), "freq=D;interval=1;count=100")
        assertTrue(RecurrenceUtils.expand(s2, dayStart(2026, 6, 1), dayEnd(2026, 6, 3)).isEmpty())
    }

    @Test
    fun `结束日期与排除实例`() {
        val s = sched(at(2026, 10, 14), "freq=D;interval=1;until=${at(2026, 10, 16)}")
        val got = RecurrenceUtils.expand(s, dayStart(2026, 10, 14), dayEnd(2026, 10, 20))
        assertEquals(3, got.size) // 14/15/16

        val exTime = at(2026, 10, 15)
        val s2 = sched(at(2026, 10, 14), "freq=D;interval=1;ex=$exTime")
        val got2 = RecurrenceUtils.expand(s2, dayStart(2026, 10, 14), dayEnd(2026, 10, 16))
        assertEquals(2, got2.size) // 14/16，15 被排除
    }

    @Test
    fun `非重复日程`() {
        val s = sched(at(2026, 10, 14), null)
        assertEquals(1, RecurrenceUtils.expand(s, dayStart(2026, 10, 14), dayEnd(2026, 10, 14)).size)
        assertTrue(RecurrenceUtils.expand(s, dayStart(2026, 10, 15), dayEnd(2026, 10, 15)).isEmpty())
    }

    @Test
    fun `中文描述`() {
        assertEquals("每天", RecurrenceUtils.describe(RepeatRule(RepeatFreq.DAILY)))
        assertEquals("每2天", RecurrenceUtils.describe(RepeatRule(RepeatFreq.DAILY, interval = 2)))
        assertEquals("每周一、三", RecurrenceUtils.describe(RepeatRule(RepeatFreq.WEEKLY, weekdays = listOf(3, 1))))
        assertEquals("每月", RecurrenceUtils.describe(RepeatRule(RepeatFreq.MONTHLY)))
        assertEquals("每年", RecurrenceUtils.describe(RepeatRule(RepeatFreq.YEARLY)))
        assertEquals(
            "每天（共5次）",
            RecurrenceUtils.describe(RepeatRule(RepeatFreq.DAILY, count = 5))
        )
        assertNull(RecurrenceUtils.describe(null))
    }
}
