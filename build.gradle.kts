// 个人工作台 - 根 build 配置
// 技术栈: Kotlin + Jetpack Compose (详见 PRD 5.1)
// 多模块: :app (Android) + :desktop (Compose Multiplatform Desktop)
// 插件版本统一在根声明 (apply false)，子模块直接引用不带版本
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    // Kotlin 1.9.x 的 Compose 编译器由 org.jetbrains.compose 插件自动配置，
    // 不需要 Kotlin 2.0 才有的 org.jetbrains.kotlin.plugin.compose
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}
