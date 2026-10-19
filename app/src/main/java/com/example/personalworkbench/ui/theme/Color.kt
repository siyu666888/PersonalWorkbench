package com.example.personalworkbench.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 个人工作台 APP - 设计系统色板
 * 来源: UI设计规范 1.2「色彩系统」 + Figma标注规范 2.2「Color Token」
 * 种子色 (Seed) = #6750A4 (Material 3 动态色彩默认主色)
 */

// ---- Primitive (基础值) ----
val Purple100 = Color(0xFFEADDFF)
val Purple200 = Color(0xFFD0BCFF)
val Purple500 = Color(0xFF6750A4)
val Purple700 = Color(0xFF4F378B)
val Purple900 = Color(0xFF21005D)

val Surface0 = Color(0xFFFEF7FF)
val Surface1 = Color(0xFFF7F2FA)
val Surface2 = Color(0xFFE7E0EC)

val Error500 = Color(0xFFB3261E)
val Error300 = Color(0xFFF2B8B5)

// ---- 功能语义色 (UI规范 1.2) ----
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFF9800)
val Danger  = Color(0xFFF44336)
val Info   = Color(0xFF2196F3)

// ---- AI 语义色 (智能问答入口/对话) ----
val Ai = Color(0xFF6C4FD6)            // AI 主色 (紫罗兰)
val AiLight = Color(0xFF8B6FF0)       // 深色主题下的 AI 主色
val AiContainer = Color(0xFFECE6FF)   // 浅色主题 AI 容器/气泡
val AiContainerDark = Color(0xFF352A5E) // 深色主题 AI 容器/气泡

// ---- 强调色预设 (设置页可选, 0-7) ----
val AccentOptions = listOf(
    Color(0xFF6750A4), // 0 紫 (默认)
    Color(0xFF0B57D0), // 1 蓝
    Color(0xFF1E8E3E), // 2 绿
    Color(0xFFE37400), // 3 橙
    Color(0xFFD93025), // 4 红
    Color(0xFF00897B), // 5 青
    Color(0xFFC2185B), // 6 粉
    Color(0xFF4F5BD5)  // 7 靛
)
