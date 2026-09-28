package com.situ.aichat.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1-3 Baseline Profile 生成（典型旅程：冷启 → 聊天列表 → 会话 → 资料页）。
 * 跑法：`./gradlew :app:generateBaselineProfile`（已连接 API 33+ user build 设备/AVD 即可，免 root）。
 * 产物 app/src/release/generated/baselineProfiles/ 两份 txt 提交进 git（侧载分发随包生效的本体）。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** 冷启到首帧 → 同时入 startup-prof.txt（dex 布局优化，P1-30 已开 R8 即生效）。 */
    @Test
    fun startup() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        device.pinAppLocale()
        pressHome()
        startActivityAndWait()
        device.dismissFirstRunGates()
    }

    /** 典型旅程（两测试产物由插件自动合并进 baseline-prof.txt）。首迭代过门+建角色，后续迭代直走列表。 */
    @Test
    fun fullJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        device.pinAppLocale()
        pressHome()
        startActivityAndWait()
        device.dismissFirstRunGates()
        device.ensureCharacterExists()
        device.openFirstChat()
        device.openCharacterProfile()
    }
}
