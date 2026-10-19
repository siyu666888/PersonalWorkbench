# PersonalWorkbench 后续开发可行性建议（V1.6.1 基线）

> 本文基于对当前代码库的实地核查（2026-10-20 生成），所有结论均指向具体文件与行号，不做泛泛而谈。
> 成本口径：**低 = ≤0.5 人日 / 中 = 1–3 人日 / 高 = >3 人日**。

---

## 一、当前技术栈

| 维度 | 现状 | 备注 |
| --- | --- | --- |
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） | 全 Compose，无 XML 布局 |
| 架构 | MVVM + Repository，Offline-First，Room 为唯一数据源 | `di/AppContainer.kt` 手动 DI（PRD 允许 Hilt 可选） |
| 数据层 | Room **version 9**，10 个迁移；DataStore Preferences | `data/local/Migrations.kt`、`AppDatabase.kt:44` |
| 异步 | Coroutines + Flow | Repository 内 IO 线程 |
| 导航 | Navigation Compose | 5 个顶层 Tab |
| 提醒 | WorkManager（一次性 + 每日周期）+ 本地通知 | 无精确闹钟权限依赖 |
| 桌面端 | **独立 `:desktop` 模块**（Compose Multiplatform，19 文件 / 4,054 行） | **不依赖 `:app`，数据层各写一份** |
| 构建 | AGP 8.2 系 / Gradle 8.7 / JDK 17，minSdk 26 / targetSdk 34，R8 + 资源压缩 | release 实测 **2.99 MB** |
| 测试 | Robolectric 单测 **85 例全绿**；**无 instrumentation 测试** | `app/src/androidTest` 不存在 |
| 规模 | `:app` 96 个 Kotlin 文件 / **18,496 行**；11 处 TODO/FIXME | 单模块 |

## 二、已完成的核心功能（V1.0 → V1.6.1，31 个版本）

- **八大业务模块**：首页工作台、待办（子任务/回收站/每日打卡聚合/高级筛选）、笔记（Markdown 富文本）、日程（年/月/周/日四视图 + 农历节气 + 重复规则）、目标（数值/习惯/里程碑 + 打卡补签 + 成就墙）、文件（SAF + 分类 + 关联项目/日志）、工程（项目/里程碑/资源/问题/日报 + 甘特图）、统计报表（周/月报 + 环比 + 图表）
- **横切能力**：全局搜索、AI 智能助手、应用锁（BiometricPrompt）、WorkManager 提醒、自动备份 + JSON 快照恢复、崩溃日志本地留存、主题与 8 色强调色、平板/折叠屏自适应
- **开源配套**：MIT 许可证、CONTRIBUTING、Issue 模板、README（含 32 个版本日志）、CI（测试 + debug/release 构建 + 签名校验）、README 演示截图与 GIF（Robolectric 原生渲染生成）

## 三、面临的主要挑战与限制

| # | 挑战 | 证据 | 影响 |
| --- | --- | --- | --- |
| C1 | **对外承诺与实现不一致（最高风险）** | README:85 写「本地离线助手…数据不出本机」，但 `SettingsRepository.kt:65` 默认端点为 `https://api.siliconflow.cn/v1`，`AiAssistantViewModel.kt:161` 用 HttpURLConnection 发送，且会把待办/日程/笔记/目标/工程/日志真实数据注入上下文 | 仓库已公开，属**虚假隐私宣称**；用户数据实际出境到第三方 |
| C2 | **桌面端与主工程完全割裂** | `desktop/build.gradle.kts` 不依赖 `:app`，自带 4,054 行；CI 无任何 desktop 构建步骤 | 双份数据层维护，代码漂移无告警，桌面版随时可能编译不过 |
| C3 | **UI 验证手段受限** | 本机 hypervisor 驱动未安装，x86 模拟器 58 分钟无法引导；现靠 Robolectric `@GraphicsMode(NATIVE)` 截图 | 无法做真机交互/手势/性能验证，滑动删除、输入法遮挡等只能靠推理 |
| C4 | **无 UI 层自动化测试** | 仅 85 例单测（多为工具/DAO/启动冒烟），无 Compose UI 测试 | 本次「跨年日期」「打卡排序」这类**纯 UI 缺陷**恰好是测试盲区 |
| C5 | **数据库迁移覆盖不足** | DB v9 / 10 个迁移，测试仅覆盖 4→5（`StartupSmokeTest.kt`） | 升级崩溃是致命缺陷，8→9 等新迁移无回归保护 |
| C6 | **巨型 Composable** | CalendarScreen 1,196 行、TodoScreen 1,050、ReportsScreen 984、ProjectDetail 826、GoalDetail 765 | 难维护、难测试、编译慢（全量编译 ~2–3 分钟） |
| C7 | **版本可追溯性弱** | git 仅 13 个提交（2026-10-19 一次性初始化），版本日期需靠记忆日志与 APK mtime 反推；1.5.3~1.5.6 无任何记录 | 无法定位「某改动在哪版引入」，事故复盘困难 |
| C8 | **高速迭代带来的文档滞后** | 10 天 31 个版本；本次才补上 Changelog 缺失的 8 个版本 | 文档/测试跟不上功能速度 |

