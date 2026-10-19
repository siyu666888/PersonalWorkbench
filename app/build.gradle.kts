import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// ---------------------------------------------------------------------------
// 发布签名配置
// keystore 本体存放于仓库外（D:/devtools/keystore/personalworkbench-release.jks），
// keystore.properties 已加入 .gitignore，禁止提交到版本库。
// 文件缺失时（如他人拉取源码 / CI）自动跳过签名，debug 构建不受影响。
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasReleaseSigning) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.personalworkbench"
    compileSdk = 34

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // minSdk 26 默认只启用 v1/v2；显式开启 v3 以支持后续密钥轮换。
                // API 26/27 设备会自动忽略 v3 块，回退 v2，无兼容性风险。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.personalworkbench"
        minSdk = 26          // PRD 6.2: 最低支持 Android 8.0 (API 26)
        targetSdk = 34
        versionCode = 31
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Room schema 导出位置：配合 exportSchema = true，迁移前可校验、可写迁移测试
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Compose 编译器版本 (Kotlin 1.9 兼容)
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // Robolectric 冒烟测试需要 App 合并后的 manifest 与资源（否则用默认空 manifest，Activity 无法解析）
        unitTests.isIncludeAndroidResources = true
    }

    // 交付命名规范（用户约定，V1.5.6 起）：<应用名>-<版本号>-<release|debug>.apk
    // 例：PersonalWorkbench-1.6.0-release.apk（SudokuMaster-1.6.0-release 同款格式）
    // 注意：AGP 8 的 applicationVariants.outputs 是 List，用 forEach 遍历；
    //       configureEach 在 Kotlin DSL 里是接收者风格 lambda（无显式参数）；
    //       outputFileName 的 setter 在 AGP 8 移到了内部实现类 BaseVariantOutputImpl，需转型。
    applicationVariants.configureEach {
        val variant = this
        outputs.forEach { output ->
            val impl = output as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val newName = "PersonalWorkbench-${variant.versionName}-${variant.buildType.name}.apk"
            if (impl.outputFileName != newName) {
                impl.outputFileName = newName
            }
        }
    }
}

dependencies {
    // ---- 核心 ----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ---- Compose (Material 3) ----
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Navigation ----
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ---- 数据库 Room ----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ---- 偏好设置 DataStore ----
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---- 本地提醒调度 WorkManager ----
    // 替代 AlarmManager：无需 SCHEDULE_EXACT_ALARM（Google Play 受限权限），
    // 任务持久化、跨重启/跨厂商 ROM 可靠，代价是触发时刻不精确（省电策略下可能晚几分钟）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ---- 异步 ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---- 图片加载 (缩略图缓存) ----
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ---- 日期处理 ----
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.6")

    // ---- 应用锁生物识别 (Feature 3) ----
    implementation("androidx.biometric:biometric:1.1.0")

    // ---- 测试 ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // Robolectric：JVM 上真实启动 Application/Activity，复现「打开闪退」类启动崩溃（V1.3.8 排障）
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}
