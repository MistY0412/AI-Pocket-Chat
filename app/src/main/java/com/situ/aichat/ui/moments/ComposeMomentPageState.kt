package com.situ.aichat.ui.moments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.ui.components.AppHaptics

/** 发布页三个派生量（原 ComposeMomentScreen :74–76 逐式·纯·T1）。 */
internal val ComposeMomentState.charCount: Int get() = content.length
internal val ComposeMomentState.overLimit: Boolean get() = charCount > ComposeMomentViewModel.MAX_CHARS
internal val ComposeMomentState.canPublish: Boolean get() = content.isNotBlank() && !overLimit && !publishing

/** 还能不能加图（原 ImageSection :169 逐式·纯·T1）。 */
internal fun composeMomentCanAddImage(images: List<String>): Boolean = images.size < ComposeMomentViewModel.MAX_IMAGES

/**
 * 发布页页级状态与流程（琉璃 2.0 卷六·二抽出·朋友圈发布页·乙 §3.3.2 改造·两张脸共用）：关页守卫（有改动先问保留）、
 * 选人弹层 / 保留对话框 / 看大图三处开关、发布（可发时 success 轻震）、页内提示条。
 */
@Stable
internal class ComposeMomentPageState(
    private val viewModel: ComposeMomentViewModel,
    private val onClose: () -> Unit,
    private val launchImagePicker: () -> Unit,
    val snackbarHostState: SnackbarHostState,
) {
    var showKeepDialog by mutableStateOf(false)
    var showPicker by mutableStateOf(false)
    var viewerPath by mutableStateOf<String?>(null)

    fun attemptClose() { if (viewModel.hasUnsavedChanges) showKeepDialog = true else onClose() }
    fun keepAndClose() { showKeepDialog = false; viewModel.keepDraft(); onClose() }
    fun discardAndClose() { showKeepDialog = false; viewModel.discard(); onClose() }
    fun pickImages() = launchImagePicker()
    fun publish(haptics: AppHaptics) {
        if (viewModel.state.value.canPublish) haptics.success()
        viewModel.publish(onClose)
    }
    fun openPicker() { viewModel.refreshMentionAvailability(); showPicker = true }
    fun closePicker() { showPicker = false }
    fun openViewer(path: String) { viewerPath = path }
    fun closeViewer() { viewerPath = null }
}

/**
 * 页级状态 + 两条页内提示（§3.3.2）：进页若是接着草稿写的，弹一次「已接着上次没发完的写 · 清空」（Short·点清空 = clearAll）；
 * 「说一段」出错时先收掉当前提示再弹（同日记 `ComposeDiaryPageState`）。
 */
@Composable
internal fun rememberComposeMomentPageState(viewModel: ComposeMomentViewModel, onClose: () -> Unit): ComposeMomentPageState {
    val currentOnClose by rememberUpdatedState(onClose)
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ComposeMomentViewModel.MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }
    val snackbarHostState = remember { SnackbarHostState() }
    val restored = stringResource(R.string.moment_compose_restored)
    val clear = stringResource(R.string.moment_compose_restored_clear)
    LaunchedEffect(viewModel) {
        if (viewModel.state.value.restoredFromDraft) {
            viewModel.consumeDraftRestored()
            val r = snackbarHostState.showSnackbar(restored, actionLabel = clear, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) viewModel.clearAll()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.voiceMessage.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }
    return remember(viewModel) {
        ComposeMomentPageState(
            viewModel = viewModel,
            onClose = { currentOnClose() },
            launchImagePicker = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            snackbarHostState = snackbarHostState,
        )
    }
}
