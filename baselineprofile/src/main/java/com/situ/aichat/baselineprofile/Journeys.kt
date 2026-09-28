package com.situ.aichat.baselineprofile

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.situ.aichat"
internal const val CHARACTER_NAME = "小基线"

/**
 * 旅程钉死的 App 显示语言（批8 复核修 HIGH）：App 首启 ensureDefaultLocale（13.10d）把 per-app locale
 * 种成 zh-CN，而 instrumentation 进程跟随设备 locale（AVD=en-US）——按测试进程 locale 反查资源会拿到
 * 英文串、全部选择器确定性 miss（实证：首版产物只含 EulaScreen 规则）。生成期显式 set-app-locales 钉死
 * + 资源解析按同 tag 走 createConfigurationContext，两端对齐，无查询解析脆弱点。
 */
internal const val APP_LOCALE = "zh-CN"

/**
 * 聊天输入占位符 → 聊天屏到达哨兵。Fable-5 起已迁字符串资源 R.string.chat_input_placeholder
 * （挂账②清账）：旅程已钉死 APP_LOCALE=zh-CN，资源解析恒定；本哨兵值必须与该资源 zh 值逐字一致，
 * 改资源值必须同步改这里（CLAUDE.md §5）。
 */
internal const val CHAT_INPUT_PLACEHOLDER = "说点什么…"
private const val SHORT_WAIT = 3_000L
private const val LAUNCH_WAIT = 15_000L

/** 旅程阶段标记（logcat 调试用）+ 失败现场可见文本（断言信息携带，定位选择器漂移）。 */
internal fun UiDevice.bpLog(msg: String) {
    executeShellCommand("log -t BPJourney $msg")
}

/**
 * 节点「找到了却在动手前失效」（入场 / 翻页动画期间 Compose 重建节点）时重找重试：[action] 内部自己 findObject，
 * 失效就等一帧再来，最多 [times] 次（2026-09-28 稳定性防线 G 实跑：建角色页名字框 setText 撞 StaleObjectException）。
 */
internal fun UiDevice.retryOnStale(times: Int = 5, action: () -> Unit) {
    repeat(times - 1) {
        try {
            return action()
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            waitForIdle()
        }
    }
    action()
}

internal fun UiDevice.visibleTexts(): String {
    val out = java.io.ByteArrayOutputStream()
    dumpWindowHierarchy(out)
    return Regex("text=\"([^\"]+)\"").findAll(out.toString("UTF-8"))
        .joinToString(" | ") { it.groupValues[1] }.take(400)
}

/** 生成期把目标 App 显示语言钉死为 [APP_LOCALE]（幂等，每旅程开头调；与首启种子同值）。 */
internal fun UiDevice.pinAppLocale() {
    executeShellCommand("cmd locale set-app-locales $TARGET_PACKAGE --user 0 --locales $APP_LOCALE")
}

/** 按资源名取目标 App 实串：解析 locale 钉死为 [APP_LOCALE]（绝不跟随测试进程/设备 locale）。 */
internal fun appString(name: String): String {
    val baseCtx = InstrumentationRegistry.getInstrumentation().context.createPackageContext(TARGET_PACKAGE, 0)
    val cfg = Configuration(baseCtx.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(APP_LOCALE))
    }
    val res = baseCtx.createConfigurationContext(cfg).resources
    val id = res.getIdentifier(name, "string", TARGET_PACKAGE)
    check(id != 0) { "string resource not found: $name" }
    return res.getString(id)
}

/**
 * 首启过门（条件式+硬断言收口，批8 复核修 MED）：主内容已在 → 短等命中即返回（非首迭代零成本+保留自愈）；
 * 否则清协议（滚到底解锁）→ 清引导（「开始体验」=翻页，「稍后再说」=完成唯一出口）→ check 必达主内容。
 * 任何选择器漂移在生成期就红，绝不静默产出瘦 profile。
 */