---

## 四、改进方案

### A. 功能优先级（做什么）

| 建议 | 具体做法 | 预期收益 | 成本 |
| --- | --- | --- | --- |
| **A1 修复 AI 隐私声明（最优先）** | ① 改 README:85 的「数据不出本机」为准确描述；② `SettingsRepository.kt:65` 默认端点改为**空**，未配置即纯本地问答；③ AI 设置页明示「启用后本机数据将发送至第三方端点」；④ 首次联网调用弹二次确认 | 消除合规与信任风险；「本地优先」卖点反而更可信 | **低**（0.5 天） |
| **A2 待办自定义重复规则** | 复用成熟的 `RecurrenceUtils`（日程已在用），给 Task 加 `repeatRule` 字段 + 迁移 | 打卡/日报类场景自动化；与已有「每日打卡」聚合天然衔接 | 中 |
| **A3 Glance 桌面小组件** | 「今日待办 / 目标打卡」小组件，点击直达 | 显著提升留存与使用频次（效率类应用的主战场） | 中 |
| **A4 数据可迁移性：导出 Markdown / CSV** | 笔记、日志、报表一键导出为通用格式 | 降低用户对私有 JSON 备份的依赖焦虑，开源项目说服力更强 | 中 |
| **A5 暂缓：多设备云同步** | 需引入账号体系 + 冲突解决，与「单机/数据自主」定位冲突 | — | **高**，建议放到 A1–A4 完成后再评估 |

> 优先级建议：**A1（立即）→ A4 → A2 → A3**。先把「对外承诺可信」和「数据可带走」补齐，再追加功能面。

### B. 架构优化

| 建议 | 具体做法 | 预期收益 | 成本 |
| --- | --- | --- | --- |
| **B1 让 CI 至少编译 desktop（止血）** | `.github/workflows/ci.yml` 增加 `./gradlew :desktop:compileKotlin` 或打包任务 | 立即暴露桌面端编译漂移，成本极低的止损 | **低** |
| **B2 抽取 KMP 共享核心（治本）** | 新建 `:core` 模块承载 entities/DAO/Repository/工具类，`:app` 与 `:desktop` 共同依赖；UI 各写各的 | 消除 4,054 行双份维护；两端数据逻辑天然一致 | **高**（建议分阶段：先 core:model → core:data） |
| **B3 拆分巨型 Composable** | 按 feature 分包（如 `ui/calendar/{month,week,day,editor}`），把 >800 行文件的子组件提取为私有 `@Composable` + `@Preview` | 可维护性、可测试性提升；配合 Preview 可部分绕开 C3 的验证限制 | 中（可逐模块推进） |
| **B4 Repository 接口化（为可测试性）** | 给 `TaskRepository` 等提取接口，ViewModel 依赖接口 | 单测可用 fake 替身，摆脱「必须起 Room 才能测 ViewModel」 | 中 |
| **B5 暂缓引入 Hilt** | 手动 DI 目前足够，且引入会连带影响 `:desktop` | — | 中，收益不明显，**不建议当前做** |

### C. 性能提升

