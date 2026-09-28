package com.situ.aichat.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1-3 启动宏基准（量化 Baseline Profile 前后）：
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`，对比两测试的
 * timeToInitialDisplayMs 中位数。AVD 数字仅方向性（suppressErrors=EMULATOR）；正式量化在小米 14 真机批。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() =
        startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        device.pinAppLocale()
        pressHome()
        startActivityAndWait()
        // 门清后非首迭代主内容 5s 内命中即返回（反转探测·保留自愈）；启动计时取自 launch trace 不受影响。
        device.dismissFirstRunGates()
    }
}
