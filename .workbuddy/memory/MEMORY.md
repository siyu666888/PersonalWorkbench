# PersonalWorkbench 项目长期记忆（D:/PersonalWorkbench）

## 交付命名规范（用户约定，V1.5.6 起）
- APK 输出统一 `<应用名>-<版本号>-<release|debug>.apk`，例：`PersonalWorkbench-1.6.0-release.apk`（用户给的格式样例：`SudokuMaster-1.6.0-release`）。
- 实现位置：`app/build.gradle.kts` 的 `applicationVariants.configureEach { outputs.forEach { (it as BaseVariantOutputImpl).outputFileName = ... } }`，随 versionName 自动更新，release/debug 均生效，禁止再手工改名交付。
- AGP 8 坑：`configureEach` 在 Kotlin DSL 是接收者风格 lambda（无显式参数）；`BaseVariantOutput.outputFileName` setter 已移到内部类 `com.android.build.gradle.internal.api.BaseVariantOutputImpl`，直接赋值会报 Unresolved reference，需转型。

## 版本与数据库
- V1.5.6 / versionCode 30，DB v8（MIGRATION_7_8：files 加 related_type/related_id）。
- 八模块视觉/交互统一迭代（V1.5.2–V1.5.6）已全部完成：全局规范/首页/目标/待办/笔记日志/报表/工程/文件。

## 构建
`cd /d/PersonalWorkbench && ANDROID_HOME=/d/devtools/android-sdk /d/devtools/gradle-8.7/bin/gradle.bat testDebugUnitTest assembleRelease`
