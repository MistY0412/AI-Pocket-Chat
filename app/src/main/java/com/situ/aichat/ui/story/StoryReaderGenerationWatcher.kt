package com.situ.aichat.ui.story

import android.util.Log
import com.situ.aichat.data.local.entity.StoryChapterEntity
import com.situ.aichat.story.StoryGenerationTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 写好了、等用户点「翻开」的那一章（琉璃阅读器 R2）。 */
data class StoryReadyChapter(val chapterId: String, val chapterNumber: Int)

/**
 * 阅读器「生成完成后翻不翻章」协作者（琉璃 2.0 卷六·三·下甲·自 [StoryReaderViewModel] init 块只搬出逻辑体，VM 只留接线）。
 *
 * - [autoJump] = true（默认·暖陶脸）：**逐字节同原行为**——活跃生成 非空 → 空时查最新章，≠ 当前章就跳过去。
 * - [autoJump] = false（琉璃脸 R2「可接着读」）：不跳；最新章的章号**等于刚才在写的章号**（= 真写出来了，不是失败回落）
 *   时放进 [ready]，由「第 N 章写好了 · 翻开 ›」让用户自己翻。
 * - 清 [ready]：新一轮开写 / 当前章变成它（点了翻开，或用户自己用 ‹ › / 章节列表翻过去）/ [openReady]。
 */
internal class StoryReaderGenerationWatcher(
    private val scope: CoroutineScope,
    private val activeGeneration: StateFlow<StoryGenerationTaskManager.GenerationProgress?>,
    private val storyId: StateFlow<String?>,
    private val latestChapterMeta: suspend (String) -> StoryChapterEntity?,
    private val currentChapterId: MutableStateFlow<String>,
) {
    /** 由脸在进页事里声明（`StoryReaderEffects`）；主线程读写。 */
    var autoJump: Boolean = true

    private val _ready = MutableStateFlow<StoryReadyChapter?>(null)
    val ready: StateFlow<StoryReadyChapter?> = _ready.asStateFlow()

    fun start() {
        // 生成完成（活跃生成 非空→空）→ 跳到最新章（= iOS onChange progress nil 跳 latest）。
        scope.launch {
            var wasGenerating = false
            var writingNumber: Int? = null
            activeGeneration.collect { gen ->
                val nowGenerating = gen != null
                if (gen != null) {
                    writingNumber = gen.chapterNumber
                    _ready.value = null
                }
                if (wasGenerating && !nowGenerating) {
                    val sid = storyId.value
                    if (sid != null) {
                        val latest = latestChapterMeta(sid)
                        if (latest != null && latest.id != currentChapterId.value) {
                            if (autoJump) {
                                currentChapterId.value = latest.id
                                Log.i(TAG, "生成完成，跳到最新章 #${latest.chapterNumber}")
                            } else if (latest.chapterNumber == writingNumber) {
                                _ready.value = StoryReadyChapter(latest.id, latest.chapterNumber)
                            }
                        }
                    }
                }
                wasGenerating = nowGenerating
            }
        }
        // 用户自己翻到了那一章 → 胶囊已无意义。
        scope.launch {
            currentChapterId.collect { id -> if (_ready.value?.chapterId == id) _ready.value = null }
        }
    }

    /** 点「翻开」：翻到写好的那章并清胶囊（没有就不动）。 */
    fun openReady() {
        val ready = _ready.value ?: return
        currentChapterId.value = ready.chapterId
        _ready.value = null
    }

    private companion object {
        const val TAG = "StoryReader"
    }
}
