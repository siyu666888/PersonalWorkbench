package com.example.personalworkbench.ui.logs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalworkbench.util.MarkdownUtil
import com.example.personalworkbench.util.MdBlock

/**
 * 工作日志 Markdown 预览（V1.4.1）
 * 把日志正文按块渲染：标题/列表/引用/分隔线/代码/表格/统计图。
 */
@Composable
fun LogMarkdownPreview(
    text: String,
    chartData: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val blocks = MarkdownUtil.parse(text)
    val colors = MaterialTheme.colorScheme
    val codeBg = colors.surfaceContainerHighest

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (blocks.isEmpty()) {
            Text(
                "（还没有内容，左侧输入后切到「预览」查看排版效果）",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.outline
            )
        }
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    MarkdownUtil.inline(b.text),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )

                is MdBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (b.ordered) "${b.index}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                        modifier = Modifier.width(if (b.ordered) 22.dp else 14.dp)
                    )
                    Text(
                        MarkdownUtil.inline(b.text, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                }

                is MdBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .background(colors.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        MarkdownUtil.inline(b.text, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                is MdBlock.Paragraph -> Text(
                    MarkdownUtil.inline(b.text, codeBg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )

                is MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outlineVariant)
                )

                is MdBlock.CodeBlock -> Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        b.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.onSurfaceVariant
                    )
                }

                is MdBlock.Table -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    b.rows.forEachIndexed { idx, cells ->
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            cells.forEach { cell ->
                                Text(
                                    cell,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (idx == 0) colors.onSurface else colors.onSurfaceVariant,
                                    modifier = Modifier.width(88.dp).padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                is MdBlock.Chart -> MiniBarChart(
                    title = if (b.range.startsWith("month")) "近 30 天日志字数" else "近 7 天日志字数",
                    data = chartData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 迷你柱状图（插入到日志里的图表块）
 * [data] 为 (标签, 数值) 列表，数值按比例映射高度。
 */
@Composable
fun MiniBarChart(
    title: String,
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                "合计 ${data.sumOf { it.second }} 字",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
        Spacer(Modifier.height(10.dp))
        if (data.isEmpty() || data.all { it.second == 0 }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无日志数据", style = MaterialTheme.typography.labelSmall, color = emptyColor)
            }
        } else {
            val max = (data.maxOf { it.second }).coerceAtLeast(1)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val n = data.size
                if (n == 0) return@Canvas
                val slot = size.width / n
                val barW = (slot * 0.52f).coerceAtMost(26.dp.toPx())
                data.forEachIndexed { i, (_, v) ->
                    val ratio = v.toFloat() / max
                    val h = (size.height * 0.82f * ratio).coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)
                    val left = slot * i + (slot - barW) / 2f
                    drawRoundRect(
                        color = if (v > 0) barColor else trackColor,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barW, h.coerceAtLeast(1.dp.toPx())),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                data.forEach { (label, _) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = labelColor,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