| 建议 | 具体做法 | 预期收益 | 成本 |
| --- | --- | --- | --- |
| **C1 Baseline Profile** | 当前**无 baseline profile**；用 Macrobenchmark 生成或手写启动/滚动 Profile，挂到 `baselineProfiles` | Compose 应用首屏与列表滚动的即时编译开销下降，冷启动与帧稳定性改善最明显 | 中（1–2 天，需真机/或 Gradle Managed Device） |
| **C2 构建提速（配置级）** | 开启 Gradle configuration cache、并行执行、Kotlin daemon 复用；必要时拆模块（B2/B3 顺带收益） | 当前全量编译 2–3 分钟，日常迭代体感提升 | **低**（配置）；高（拆模块） |
| **C3 大列表分页** | notes / files / records 目前一次性 `observeAll`；数据量大时改用 `Paging3` 或 `LIMIT/OFFSET` | 千条以上数据滚动流畅 | 中 |
| **C4 审核 R8 规则** | `proguard-rules.pro` 仅 31 行；结合每次发布输出的 `seeds.txt` 清理不必要的 `-keep` | release 已仅 2.99 MB，收益有限，属顺手项 | **低** |

### D. 技术债务处理

| 建议 | 具体做法 | 预期收益 | 成本 |
| --- | --- | --- | --- |
| **D1 补 Compose UI 测试（重点）** | 用 `createComposeRule` 覆盖关键用户流：新建待办 → 勾选、目标打卡 → 列表顺序、日志填写 → 保存、切 Tab 导航 | 直接补上本次暴露的盲区（跨年日期、打卡排序都是纯 UI 缺陷） | 中 |
| **D2 数据库全链路迁移测试** | 用 `MigrationTestHelper` 覆盖 1→9 全路径，重点补 8→9 | 杜绝「老用户升级即崩溃」这类致命问题 | 中 |
| **D3 日期格式化收敛** | 已在 `DateUtils.kt` 统一默认值并新增 `formatShortDate`；建议再建立「领域格式化入口」并写死规则：任何新代码不得直接传 `"M月d日"` | 防止再次散落（本次共清理 ~20 处） | **低** |
| **D4 建立发布与追溯流程** | 每次发布：打 git tag → GitHub Release（附 APK + `mapping.txt`）→ 同步 Changelog/README/CI 版本号 | 根治 C7；`mapping.txt` 归档后线上崩溃才能还原 | **低** |
| **D5 核对 Changelog 推定日期** | 1.5.3 / 1.5.4 / 1.5.5 / 1.5.6 四条日期为按 versionCode 推定，需与实际核对 | 文档准确性 | **极低** |
| **D6 清理 11 处 TODO/FIXME** | 逐条判定：实现 / 转 Issue / 删除 | 减少隐性债务 | **低** |
| **D7 密钥与产物备份** | keystore 在仓库外且不可恢复；README 已提示，建议追加离线备份清单与每次归档 `mapping.txt` | 避免「无法再更新已发布应用」 | **低** |
| **D8 打通真机/模拟器验证（可选）** | 开启 Windows 虚拟化（Hyper-V / 内存完整性）后 x86 模拟器可用；或引入 Paparazzi 做像素级回归 | 根治 C3，可验证手势、输入法、性能 | 低（开开关）／中（Paparazzi） |

---

## 五、建议的落地顺序

**第一阶段｜止血与合规（约 1–2 天，性价比最高）**
A1 隐私声明修复 → D5 日期核对 → B1 desktop 进 CI → D4 发布流程（tag + Release + mapping 归档）

**第二阶段｜质量防线（约 3–5 天）**
D1 Compose UI 测试 → D2 全链路迁移测试 → D3 日期格式化收敛 → D6 TODO 清理

**第三阶段｜体验与性能（约 3–5 天）**
C1 Baseline Profile → C2 构建提速配置 → A4 导出 Markdown/CSV

**第四阶段｜结构演进（持续，按模块推进）**
B3 拆分巨型 Composable → A2 待办重复规则 → A3 桌面小组件 → B2 KMP 共享核心（分阶段）

> 判断依据：C1（隐私）与 C5（迁移崩溃）风险等级最高但修复成本低，应最先做；
> B2（KMP 共享核心）收益最大但会牵动两个模块，建议放在桌面端确有价值时再启动。
