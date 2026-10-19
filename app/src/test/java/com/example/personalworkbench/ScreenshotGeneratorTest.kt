package com.example.personalworkbench

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.LocalWindowSizeClass
import com.example.personalworkbench.ui.calendar.CalendarScreen
import com.example.personalworkbench.ui.goals.GoalsScreen
import com.example.personalworkbench.ui.home.HomeScreen
import com.example.personalworkbench.ui.logs.LogFillScreen
import com.example.personalworkbench.ui.notes.NotesScreen
import com.example.personalworkbench.ui.reports.ReportsScreen
import com.example.personalworkbench.ui.settings.SettingsScreen
import com.example.personalworkbench.ui.theme.PersonalWorkbenchTheme
import com.example.personalworkbench.ui.todo.TodoScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * README 演示素材生成器（V1.6.0 开源配套）。
 *
 * 为什么不走模拟器：本机 x86 镜像需硬件加速而hypervisor 未安装，
 * 无头软件模拟引导 15 分钟仍失败。Robolectric 的 [GraphicsMode.Mode.NATIVE]
 * 走 native-skia 真实像素管线，在 JVM 内即可把 Compose 界面栅格化成 PNG。
 *
 * 运行：./gradlew :app:testDebugUnitTest --tests "ScreenshotGeneratorTest"
 * 产物：docs/screenshots/ 下的 PNG（提交进仓库供README 引用）
 *
 * 注意：本类只生成素材，**不参与 CI 断言**（无assert，失败即抛异常），
 * CI 仍由 StartupSmokeTest 等 71 项功能测试把关。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi", application = StartupTestApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class ScreenshotGeneratorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val container: AppContainer by lazy {
        AppContainer(ApplicationProvider.getApplicationContext())
    }

    /** 输出目录：优先仓库根 docs/screenshots（单测工作目录为模块目录，故上溯） */
    private fun outputDir(): File {
        val candidates = listOf(
            File("../docs/screenshots"),
            File("docs/screenshots"),
            File("app/../docs/screenshots")
        )
        val dir = candidates.firstOrNull { it.parentFile?.exists() == true } ?: candidates.first()
        dir.mkdirs()
        return dir
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        val root: View = composeRule.activity.window.decorView
        // 让根视图完成一次测量与布局，保证有真实尺寸
        root.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, WIDTH_PX, HEIGHT_PX)
        ShadowLooper.idleMainLooper()

        val bmp = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))

        val file = File(outputDir(), "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        println("[screenshot] ${file.absolutePath} (${file.length() / 1024} KB)")
    }

    /** 预置演示数据：空的库页面截图没有说服力 */
    private fun seed() {
        val now = System.currentTimeMillis()
        val day = 24L * 3600 * 1000
        runBlocking {
            container.database.taskDao().insert(
                TaskEntity(
                    title = "整理本周项目周报",
                    description = "汇总三个子项目的进度与风险",
                    dueDate = now + day,
                    priority = Priority.HIGH,
                    tags = listOf("工作")
                )
            )
            container.database.taskDao().insert(
                TaskEntity(
                    title = "阅读《深度工作》第三章",
                    dueDate = now + 2 * day,
                    priority = Priority.MEDIUM
                )
            )
            container.database.taskDao().insert(
                TaskEntity(title = "预约体检", dueDate = now - day, priority = Priority.LOW)
            )
            container.database.scheduleDao().insert(
                ScheduleEntity(
                    title = "项目周会",
                    startTime = now + 3 * 3600_000L,
                    endTime = now + 4 * 3600_000L,
                    isAllDay = false,
                    reminderMinutes = 15
                )
            )
            container.database.scheduleDao().insert(
                ScheduleEntity(
                    title = "读书时间",
                    startTime = now + 20 * 3600_000L,
                    endTime = now + 21 * 3600_000L,
                    isAllDay = false
                )
            )
            container.database.noteDao().insert(
                NoteEntity(
                    title = "会议纪要 · 项目周会",
                    content = "1. 里程碑 M2 按期交付\n2. 下周完成联调\n3. 风险：第三方接口延期",
                    preview = "1. 里程碑 M2 按期交付…"
                )
            )
            container.database.noteDao().insert(
                NoteEntity(
                    title = "读书笔记",
                    content = "深度工作：专注是对抗干扰的第一道防线…",
                    preview = "深度工作：专注是对抗干扰的第一道防线…"
                )
            )
        }
        ShadowLooper.idleMainLooper()
    }

    /**
     * 每个页面一个测试方法：ComposeTestRule 在单个测试内只允许 setContent 一次，
     * 因此不能用循环遍历页面（拆成多个 @Test 后 Gradle 一次运行全部生成）。
     */
    @Test
    fun shot_01_home() = shot("01-home") {
        HomeScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_02_todo() = shot("02-todo") {
        TodoScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_03_calendar() = shot("03-calendar") {
        CalendarScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_04_notes() = shot("04-notes") {
        NotesScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_05_goals() = shot("05-goals") {
        GoalsScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_06_reports() = shot("06-reports") {
        ReportsScreen(nav = rememberNavController(), container = container)
    }

    @Test
    fun shot_07_settings() = shot("07-settings") {
        SettingsScreen(nav = rememberNavController(), container = container)
    }

    /** V1.6.1 顶栏重排的视觉验证：日志填写页（含日期主标题 / 状态副标题 / 保存按钮） */
    @Test
    fun shot_08_log_fill() = shot("08-log-fill") {
        LogFillScreen(
            nav = rememberNavController(),
            container = container,
            date = com.example.personalworkbench.util.dayStartMillis(System.currentTimeMillis())
        )
    }

    /** 预置演示数据 → 组装页面 → 等待稳定 → 截图落盘 */
    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        seed()
        composeRule.setContent { Themed(content) }
        capture(name)
    }

    @androidx.compose.runtime.Composable
    private fun Themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        PersonalWorkbenchTheme(darkTheme = false, accentIndex = 0) {
            CompositionLocalProvider(
                LocalWindowSizeClass provides windowSizeClass
            ) { content() }
        }
    }

    private val windowSizeClass by lazy {
        WindowSizeClass.calculateFromSize(DpSize(411.dp, 891.dp))
    }

    private companion object {
        /** 411dp × 891dp @ xxhdpi(3x) = 1233 × 2673 px，Pixel 尺寸 */
        const val WIDTH_PX = 1233
        const val HEIGHT_PX = 2673
    }
}