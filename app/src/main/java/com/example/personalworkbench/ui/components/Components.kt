package com.example.personalworkbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalworkbench.ui.theme.LocalSemanticColors

/**
 * 通用顶部应用栏 (UI规范 2.1)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PWTopAppBar(
    title: String,
    navigationIcon: ComComposableUnit? = null,
    actions: ComComposableUnit? = null
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

/** 空状态 (UI规范 11.3): 插画占位 + 文案 + 引导按钮 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = EmptyState(rememberVectorPainter(icon), title, subtitle, actionLabel, onAction, modifier)

/** 空状态（彩色矢量图标重载，G-3）：直接传入 drawable 的 Painter，保留其原始渐变着色 */
@Composable
fun EmptyState(
    painter: Painter,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // 彩色图标不做 tint，否则渐变会被单色覆盖
                Icon(painter, contentDescription = null, modifier = Modifier.size(56.dp),
                    tint = Color.Unspecified)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text(actionLabel)
            }
        }
    }
}

/** 确认对话框 (通用交互 11.5) */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onDismiss) { Text(dismissLabel) } },
        shape = MaterialTheme.shapes.large
    )
}

// 简单函数类型别名，避免重复写 @Composable () -> Unit
typealias ComComposableUnit = @Composable () -> Unit

/**
 * 生动场景插画：笔记本 + 铅笔（品牌绿），用于笔记/日志空状态 (V1.5.2 视觉规范·四)。
 * 纯 Canvas 绘制，无外部资源依赖，深浅主题均可读。
 */
@Composable
fun NotebookIllustration(modifier: Modifier = Modifier) {
    val semantic = LocalSemanticColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val paperW = w * 0.60f
        val paperH = h * 0.84f
        val px = w * 0.05f
        val py = (h - paperH) / 2f
        // 投影感
        drawRoundRect(
            color = semantic.success.copy(alpha = 0.14f),
            topLeft = Offset(px + 8f, py + 10f),
            size = Size(paperW, paperH),
            cornerRadius = CornerRadius(16f)
        )
        // 纸面
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px, py),
            size = Size(paperW, paperH),
            cornerRadius = CornerRadius(16f)
        )
        // 左侧装订绿条
        drawRoundRect(
            color = semantic.success,
            topLeft = Offset(px, py),
            size = Size(paperW * 0.16f, paperH),
            cornerRadius = CornerRadius(16f, 0f)
        )
        // 横线条纹
        val lineColor = Color(0xFFD7D2E0)
        repeat(5) { i ->
            val ly = py + paperH * (0.30f + i * 0.125f)
            drawLine(
                color = lineColor,
                start = Offset(px + paperW * 0.24f, ly),
                end = Offset(px + paperW * 0.90f, ly),
                strokeWidth = 3f
            )
        }
        // 铅笔（旋转）
        val penLen = h * 0.52f
        val penW = w * 0.11f
        val cx = px + paperW * 1.04f
        val cy = py + paperH * 0.60f
        rotate(-32f, Offset(cx, cy)) {
            drawRoundRect(
                color = semantic.success,
                topLeft = Offset(cx - penW / 2f, cy - penLen / 2f),
                size = Size(penW, penLen),
                cornerRadius = CornerRadius(penW / 2f)
            )
            // 木质笔尖
            val tip = Path().apply {
                moveTo(cx - penW / 2f, cy + penLen / 2f)
                lineTo(cx + penW / 2f, cy + penLen / 2f)
                lineTo(cx, cy + penLen / 2f + penW * 1.15f)
                close()
            }
            drawPath(tip, color = Color(0xFFE0A458))
            // 石墨尖
            val lead = Path().apply {
                moveTo(cx - penW * 0.30f, cy + penLen / 2f + penW * 0.5f)
                lineTo(cx + penW * 0.30f, cy + penLen / 2f + penW * 0.5f)
                lineTo(cx, cy + penLen / 2f + penW * 1.15f)
                close()
            }
            drawPath(lead, color = Color(0xFF3A3A3A))
            // 橡皮头
            drawRoundRect(
                color = Color(0xFFFFC1CC),
                topLeft = Offset(cx - penW / 2f, cy - penLen / 2f - penW * 0.55f),
                size = Size(penW, penW * 0.65f),
                cornerRadius = CornerRadius(penW / 2f, penW / 2f)
            )
        }
    }
}

