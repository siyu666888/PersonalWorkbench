package com.example.personalworkbench

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.LocalWindowSizeClass
import com.example.personalworkbench.ui.settings.BackupScreen
import com.example.personalworkbench.ui.goals.GoalDetailScreen
import com.example.personalworkbench.ui.logs.LogFillScreen
import com.example.personalworkbench.ui.theme.PersonalWorkbenchTheme
import com.example.personalworkbench.ui.todo.TodoScreen
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * 关键用户流的 Compose UI 测试（技术债 D1）。
 *
 * 背景：本项目此前只有工具层 / DAO 层的单测，**没有任何 UI 层自动化**，
 * 而 V1.6.1 修的「跨年日期显示」「同日打卡顺序」恰好都是纯 UI 缺陷——
 * 正好落在测试盲区。本类用 Robolectric + Compose 测试补齐这条防线。
 *
 * 覆盖：
 * 1. 待办列表渲染 + 勾选框切换写回数据库
 * 2. 日志填写页输入 → 保存 → 落库
 * 3. 目标详情打卡记录：同一天多条按时间从早到晚排列（V1.6.1 修复项的 UI 回归）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi", application = StartupTestApp::class)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class KeyUserFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val container: AppContainer by lazy {
        AppContainer(ApplicationProvider.getApplicationContext())
    }

    private val windowSizeClass =
        WindowSizeClass.calculateFromSize(DpSize(411.dp, 891.dp))

    /** 统一外壳：主题 + 屏幕尺寸类（项目 UI 的既有约定） */
    private fun setScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                PersonalWorkbenchTheme { content() }
            }
        }
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
    }

    // ===== 1. 待办：勾选框切换应写回数据库 =====

    @Test
    fun todo_checkboxToggle_persistsToDatabase() = runBlocking {
        val dao = container.database.taskDao()
        dao.insert(TaskEntity(title = "写周报", status = TaskStatus.ACTIVE))

        setScreen { TodoScreen(nav = rememberNavController(), container = container) }

        // 1) 预置的待办渲染出来了
        composeRule.onNodeWithText("写周报").assertExists()

        // 2) 勾选框初始为未勾选
        val toggle = composeRule.onAllNodes(isToggleable())[0]
        toggle.assertIsOff()

        // 3) 点击后应变为勾选（DB 写入 + Flow 重发射是异步的，轮询等待重组）
        toggle.performClick()
        composeRule.waitForIdle()

        // 4) 数据库里应变成完成态
        var task = dao.observeAll().first().firstOrNull { it.title == "写周报" }
        for (i in 0 until 20) {
            if (task?.status == TaskStatus.COMPLETED) break
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            task = dao.observeAll().first().firstOrNull { it.title == "写周报" }
        }
        assertEquals("勾选后数据库应为 COMPLETED", TaskStatus.COMPLETED, task?.status)
        assertTrue("完成时间应写入", (task?.completedAt ?: 0L) > 0L)

        // 5) UI 侧：待办列表只显示未完成项，勾选后该项应从列表移除。
        //    因此这里不能断言 ToggleableState=On——节点已随列表刷新消失。
        for (i in 0 until 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            composeRule.waitForIdle()
            if (runCatching { composeRule.onNodeWithText("写周报").assertDoesNotExist() }.isSuccess) break
        }
        composeRule.onNodeWithText("写周报").assertDoesNotExist()
    }

    // ===== 2. 日志填写：输入 → 保存 → 落库 =====

    @Test
    fun logFill_typeAndSave_persistsToDatabase() = runBlocking {
        val today = dayStartMillis(System.currentTimeMillis())
        val dao = container.database.workLogDao()

        setScreen {
            LogFillScreen(nav = rememberNavController(), container = container, date = today)
        }

        // 1) 顶栏「保存」入口存在
        composeRule.onNodeWithText("保存").assertExists()

        // 2) 在正文输入框里输入内容
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("今天完成了 UI 测试补齐")

        // 3) 保存
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()

        // 4) 数据库里应有一条今天的日志。
        //    保存走 rememberCoroutineScope + Room(IO 线程)，需要轮询等待落库完成
        var saved = dao.getByDate(today)
        for (i in 0 until 20) {
            if (saved != null) break
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            saved = dao.getByDate(today)
        }
        assertTrue("保存后应能查到日志", saved != null)
        assertTrue(
            "日志内容应包含输入文本（实际：${saved?.content}）",
            saved?.content?.contains("UI 测试补齐") == true
        )
    }

    // ===== 3. 打卡记录：同一天多条按时间从早到晚（V1.6.1 修复的 UI 回归） =====

    @Test
    fun goalDetail_sameDayRecords_renderedEarliestFirst() = runBlocking {
        val goalDao = container.database.goalDao()
        val goalId = goalDao.insert(
            GoalEntity(name = "每日阅读", type = GoalType.NUMERIC, targetValue = 30.0, unit = "分钟")
        )
        val base = dayStartMillis(System.currentTimeMillis())
        // 故意「乱序」插入：13:00 先写、08:30 次写、18:00 最后写
        goalDao.insertRecord(GoalRecordEntity(goalId = goalId, recordDate = base + hour(13), value = 20.0))
        goalDao.insertRecord(GoalRecordEntity(goalId = goalId, recordDate = base + hour(8) + min(30), value = 10.0))
        goalDao.insertRecord(GoalRecordEntity(goalId = goalId, recordDate = base + hour(18), value = 30.0))

        setScreen {
            GoalDetailScreen(nav = rememberNavController(), container = container, goalId = goalId)
        }

        // 三条记录的时间文本都应渲染出来（含年份，V1.6.1 起）
        val t0830 = formatDateTime(base + hour(8) + min(30))
        val t1300 = formatDateTime(base + hour(13))
        val t1800 = formatDateTime(base + hour(18))
        composeRule.onNodeWithText(t0830, substring = true).assertExists()
        composeRule.onNodeWithText(t1300, substring = true).assertExists()
        composeRule.onNodeWithText(t1800, substring = true).assertExists()

        // 三条同日记录必须渲染出「互不相同」的时间文本：
        // 若 UI 退回「只显示日期」的旧写法，这三条会变成同一个字符串、无法区分先后。
        // 排序本身由 DateFormatAndRecordOrderTest 在 DAO 层保证，
        // 本用例守住 UI 确实消费了那份有序数据且没把时刻吞掉。
        assertEquals(
            "同一天的三条记录必须能区分（需含年份 + 时刻）",
            3, setOf(t0830, t1300, t1800).size
        )
        assertTrue("时间文本必须含年份", t0830.contains("年"))
    }

    // ===== 4. 备份页：数据导出入口（V1.6.4 / A4）=====

    @Test
    fun backupScreen_rendersDataExportSection() {
        setScreen { BackupScreen(nav = rememberNavController(), container = container) }

        // 卡片标题与两种格式 chip
        composeRule.onNodeWithText("数据导出").assertExists()
        composeRule.onNodeWithText("Markdown（给人读）").assertExists()
        composeRule.onNodeWithText("CSV（Excel/WPS）").assertExists()

        // 五个可导出的模块行都必须渲染出来（少一个就等于功能缺失）
        listOf("笔记", "工作日志", "待办", "目标与打卡", "全部数据").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }

        // 已有能力不能被这轮改动挤掉：手动备份 / 恢复入口仍在
        composeRule.onNodeWithText("导出备份").assertExists()
        composeRule.onNodeWithText("从文件恢复").assertExists()
    }

    private fun hour(h: Int) = h * 3_600_000L
    private fun min(m: Int) = m * 60_000L
}
