package com.situ.aichat.ui.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.model.DiaryVisibility
import com.situ.aichat.prompt.diary.DiaryGuideAnswers
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.AppMotion
import com.situ.aichat.ui.components.rememberReduceMotion
import java.time.LocalDate

/** 配图上限（多图 ≤ 9·相册多选上限同一个数）。 */
internal const val DIARY_MAX_IMAGES = 9

internal val ComposeDiaryState.hasContent: Boolean get() = content.isNotBlank()
internal val ComposeDiaryState.canSave: Boolean get() = hasContent
internal val ComposeDiaryState.canAddImage: Boolean get() = images.size < DIARY_MAX_IMAGES
/** J5「今天的素材」只在正文为空且有素材时出现。 */
internal fun ComposeDiaryState.showsMaterialChips(chips: List<MaterialChip>): Boolean = content.isEmpty() && chips.isNotEmpty()

/**
 * 撰写页页级状态与流程（琉璃 2.0 卷六·一 §3.7：自 [ComposeDiaryScreen] **只搬不改**抽出，两张脸共用）。
 * 状态字段与动作一一对应原屏里的 `remember` 量与内联 lambda。
 */
@Stable
internal class ComposeDiaryPageState(
    val snackbarHostState: SnackbarHostState,
    val headScale: Animatable<Float, AnimationVector1D>,
    private val viewModel: ComposeDiaryViewModel,
    private val onClose: () -> Unit,
    private val launchImagePicker: () -> Unit,
) {
    var showDiscard by mutableStateOf(false)
    /**
     * R1 🔵-1：会话内用户切换心情的计数（页级 remember·跨邮票进出组合存活）——仅经用户点 MoodPill 递增；
     * 编辑预置心情/进程恢复/AI 回填都不碰它，故那些场景邮票 tick=0 → 静置落位不盖章不震（无操作不震动）。
     */
    var moodSelectTick by mutableIntStateOf(0)
        private set
    /** U2①：三问引导 sheet（「让 TA 帮你起个头」升级为可留空的三问·答案注入生成）。 */
    var showGuideSheet by mutableStateOf(false)
    /** M7/刀④ 发布落定仪式进行中（true 后由 [rememberComposeDiaryPageState] 播完仪式再保存）。 */
    var publishing by mutableStateOf(false)
        private set

    fun attemptClose() {
        if (viewModel.hasUnsavedChanges) showDiscard = true else onClose()
    }
    fun pickImages() = launchImagePicker()
    fun toggleMood(emoji: String, text: String) {
        moodSelectTick++
        viewModel.toggleMood(emoji, text)
    }
    fun toggleVisibility(current: DiaryVisibility) {
        viewModel.setVisibility(if (current == DiaryVisibility.OPEN_TO_AI) DiaryVisibility.PRIVATE else DiaryVisibility.OPEN_TO_AI)
    }
    /** 素材芯片：起笔句尾带换行（走 J1 镜像）。 */
    fun pickMaterial(starter: String) = viewModel.setContent(starter + "\n")
    fun saveDraft() = viewModel.save(asDraft = true, onDone = onClose)
    fun record(haptics: AppHaptics) {
        if (!publishing) {
            haptics.success()
            publishing = true
        }
    }
    fun generate(guide: DiaryGuideAnswers) {
        showGuideSheet = false
        viewModel.generateAiDraft(guide)
    }
    fun discardAndClose() {
        showDiscard = false
        viewModel.discard()
        onClose()
    }
}

@Composable
internal fun rememberComposeDiaryPageState(
    viewModel: ComposeDiaryViewModel,
    onClose: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
): ComposeDiaryPageState {
    val reduceMotion = rememberReduceMotion()
    val currentOnClose by rememberUpdatedState(onClose)
    val snackbarHostState = remember { SnackbarHostState() }

    // P0-15：AI 帮写失败反馈——未配置 API 显「去设置」跳配置；其它失败显具体原因（含网络）。
    val noApiText = stringResource(R.string.diary_ai_no_api)
    val genFailedText = stringResource(R.string.diary_ai_generate_failed)
    val goSettingsText = stringResource(R.string.bg_action_open_settings)
    LaunchedEffect(Unit) {
        viewModel.aiDraftError.collect { err ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (err) {
                ComposeDiaryViewModel.AiDraftError.NoApi -> {
                    val result = snackbarHostState.showSnackbar(message = noApiText, actionLabel = goSettingsText)
                    if (result == SnackbarResult.ActionPerformed) onNavigateToApiConfig()
                }
                is ComposeDiaryViewModel.AiDraftError.Failed ->
                    snackbarHostState.showSnackbar(err.message ?: genFailedText)
            }
        }
    }

    // J6 说一段一次性提示（录音失败/太短/转写失败三态·已解析成串）。
    LaunchedEffect(Unit) {
        viewModel.voiceMessage.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(DIARY_MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }
    val headScale = remember { Animatable(1f) }
    val state = remember(viewModel) {
        ComposeDiaryPageState(
            snackbarHostState = snackbarHostState,
            headScale = headScale,
            viewModel = viewModel,
            onClose = { currentOnClose() },
            launchImagePicker = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
    // M7/刀④ 发布落定仪式：点「记下」→ 日期头轻弹落定（celebrate ζ0.5·每屏限一处）→ 保存返回。reduceMotion 直接保存。
    LaunchedEffect(state.publishing) {
        if (state.publishing) {
            if (!reduceMotion) {
                headScale.animateTo(1.06f, AppMotion.livelySpring())
                headScale.animateTo(1f, AppMotion.celebrateSpring())
            }
            viewModel.save(asDraft = false, onDone = { currentOnClose() })
        }
    }
    return state
}

/** M3 每日引导语：按当天日期取一句（一天一句·稳定不随重组抖动·原 :125–126 逐字）。 */
@Composable
internal fun rememberDiaryDailyPrompt(): String {
    val prompts = stringArrayResource(R.array.diary_compose_prompts)
    return remember(prompts.size) { prompts[(LocalDate.now().dayOfYear - 1).mod(prompts.size)] }
}

/** M1/M2 心情色回声（原 :129–130）：选好心情 → 洇染到该情绪浅档；无心情 → [fallback]（暖陶 = 页底色、琉璃 = 纸色）。 */
@Composable
internal fun rememberComposeDiaryWash(moodEmoji: String?, fallback: Color): Color {
    // M1/M2 心情色回声：选好心情 → 日期头背后洇染对应装饰浅档（效果轴·reduceMotion 保留）。
    val washTarget = diaryMoodTint(moodEmoji) ?: fallback
    val wash by animateColorAsState(washTarget, tween(durationMillis = 320), label = "diaryMoodWash")
    return wash
}
