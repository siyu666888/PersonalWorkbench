package com.example.personalworkbench.desktop.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体规范 (UI规范 1.5 / Figma标注规范 2.5)
 * 字体: 思源黑体 (Source Han Sans / Noto Sans SC)
 *
 * V1.3.3 排版调整说明（中文场景）：
 * 1. **行高加大**：中文字形饱满且无升降部缓冲，Material 默认的 1.2~1.4 倍行高在中文里
 *    显得拥挤，正文与标题统一提到 1.5~1.7 倍，多行阅读明显更松快。
 * 2. **display 档位下调**：57sp 在中文下几乎不可用（一个词就占满屏），
 *    整体降到 24~36sp，与 headline 拉开差距但保留层级。
 * 3. **labelMedium 由 11sp 提到 12sp**：底部导航、徽标、Chip 大量使用该档，
 *    11sp 在低分屏上偏糊，12sp 是可读性与信息密度的平衡点。
 * 4. **labelSmall 保持 11sp**：全项目 60+ 处用于日期/标签等辅助信息，刻意压小以突出主内容。
 */
private val AppFontFamily = FontFamily.Default // 实际项目替换为 Noto Sans SC

val PersonalWorkbenchTypography = Typography(
    // ---- display（本项目未直接使用，保留档位以备大数字/空态插图）----
    displayLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),

    // ---- headline ----
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),

    // ---- title（页面标题 / 卡片标题）----
    titleLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 25.sp),
    titleSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp),

    // ---- body（正文；中文行高 1.6~1.65 倍）----
    bodyLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 19.sp),

    // ---- label（按钮 / 导航 / 徽标 / 辅助信息）----
    labelLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)
