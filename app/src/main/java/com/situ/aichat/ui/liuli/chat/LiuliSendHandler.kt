package com.situ.aichat.ui.liuli.chat

import com.situ.aichat.ui.chat.ChatSendFlightState

/**
 * 发送一条消息的完整通路（纯函数·T2-5）：VM 受理 → 绕心情四色一步 → 交给飞入握手。
 *
 * A-7：清空输入框是 [commit]，由 [ChatSendFlightState.tryBegin] 决定「押后到新气泡就位那一帧」还是
 * 「立即」（闸关时立即 = 与旧写法同帧）。发送被拒（[send] 回 false）根本不进握手，输入框原样保留。
 */
internal fun liuliSendHandler(
    text: String,
    send: (String) -> Boolean,
    gatesOpen: Boolean,
    sendFlight: ChatSendFlightState,
    commit: () -> Unit,
    onAccepted: () -> Unit,
): Boolean {
    if (!send(text)) return false
    onAccepted()
    sendFlight.tryBegin(text, gatesOpen, commit)
    return true
}