internal fun UiDevice.dismissFirstRunGates() {
    val mainSentinel = By.text(appString("tab_chats"))
    if (wait(Until.hasObject(mainSentinel), 5_000L)) return
    // 屏哨兵=接受按钮（固定底栏·不随滚动）。AVD 实证三阶教训：①按钮 UiAutomator 属性与 Compose 禁用态
    // 脱钩（enabled 恒 true）→ 盲点+以结果为准；②协议标题随内容滚动，gone(title) 在首次滚动后即误报
    // 「已离屏」——前三轮生成全部假性通过 EULA 段的根因；按钮栏固定，gone(accept) 才是真离屏信号。
    val acceptSel = By.text(appString("agreement_accept_button"))
    bpLog("gates: probing EULA")
    if (wait(Until.hasObject(acceptSel), SHORT_WAIT)) {
        bpLog("gates: EULA visible, clearing")
        var attempts = 0
        while (attempts++ < 30) {
            findObject(acceptSel)?.click()
            if (wait(Until.gone(acceptSel), 1_000L)) break // 禁用态点击被吞→滚一屏再试；离屏=真接受
            findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            waitForIdle()
        }
        check(!hasObject(acceptSel)) { "EULA accept failed after $attempts attempts; screen=[${visibleTexts()}]" }
        bpLog("gates: EULA cleared")
    }
    if (wait(Until.hasObject(By.text(appString("onboarding_welcome_subtitle"))), SHORT_WAIT)) {
        // AVD 实证（批8 复核修 HIGH 的三阶根因）：Pager 翻页动画期间相邻页已组合——「开始体验」点击后
        // 下一轮立即能找到滑入中的「稍后再说」，但 click 用 stale bounds 点空。循环改结果驱动：
        // 每轮动作前 waitForIdle 等动画落定；点「稍后再说」后以主内容出现为准，未达则下轮重试。
        val laterSel = By.text(appString("onboarding_reliability_later"))
        val startSel = By.text(appString("onboarding_start_button"))
        bpLog("gates: onboarding visible, paging")
        var attempts = 0
        while (attempts++ < 12) {
            waitForIdle()
            val later = findObject(laterSel)
            if (later != null) {
                bpLog("gates: clicking later (attempt $attempts)")
                later.click()
                if (wait(Until.hasObject(mainSentinel), 2_000L)) break
                continue
            }
            val start = findObject(startSel)
            if (start != null) {
                bpLog("gates: clicking start (attempt $attempts)")
                start.click()
                // AVD 实证（四阶教训）：点击启动翻页动画后，下一轮的高频点击会触停动画（触摸即中断
                // animateScrollToPage·pager 回弹原页=连点 9 次纹丝不动）。点后必须等页 5 哨兵出现再继续。
                wait(Until.hasObject(laterSel), 2_000L)
            } else {
                bpLog("gates: swiping (attempt $attempts)")
                swipe(displayWidth * 4 / 5, displayHeight / 2, displayWidth / 5, displayHeight / 2, 12)
            }
        }
    }
    check(wait(Until.hasObject(mainSentinel), LAUNCH_WAIT)) { "first-run gates not cleared; screen=[${visibleTexts()}]" }
}

/**
 * 确保「小基线」角色存在并停在联系人页（AVD 实证五阶教训：零消息会话不进聊天列表——本旅程无 API key
 * 永远零消息，列表恒空态 → 改走联系人页，角色存在性与消息无关）。
 * 联系人有数据但无「小基线」→ 硬失败提示换干净设备（绝不往真机真实数据里写垃圾角色——批8 复核裁决）。
 * 名字框定位走 By.clazz(EditText)（批8 复核修 MED：带 label 的 M3 输入框未聚焦+空值时 placeholder
 * 根本不组合；Compose 可编辑节点 className 恒为 android.widget.EditText；setText 走 ACTION_SET_TEXT 免聚焦）。
 */
