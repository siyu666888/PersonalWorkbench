package com.example.personalworkbench.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.LocalSemanticColors
import com.example.personalworkbench.desktop.theme.PersonalWorkbenchTheme
import com.example.personalworkbench.desktop.ui.CalendarScreen
import com.example.personalworkbench.desktop.ui.GoalsScreen
import com.example.personalworkbench.desktop.ui.HomeScreen
import com.example.personalworkbench.desktop.ui.NotesScreen
import com.example.personalworkbench.desktop.ui.SettingsScreen
import com.example.personalworkbench.desktop.ui.TodoScreen
import kotlinx.coroutines.launch
import java.io.File

/** 桌面版导航目的地 */
enum class DesktopScreen(val label: String, val icon: ImageVector, val group: String) {
    HOME("首页", Icons.Outlined.Home, "工作台"),
    TODO("待办", Icons.Outlined.CheckCircle, "工作台"),
    NOTES("笔记", Icons.Outlined.Edit, "工作台"),
    CALENDAR("日程", Icons.Outlined.DateRange, "工作台"),
    GOALS("目标", Icons.Outlined.StarBorder, "规划"),
    SETTINGS("设置", Icons.Outlined.Settings, "规划")
}

fun main(args: Array<String>) {
    // 离屏自检：--selftest <输出目录> 渲染各页面为 PNG（不依赖显示器是否点亮，用于 UI 回归）
    val selfTest = args.indexOf("--selftest")
    if (selfTest >= 0) {
        val dir = File(args.getOrNull(selfTest + 1) ?: ".")
        DesktopScreen.entries.forEach { renderScreenTo(it, File(dir, "shot-${it.name.lowercase()}.png")) }
        println("selftest done -> ${dir.absolutePath}")
        return
    }
    runDesktopWindow(args)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun renderScreenTo(screen: DesktopScreen, out: File) {
    val scene = androidx.compose.ui.ImageComposeScene(
        width = 1280,
        height = 860,
        density = androidx.compose.ui.unit.Density(1f)
    ) {
        DesktopApp(screen)
    }
    val image = scene.render()
    val data = image.encodeToData()
    out.parentFile?.mkdirs()
    if (data != null) out.writeBytes(data.bytes)
    scene.close()
    println("rendered ${screen.name} -> ${out.absolutePath}")
}

private fun runDesktopWindow(args: Array<String>) = application {
    val startScreen = args.firstOrNull { it.startsWith("--start-screen=") }
        ?.substringAfter('=')
        ?.let { runCatching { DesktopScreen.valueOf(it.trim().uppercase()) }.getOrNull() }
        ?: DesktopScreen.HOME
    Window(
        onCloseRequest = ::exitApplication,
        title = "个人工作台 V1.4.0",
        alwaysOnTop = args.contains("--on-top"),
        state = rememberWindowState(width = 1280.dp, height = 860.dp)
    ) {
        DesktopApp(startScreen)
    }
}

@Composable
fun DesktopApp(initialScreen: DesktopScreen = DesktopScreen.HOME) {
    val store = remember { Store(File(System.getProperty("user.home"), ".personalworkbench")) }
    val settings by store.settings.collectAsState()
    val data by store.data.collectAsState()
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(initialScreen) }

    PersonalWorkbenchTheme(darkTheme = settings.darkTheme, accentIndex = settings.accentIndex) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DesktopSidebar(
                current = screen,
                onSelect = { screen = it },
                taskCount = data.tasks.count { it.status.name == "ACTIVE" },
                noteCount = data.notes.size,
                goalCount = data.goals.size
            )
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                val wide = maxWidth >= 900.dp
                Box(
                    modifier = Modifier
                        .widthIn(max = DesktopDesign.contentMaxWidth)
                        .fillMaxSize()
                        .padding(horizontal = if (wide) DesktopDesign.spaceXxl else DesktopDesign.spaceLg)
                ) {
                    when (screen) {
                        DesktopScreen.HOME -> HomeScreen(store = store, onNavigate = { screen = it })
                        DesktopScreen.TODO -> TodoScreen(store = store)
                        DesktopScreen.NOTES -> NotesScreen(store = store)
                        DesktopScreen.CALENDAR -> CalendarScreen(store = store)
                        DesktopScreen.GOALS -> GoalsScreen(store = store)
                        DesktopScreen.SETTINGS -> SettingsScreen(
                            store = store,
                            onFlush = { scope.launch { store.flush() } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 侧边导航（V1.4.1 重设计）
 * 此前用 Material NavigationRail：图标在上文字在下、宽仅 76–92dp，
 * 在 1280px 桌面上显得空旷且信息量低。改为 232dp 完整侧栏：
 * 品牌区 + 分组导航（带计数徽标）+ 底部数据概览，鼠标操作路径更短。
 */
@Composable
private fun DesktopSidebar(
    current: DesktopScreen,
    onSelect: (DesktopScreen) -> Unit,
    taskCount: Int,
    noteCount: Int,
    goalCount: Int
) {
    val semantic = LocalSemanticColors.current
    Column(
        modifier = Modifier
            .width(DesktopDesign.sidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = DesktopDesign.spaceLg),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ---- 品牌区 ----
        Row(
            modifier = Modifier.padding(horizontal = DesktopDesign.spaceLg, vertical = DesktopDesign.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "工",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "个人工作台",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "V1.4.0 桌面版",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(DesktopDesign.spaceSm))

        // ---- 分组导航 ----
        DesktopScreen.entries.groupBy { it.group }.forEach { (group, items) ->
            Text(
                group,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(
                    start = DesktopDesign.spaceLg,
                    top = DesktopDesign.spaceSm,
                    bottom = 4.dp
                )
            )
            items.forEach { item ->
                NavItem(
                    screen = item,
                    selected = current == item,
                    badge = when (item) {
                        DesktopScreen.TODO -> taskCount.takeIf { it > 0 }?.toString()
                        DesktopScreen.NOTES -> noteCount.takeIf { it > 0 }?.toString()
                        DesktopScreen.GOALS -> goalCount.takeIf { it > 0 }?.toString()
                        else -> null
                    },
                    onClick = { onSelect(item) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- 底部数据概览 ----
        Column(
            modifier = Modifier
                .padding(horizontal = DesktopDesign.spaceMd)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(DesktopDesign.cardRadius)
                )
                .padding(DesktopDesign.spaceMd)
        ) {
            Text(
                "本机数据",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            MiniStat("待办", taskCount, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            MiniStat("笔记", noteCount, semantic.info)
            Spacer(Modifier.height(4.dp))
            MiniStat("目标", goalCount, semantic.success)
        }
        Spacer(Modifier.height(DesktopDesign.spaceSm))
        Text(
            "数据仅存本机",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = DesktopDesign.spaceLg)
        )
    }
}

@Composable
private fun NavItem(
    screen: DesktopScreen,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .padding(horizontal = DesktopDesign.spaceMd)
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = DesktopDesign.spaceMd, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            screen.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = fg
        )
        Spacer(Modifier.width(10.dp))
        Text(
            screen.label,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$value",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
