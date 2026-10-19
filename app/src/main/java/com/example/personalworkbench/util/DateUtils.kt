package com.example.personalworkbench.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow

/** 某时间戳所在日的 00:00 (毫秒) */
fun dayStartMillis(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun dayEndMillis(ts: Long): Long = dayStartMillis(ts) + 86_399_000L

/**
 * Material3 DatePicker 返回的 selectedDateMillis 是 **UTC 零点**，
 * 直接拿来当本地日期会因时区偏移差一天。这里换算回本地零点。
 */
fun datePickerMillisToLocal(utcMillis: Long): Long {
    val tz = TimeZone.getDefault()
    val cal = Calendar.getInstance().apply { timeInMillis = utcMillis }
    cal.add(Calendar.MILLISECOND, tz.getOffset(utcMillis))
    return cal.timeInMillis
}

fun monthStartMillis(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.DAY_OF_MONTH, 1)
    return dayStartMillis(c.timeInMillis)
}

/** 该时间戳所在周的周一 00:00 (毫秒)，中国习惯周一为一周之首 */
fun weekStartMillis(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val dow = c.get(Calendar.DAY_OF_WEEK) // SUNDAY=1, MONDAY=2, ...
    val offset = when (dow) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6 // SUNDAY
    }
    return c.timeInMillis - offset * 86_400_000L
}

fun addMonths(ts: Long, months: Int): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.add(Calendar.MONTH, months)
    return c.timeInMillis
}

fun formatDate(ts: Long?, pattern: String = "M月d日"): String =
    if (ts == null) "" else SimpleDateFormat(pattern, Locale.CHINA).format(Date(ts))

fun formatDateTime(ts: Long?, pattern: String = "M月d日 HH:mm"): String =
    if (ts == null) "" else SimpleDateFormat(pattern, Locale.CHINA).format(Date(ts))

fun formatTime(ts: Long?, pattern: String = "HH:mm"): String =
    if (ts == null) "" else SimpleDateFormat(pattern, Locale.CHINA).format(Date(ts))

fun formatMonth(ts: Long): String = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(ts))

/** 文件大小格式化 (字节 -> 人类可读) */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt()
    val idx = digitGroups.coerceIn(0, units.lastIndex)
    return String.format(Locale.CHINA, "%.1f %s", bytes / 1024.0.pow(idx), units[idx])
}

/** 该月共有多少天 */
fun daysInMonth(ts: Long): Int =
    Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.DAY_OF_MONTH, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

/**
 * 月视图 6x7 网格: 返回 42 个「当天 00:00」的毫秒时间戳，以周一为每周第一天（中国习惯）。
 */
fun monthGrid(ts: Long): List<Long> {
    val c = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDay = dayStartMillis(c.timeInMillis)
    val dayOfWeek = Calendar.getInstance().apply { timeInMillis = firstDay }.get(Calendar.DAY_OF_WEEK)
    val offset = when (dayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6 // SUNDAY
    }
    val start = firstDay - offset * 86_400_000L
    return (0 until 42).map { start + it * 86_400_000L }
}

/** 两个时间戳是否同一个月 */
fun isSameMonth(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
}

/**
 * 该时间戳所在「自然周」是今年的第几周（V1.3.8 年/月/周视图显示用）。
 * 约定：周一为一周之首（与 [weekStartMillis] 一致），第 1 周为「包含 1 月 1 日」的那一周。
 * 例：若 1 月 1 日是周五，则 12/29(周一)-1/4 这一周记为第 1 周。
 */
fun weekOfYear(ts: Long): Int {
    val ws = weekStartMillis(ts)
    val cal = Calendar.getInstance().apply { timeInMillis = ws }
    val year = cal.get(Calendar.YEAR)
    val jan1 = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val firstMonday = weekStartMillis(jan1)
    return ((ws - firstMonday) / (7L * 86_400_000L)).toInt() + 1
}
