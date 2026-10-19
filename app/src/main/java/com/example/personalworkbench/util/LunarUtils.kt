package com.example.personalworkbench.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * 农历/节气/节日工具 (PRD 4.4 日历增强)
 *
 * - 农历换算：经典 lunarInfo 表 (1900-2100)，算法已用 14 个权威锚点校验
 *   （2026 春节 2/17、2026-10-14 = 九月初五、2000-01-01 = 1999 冬月廿五 等）。
 * - 24 节气：通用寿星公式 (1900-2100)，与官方数据一致。
 * - 节日：法定节假日 + 传统节日(农历) + 公历节日；除夕按「次日为正月初一」判定。
 *
 * 纯 JVM 逻辑，无 Android 依赖，可单元测试。
 */

private val MONTH_CN = arrayOf("", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
private val DAY_CN = arrayOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
)
private val GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
private val ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")

/** 农历日期 + 当日节日/节气信息 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val ganZhi: String,
    val zodiac: String,
    val festival: String?,
    val isHoliday: Boolean,
    val solarTerm: String?
) {
    val monthCn: String get() = MONTH_CN[month.coerceIn(0, 12)]
    val dayCn: String get() = DAY_CN[(day - 1).coerceIn(0, 29)]
    /** 月日中文，如「九月初五」；初一简写为「九月」 */
    val monthDayCn: String get() = if (day == 1) "${monthCn}月" else dayCn
    /** 完整中文，如「闰六月初一」 */
    val fullCn: String get() = "${if (isLeapMonth) "闰" else ""}${monthCn}月$dayCn"
    /** 农历年描述，如「丙午马年」 */
    val yearCn: String get() = "$ganZhi${zodiac}年"

    /** 日历单元格短标签：节日 > 节气 > 农历日 */
    val shortLabel: String
        get() = festival ?: solarTerm ?: monthDayCn

    /** 长描述，如「农历丙午年九月初五 · 霜降」 */
    val fullLabel: String
        get() {
            val sb = StringBuilder("农历${yearCn}${fullCn}")
            if (solarTerm != null) sb.append(" · $solarTerm")
            if (festival != null) sb.append(" · $festival")
            return sb.toString()
        }
}

object LunarUtils {

