package com.example.personalworkbench.ui.navigation

import androidx.annotation.DrawableRes
import com.example.personalworkbench.R
import java.net.URLEncoder

/** 底部导航 Tab (PRD 十二章 / UI规范 2.2): 首页/待办/笔记/日程/我的
 *  V1.3.0 起图标改为彩色拟物风格矢量图（保留原色渲染，不做单色 tint）。 */
sealed class TopLevelRoute(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    data object Home : TopLevelRoute("home", "首页", R.drawable.ic_nav_home_color)
    data object Todo : TopLevelRoute("todo", "待办", R.drawable.ic_nav_todo_color)
    data object Note : TopLevelRoute("notes", "笔记", R.drawable.ic_nav_note_color)
    data object Calendar : TopLevelRoute("calendar", "日程", R.drawable.ic_nav_calendar_color)
    data object Settings : TopLevelRoute("settings", "我的", R.drawable.ic_nav_me_color)
}

/** 全部路由常量 */
object Routes {
    const val HOME = "home"
    const val TODO = "todo"
    const val NOTES = "notes"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    const val FILES = "files"
    const val NOTE_EDITOR = "noteEditor/{noteId}"
    const val GOAL_DETAIL = "goalDetail/{goalId}"
    const val TODO_DETAIL = "todoDetail/{taskId}"
    const val RECYCLE_BIN = "recycleBin"
    /** 备份与恢复（V1.4.7）：自动备份列表 + 从文件恢复/导出，统一入口 */
    const val BACKUPS = "backups"
    const val PROJECT_LIST = "projectList"
    const val PROJECT_DETAIL = "projectDetail/{projectId}"
    const val APP_LOCK = "appLock"
    const val SEARCH = "search"
    /** AI 助手（V1.5.2）：支持从首页命令栏带指令进入，q 为可选初始指令 */
    const val AI_CHAT = "aiChat?q={q}"
    const val REPORTS = "reports"
    const val LOG_FILL = "logFill/{date}"
    /** 工作日志列表 / 阅读页（V1.4.1） */
    const val LOGS = "logs"

    fun noteEditor(noteId: Long) = "noteEditor/$noteId"
    fun goalDetail(goalId: Long) = "goalDetail/$goalId"
    fun todoDetail(taskId: Long) = "todoDetail/$taskId"
    fun projectDetail(projectId: Long) = "projectDetail/$projectId"
    fun logFill(date: Long) = "logFill/$date"
    /** 进入 AI 助手并携带初始指令（首页命令栏的语音/文字指令），空字符串等同无指令 */
    fun aiChat(q: String = "") = "aiChat?q=" + URLEncoder.encode(q, "UTF-8")
}
