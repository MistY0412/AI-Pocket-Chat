package com.situ.aichat.ui.story

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.StoryEntity
import com.situ.aichat.story.StoryArchiveDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 结局档案页的两个导出动作（两张脸共用）。 */
internal class StoryArchiveActions(val share: () -> Unit, val export: () -> Unit)

/**
 * 结局档案页动作件（原 StoryArchiveDetailScreen :80–107 与两钮 onClick :118–139 逐字·含原注释）：
 * 「继续写」成功 → Toast + 退出；txt 导出 launcher（正文拼装挪出主线程）；分享长图（失败 Toast）。
 */
@Composable
internal fun rememberStoryArchiveActions(
    viewModel: StoryArchiveDetailViewModel,
    state: StoryArchiveUiState?,
    onBack: () -> Unit,
): StoryArchiveActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 「继续写这个故事」成功 → 提示 + 退出档案详情（书已回在读区、后台正写下一章）。
    LaunchedEffect(Unit) {
        viewModel.continueWritingDone.collect {
            Toast.makeText(context, R.string.story_continue_writing_toast, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // txt 导出格式串（在 Composable 里解析，供非 Composable 的 launcher 回调用）。
    val chapterHeaderFmt = stringResource(R.string.story_export_chapter_header)
    val choicePrefixFmt = stringResource(R.string.story_export_choice_prefix)

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                // 整本正文拼装（长篇可上十万字）挪出主线程；null = 状态还没落定，早退语义同原来。
                val text = withContext(Dispatchers.Default) {
                    viewModel.buildTxt(chapterHeaderFmt, choicePrefixFmt)
                } ?: return@launch
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                }
                Toast.makeText(context, R.string.story_export_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    return StoryArchiveActions(
        share = {
            state?.let { s ->
                val shareContent = buildShareContent(context, s.story, s.digest)
                scope.launch {
                    val uri = viewModel.renderShareImage(context, shareContent)
                    if (uri != null) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, null)) }
                    } else {
                        Toast.makeText(context, R.string.story_share_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        export = { state?.let { s -> createDoc.launch("${s.story.title}.txt") } },
    )
}

/** 「✎ 继续写这个故事」（原 ContinueWritingButton 的文案拼装·两张脸共用）。 */
@Composable
internal fun storyArchiveContinueLabel(): String = "✎ " + stringResource(R.string.story_archive_continue_writing)

/** 组装分享长图内容（本地化文案在此解析·渲染器只画不查表）。 */
private fun buildShareContent(context: android.content.Context, story: StoryEntity, digest: StoryArchiveDigest) =
    StoryShareCardContent(
        coverColorScheme = story.coverColorScheme,
        storyId = story.id,
        title = story.title,
        genreLine = "${story.genre} · ${story.writingStyle}",
        footprintLine = context.getString(R.string.story_share_footprint, digest.chapterCount, digest.choiceCount, digest.dayCount),
        quote = digest.quote,
        signatureLine = context.getString(R.string.story_share_signature),
    )
