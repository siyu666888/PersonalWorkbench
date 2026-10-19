// 个人工作台 - 桌面版 (Compose Multiplatform Desktop)
// 共享 Android 版的纯 Kotlin 逻辑（农历/重复规则/日期/项目进度），
// 数据层改为 JSON 文件持久化，UI 为 Compose Material3。
// 打包：./gradlew :desktop:packageExeAppImage  → 产出可直接运行的 PersonalWorkbench.exe
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

version = "1.4.0"
group = "com.example.personalworkbench"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

// 打可直接双击运行的 EXE：jpackage app-image 模式
// （不依赖 Inno Setup / WiX，本机 JDK 17 自带 jpackage）
val jdkHome = "D:/devtools/jdk17"

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.personalworkbench.desktop.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

/** 把运行时 classpath 全部 jar 与本模块 jar 平铺到 build/jpackage-in（改名为唯一名避免冲突） */
val stageJpackageInput by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("jpackage-in"))
    var seq = 0
    from(configurations.getByName("runtimeClasspath")) {
        rename { _ -> "dep-${seq++}.jar" }
    }
    from(tasks.jar) {
        rename { "PersonalWorkbench.jar" }
    }
}

/** jpackage app-image：产出 build/app-image/PersonalWorkbench/PersonalWorkbench.exe */
val packageExeAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "打包个人工作台桌面版为可直跑 EXE (jpackage app-image)"
    dependsOn(stageJpackageInput)
    val dest = layout.buildDirectory.dir("app-image")
    // jpackage 不会覆盖已存在的输出目录，先清理本任务自己的构建产物
    doFirst {
        val target = dest.get().asFile.resolve("PersonalWorkbench")
        if (target.exists()) target.deleteRecursively()
    }
    commandLine(
        "$jdkHome/bin/jpackage.exe",
        "--type", "app-image",
        "--name", "PersonalWorkbench",
        "--dest", dest.get().asFile.absolutePath,
        "--input", layout.buildDirectory.dir("jpackage-in").get().asFile.absolutePath,
        "--main-jar", "PersonalWorkbench.jar",
        "--main-class", "com.example.personalworkbench.desktop.MainKt",
        "--app-version", "1.4.0",
        "--vendor", "PersonalWorkbench",
        "--description", "个人工作台 - 桌面版 V1.4.0",
        "--java-options", "-Xmx512m"
    )
}