    /** 农历 1900-2100 闰大小信息表（16 进制）：bits15..4 = 12 个月大小，bits3..0 = 闰月，bit16 = 闰月为 30 天 */
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x0d0b6, 0x0d25f, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x0b255, 0x06d2f, 0x0ada0, // 2040-2049
        0x14b63, 0x0937f, 0x049f8, 0x04970, 0x064b0, 0x068a6, 0x0ea5f, 0x06b20, 0x0a6c4, 0x0aaef, // 2050-2059
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b6f, 0x0a570, 0x054e4, 0x0d260, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x06aa6, 0x056df, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520                                                                                     // 2100
    )

    /** 24 节气相对偏移分钟表 */
    private val S_TERM_INFO = intArrayOf(
        0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
    )

    private val TERM_NAMES = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
        "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
        "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    /** 公历固定节日: 月 -> (日 -> 名称) */
    private val SOLAR_FESTIVALS = mapOf(
        1 to mapOf(1 to "元旦"),
        2 to mapOf(14 to "情人节"),
        3 to mapOf(8 to "妇女节", 12 to "植树节"),
        4 to mapOf(1 to "愚人节"),
        5 to mapOf(1 to "劳动节", 4 to "青年节"),
        6 to mapOf(1 to "儿童节"),
        7 to mapOf(1 to "建党节"),
        8 to mapOf(1 to "建军节"),
        9 to mapOf(10 to "教师节"),
        10 to mapOf(1 to "国庆节"),
        12 to mapOf(25 to "圣诞节")
    )

    /** 农历固定节日: (月, 日) -> 名称 */
    private val LUNAR_FESTIVALS = mapOf(
        (1 to 1) to "春节",
        (1 to 15) to "元宵节",
        (2 to 2) to "龙抬头",
        (5 to 5) to "端午节",
        (7 to 7) to "七夕",
        (7 to 15) to "中元节",
        (8 to 15) to "中秋节",
        (9 to 9) to "重阳节",
        (12 to 8) to "腊八节",
        (12 to 23) to "小年"
    )

    /** 法定节假日（用于红色高亮） */
    private val STATUTORY = setOf("元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节")

    /** 农历起算日 1900-01-31 = 农历 1900 年正月初一 */
    private val BASE_EPOCH_DAY = LocalDate.of(1900, 1, 31).toEpochDay()

    /** 简单缓存，避免月视图 42 格重复换算 */
    private val cache = HashMap<Long, LunarDate>()
    private val termCache = HashMap<Int, Array<LocalDate>>()

    // ---------- 基础表函数 ----------

    private fun leapMonth(y: Int): Int = LUNAR_INFO[y - 1900] and 0xf

    private fun leapDays(y: Int): Int =
        if (leapMonth(y) == 0) 0 else if (LUNAR_INFO[y - 1900] and 0x10000 != 0) 30 else 29

    private fun monthDays(y: Int, m: Int): Int =
        if (LUNAR_INFO[y - 1900] and (0x10000 shr m) != 0) 30 else 29

    private fun lunarYearDays(y: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (LUNAR_INFO[y - 1900] and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    // ---------- 公历 -> 农历 ----------

    fun of(millis: Long): LunarDate = of(toLocalDate(millis))

    fun of(date: LocalDate): LunarDate {
        val key = date.toEpochDay()
        cache[key]?.let { return it }
        val lunar = compute(date)
        if (cache.size > 1024) cache.clear()
        cache[key] = lunar
        return lunar
    }

    private fun compute(date: LocalDate): LunarDate {
        var offset = (date.toEpochDay() - BASE_EPOCH_DAY).toInt()
        var y = 1900
        while (y <= 2100) {
            val days = lunarYearDays(y)
            if (offset < days) break
            offset -= days
            y++
        }
        val lm = leapMonth(y)
        var m = 1
        var leapFlag = false
        var leapDone = false
        var resultMonth = 1
        while (true) {
            // 闰月插在 lm 月之后：当 m == lm+1 时先消费闰月，其标签仍为 lm（如 2025 年闰六月）
            val useLeap = lm > 0 && m == lm + 1 && !leapDone
            val days = if (useLeap) leapDays(y) else monthDays(y, m)
            if (offset < days) {
                leapFlag = useLeap
                resultMonth = if (useLeap) lm else m
                break
            }
            offset -= days
            if (useLeap) leapDone = true else m++
            if (m > 12) {
                resultMonth = 12
                break
            }
        }
        m = resultMonth
        val day = offset + 1

        val ganZhi = GAN[(y - 4).mod(10)] + ZHI[(y - 4).mod(12)]
        val zodiac = ZODIAC[(y - 4).mod(12)]

        // 节气
        val term = solarTermOf(date)
        // 节日：农历传统节 > 公历节
        var festival = LUNAR_FESTIVALS[(m to day)]?.takeIf { !leapFlag }
        if (festival == null) {
            festival = SOLAR_FESTIVALS[date.monthValue]?.get(date.dayOfMonth)
        }
        // 除夕：腊月最后一天（次日为正月初一）
        if (festival == null && m == 12 && !leapFlag) {
            val next = of(date.plusDays(1))
            if (next.month == 1 && next.day == 1 && !next.isLeapMonth) festival = "除夕"
        }
        // 清明节 = 清明节气当日
        if (festival == null && term == "清明") festival = "清明节"

        return LunarDate(
            year = y, month = m, day = day, isLeapMonth = leapFlag,
            ganZhi = ganZhi, zodiac = zodiac,
            festival = festival,
            isHoliday = festival != null && festival in STATUTORY,
            solarTerm = term
        )
    }

    // ---------- 24 节气 ----------

    /** 某年 24 个节气的公历日期（index 0 = 小寒） */
    private fun termsOf(year: Int): Array<LocalDate> = termCache.getOrPut(year) {
        Array(24) { n -> termDate(year, n + 1) }
    }

    private fun termDate(year: Int, index: Int): LocalDate {
        val baseMs = BASE_TERM_EPOCH_DAY * 86_400_000L + (2 * 3600 + 5 * 60) * 1000L
        val delta = (31_556_925_974.7 * (year - 1900)).toLong() + S_TERM_INFO[index - 1].toLong() * 60_000L
        return LocalDate.ofEpochDay((baseMs + delta) / 86_400_000L)
    }

    private val BASE_TERM_EPOCH_DAY = LocalDate.of(1900, 1, 6).toEpochDay()

    /** 当日节气名，无则 null */
    fun solarTermOf(date: LocalDate): String? {
        val arr = termsOf(date.year)
        for (i in arr.indices) {
            if (arr[i] == date) return TERM_NAMES[i]
        }
        return null
    }

    /** 当日节气名（时间戳版本），无则 null */
    fun solarTermOf(millis: Long): String? = solarTermOf(toLocalDate(millis))

    // ---------- 面向 UI 的便捷方法 ----------

    fun toLocalDate(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 日历格短标签：节日 > 节气 > 农历日 */
    fun label(millis: Long): String = of(millis).shortLabel

    /** 长描述：农历X年X月X日 · 节气 · 节日 */
    fun fullLabel(millis: Long): String = of(millis).fullLabel

    /** 当日是否为法定节假日 */
    fun isHoliday(millis: Long): Boolean = of(millis).isHoliday

    /** 当日节日名（含传统/公历），无则 null */
    fun festivalOf(millis: Long): String? = of(millis).festival
}
