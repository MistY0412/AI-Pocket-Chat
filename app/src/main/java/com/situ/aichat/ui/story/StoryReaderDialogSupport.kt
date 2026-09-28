package com.situ.aichat.ui.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.story.StoryChapterDraft
import com.situ.aichat.story.StoryEndingType
import com.situ.aichat.story.StoryTextSanitizer

// 阅读器弹层与对话框两张脸共用的判据 / 取值 / 页内态（琉璃 2.0 卷六·三·下乙 chunk 1·自 StoryReaderDialogs /
// StoryCustomChoiceSheet / StoryPreviousDraftSheet / StoryDirectorSheet 只搬不改）。琉璃版不许另写一份同式。

/** 「续写」确认的正文（原 ReaderDialogs `Continue` 支的 `if` 式·纯·T1）：当前章还挂着未答选择 → 提醒版。 */
@StringRes
internal fun storyContinueAlertBodyRes(currentHasPendingChoice: Boolean): Int =
    if (currentHasPendingChoice) R.string.story_alert_continue_pending_msg else R.string.story_alert_continue_msg

/** 结局三选的前两项（原 `EndingPicker` 支前两枚钮的次序与映射·纯·T1）：开放式 / AI 自由发挥；第三项「我来指定结局」走 onOpenEndingCustom。 */
internal val storyEndingQuickOptions: List<Pair<Int, String>> = listOf(
    R.string.story_ending_open to StoryEndingType.OPEN,
    R.string.story_ending_ai to StoryEndingType.AI,
)

/** 重写指令（原 `RewriteInstruction` 支 `it.ifEmpty { null }`·纯·T1）：空串 = 直接重写（null）。 */
internal fun storyRewriteInstruction(text: String): String? = text.ifEmpty { null }

/** 角色现状只读区正文（原 `CharacterStates` 支式）：空白 → 「暂无」文案。 */
@Composable
internal fun storyCharacterStatesText(characterStates: String?): String =
    characterStates?.takeIf { it.isNotBlank() } ?: stringResource(R.string.story_states_empty)

/** 多行输入弹窗的确认（原 TextInputDialog `onConfirm` 式）：trim 后交出，再关框。 */
internal fun storyDialogInputConfirm(text: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    onConfirm(text.trim())
    onDismiss()
}

