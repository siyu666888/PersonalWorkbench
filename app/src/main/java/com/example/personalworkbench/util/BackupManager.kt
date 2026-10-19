package com.example.personalworkbench.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personalworkbench.data.local.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地备份/恢复 (PRD 4.8)。
 *
 * V1.4.0 起：备份内容从「整库二进制 zip」升级为「可读 JSON 快照」。
 * - 快照是标准的 JSON 对象：每个数据表对应一个数组，每行是一个列名→值的对象；
 *   另含一个 `_meta` 字段记录导出时间，便于人工查看与排错。
 * - 数据库无关列类型由游标推断（INTEGER/FLOAT/TEXT/BLOB/NULL），恢复时按类型回绑，
 *   因此**不需要**为每个实体手写序列化代码，未来新增表也会自动纳入备份。
 * - 恢复通过 Room 同一连接 (`openHelper.writableDatabase`) 执行 `DELETE + INSERT`，
 *   Room 的 InvalidationTracker 会感知变更并即时刷新各页面，无需重启。
 *
 * 本地优先、无云端依赖；恢复后需重启应用以重新打开数据库（仅文件级覆盖场景，JSON 恢复无需重启）。
 */
object BackupManager {

    private val dbName = AppDatabase.DATABASE_NAME

    /** 不参与快照/恢复的表（SQLite / Room 内部表） */
    private val INTERNAL_TABLES = setOf("android_metadata", "sqlite_sequence", "room_master_table")

    // ===== 每日自动备份（V1.4.0：JSON 快照）=====

    /** 自动备份目录：App 私有外部存储，无需任何权限，用户可通过文件管理器/USB 访问 */
    private fun autoBackupDir(context: Context): File =
        File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    /** 备份文件名：workbench-backup-yyyyMMdd.json（含日期，便于识别与按日期清理） */
    internal fun autoBackupName(dateMillis: Long): String =
        "workbench-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US)
            .format(Date(dateMillis)) + ".json"

    /** 自动备份保留天数：最多 7 天，过期删除 */
    const val AUTO_BACKUP_KEEP_DAYS = 7

    /**
     * 每日自动备份：把全部数据表导出为 JSON 快照写到 [autoBackupDir]，文件名含当天日期。
     * - 同一天重复调用会覆盖当天文件（「覆盖当天之前的记录」）；
     * - 顺带清理 [AUTO_BACKUP_KEEP_DAYS] 天前的旧备份。
     * 返回是否成功；任何 IO 异常都不抛出（自动任务不能影响启动）。
     */
    fun autoBackup(context: Context, db: SupportSQLiteDatabase, todayMillis: Long): Boolean = runCatching {
        val dir = autoBackupDir(context)
        val target = File(dir, autoBackupName(todayMillis))
        target.writeText(snapshotJson(db))
        pruneOldBackups(dir, todayMillis)
        true
    }.getOrDefault(false)

    /** 清理过期自动备份：保留最近 [AUTO_BACKUP_KEEP_DAYS] 天（含当天） */
    private fun pruneOldBackups(dir: File, todayMillis: Long) {
        val cutoff = todayMillis - AUTO_BACKUP_KEEP_DAYS * 86_400_000L
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        dir.listFiles()?.forEach { f ->
            if (!f.name.startsWith("workbench-backup-") || !f.name.endsWith(".json")) return@forEach
            val datePart = f.name.removePrefix("workbench-backup-").removeSuffix(".json")
            val fileDate = runCatching { fmt.parse(datePart)?.time }.getOrNull() ?: return@forEach
            if (fileDate < cutoff) f.delete()
        }
    }