internal fun UiDevice.ensureCharacterExists() {
    findObject(By.text(appString("tab_contacts")))?.click()
    check(wait(Until.hasObject(By.desc(appString("contacts_create"))), SHORT_WAIT)) { "contacts screen not reached; screen=[${visibleTexts()}]" }
    if (wait(Until.hasObject(By.text(CHARACTER_NAME)), 1_500L)) {
        bpLog("char: exists")
        return
    }
    check(hasObject(By.text(appString("contacts_empty_title")))) {
        "contacts has data but no '$CHARACTER_NAME' — run generation on a clean device/AVD"
    }
    findObject(By.desc(appString("contacts_create"))).click()
    // 以编辑页的「保存」为到达哨兵，不能只等 EditText：转场期间联系人页的搜索框也是 EditText，名字会写进旧页 / 写空
    // （2026-09-28 稳定性防线 G 五连跑 1 次：编辑页名字框空着、保存点了没反应）。写完再确认名字真上了屏，没上就重写。
    check(wait(Until.hasObject(By.text(appString("action_save"))), SHORT_WAIT)) { "character edit screen not reached; screen=[${visibleTexts()}]" }
    var named = false
    var tries = 0
    while (!named && tries++ < 5) {
        waitForIdle()
        retryOnStale { findObject(By.clazz("android.widget.EditText")).text = CHARACTER_NAME }
        named = wait(Until.hasObject(By.text(CHARACTER_NAME)), 1_000L)
    }
    check(named) { "character name not entered after $tries tries; screen=[${visibleTexts()}]" }
    // 同 EULA 钮：Compose 按钮的 UiAutomator enabled 位不反映禁用态——盲点+以到达聊天屏为准。
    findObject(By.text(appString("action_save")))?.click()
    check(wait(Until.hasObject(By.text(CHAT_INPUT_PLACEHOLDER)), LAUNCH_WAIT)) { "chat screen not reached after save; screen=[${visibleTexts()}]" }
    bpLog("char: created, now in chat; back to scaffold")
    pressBack() // onSaved 已 popUpTo(chats)：返回=聊天列表（空态正常）
    findObject(By.text(appString("tab_contacts")))?.click()
    check(wait(Until.hasObject(By.text(CHARACTER_NAME)), SHORT_WAIT)) { "contacts row not found after create; screen=[${visibleTexts()}]" }
}

/** 联系人行点击=进聊天（ContactsScreen onOpenChat）。前置=已在联系人页（ensureCharacterExists 收口态）。 */
internal fun UiDevice.openFirstChat() {
    findObject(By.text(CHARACTER_NAME))?.click()
    check(wait(Until.hasObject(By.text(CHAT_INPUT_PLACEHOLDER)), LAUNCH_WAIT)) { "chat screen not reached; screen=[${visibleTexts()}]" }
}

/**
 * 顶栏标题点击→资料页；滚一屏覆盖 P1-31 LazyColumn。StaleObjectException 重试（AVD 实证六阶教训：聊天屏入场动画期间
 * 节点在 findObject 与 click 之间失效）。
 * 取「屏上最靠上的角色名全等节点」= 顶栏：空会话屏的开场建议卡也显示角色名，旧写法 findObject(By.text) 会点到卡片
 * 进不了资料页（2026-09-28 稳定性防线 G 实跑发现——Baseline Profile 的 fullJourney 同病，此处一并修）。
 */
internal fun UiDevice.openCharacterProfile() {
    val profileSentinel = By.desc(appString("profile_edit_character"))
    var attempts = 0
    while (attempts++ < 5 && !hasObject(profileSentinel)) {
        waitForIdle()
        try {
            findObjects(By.text(CHARACTER_NAME)).minByOrNull { it.visibleBounds.top }?.click()
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            // 节点失效=屏内容在动（入场动画/导航）→ 下轮重找
        }
        wait(Until.hasObject(profileSentinel), 2_000L)
    }
    check(hasObject(profileSentinel)) { "profile screen not reached; screen=[${visibleTexts()}]" }
    // 停留加厚 + 离屏收尾（AVD 实证七阶教训：资料页若是旅程最后一屏、进程随即被杀，ART profile
    // 尾段采样来不及冲刷——此前各屏全有规则唯资料页 0 规则）。下滚+回滚多执行一轮组合，再返回
    // 主脚手架让资料页方法离开「临死窗口」。
    findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f)
    waitForIdle()
    findObject(By.scrollable(true))?.scroll(Direction.UP, 0.6f)
    waitForIdle()
    pressBack() // 资料页 → 聊天
    wait(Until.hasObject(By.text(CHAT_INPUT_PLACEHOLDER)), SHORT_WAIT)
    pressBack() // 聊天 → 主脚手架
    wait(Until.hasObject(By.text(appString("tab_chats"))), SHORT_WAIT)
    // ART profile saver 节奏=首保存 ~5s 后退避（5s/25s/65s…）：迭代 ~22s 即杀则资料页方法（~15-20s 执行）
    // 永远等不到 25s 保存点——驻留 6s 让保存点落在存活期内（AVD 实证：无此驻留资料页恒 0 规则）。
    Thread.sleep(6_000)
}
