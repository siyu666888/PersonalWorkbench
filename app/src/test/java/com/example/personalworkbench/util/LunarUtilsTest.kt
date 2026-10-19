package com.example.personalworkbench.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 农历/节气算法回归测试。
 * 锚点来自权威农历数据（春节日期、当日农历、节气公历日期）。
 */
class LunarUtilsTest {

    private fun at(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun lunarOf(y: Int, m: Int, d: Int) = LunarUtils.of(at(y, m, d))

    @Test
    fun `春节与除夕`() {
        val spring2026 = lunarOf(2026, 2, 17)
        assertEquals(1, spring2026.month)
        assertEquals(1, spring2026.day)
        assertEquals("春节", spring2026.festival)

        val eve2026 = lunarOf(2026, 2, 16)
        assertEquals(12, eve2026.month)
        assertEquals(29, eve2026.day)
        assertEquals("除夕", eve2026.festival)

        for ((y, m, d) in listOf(
            Triple(2020, 1, 25), Triple(2023, 1, 22), Triple(2024, 2, 10),
            Triple(2010, 2, 14), Triple(2033, 1, 31), Triple(2025, 1, 29)
        )) {
            val l = lunarOf(y, m, d)
            assertEquals("$y-$m-$d 应为正月初一", 1, l.month)
            assertEquals("$y-$m-$d 应为初一", 1, l.day)
        }
    }

    @Test
    fun `当日农历换算`() {
        val today = lunarOf(2026, 10, 14)
        assertEquals(2026, today.year)
        assertEquals(9, today.month)
        assertEquals(5, today.day)
        assertEquals("丙午", today.ganZhi)
        assertEquals("马", today.zodiac)
        assertEquals("九月初五", today.fullCn)

        // 九月初一 / 重阳 / 十月初一 边界
        assertEquals(1, lunarOf(2026, 10, 10).day)
        val chongyang = lunarOf(2026, 10, 18)
        assertEquals(9, chongyang.day)
        assertEquals("重阳节", chongyang.festival)
        val oct1 = lunarOf(2026, 11, 9)
        assertEquals(10, oct1.month)
        assertEquals(1, oct1.day)

        // 2000-01-01 = 农历 1999 年冬月廿五
        val y2k = lunarOf(2000, 1, 1)
        assertEquals(1999, y2k.year)
        assertEquals(11, y2k.month)
        assertEquals(25, y2k.day)
    }

    @Test
    fun `闰月年份`() {
        // 2025 年闰六月：2025-08-01 应为闰六月初八前后，总之月为 6 且 isLeapMonth=true
        val leap = LunarUtils.of(at(2025, 7, 26))
        assertEquals(6, leap.month)
        assertTrue("2025-07-26 应落在闰六月", leap.isLeapMonth)
        assertEquals("闰六月", "${if (leap.isLeapMonth) "闰" else ""}${leap.monthCn}月")
    }

    @Test
    fun `传统节日与法定假日`() {
        assertEquals("元宵节", lunarOf(2026, 3, 3).festival)
        assertEquals("中秋节", lunarOf(2026, 9, 25).festival)
        assertEquals("端午节", lunarOf(2026, 6, 19).festival)
        assertEquals("元旦", lunarOf(2026, 1, 1).festival)
        assertTrue(lunarOf(2026, 1, 1).isHoliday)
        assertEquals("国庆节", lunarOf(2026, 10, 1).festival)
        assertTrue(lunarOf(2026, 10, 1).isHoliday)
    }

    @Test
    fun `二十四节气`() {
        assertEquals("立春", LunarUtils.solarTermOf(at(2026, 2, 4)))
        assertEquals("雨水", LunarUtils.solarTermOf(at(2026, 2, 18)))
        assertEquals("惊蛰", LunarUtils.solarTermOf(at(2026, 3, 5)))
        assertEquals("清明", LunarUtils.solarTermOf(at(2026, 4, 5)))
        assertEquals("霜降", LunarUtils.solarTermOf(at(2026, 10, 23)))
        assertEquals("冬至", LunarUtils.solarTermOf(at(2026, 12, 22)))
        // 清明节由节气推导
        assertEquals("清明节", LunarUtils.of(at(2026, 4, 5)).festival)
    }
}
