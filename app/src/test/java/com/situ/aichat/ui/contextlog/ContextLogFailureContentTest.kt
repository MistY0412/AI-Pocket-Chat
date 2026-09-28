package com.situ.aichat.ui.contextlog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.diagnostics.LlmFailureKind
import com.situ.aichat.ui.contextlog.LogPageFixtures.at
import com.situ.aichat.ui.contextlog.LogPageFixtures.turnState
import com.situ.aichat.ui.contextlog.model.LogDayKind
import com.situ.aichat.ui.contextlog.model.LogFailureView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-13（四期·图纸四 §4.6）：八类各自的名称 / 原因 / 两步建议逐字（期望值照 §4.9 表重新打字）、「去 API 设置」只在三类出现、
 * 详情键值、原始报错默认收起点开可见。
 */
internal abstract class LogFailureContentCases : LogPageTestBase() {

    @Composable
    abstract fun Content(state: ContextLogEntryUiState, onOpenApiSettings: () -> Unit)

    /** 类别 → 名称 / 原因 / 第一步 / 第二步（§4.9 逐字）。 */
    private val copy = mapOf(
        LlmFailureKind.TIMEOUT to listOf("超时", "服务商太久没回话，App 等不及就断开了，这一次没有拿到回复。", "稍后再试一次", "经常超时的话，换一个响应更快的模型，或者在「API 设置」里换一家服务商"),
        LlmFailureKind.BAD_FORMAT to listOf("格式不对", "服务商说这次的请求它处理不了（或者它回来的内容 App 读不懂），常见原因是模型名或接口地址填错了。", "去「API 设置」核对模型名和接口地址", "刚换过服务商的话，先点一下「测试连接」"),
        LlmFailureKind.RATE_LIMITED to listOf("太频繁", "短时间内请求太多，服务商让 App 先歇一会儿。", "等一两分钟再聊", "经常碰到的话，可以把记忆总结、成长分析这些后台功能分到另一个 API 配置上"),
        LlmFailureKind.CONTENT_BLOCKED to listOf("内容被拦", "服务商的安全审核认为这次的内容不合适，拦下了，这一轮没有拿到回复。", "换一种说法再试", "经常被拦的话，换一家审核宽松一些的服务商"),
        LlmFailureKind.INVALID_KEY to listOf("key 无效", "服务商不认这把 API key——可能填错了、过期了，或者被停用了。", "去「API 设置」重新粘贴 key", "在服务商后台确认这把 key 还有效"),
        LlmFailureKind.INSUFFICIENT_BALANCE to listOf("余额不足", "服务商说这个账户的余额不够了，这一轮没有拿到回复。", "去服务商后台充值", "或者在「API 设置」换一个 key、换一家服务商"),
        LlmFailureKind.NETWORK to listOf("网络断了", "这一次手机没能连上服务商——可能没网、信号不好，或者服务商地址连不通。", "检查网络后再试", "用了代理的话，看看代理是否开着"),
        LlmFailureKind.OTHER to listOf("其他", "服务商那边出了别的问题（比如服务器忙、临时故障），这一次没有拿到回复。", "稍后再试", "一直出现的话，看看下面的原始报错里写了什么"),
    )
    private val withButton = setOf(LlmFailureKind.INVALID_KEY, LlmFailureKind.INSUFFICIENT_BALANCE, LlmFailureKind.BAD_FORMAT)

    private fun state(kind: LlmFailureKind) = turnState(entry = LogPageFixtures.turnEntry.copy(isSuccess = false)).copy(
        failure = LogFailureView(kind, 402, "HTTP 402 · Insufficient Balance", 2, listOf(at(27, 21, 31), at(27, 21, 52)), LogDayKind.TODAY),
    )

    @Test
    fun eightKinds_copyAndButton() {
        val kind = mutableStateOf(LlmFailureKind.TIMEOUT)
        host { Content(state(kind.value), onOpenApiSettings = { events += "api" }) }
        for ((k, texts) in copy) {
            compose.runOnIdle { kind.value = k }
            compose.waitForIdle()
            for (t in texts) assertEquals("$k「$t」", 1, count(t))
            assertEquals("$k 的按钮", if (k in withButton) 1 else 0, count("去 API 设置"))
            assertEquals(1, count("1."))
            assertEquals(1, count("2."))
        }
    }

    @Test
    fun detailsAndRawErrorCollapsed() {
        host { Content(state(LlmFailureKind.INSUFFICIENT_BALANCE), onOpenApiSettings = { events += "api" }) }
        for (t in listOf("失败详情", "可以这样做", "时间", "今天 21:52:07", "角色", "林晚 · 你：「在干嘛呢」", "模型", "deepseek-chat", "同类失败", "今天 2 次（21:31、21:52）", "原始报错")) {
            assertEquals("「$t」", 1, count(t))
        }
        assertEquals("默认收起", 0, count("HTTP 402 · Insufficient Balance"))
        compose.onNodeWithText("原始报错", useUnmergedTree = true).performClick()
        assertEquals(1, count("HTTP 402 · Insufficient Balance"))
        compose.onNodeWithText("去 API 设置", useUnmergedTree = true).performClick()
        assertEquals(listOf("api"), events)
    }

    @Test
    fun succeededEntry_treatedAsMissing() {
        host { Content(turnState(), onOpenApiSettings = {}) }
        assertEquals(1, count("记录不存在或已被清除"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h2400dp")
internal class ContextLogFailureContentTest : LogFailureContentCases() {
    override val skin = AppSkin.CLAY

    @Composable
    override fun Content(state: ContextLogEntryUiState, onOpenApiSettings: () -> Unit) = ContextLogFailureContent(state, {}, onOpenApiSettings)
}
