package com.example.personalworkbench.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范 (UI规范 1.3 / Figma标注规范 2.4)
 * 卡片 16dp / 弹窗 28dp / 按钮 20dp / Chip 8dp / 输入框 4dp / 头像 50%
 */
val PersonalWorkbenchShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // 输入框底部圆角 (sys-radius-xs)
    small = RoundedCornerShape(8.dp),        // Chip / 标签 (sys-radius-sm)
    medium = RoundedCornerShape(16.dp),      // 卡片 / 列表项 (sys-radius-md)
    large = RoundedCornerShape(28.dp),       // 弹窗 / Bottom Sheet (sys-radius-lg)
    extraLarge = RoundedCornerShape(28.dp)   // Large FAB (sys-radius-xl)
)
