package com.situ.aichat.ui.character

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.situ.aichat.util.AvatarStore
import com.situ.aichat.util.WallpaperStore
import kotlinx.coroutines.launch

/**
 * 头像 / 聊天壁纸的「选图 → 取景裁剪 → 存成品」流程（琉璃 2.0 卷五 §3.1：自 [CharacterEditScreen] **只搬不改**抽出，
 * 暖陶与琉璃两张脸共用）。本会话裁出的中间壁纸即时回收防孤儿（复核 confirmed MED·原注释照搬）。
 */
@Stable
internal class CharacterEditMediaFlow(
    val pickAvatar: () -> Unit,
    val pickWallpaper: () -> Unit,
    val removeWallpaper: () -> Unit,
    internal val pendingAvatarUri: MutableState<Uri?>,
    internal val pendingWallpaperUri: MutableState<Uri?>,
    internal val onAvatarCropped: (Bitmap) -> Unit,
    internal val onWallpaperCropped: (Bitmap) -> Unit,
)

@Composable
internal fun rememberCharacterEditMediaFlow(viewModel: CharacterEditViewModel): CharacterEditMediaFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingAvatarUri = remember { mutableStateOf<Uri?>(null) }
    // 选完图先进圆形取景裁剪屏（甲 3）；「就这样」才存裁好的成品图，「取消」不改原头像。
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingAvatarUri.value = uri
    }
    val pendingWallpaperUri = remember { mutableStateOf<Uri?>(null) }
    // 本次编辑会话内裁剪产生的中间成品图路径（只含本会话 WallpaperStore.save 出来的·绝不含 DB 在用路径）；
    // 重选/移除时即时回收防孤儿（复核 confirmed MED）；取消/退出不保存遗留的由冷启 WallpaperMaintenanceService 兜底。
    val sessionCroppedWallpapers = remember { mutableListOf<String>() }
    // 选完图先进裁剪取景编辑器（契约 §10 C1）；「完成」才存裁好的成品图，「取消」不改。
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingWallpaperUri.value = uri
    }
    return CharacterEditMediaFlow(
        pickAvatar = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        pickWallpaper = { pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        removeWallpaper = {
            sessionCroppedWallpapers.forEach { WallpaperStore.delete(it) }
            sessionCroppedWallpapers.clear()
            viewModel.update { it.copy(chatWallpaperPath = null) }
        },
        pendingAvatarUri = pendingAvatarUri,
        pendingWallpaperUri = pendingWallpaperUri,
        onAvatarCropped = { cropped ->
            scope.launch {
                AvatarStore.save(context, cropped)?.let { path -> viewModel.update { it.copy(avatarPath = path) } }
            }
            pendingAvatarUri.value = null
        },
        onWallpaperCropped = { cropped ->
            scope.launch {
                WallpaperStore.save(context, cropped)?.let { path ->
                    // 即时回收本会话上一张中间成品图（只删本会话 save 出来的·绝不碰 DB 在用壁纸）。
                    sessionCroppedWallpapers.forEach { WallpaperStore.delete(it) }
                    sessionCroppedWallpapers.clear()
                    sessionCroppedWallpapers.add(path)
                    viewModel.update { it.copy(chatWallpaperPath = path) }
                }
            }
            pendingWallpaperUri.value = null
        },
    )
}

/** 两枚裁剪屏（全屏黑底 `Dialog`·与主题无关·两张脸同一枚）。放在页面 Composable 的末尾。 */
@Composable
internal fun CharacterEditMediaDialogs(flow: CharacterEditMediaFlow) {
    flow.pendingAvatarUri.value?.let { uri ->
        AvatarCropScreen(uri = uri, onCancel = { flow.pendingAvatarUri.value = null }, onConfirm = flow.onAvatarCropped)
    }
    flow.pendingWallpaperUri.value?.let { uri ->
        WallpaperCropScreen(imageUri = uri, onCancel = { flow.pendingWallpaperUri.value = null }, onConfirm = flow.onWallpaperCropped)
    }
}
