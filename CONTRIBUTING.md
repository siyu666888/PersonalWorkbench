# 贡献指南（CONTRIBUTING）

感谢你愿意为 PersonalWorkbench（个人工作台）贡献代码或想法！本指南面向所有开源读者，说明如何参与、如何构建、以及本仓库的约定。英文读者可参考各代码块中的命令与目录名，核心流程一致。

> 本项目是一个**纯本地**的 Android 个人工作台（目标 / 待办 / 笔记 / 日程 / 工程项目 / 文件 / 日志 / 报表），无账号、无云端依赖。提交前请确认你的改动符合「本地优先、隐私零上传」的设计初衷。

## 1. 你可以这样参与

- **提 Issue**：报 Bug、提功能建议、反馈体验问题都欢迎。
- **提 Pull Request**：修 Bug、补文档、完善测试、界面与交互优化。
- **讨论**：对设计取舍有疑问，先开 Issue 讨论比直接提 PR 更高效。

## 2. 本地开发环境

| 项 | 要求 |
|---|---|
| JDK | 17（AGP 8.2.2 要求） |
| Android SDK | compileSdk 34、build-tools 34.0.0（最低支持 minSdk 26 / Android 8.0） |
| Gradle | 使用仓库自带 Wrapper（`./gradlew`，锁定 8.7），**不要**本机全局 Gradle |
| IDE | Android Studio（Kotlin 1.9.24 + Compose） |

```bash
# 克隆（Fork 后）
git clone https://github.com/<你的用户名>/PersonalWorkbench.git
cd PersonalWorkbench
```

> `local.properties`（SDK 路径）由 Android Studio 首次同步自动生成，已在 `.gitignore` 中，无需手动创建。

## 3. 构建与测试

```bash
# 运行单元测试（Robolectric，71 项，必须全绿）
./gradlew testDebugUnitTest

# 构建 debug 包
./gradlew assembleDebug

# 构建 release 包（需 keystore.properties，缺省时自动跳过签名，见 README）
./gradlew assembleRelease
```

每次 push / PR 会自动触发 CI（`.github/workflows/ci.yml`）：跑全部测试 + 构建 debug 包；配置了 Secrets 的仓库还会构建并校验签名的 release 包。**PR 合并前必须保证 CI 绿**。

## 4. 代码约定

- **架构**：Jetpack Compose + Material 3，MVVM（ViewModel + Flow），Room 本地库，手动 DI（`di/AppContainer.kt`）。
- **目录**：`app/src/main/java/com/example/personalworkbench/` 下按 `ui/<模块>/`、`data/`、`viewmodel/`、`reminder/`、`util/` 组织，新模块请遵循同样结构。
- **异步**：数据库与文件 IO 走 Repository 的协程 executor，**禁止在主线程做阻塞 IO**。
- **UI**：使用 `LocalSemanticColors` 语义色（成功/警告/危险/信息），按钮与 FAB 统一品牌绿；低龄化风格：大字体、卡通插画、语音反馈优先。
- **语音**：音节键格式为「拼音+声调数字」，分词规则在 Java 与 Python 侧必须保持一致。
- **命名**：APK 交付物固定为 `<应用名>-<版本号>-<release|debug>.apk`（构建层自动完成）。

## 5. 提交规范

采用简短的前缀 + 中文描述（与仓库历史一致）：

```
<type>: <中文简述>
```

常用 type：`feat`（功能）/ `fix`（修复）/ `ci`（流水线）/ `test`（测试）/ `docs`（文档）/ `chore`（杂务）/ `perf`（性能）。一次提交只做一件事，改动超过一个主题请拆分。

## 6. 数据库变更（重要）

修改 Room 实体后**必须**：

1. 在 `data/local/Migrations.kt` 追加 `MIGRATION_x_y`（ADD COLUMN 等纯增量迁移），并注册进 `ALL`；
2. 在 `data/local/AppDatabase.kt` 提升 `version`；
3. 本地构建会自动导出新 schema 到 `app/schemas/`，请一并提交；
4. 迁移冒烟测试（`StartupSmokeTest` 的 v4→v9 夹具）会自动校验迁移后 schema，**注意夹具需包含建索引**，勿再犯只建表不建索引的错。

破坏性迁移（删表/改列类型）需在 PR 描述中单独说明并更新对应测试夹具。

## 7. Pull Request 检查清单

- [ ] `./gradlew testDebugUnitTest` 本地全绿
- [ ] 涉及数据库的改动：已加迁移 + 提升 version + 提交 schemas
- [ ] 未提交 `keystore.properties`、`*.jks`、`local.properties`、`outputs/`（已在 `.gitignore`）
- [ ] 提交信息符合第 5 节规范
- [ ] 界面改动：在 PR 中附截图或说明（如有 UI 测试更佳）

## 8. 许可证

提交 PR 即表示你同意自己的贡献以本仓库的 [MIT License](LICENSE) 发布。