/** 自由输入可提交的文本（原 StoryCustomChoiceSheet `trimmed` 与两处 `isNotEmpty()`·纯·T1）：trim 后为空 → null（确认置灰）。 */
internal fun storyCustomChoiceSubmitText(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

/** 自由输入弹层呈现后再弹键盘的等待（原 `delay(250)`）。 */
internal const val STORY_CUSTOM_CHOICE_FOCUS_DELAY_MS = 250L

/** 「上一版」正文（原 StoryPreviousDraftSheet `prose` 式）：过 [com.situ.aichat.story.StoryTextSanitizer] 剥沉浸标签与尾部元数据——生肉零泄漏（全故事域红线）。 */
@Composable
internal fun rememberStoryPrevDraftProse(draft: StoryChapterDraft): String =
    remember(draft.content) { StoryTextSanitizer.sanitize(draft.content.orEmpty()) }

/** 「上一版 · 〈旧稿标题〉」（原 `title` 与标题 `Text` 两式）：旧稿标题缺 / 空白 → 只「上一版」。 */
@Composable
internal fun storyPrevDraftTitle(draft: StoryChapterDraft): String {
    val title = draft.title?.takeIf { it.isNotBlank() }
    return stringResource(R.string.story_prev_draft_title) + (title?.let { " · $it" } ?: "")
}

/**
 * 导演台页内态（原 StoryDirectorSheet 六个 `remember` + 三个 dirty 判据 + `save()` 逐式·含原注释·两张脸共用）。
 * 只存界面态；判据与动作每次接收**当下的**已存走向 / 节拍底稿 / 回调（上半卷 [StoryRoleEditorState] 同法：
 * `remember` 住的态对象不存回调，否则停首帧）。
 */
@Stable
internal class StoryDirectorEditorState(initialFlow: String, initialBeats: String) {
    var flowText by mutableStateOf(initialFlow)
    var beatsText by mutableStateOf(initialBeats)
    // 防重入（卷二 StoryFieldEditorViewModel 同款）：写口是 fire-and-forget，连点会发两遍。
    var saving by mutableStateOf(false)
    var confirmDiscard by mutableStateOf(false)
    var confirmWithdraw by mutableStateOf(false)

    // ⚠️ 三个 dirty 判据一律写成**函数**、点到才算，绝不缓存成捕获值（装机实测踩坑·图纸 §11 D-7）：
    // 局部 fun 的函数引用（`::save`）在重组之间被 Compose 判为「参数没变」→ 整个按钮被跳过更新 →
    // 里面捕获的 `val beatsDirty` 永远停在首帧的 false，用户改了字照样一个字节都不写库（静默失效）。
    // 写成函数后读的是 MutableState 的当下值，与是谁持有这个闭包无关。
    // 编辑模式下「非空」还不够：与已存走向逐字相同 = 没改，纯关面板不写库（清空则交撤回按钮，见 hint）。
    fun flowDirty(savedDirection: String?) =
        flowText.isNotBlank() && (savedDirection == null || flowText.trim() != savedDirection.trim())
    fun beatsDirty(initialBeats: String) = beatsText.trim() != initialBeats.trim()
    fun dirty(savedDirection: String?, initialBeats: String) = flowDirty(savedDirection) || beatsDirty(initialBeats)

    /** 保存分派（§4.4）：哪栏变了发哪条；栏 A 的**路由**看 [directionCommitted]——已答覆盖直写，没答走创建路。 */
    fun save(
        savedDirection: String?,
        initialBeats: String,
        directionCommitted: Boolean,
        onSubmitFlow: (String) -> Unit,
        onOverwriteDirection: (String) -> Unit,
        onSaveBeats: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (saving) return
        saving = true
        if (flowDirty(savedDirection)) {
            if (directionCommitted) onOverwriteDirection(flowText.trim()) else onSubmitFlow(flowText.trim())
        }
        if (beatsDirty(initialBeats)) onSaveBeats(beatsText)
        onDismiss()
    }

    /** 下拉 / 返回 / 关闭：改过先问（弃改确认），没改直接关（原 `AppSheet(onDismissRequest = …)` 式）。 */
    fun requestDismiss(savedDirection: String?, initialBeats: String, onDismiss: () -> Unit) {
        if (dirty(savedDirection, initialBeats)) confirmDiscard = true else onDismiss()
    }

    /** 「恢复 AI 预排」（原钮 `onClick` 式）：保存中不响应；发出即关。 */
    fun restoreAiBeats(onRestoreAiBeats: () -> Unit, onDismiss: () -> Unit) {
        if (!saving) { saving = true; onRestoreAiBeats(); onDismiss() }
    }

    /** 「撤回走向」第一段（原钮 `onClick` 式）：只弹确认；保存中不响应。 */
    fun requestWithdraw() {
        if (!saving) confirmWithdraw = true
    }

    /** 撤回确认（原确认框 `onConfirm` 式）：发撤回并关。 */
    fun confirmWithdrawAndClose(onWithdrawDirection: () -> Unit, onDismiss: () -> Unit) {
        confirmWithdraw = false
        saving = true
        onWithdrawDirection()
        onDismiss()
    }

    /** 弃改确认（原确认框 `onConfirm` 式）：一个字节都不写库，直接关。 */
    fun discardAndClose(onDismiss: () -> Unit) {
        confirmDiscard = false
        onDismiss()
    }
}

/** 节拍底稿（原 `remember(beats) { beats.orEmpty() }`）：dirty 判据以它为准（键 = 当下的 beats）。 */
@Composable
internal fun rememberStoryDirectorInitialBeats(beats: String?): String = remember(beats) { beats.orEmpty() }

/** 页内态（原两枚 `mutableStateOf` 的初值：走向 = 已存走向或空、节拍 = 底稿）。 */
@Composable
internal fun rememberStoryDirectorEditorState(savedDirection: String?, initialBeats: String): StoryDirectorEditorState =
    remember { StoryDirectorEditorState(savedDirection.orEmpty(), initialBeats) }