/** 笔记/日志空状态：生动书写场景 + 文案 + 引导按钮 (V1.5.2 视觉规范·四·空状态强化) */
@Composable
fun WriteSceneEmptyState(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(148.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NotebookIllustration(Modifier.size(104.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f),
                colors = ButtonDefaults.buttonColors(containerColor = LocalSemanticColors.current.success)) {
                Text(actionLabel)
            }
        }
    }
}

/** 文件盒生动插画（V1.5.5 模块七）：敞开的品牌绿文件夹 + 探出的文档 + 回形针 */
@Composable
fun FileBoxIllustration(modifier: Modifier = Modifier) {
    val semantic = LocalSemanticColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 投影感
        drawRoundRect(
            color = semantic.success.copy(alpha = 0.14f),
            topLeft = Offset(w * 0.10f + 6f, h * 0.30f + 8f),
            size = Size(w * 0.80f, h * 0.56f),
            cornerRadius = CornerRadius(14f)
        )
        // 探出的两张文档（后层先画）
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.22f, h * 0.14f),
            size = Size(w * 0.24f, h * 0.34f),
            cornerRadius = CornerRadius(6f)
        )
        drawRoundRect(
            color = Color(0xFFF3F6F4),
            topLeft = Offset(w * 0.50f, h * 0.10f),
            size = Size(w * 0.26f, h * 0.38f),
            cornerRadius = CornerRadius(6f)
        )
        // 文档上的字行
        repeat(3) { i ->
            drawLine(
                color = Color(0xFFC9D4CE),
                start = Offset(w * (0.26f + i * 0.02f), h * (0.22f + i * 0.07f)),
                end = Offset(w * (0.40f - i * 0.02f), h * (0.22f + i * 0.07f)),
                strokeWidth = 2.5f
            )
        }
        repeat(3) { i ->
            drawLine(
                color = Color(0xFFD3DDD7),
                start = Offset(w * (0.55f + i * 0.02f), h * (0.19f + i * 0.08f)),
                end = Offset(w * (0.71f - i * 0.02f), h * (0.19f + i * 0.08f)),
                strokeWidth = 2.5f
            )
        }
        // 文件夹后片
        drawRoundRect(
            color = semantic.success.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.10f, h * 0.34f),
            size = Size(w * 0.80f, h * 0.46f),
            cornerRadius = CornerRadius(14f)
        )
        // 文件夹前片（品牌绿）
        drawRoundRect(
            color = semantic.success,
            topLeft = Offset(w * 0.10f, h * 0.42f),
            size = Size(w * 0.80f, h * 0.38f),
            cornerRadius = CornerRadius(14f)
        )
        // 前片高光
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.10f, h * 0.42f),
            size = Size(w * 0.80f, h * 0.10f),
            cornerRadius = CornerRadius(14f)
        )
        // 回形针（旋转小圆环 + 尾）
        val clipCx = w * 0.68f
        val clipCy = h * 0.40f
        rotate(-24f, Offset(clipCx, clipCy)) {
            drawRoundRect(
                color = Color(0xFF9AA5A0),
                topLeft = Offset(clipCx - w * 0.035f, clipCy - h * 0.11f),
                size = Size(w * 0.07f, h * 0.22f),
                cornerRadius = CornerRadius(w * 0.035f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(clipCx - w * 0.018f, clipCy - h * 0.085f),
                size = Size(w * 0.036f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.018f)
            )
        }
    }
}

/** 文件管理空状态：生动文件盒场景 + 文案 + 引导按钮 (V1.5.5 视觉规范·七·空状态强化) */
@Composable
fun FileBoxEmptyState(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(148.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FileBoxIllustration(Modifier.size(104.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth(0.6f),
                colors = ButtonDefaults.buttonColors(containerColor = LocalSemanticColors.current.success)) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * 圆形勾选框（V1.3.5）：把 Material3 默认方形 Checkbox 换成圆形，
 * 选中时填充主色并显示白色对勾，未选中时仅描边。用于待办 / 日程 / 目标里程碑的「完成」勾选。
 */
@Composable
fun CircleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}
