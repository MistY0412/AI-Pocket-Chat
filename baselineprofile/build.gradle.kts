import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// P1-3 Baseline Profile 生成 + 启动宏基准（com.android.test 单模块承载两类测试，
// androidx.baselineprofile 插件按 androidx.benchmark.enabledRules 运行参数自动分流——AGP 8.13 官方模板形态）。
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.situ.aichat.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 模拟器跑宏基准把 EMULATOR 错误降为告警（量化结论以小米 14 真机批为准）。
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // 稳定性防线 A：与 :app 同口径（警告即错误·-PkotlinWarningsAsErrors=false 应急关）。
        allWarningsAsErrors = providers.gradleProperty("kotlinWarningsAsErrors").orNull != "false"
    }
}

// 生成用已连接设备（现成 Pixel_9_Pro AVD / 小米 14），不引 GMD、不下镜像。
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