    /** 列出当前自动备份（新的在前），供设置页展示 */
    fun listAutoBackups(context: Context): List<File> =
        autoBackupDir(context).listFiles()
            ?.filter { it.name.startsWith("workbench-backup-") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 删除一条自动备份文件（备份列表「删除」用） */
    fun deleteAutoBackup(file: File): Boolean = file.delete()

    /** 从本机自动备份文件恢复（备份列表「恢复」用；与 [restore] 同一套 JSON 反序列化逻辑） */
    fun restoreFromFile(db: SupportSQLiteDatabase, file: File): Result<Boolean> = runCatching {
        restoreFromJson(db, file.readText())
        true
    }

    // ===== 手动备份 / 恢复（SAF，JSON 快照）=====

    /** 导出可读 JSON 快照到用户选定的 SAF 位置（不再打包二进制 db） */
    fun export(context: Context, db: SupportSQLiteDatabase, uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.bufferedWriter().use { it.write(snapshotJson(db)) }
        } ?: error("无法写入所选位置")
    }

    /** 从用户选定的 JSON 快照恢复（覆盖式：先清空各表再插入） */
    fun restore(context: Context, db: SupportSQLiteDatabase, uri: Uri): Result<Boolean> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: error("无法读取所选备份")
        restoreFromJson(db, json)
        true
    }

    // ===== JSON 快照核心：通用、与具体表结构解耦 =====

    /** 把当前数据库所有用户表导出为可读 JSON 字符串 */
    fun snapshotJson(db: SupportSQLiteDatabase): String {
        val root = JSONObject()
        for (table in userTables(db)) {
            val arr = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor ->
                val n = cursor.columnCount
                val names = (0 until n).map { cursor.getColumnName(it) }
                while (cursor.moveToNext()) {
                    val obj = JSONObject()
                    for (i in 0 until n) {
                        val name = names[i]
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> obj.put(name, cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> obj.put(name, cursor.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> obj.put(name, cursor.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> obj.put(
                                name, Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP)
                            )
                        }
                    }
                    arr.put(obj)
                }
            }
            root.put(table, arr)
        }
        root.put(
            "_meta", JSONObject()
                .put("schema", AppDatabase.DATABASE_NAME)
                .put("exportedAt", System.currentTimeMillis())
        )
        return root.toString(2)
    }

    /** 从 JSON 快照恢复：按表 DELETE 后逐行 INSERT（事务内完成） */
    fun restoreFromJson(db: SupportSQLiteDatabase, json: String) {
        val root = JSONObject(json)
        val tables = userTables(db)
        db.beginTransaction()
        try {
            for (table in tables) {
                if (!root.has(table)) continue
                val arr = root.getJSONArray(table)
                db.execSQL("DELETE FROM `$table`")
                for (j in 0 until arr.length()) {
                    val obj = arr.getJSONObject(j)
                    val cols = obj.keys().asSequence().toList()
                    if (cols.isEmpty()) continue
                    val colList = cols.joinToString(",") { "`$it`" }
                    val placeholders = cols.joinToString(",") { "?" }
                    val stmt = db.compileStatement("INSERT INTO `$table` ($colList) VALUES ($placeholders)")
                    stmt.clearBindings()
                    cols.forEachIndexed { idx, col ->
                        val v = obj.get(col)
                        when {
                            v === JSONObject.NULL -> stmt.bindNull(idx + 1)
                            v is Boolean -> stmt.bindLong(idx + 1, if (v) 1L else 0L)
                            v is Int -> stmt.bindLong(idx + 1, v.toLong())
                            v is Long -> stmt.bindLong(idx + 1, v)
                            v is Double -> stmt.bindDouble(idx + 1, v)
                            v is Float -> stmt.bindDouble(idx + 1, v.toDouble())
                            v is String -> stmt.bindString(idx + 1, v)
                            v is ByteArray -> stmt.bindBlob(idx + 1, v)
                            else -> stmt.bindString(idx + 1, v.toString())
                        }
                    }
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 列出数据库中的用户表（排除内部表），自动适配未来新增表 */
    private fun userTables(db: SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name !in INTERNAL_TABLES) tables.add(name)
            }
        }
        return tables
    }
}
