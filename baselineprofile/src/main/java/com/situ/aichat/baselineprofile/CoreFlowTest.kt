package com.situ.aichat.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 稳定性防线 G（2026-09-28 用户拍板）：核心流程 UI 测试——在混淆后的包（benchmarkRelease）上从头走一遍：
 * 冷启 → 过协议 / 引导 → 四个底栏页逐个点开 → 建角色 → 进聊天 → 进资料页 → 回聊天发一条消息 → 「聊天」页会话预览出现这句（= 已落库）。
 * 每步硬断言（失败信息带当时屏上文字）；复用 [Journeys] 的已打磨旅程，不另写第二份选择器。
 *
 * 跑法 = `tools/stability/core_flow.sh --device <模拟器> --wipe-ok`（全新安装起步）。只在 instrumentation 参数
 * `coreFlow=true` 时运行——生成 Baseline Profile / 跑启动基准时本类自动跳过，不拖慢、不污染那两条流程。
 */
@RunWith(AndroidJUnit4::class)
class CoreFlowTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun onlyWhenAsked() = assumeTrue(
        "只由 tools/stability/core_flow.sh 触发（instrumentation 参数 coreFlow=true）",
        InstrumentationRegistry.getArguments().getString(ARG_CORE_FLOW) == "true",
    )

    @Test
    fun coreFlow() {
        device.pinAppLocale()
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity")
        device.dismissFirstRunGates()
        device.visitBottomTabs()
        device.ensureCharacterExists()
        device.openFirstChat()
        device.openCharacterProfile()
        device.findObject(By.text(appString("tab_contacts")))?.click()
        device.openFirstChat()
        device.sendMessageAndSeeItSaved()
        check(device.executeShellCommand("pidof $TARGET_PACKAGE").isNotBlank()) { "app process died at end of core flow" }
    }

    /** 四个底栏页逐个点开：每页点完 App 仍在前台、底栏仍在（页面组合期崩溃 / 跳出 App 都会在这里红）。 */
    private fun UiDevice.visitBottomTabs() {
        for (tab in listOf("tab_contacts", "tab_moments", "tab_profile", "tab_chats")) {
            val sel = By.text(appString(tab))
            check(wait(Until.hasObject(sel), SHORT_WAIT)) { "bottom tab '$tab' missing; screen=[${visibleTexts()}]" }
            retryOnStale { findObject(sel).click() }
            waitForIdle()
            check(wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), SHORT_WAIT)) { "left the app after tapping '$tab'" }
            bpLog("tabs: $tab ok")
        }
    }

    /**
     * 输入 → 点发送 → 输入框回到占位符（已清空）→ 退回「聊天」页，会话行预览必须是这句话（「你: 核心流程体检」）。
     * 本机无 API key：回复会失败，但用户这句必须落库——「发出去的话不见了」正是核心流程最该拦的坏法。
     * 不拿聊天屏里的气泡当判据：气泡截图里在，无障碍树里却时有时无（记忆 reference-uiautomator-dump-omits-nodes；
     * 2026-09-28 本测试 6 跑 3 次读不到、截图里气泡都在）；会话行预览只在消息真正落库后出现（零消息会话不进列表），稳且更硬。
     */
    private fun UiDevice.sendMessageAndSeeItSaved() {
        val inputSel = By.clazz("android.widget.EditText")
        check(wait(Until.hasObject(inputSel), SHORT_WAIT)) { "chat input not found; screen=[${visibleTexts()}]" }
        // 先点一下输入框再填字（照真人来）：琉璃输入框没聚焦时，无障碍「直接设文字」不生效（09-28 默认脸改琉璃后实测：
        // 设完仍是空、点一下再设就进去了）；暖陶两种都行。点了会弹键盘，发完先收键盘再返回（见下）。
        retryOnStale { findObject(inputSel).click() }
        waitForIdle()
        retryOnStale { findObject(inputSel).text = PROBE_MESSAGE }
        val send = By.desc(appString("a11y_send"))
        check(wait(Until.hasObject(send), SHORT_WAIT)) { "send button not shown after typing; screen=[${visibleTexts()}]" }
        retryOnStale { findObject(send).click() }
        check(wait(Until.hasObject(By.text(CHAT_INPUT_PLACEHOLDER)), LAUNCH_WAIT)) { "input not cleared after send; screen=[${visibleTexts()}]" }
        val chatsTab = By.text(appString("tab_chats"))
        pressBack() // 键盘还在就先收键盘；不在则这一下直接 聊天 → 主脚手架
        if (!wait(Until.hasObject(chatsTab), 1_500L)) pressBack() // 聊天 → 主脚手架（停在进来时的联系人页）
        check(wait(Until.hasObject(chatsTab), SHORT_WAIT)) { "main scaffold not back after send; screen=[${visibleTexts()}]" }
        retryOnStale { findObject(chatsTab).click() }
        check(wait(Until.hasObject(By.textContains(PROBE_MESSAGE)), LAUNCH_WAIT)) { "sent message not saved (no chat-list preview); screen=[${visibleTexts()}]" }
        bpLog("send: saved, chat-list preview shown")
    }

    private companion object {
        const val ARG_CORE_FLOW = "coreFlow"
        const val PROBE_MESSAGE = "核心流程体检"
        const val SHORT_WAIT = 3_000L
        const val LAUNCH_WAIT = 15_000L
    }
}
