package com.example.personalworkbench

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.personalworkbench.ui.LocalWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.navigation.TopLevelRoute
import com.example.personalworkbench.ui.navigation.TopLevelRoute.*
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.ui.theme.PersonalWorkbenchTheme
import com.example.personalworkbench.ui.home.HomeScreen
import com.example.personalworkbench.ui.todo.TodoScreen
import com.example.personalworkbench.ui.notes.NotesScreen
import com.example.personalworkbench.ui.notes.NoteEditorScreen
import com.example.personalworkbench.ui.calendar.CalendarScreen
import com.example.personalworkbench.ui.goals.GoalsScreen
import com.example.personalworkbench.ui.goals.GoalDetailScreen
import com.example.personalworkbench.ui.files.FilesScreen
import com.example.personalworkbench.ui.engineering.ProjectListScreen
import com.example.personalworkbench.ui.engineering.ProjectDetailScreen
import com.example.personalworkbench.ui.search.SearchScreen
import com.example.personalworkbench.ui.ai.AiChatScreen
import com.example.personalworkbench.ui.reports.ReportsScreen
import com.example.personalworkbench.ui.logs.LogFillScreen
import com.example.personalworkbench.ui.logs.LogListScreen
import com.example.personalworkbench.ui.settings.SettingsScreen
import com.example.personalworkbench.ui.settings.BackupScreen
import com.example.personalworkbench.ui.settings.RecycleBinScreen
import com.example.personalworkbench.ui.settings.AppLockScreen
import com.example.personalworkbench.ui.todo.TodoDetailScreen
import com.example.personalworkbench.viewmodel.MainViewModel
import com.example.personalworkbench.ui.security.AppLockGate
import com.example.personalworkbench.util.dayStartMillis

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 计算窗口尺寸类别（平板/折叠屏自适应，V1.4.0），通过 CompositionLocal 下发给各页面
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                val app = application as PersonalWorkbenchApp
                val mainVm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.container))
                val themeMode by mainVm.themeMode.collectAsStateWithLifecycle(
                    initialValue = com.example.personalworkbench.data.preferences.SettingsRepository.ThemeMode.SYSTEM
                )
                val appLockEnabled by mainVm.appLockEnabled.collectAsStateWithLifecycle(false)
                val accent by mainVm.accentIndex.collectAsStateWithLifecycle(0)
                val darkTheme = when (themeMode) {
                    com.example.personalworkbench.data.preferences.SettingsRepository.ThemeMode.LIGHT -> false
                    com.example.personalworkbench.data.preferences.SettingsRepository.ThemeMode.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                PersonalWorkbenchTheme(darkTheme = darkTheme, accentIndex = accent) {
                    AppLockGate(activity = this@MainActivity, appLockEnabled = appLockEnabled) {
                        AppRoot(app.container, mainVm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    container: com.example.personalworkbench.di.AppContainer,
    mainVm: MainViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
    val topLevelRoutes = listOf(Home.route, Todo.route, Note.route, Calendar.route, Settings.route)
    val showBottomBar = topLevelRoutes.contains(currentRoute)
    var homeRefresh by remember { mutableStateOf(0) }

    // 切 Tab 防抖：NavHost 过渡动画期间忽略连点，避免快速点击时导航状态错乱
    // （曾出现「点首页没反应 / 偶发跳到日程」，根因就是过渡期重复 navigate 互相打断）
    var lastTabClickAt by remember { mutableStateOf(0L) }

    // 本地提醒依赖通知触达：Android 13+ 需运行时授权，未授权时主动申请
    // （放在 AppRoot 内，AppLockGate 未解锁前不会执行，避免与应用锁弹窗叠加）
    RequestNotificationPermissionOnce()

    /** 底部导航切 Tab：清掉当前 Tab 之上的所有页面，保证一定落到目标 Tab 的根页面 */
    fun switchTab(route: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTabClickAt < 180L) return
        lastTabClickAt = now
        navController.navigate(route) {
            // 关键：既不做 saveState 也不做 restoreState。
            // 之前用 popUpTo(起始页){ saveState = true } + restoreState = true，
            // 起始页「首页」被保存下来的回退栈里含有上一次停留在的 Tab（如日程），
            // 于是点「首页」时 restoreState 把 [home, calendar] 整条恢复回来 ——
            // 表现为「点首页没反应 / 经常跳到日程页」。
            // 切 Tab 要的是「确定落到该 Tab 的根页面」，确定性优先，不做状态恢复。
            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                    // 不强制高度：交给 Material3 自身 sizing（80dp + 系统导航栏 inset），
                    // 之前写死 86dp 会与其内部 Row 的 80dp 互相打架，导致图标/文字被挤压错位
                ) {
                    val items = listOf(Home, Todo, Note, Calendar, Settings)
                    // 选中态用实时 currentDestination，避免组合状态在过渡期滞后造成「高亮错页」
                    val current = navController.currentDestination
                    items.forEach { item ->
                        val selected = current?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val sameTab = selected
                                switchTab(item.route)
                                // 重复点击「主页」Tab 时刷新首页内容
                                if (sameTab && item.route == Routes.HOME) homeRefresh++
                            },
                            icon = {
                                // 彩色拟物图标：不做单色 tint，保留自身配色；尺寸由 24dp 放大到 28dp
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(item.iconRes),
                                        contentDescription = item.label,
                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(3.dp))
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        letterSpacing = 0.4.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            },
                            // 选中态高亮（V1.5.2 全局规范）：绿色指示药丸 + 绿色文字，强化当前 Tab 反馈
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = LocalSemanticColors.current.success.copy(alpha = 0.18f),
                                selectedIconColor = LocalSemanticColors.current.success,
                                selectedTextColor = LocalSemanticColors.current.success,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) { HomeScreen(navController, container, homeRefresh) }
            composable(Routes.TODO) { TodoScreen(navController, container) }
            composable("todoDetail/{taskId}") {
                val taskId = it.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
                TodoDetailScreen(navController, container, taskId)
            }
            composable(Routes.NOTES) { NotesScreen(navController, container) }
            composable(Routes.NOTE_EDITOR) { backStack ->
                val noteId = backStack.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
                NoteEditorScreen(navController, container, noteId)
            }
            composable(Routes.CALENDAR) { CalendarScreen(navController, container) }
            composable(Routes.GOALS) { GoalsScreen(navController, container) }
            composable(Routes.GOAL_DETAIL) { backStack ->
                val goalId = backStack.arguments?.getString("goalId")?.toLongOrNull() ?: 0L
                GoalDetailScreen(navController, container, goalId)
            }
            composable(Routes.FILES) { FilesScreen(navController, container) }
            composable(Routes.SETTINGS) { SettingsScreen(navController, container) }
            composable(Routes.RECYCLE_BIN) { RecycleBinScreen(navController, container) }
            composable(Routes.BACKUPS) { BackupScreen(navController, container) }
            composable(Routes.APP_LOCK) { AppLockScreen(navController, container) }
            composable(Routes.SEARCH) { SearchScreen(navController, container) }
            composable(Routes.AI_CHAT) { backStack ->
                val q = backStack.arguments?.getString("q").orEmpty()
                AiChatScreen(navController, container, q)
            }
            composable(Routes.REPORTS) { ReportsScreen(navController, container) }
            composable(Routes.LOG_FILL) { backStack ->
                val date = backStack.arguments?.getString("date")?.toLongOrNull() ?: dayStartMillis(System.currentTimeMillis())
                LogFillScreen(navController, container, date)
            }
            composable(Routes.LOGS) { LogListScreen(navController, container) }
            composable(Routes.PROJECT_LIST) { ProjectListScreen(navController, container) }
            composable(Routes.PROJECT_DETAIL) { backStack ->
                val pid = backStack.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                ProjectDetailScreen(navController, container, pid)
            }
        }
    }

    // 备份提醒 (PRD 7.1): 第 3 次启动 / 每 7 天
    LaunchedEffect(Unit) { mainVm.checkBackupReminder() }
    val backupMsg by mainVm.backupReminder.collectAsStateWithLifecycle(initialValue = null)
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(backupMsg) {
        backupMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Android 13+ (API 33) 起通知需运行时授权。
 * 未授权时申请一次；用户连拒两次后系统会自动拒绝，不会反复打扰。
 * API 32 及以下安装时即授予，无需处理。
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* 结果不影响界面，授权状态由系统在发通知时兜底 */ }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
