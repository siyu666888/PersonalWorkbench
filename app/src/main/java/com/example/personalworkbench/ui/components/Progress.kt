package com.example.personalworkbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.ui.theme.LocalSemanticColors

/** 优先级圆点 (UI规范 4.3): 高=红 中=橙 低=绿 */
@Composable
fun PriorityDot(priority: Priority, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val color = when (priority) {
        Priority.HIGH -> LocalSemanticColors.current.danger
        Priority.MEDIUM -> LocalSemanticColors.current.warning
        Priority.LOW -> LocalSemanticColors.current.success
    }
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** 优先级 -> 文字标签 */
fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
}

/** 环形进度条 (UI规范 7.3): 颜色随完成度动态变化 <30% 红 / 30-70% 橙 / >70% 主色 / 100% 绿 */
@Composable
fun RingProgress(
    percent: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    stroke: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val semantic = LocalSemanticColors.current
    val color = when {
        percent >= 100 -> semantic.success
        percent > 70 -> MaterialTheme.colorScheme.primary
        percent >= 30 -> semantic.warning
        else -> semantic.danger
    }
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val r = (size.minDimension - stroke.toPx()) / 2f
            drawCircle(color = trackColor, radius = r, style = Stroke(width = stroke.toPx()))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (percent / 100f),
                useCenter = false,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            )
        }
        Text("${if (percent > 100) 100 else percent}%", style = MaterialTheme.typography.labelSmall)
    }
}

/** 通用筛选 Chip (UI规范 2.8) */
@Composable
fun FilterChipItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun AssistChipItem(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}
