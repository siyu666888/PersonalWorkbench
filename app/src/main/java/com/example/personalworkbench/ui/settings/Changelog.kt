package com.example.personalworkbench.ui.settings

/**
 * 历史更新记录（关于/版本页展示用）。
 * 内容取自项目 README 各版本发布说明，按版本号倒序排列。
 */
data class ChangelogEntry(
    val version: String,
    val title: String,
    val highlights: List<String>
)

object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.4.7",
            title = "待办左滑浮现删除 · 自动备份列表与统一恢复",
            highlights = listOf(
                "待办移除左/右滑直接删除手势：改为左滑条目后右侧浮现悬浮删除按钮，超过 40% 松手弹性展开、按钮淡入缩放，点击才删除（仍带撤销提示）",
                "新增「备份与恢复」页：自动备份记录列表展示每条的时间/大小/状态（最新/今天），可任选一条恢复或删除，也可「立即备份」",
                "备份与恢复统一入口：自动备份恢复、从文件恢复（SAF）、导出备份（SAF）合并到同一页，共用同一套 JSON 快照逻辑，体验一致",
                "设置页「数据备份」卡改为单一「备份与恢复」入口进入该页"
            )
        ),
        ChangelogEntry(
            version = "1.4.6",
            title = "待办滑动删除可撤销 · 日志/笔记排版统一",
            highlights = listOf(
                "待办左滑/右滑删除改为「可撤销」：先软删除并弹出带「撤销」的提示，误删可在短时间内恢复，不再直接丢失",
                "统一日志与笔记列表卡片的视觉语言：圆角 14dp、内边距 14dp、主文本 titleMedium、正文 bodyMedium、底部元信息 labelSmall，间距与对齐一致",
                "统一两模块列表容器边距（16/12/16/88dp，底部给悬浮按钮留白）与空状态版式（圆形图标容器 + headlineSmall 标题 + bodyMedium 副标题），视觉结构一致"
            )
        ),
        ChangelogEntry(
            version = "1.4.5",
            title = "日历不再混入待办 · 同步限本周 · 切换器放大 · 逾期误报修复",
            highlights = listOf(
                "日程页不再展示待办事项（日/周视图移除同步生成的待办区块），日历只聚焦日程本身",
                "修复刷新导致的崩溃：此前同步待办与日程实例的列表项 key 重复（LazyColumn 抛出 IllegalArgumentException）",
                "刷新同步范围限制为「今天 → 本周日」，月/年视图刷新不再把整月（含重复日程每天一条）一次性铺满待办",
                "顶部年/月/周/日视图切换器图标 20dp→24dp、文字 labelMedium→labelLarge，更醒目易点",
                "修复「今日待办未到截止时间却显示逾期」：逾期判定改为到期日当天不算逾期，跨过当天（dueDate 早于今天 00:00）才算，含由日程同步来、dueDate=事件开始时间的待办"
            )
        ),
        ChangelogEntry(
            version = "1.4.4",
            title = "日程同步待办修复：重复日程入列 · 完成状态双向同步",
            highlights = listOf(
                "修复「当日（每天/每周等重复）日程刷新后不出现在待办」：同步不再跳过重复实例，重复序列的每一天各生成一条待办",
                "修复「完成状态未同步」：日程实例已勾完成则新建待办直接为已完成；已有待办未完成的也会被对齐为完成",
                "双向勾选同步扩展为「task_id 直连 + (schedule_id, 实例时间) 定位」两条链路，重复日程勾选与待办互相同步",
                "数据库迁移 6→7：tasks 新增 schedule_id 列（+ 索引），记录来源日程"
            )
        ),
        ChangelogEntry(
            version = "1.4.3",
            title = "日程手动同步 · 封面不变形 · 彩色图标 · 时间滑动选择",
            highlights = listOf(
                "日程页新增「刷新」按钮，可手动把当前视图周期（日/周/月/年）的日程同步到待办，不依赖自动同步开关",
                "目标封面图改为整图显示（不裁切/不压缩/不变形），以中性背景承接留白",
                "待办/日程/目标/笔记等模块的空状态与操作图标统一为彩色图标",
                "日程编辑的开始/结束时间改为滑动选择（TimePicker），不再手填",
                "年/月/周视图的日期与文字字号统一放大 4sp，更易读"
            )
        ),
        ChangelogEntry(
            version = "1.4.2",
            title = "目标自定义图片 · 关于页更新记录 · 闪退修复",
            highlights = listOf(
                "目标（Goal）模块支持设置自定义封面图，可从相册选取，列表卡片与详情页均可展示",
                "「我的 - 关于」新增「历史更新记录」，可查看各版本变更要点",
                "修复 V1.4.1 因图表标记正则写法错误导致的启动闪退"
            )
        ),
        ChangelogEntry(
            version = "1.4.1",
            title = "日志可读可编辑 · AI 接入本机数据 · 桌面版视觉重设计",
            highlights = listOf(
                "新增「日志」列表页：按月分组、搜索、卡片含农历/节气/节日，可阅读与再编辑",
                "日志编辑器升级为富文本（Markdown 子集）+ 预览，支持插入近 7 天统计图",
                "AI 助手接入本机数据上下文：待办/日程/笔记/目标/工程/日志随提问注入，回答有据可依",
                "桌面版重做图标、配色、排版与布局（232dp 侧边栏 + 专用字阶）"
            )
        ),
        ChangelogEntry(
            version = "1.4.0",
            title = "当日反向同步 · 闹钟提醒 · 动态排序 · JSON 备份 · 自适应",
            highlights = listOf(
                "修复日程→待办反向同步（仅当日日程）",
                "日程编辑新增闹钟提醒入口（reminderMinutes 字段写入）",
                "最新动态按日期倒序排序",
                "快捷入口「日志填写」改为「日志」",
                "平板/折叠屏自适应（WindowSizeClass 多列 + 内容区限宽）",
                "备份升级为可读 JSON 快照"
            )
        ),
        ChangelogEntry(
            version = "1.3.9",
            title = "视图切换器重设计 · 每日自动备份 · 月视图周数列",
            highlights = listOf(
                "年/月/周/日切换器重设计为内容区顶部等宽图标+文字按钮",
                "每日自动备份（日期命名 + 当天覆盖 + 7 天滚动清理 + 设置开关）",
                "月视图左侧周数列（选中周高亮）"
            )
        ),
        ChangelogEntry(
            version = "1.3.8",
            title = "日程待办同步 · 日历四视图 · AI 接入大模型 · 启动闪退加固",
            highlights = listOf(
                "日程自动同步待办（task_id + SyncBridge 双向状态同步，迁移 4→5）",
                "日历 年/月/周/日 四视图 + 年视图 12 宫格 + 本年度周数",
                "AI 助手接入 OpenAI 兼容免费大模型（硅基流动默认，失败回退本地问答）",
                "Robolectric 启动冒烟测试加固「打开就闪退」"
            )
        ),
        ChangelogEntry(
            version = "1.3.7",
            title = "报表柱状图 · 笔记自动上拉 · 日志填写",
            highlights = listOf(
                "报表柱状图日期标签与柱体不再重叠",
                "笔记长内容自动上拉跟随光标（加舒适余量）",
                "快捷入口新增「日志填写」+ 周/月报自动汇总（work_logs 表，迁移 3→4）"
            )
        ),
        ChangelogEntry(
            version = "1.3.6",
            title = "视觉清理与输入法根治",
            highlights = listOf(
                "全局移除常驻删除按钮，改长按或下拉操作",
                "笔记输入法遮挡根治（adjustResize + 光标自动滚入）"
            )
        ),
        ChangelogEntry(
            version = "1.3.5",
            title = "笔记与勾选交互修复",
            highlights = listOf(
                "笔记置顶 + 长按操作菜单（置顶/移动/删除）",
                "勾选框由方形改为圆形（CircleCheckbox）",
                "首页今日焦点待办实时同步、可勾选完成"
            )
        ),
        ChangelogEntry(
            version = "1.3.4",
            title = "交互修复（6 项）",
            highlights = listOf(
                "修复底部导航偶发跳页 + 切 Tab 防抖",
                "笔记增加删除选项",
                "目标里程碑可修改/删除 + 里程碑驱动整体进度 + 目标跟踪卡"
            )
        ),
        ChangelogEntry(
            version = "1.3.3",
            title = "排版优化 · 工期进度自动计算 · 崩溃日志 · 日程完成 · 待办折叠",
            highlights = listOf(
                "中文排版行高 9 档优化",
                "项目总进度按「已完成工期/总工期」自动计算",
                "崩溃报告自动存本地（可查看/分享/清空）",
                "日程可勾选已完成；已完成待办子任务可折叠",
                "数据库迁移 1→2（is_completed 列）"
            )
        ),
        ChangelogEntry(
            version = "1.3.2",
            title = "本地提醒闭环 + 通知权限",
            highlights = listOf(
                "WorkManager 驱动本地提醒（任务/日程/目标），无需精确闹钟权限",
                "Android 13+ 通知权限运行时申请"
            )
        ),
        ChangelogEntry(
            version = "1.3.0",
            title = "日历农历/节气/节假日",
            highlights = listOf(
                "新增 LunarUtils：农历、干支生肖、二十四节气、传统节日与法定假日",
                "日历月视图每格显示农历/节气/节日小字"
            )
        ),
        ChangelogEntry(
            version = "1.2",
            title = "设计与体验更新",
            highlights = listOf(
                "强调色预设扩展为 8 色并真正应用到 Material 3 主题",
                "新增 AI 语义色（浅色/深色各一套）",
                "底部主页按钮刷新 + 今日焦点动态刷新"
            )
        ),
        ChangelogEntry(
            version = "1.1",
            title = "初始可用版本",
            highlights = listOf(
                "个人工作台基础模块：待办、笔记、日程、目标、工程、文件、报表",
                "本地优先 · 数据自主 · 无广告"
            )
        )
    )
}
