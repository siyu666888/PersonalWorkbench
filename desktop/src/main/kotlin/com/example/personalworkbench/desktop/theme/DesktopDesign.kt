package com.example.personalworkbench.desktop.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 桌面版设计系统（V1.4.1 重设计）
 *
 * 与 Android（移动端）版刻意区分：
 *  - **排版更紧凑**：移动端正文 16sp，桌面端 13–14sp，信息密度更适合大屏与鼠标操作；
 *  - **行高更松**：中文正文行高统一 1.55–1.6，避免小字号下拥挤；
 *  - **圆角更柔和**：卡片 12dp、输入 8dp、按钮 8dp（桌面不适用移动端 16/20/28 的大圆角）。
 */
object DesktopDesign {
    /** 8pt 间距节奏 */
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 24.dp
    val spaceXxl = 32.dp

    /** 内容区最大宽度（避免超宽屏一行拉太长） */
    val contentMaxWidth = 1160.dp
    val sidebarWidth = 232.dp
    val cardRadius = 12.dp
}

val DesktopShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(DesktopDesign.cardRadius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

/** 桌面端字体：优先系统中文字体，回退默认 */
private val DesktopFont = FontFamily.SansSerif

private fun t(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = DesktopFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

val DesktopTypography = Typography(
    displayLarge = t(34, 42, FontWeight.Bold),
    displayMedium = t(30, 38, FontWeight.Bold),
    displaySmall = t(26, 34, FontWeight.Bold),

    headlineLarge = t(24, 32, FontWeight.SemiBold),
    headlineMedium = t(21, 29, FontWeight.SemiBold),
    headlineSmall = t(19, 27, FontWeight.SemiBold),

    titleLarge = t(17, 24, FontWeight.SemiBold),
    titleMedium = t(15, 22, FontWeight.SemiBold),
    titleSmall = t(14, 20, FontWeight.Medium),

    bodyLarge = t(14, 22),
    bodyMedium = t(13, 20),
    bodySmall = t(12, 18),

    labelLarge = t(13, 18, FontWeight.Medium),
    labelMedium = t(12, 16, FontWeight.Medium),
    labelSmall = t(11, 15)
)
