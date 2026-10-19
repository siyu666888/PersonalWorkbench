package com.example.personalworkbench.util

import com.example.personalworkbench.data.local.entities.ScheduleEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 重复频率 */
enum class RepeatFreq(val code: String, val label: String) {
    NONE("N", "不重复"),
    DAILY("D", "每天"),
    WEEKLY("W", "每周"),
    MONTHLY("M", "每月"),
    YEARLY("Y", "每年");

    companion object {
        fun of(code: String?): RepeatFreq = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * 重复规则。序列化格式（存于 schedules.repeat_rule 字段，无需数据库迁移）：
 * `freq=W;interval=1;byday=1,3,5;count=0;until=0;ex=1730000000000`
 */
data class RepeatRule(
    val freq: RepeatFreq = RepeatFreq.NONE,
    /** 间隔：每 N 天/周/月/年 */
    val interval: Int = 1,
    /** 周重复的星期集合，1=周一 .. 7=周日；为空则沿用起始日的星期 */
    val weekdays: List<Int> = emptyList(),
    /** 重复总次数（含首次），0 = 不限 */
    val count: Int = 0,
    /** 结束日期（含当日），0 = 不限 */
    val until: Long = 0L,
    /** 被单独删除的实例开始时间（「仅删除此次」） */
    val exdates: List<Long> = emptyList(),
    /** 被单独勾选为「已完成」的实例开始时间（V1.3.3 日程完成勾选） */
    val doneDates: List<Long> = emptyList()
) {
    val isRepeating: Boolean get() = freq != RepeatFreq.NONE
}

private val WEEK_CN = arrayOf("", "一", "二", "三", "四", "五", "六", "日")

/**
 * 周期性日程工具 (PRD 4.4)：规则编解码、中文描述、按日期区间展开重复实例。
 * 纯 JVM 逻辑，可单元测试。
 */
object RecurrenceUtils {

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun toDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDateTime()

    private fun toMillis(dt: LocalDateTime): Long =
        dt.atZone(zone()).toInstant().toEpochMilli()

    // ---------------- 编解码 ----------------

    fun encode(rule: RepeatRule?): String? {
        if (rule == null || !rule.isRepeating) return null
        val sb = StringBuilder("freq=").append(rule.freq.code)
        sb.append(";interval=").append(rule.interval.coerceAtLeast(1))
        if (rule.weekdays.isNotEmpty()) {
            sb.append(";byday=").append(rule.weekdays.distinct().sorted().joinToString(","))
        }
        if (rule.count > 0) sb.append(";count=").append(rule.count)
        if (rule.until > 0) sb.append(";until=").append(rule.until)
        if (rule.exdates.isNotEmpty()) sb.append(";ex=").append(rule.exdates.joinToString(","))
        if (rule.doneDates.isNotEmpty()) sb.append(";done=").append(rule.doneDates.joinToString(","))
        return sb.toString()
    }

    fun parse(raw: String?): RepeatRule? {
        if (raw.isNullOrBlank()) return null
        when (raw) { // 兼容早期中文标签
            "每天" -> return RepeatRule(RepeatFreq.DAILY)
            "每周" -> return RepeatRule(RepeatFreq.WEEKLY)
            "每月" -> return RepeatRule(RepeatFreq.MONTHLY)
            "每年" -> return RepeatRule(RepeatFreq.YEARLY)
            "不重复" -> return null
        }
        if (!raw.contains('=')) return null
        var freq = RepeatFreq.NONE
        var interval = 1
        var weekdays = emptyList<Int>()
        var count = 0
        var until = 0L
        var ex = emptyList<Long>()
        var done = emptyList<Long>()
        raw.split(';').forEach { part ->
            val kv = part.split('=')
            if (kv.size != 2) return@forEach
            when (kv[0].trim()) {
                "freq" -> freq = RepeatFreq.of(kv[1].trim())
                "interval" -> interval = kv[1].trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
                "byday" -> weekdays = kv[1].split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }
                "count" -> count = kv[1].trim().toIntOrNull() ?: 0
                "until" -> until = kv[1].trim().toLongOrNull() ?: 0L
                "ex" -> ex = kv[1].split(',').mapNotNull { it.trim().toLongOrNull() }
                "done" -> done = kv[1].split(',').mapNotNull { it.trim().toLongOrNull() }
            }
        }
        return if (freq == RepeatFreq.NONE) null
        else RepeatRule(freq, interval, weekdays, count, until, ex, done)
    }

    /** 中文描述，如「每周一、三」「每2周 周二」「每天（共10次）」；不重复返回 null */
    fun describe(rule: RepeatRule?): String? {
        if (rule == null || !rule.isRepeating) return null
        val base = when (rule.freq) {
            RepeatFreq.DAILY -> if (rule.interval == 1) "每天" else "每${rule.interval}天"
            RepeatFreq.WEEKLY -> {
                val days = if (rule.weekdays.isEmpty()) "" else
                    "周" + rule.weekdays.distinct().sorted().joinToString("、") { WEEK_CN[it] }
                when {
                    rule.interval == 1 && days.isNotEmpty() -> "每$days"
                    rule.interval == 1 -> "每周"
                    days.isNotEmpty() -> "每${rule.interval}周 $days"
                    else -> "每${rule.interval}周"
                }
            }
            RepeatFreq.MONTHLY -> if (rule.interval == 1) "每月" else "每${rule.interval}个月"
            RepeatFreq.YEARLY -> if (rule.interval == 1) "每年" else "每${rule.interval}年"
            RepeatFreq.NONE -> return null
        }
        val suffix = buildString {
            if (rule.count > 0) append("（共${rule.count}次）")
            if (rule.until > 0) append("（至${formatDate(rule.until, "yyyy/M/d")}）")
        }
        return base + suffix
    }

    /** 日程的重复描述（供列表展示） */
    fun describe(schedule: ScheduleEntity): String? = describe(parse(schedule.repeatRule))

    // ---------------- 展开 ----------------

    /**
     * 把一条日程（可能是重复序列）展开到 [rangeStart, rangeEnd] 区间内。
     * 非重复日程若落在区间内则原样返回。count 语义为「序列自首次起的第 N 次」。
     */
    fun expand(one: ScheduleEntity, rangeStart: Long, rangeEnd: Long, maxInstances: Int = 2000): List<ScheduleEntity> {
        val rule = parse(one.repeatRule)
        if (rule == null) {
            return if (one.startTime in rangeStart..rangeEnd) listOf(one) else emptyList()
        }
        val duration = (one.endTime - one.startTime).coerceAtLeast(0L)
        val base = toDateTime(one.startTime)
        val untilMillis = if (rule.until > 0) dayEndMillis(rule.until) else Long.MAX_VALUE
        val result = ArrayList<ScheduleEntity>()
        var guard = 0

        /** 收下一个候选实例；返回 false 表示应当终止迭代 */
        fun accept(occ: Long, n: Int): Boolean {
            if (occ > rangeEnd) return false
            if (rule.count > 0 && n > rule.count) return false   // 超出次数上限
            if (occ > untilMillis) return false                  // 超出结束日期
            if (occ in rangeStart..rangeEnd && occ !in rule.exdates) {
                // 展开时把「此实例已完成」合入 isCompleted，UI 只需读一个字段
                result += one.copy(
                    startTime = occ,
                    endTime = occ + duration,
                    isCompleted = one.isCompleted || occ in rule.doneDates
                )
            }
            return true
        }

        val maxLoops = maxInstances * 4
        when (rule.freq) {
            RepeatFreq.DAILY -> {
                val step = rule.interval.toLong()
                var k = fastForwardSteps(base, toDateTime(rangeStart), step)
                while (guard++ < maxLoops) {
                    val occ = toMillis(base.plusDays(k * step))
                    if (!accept(occ, (k + 1).toInt())) break
                    k++
                }
            }

            RepeatFreq.WEEKLY -> {
                val days = if (rule.weekdays.isEmpty()) listOf(base.dayOfWeek.value)
                else rule.weekdays.distinct().sorted()
                val baseWeekStart = base.toLocalDate().minusDays((base.dayOfWeek.value - 1).toLong())
                // 首周中早于起始日的星期不会产生实例
                val preBase = days.count { it < base.dayOfWeek.value }
                var w = fastForwardWeeks(baseWeekStart, toDateTime(rangeStart), rule.interval)
                var n: Int = ((w * days.size).toInt() - preBase).coerceAtLeast(0)
                loop@ while (guard++ < maxLoops) {
                    for (dow in days) {
                        val date = baseWeekStart.plusWeeks(w * rule.interval.toLong()).plusDays((dow - 1).toLong())
                        val occ = toMillis(LocalDateTime.of(date, base.toLocalTime()))
                        if (occ < one.startTime) continue     // 早于序列起点
                        n++
                        if (!accept(occ, n)) break@loop
                    }
                    w++
                }
            }

            RepeatFreq.MONTHLY -> {
                var k = fastForwardMonths(base, toDateTime(rangeStart), rule.interval)
                while (guard++ < maxLoops) {
                    val monthStart = base.toLocalDate().withDayOfMonth(1).plusMonths(k * rule.interval.toLong())
                    val day = base.dayOfMonth.coerceAtMost(monthStart.lengthOfMonth())
                    val occ = toMillis(LocalDateTime.of(monthStart.withDayOfMonth(day), base.toLocalTime()))
                    if (!accept(occ, (k + 1).toInt())) break
                    k++
                }
            }

            RepeatFreq.YEARLY -> {
                var k = fastForwardYears(base, toDateTime(rangeStart), rule.interval)
                while (guard++ < maxLoops) {
                    val date = base.toLocalDate().plusYears(k * rule.interval.toLong())
                    val occ = toMillis(LocalDateTime.of(date, base.toLocalTime()))
                    if (!accept(occ, (k + 1).toInt())) break
                    k++
                }
            }

            RepeatFreq.NONE -> Unit
        }
        return result
    }

    /** 批量展开 + 按开始时间排序 */
    fun expandAll(list: List<ScheduleEntity>, rangeStart: Long, rangeEnd: Long): List<ScheduleEntity> =
        list.flatMap { expand(it, rangeStart, rangeEnd) }.sortedBy { it.startTime }

    /** UI 列表 key（同一序列的不同实例需区分） */
    fun instanceKey(s: ScheduleEntity): String = "s${s.id}_${s.startTime}"

    /**
     * 编辑重复序列中的某次实例时：保留序列起始日期（相位），仅采用被编辑实例的时间点，
     * 否则整条序列会被挪到该实例所在日期，导致此前的实例丢失。
     */
    fun rebaseToSeriesDate(seriesStart: Long, editedStart: Long, allDay: Boolean): Long {
        val seriesDate = toDateTime(seriesStart).toLocalDate()
        val time = if (allDay) java.time.LocalTime.MIDNIGHT else toDateTime(editedStart).toLocalTime()
        return toMillis(LocalDateTime.of(seriesDate, time))
    }

    /** 该日程是否为重复序列的实例 */
    fun isRepeating(s: ScheduleEntity): Boolean = parse(s.repeatRule)?.isRepeating == true

    // ---------------- 快速跳过（保持 count 语义：跳过的步数即已发生的次数） ----------------

    private fun fastForwardSteps(base: LocalDateTime, from: LocalDateTime, stepDays: Long): Long {
        if (stepDays <= 0 || !from.isAfter(base)) return 0
        val days = java.time.temporal.ChronoUnit.DAYS.between(base.toLocalDate(), from.toLocalDate())
        return ((days / stepDays) - 1).coerceAtLeast(0)
    }

    private fun fastForwardWeeks(baseWeekStart: java.time.LocalDate, from: LocalDateTime, interval: Int): Long {
        if (!from.toLocalDate().isAfter(baseWeekStart)) return 0
        val weeks = java.time.temporal.ChronoUnit.DAYS.between(baseWeekStart, from.toLocalDate()) / 7
        return ((weeks / interval.coerceAtLeast(1)) - 1).coerceAtLeast(0)
    }

    private fun fastForwardMonths(base: LocalDateTime, from: LocalDateTime, interval: Int): Long {
        if (!from.isAfter(base)) return 0
        val months = java.time.temporal.ChronoUnit.MONTHS.between(
            base.toLocalDate().withDayOfMonth(1), from.toLocalDate().withDayOfMonth(1)
        )
        return ((months / interval.coerceAtLeast(1)) - 1).coerceAtLeast(0)
    }

    private fun fastForwardYears(base: LocalDateTime, from: LocalDateTime, interval: Int): Long {
        if (!from.isAfter(base)) return 0
        val years = java.time.temporal.ChronoUnit.YEARS.between(base.toLocalDate(), from.toLocalDate())
        return ((years / interval.coerceAtLeast(1)) - 1).coerceAtLeast(0)
    }
}
