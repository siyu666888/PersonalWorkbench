package com.example.personalworkbench.crash

import android.content.Context
import android.os.Build
import com.example.personalworkbench.util.formatDateTime
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志的本地存取 (V1.3.3)
 *
 * 位置：`filesDir/crash/crash-<时间戳>.txt`，**只写本机、绝不上传**，用于事后诊断。
 * 最多保留 [MAX_KEEP] 份，超出按修改时间删最旧的，避免无限增长占满存储。
 */
object CrashLogStore {

    /** 最多保留多少份崩溃日志 */
    const val MAX_KEEP = 10

    private const val DIR_NAME = "crash"
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"

    /** 文件名用的时间格式；显式指定 Locale.US，避免部分语言环境出现非阿拉伯数字 */
    private val FILE_TIME = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 已保存的崩溃日志，新的在前 */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * 写入一份崩溃日志。
     * 任何失败都只返回 null——**记录日志本身绝不能影响/加重原有的崩溃流程**。
     */
    fun save(context: Context, thread: Thread, error: Throwable): File? = runCatching {
        val now = System.currentTimeMillis()
        val file = File(dir(context), PREFIX + FILE_TIME.format(Date(now)) + SUFFIX)
        file.writeText(render(context, thread, error, now))
        prune(context)
        file
    }.getOrNull()

    fun read(file: File): String = runCatching { file.readText() }
        .getOrElse { "读取失败：${it.message}" }

    fun delete(file: File): Boolean = file.delete()

    fun clear(context: Context): Int {
        val files = list(context)
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }

    /** 把 `crash-yyyyMMdd-HHmmss-SSS.txt` 转成 `yyyy/MM/dd HH:mm:ss` 供 UI 展示；不认识的原样返回 */
    fun displayName(fileName: String): String {
        val raw = fileName.removePrefix(PREFIX).removeSuffix(SUFFIX)
        if (raw.length < 15) return fileName
        return "${raw.substring(0, 4)}/${raw.substring(4, 6)}/${raw.substring(6, 8)} " +
            "${raw.substring(9, 11)}:${raw.substring(11, 13)}:${raw.substring(13, 15)}"
    }

    /** 只保留最近 MAX_KEEP 份 */
    private fun prune(context: Context) {
        val files = list(context)
        if (files.size <= MAX_KEEP) return
        files.drop(MAX_KEEP).forEach { runCatching { it.delete() } }
    }

    private fun render(context: Context, thread: Thread, error: Throwable, now: Long): String = buildString {
        appendLine("==== PersonalWorkbench 崩溃报告 ====")
        appendLine("时间：${formatDateTime(now, "yyyy-MM-dd HH:mm:ss.SSS")}")
        appendLine("线程：${thread.name}")
        appendLine()
        appendLine("---- 应用 ----")
        appendLine("版本：${versionName(context)} (${versionCode(context)})")
        appendLine()
        appendLine("---- 设备 ----")
        appendLine("厂商/型号：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("---- 异常堆栈 ----")
        append(stackTraceOf(error))
    }

    /** 完整异常链（含 cause）的堆栈文本 */
    private fun stackTraceOf(error: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            var cur: Throwable? = error
            val seen = HashSet<Throwable>()
            while (cur != null && seen.add(cur)) {
                pw.println(cur.toString())
                cur.stackTrace.forEach { pw.println("\tat $it") }
                cur = cur.cause
            }
        }
        return sw.toString()
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private fun versionCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(-1L)
}
