package com.example.personalworkbench.util

import android.content.Context
import android.net.Uri
import com.example.personalworkbench.data.local.entities.FileCategory
import java.io.File
import java.util.UUID

/** 根据 MIME / 扩展名判断文件分类 (UI规范 8.2) */
fun categoryOf(name: String, mimeType: String?): FileCategory {
    val m = mimeType?.lowercase() ?: ""
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        m.startsWith("image/") -> FileCategory.IMAGE
        m.startsWith("audio/") -> FileCategory.AUDIO
        m == "application/pdf" || ext == "pdf" -> FileCategory.PDF
        m.startsWith("text/") || m in listOf(
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ) || ext in listOf("txt", "md", "doc", "docx", "xls", "xlsx", "csv") -> FileCategory.DOC
        m.startsWith("image/") -> FileCategory.IMAGE
        else -> FileCategory.OTHER
    }
}

/**
 * 将用户通过 SAF 选取的文件复制到 APP 私有目录 (files/files/<uuid>_<name>)。
 * 返回: Pair(绝对路径, 文件字节大小)。
 */
fun copyUriToInternal(context: Context, uri: Uri, displayName: String): Pair<String, Long> {
    val dir = File(context.filesDir, "files").apply { mkdirs() }
    val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val target = File(dir, "${UUID.randomUUID()}_$safeName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法打开所选文件")
    return target.absolutePath to target.length()
}

// ===== 目标封面图 (V1.4.2) =====

/**
 * 把用户通过相册/图片选择器选取的图片复制到 APP 私有目录 (files/goal_images/<uuid>.<ext>)。
 * 返回私有目录下的绝对路径；调用方应把该路径存入 GoalEntity.imagePath。
 * 私有目录随应用卸载而清除，不污染相册、也不依赖 READ_EXTERNAL_STORAGE 权限。
 */
fun saveGoalImage(context: Context, uri: Uri): String {
    val dir = File(context.filesDir, "goal_images").apply { mkdirs() }
    val ext = (context.contentResolver.getType(uri)?.split('/')?.lastOrNull() ?: "jpg")
        .let { if (it in listOf("jpeg", "jpg", "png", "webp", "gif")) it else "jpg" }
    val target = File(dir, "${UUID.randomUUID()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法打开所选图片")
    return target.absolutePath
}

/** 删除目标封面图文件（更换或移除时调用，避免残留占用空间） */
fun deleteGoalImage(path: String) {
    runCatching { File(path).takeIf { it.exists() }?.delete() }
}
