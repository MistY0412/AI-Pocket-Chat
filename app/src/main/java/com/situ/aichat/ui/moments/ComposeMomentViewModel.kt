package com.situ.aichat.ui.moments

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.situ.aichat.data.local.dao.UserProfileDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.data.model.MomentAuthorType
import com.situ.aichat.data.repository.CharacterRepository
import com.situ.aichat.data.repository.MomentRepository
import com.situ.aichat.data.repository.SettingsRepository
import com.situ.aichat.moments.MentionAvailability
import com.situ.aichat.moments.MomentComposeDraft
import com.situ.aichat.moments.MomentComposeDraftStore
import com.situ.aichat.moments.MomentInteractionService
import com.situ.aichat.moments.MomentMentionAvailability
import com.situ.aichat.moments.MomentMentionRules
import com.situ.aichat.stt.SttEngine
import com.situ.aichat.stt.VoiceMessageRecorder
import com.situ.aichat.ui.diary.ComposeDiaryVoiceController
import com.situ.aichat.util.ContentImageStore
import com.situ.aichat.util.StringListJson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** 发布页编辑状态。[images]=已落盘的磁盘路径（用户排好的顺序）；[publishing] 防重复发布。 */
data class ComposeMomentState(
    val content: String = "",
    val images: List<String> = emptyList(),
    val publishing: Boolean = false,
    /** 被提醒的角色 uuid（点选顺序·不重复）。 */
    val mentions: List<String> = emptyList(),
    /** 本次进页是从草稿接着写的（乙卷弹一次「已接着上次没发完的写」后调 consumeDraftRestored 置回 false）。 */
    val restoredFromDraft: Boolean = false,
)

/**
 * 朋友圈发布 VM（M06 7.2.7，对齐 iOS `ComposeMomentView`）：500 字正文 + ≤9 图 + 提醒谁看。发布 → 落 [MomentPostEntity]
 * (authorType=user) → 调 [MomentInteractionService.scheduleAIInteraction]（延迟 = `momentCommentDelay`，
 * 对齐 iOS publishPost）触发 AI 角色延迟点赞/评论。
 *
 * 朋友圈发布页重构·甲（图纸 docs/handoff/2026-09-27-朋友圈发布页-甲-底子.md §3.6）：
 * - **草稿（J-7）**：一份，存 [MomentComposeDraftStore]；内容变化防抖 [AUTOSAVE_DEBOUNCE_MS] 自动存；离开页面（系统返回 /
 *   被回收）= [keepDraft]；发布成功 / [discard] / [clearAll] 清掉；进页从草稿恢复（剔除磁盘上已不在的图）。
 * - **图片归属（J-8）**：[ownedPaths] = 草稿恢复来的 + 本次选的；图即选即落盘（`ContentImageStore`，已缩到 1024px），
 *   保留时删「移除过的」、不保留 / 清空时全删、发布时删「没发出去的」。
 * - **提醒谁看**：可用性只经 [MomentMentionAvailability]（J-6）；发布前按 [MomentMentionRules.sanitize] 清洗（J-10）。
 * - **说一段（J-9）**：原样复用日记的 [ComposeDiaryVoiceController]（松手转写后接在正文末尾）。
 */
@HiltViewModel
class ComposeMomentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val momentRepo: MomentRepository,
    private val settingsRepo: SettingsRepository,
    private val interactionService: MomentInteractionService,
    userProfileDao: UserProfileDao,
    private val characterRepo: CharacterRepository,
    // 可用性唯一判定口（J-6）。对外 StateFlow 已占用 `mentionAvailability` 这个名字（乙卷消费的锁定签名），
    // 故构造参数叫 `mentionAvailabilityChecker`（图纸 §11 D-1·复核 R1 核准）。
    private val mentionAvailabilityChecker: MomentMentionAvailability,
    voiceRecorder: VoiceMessageRecorder,
    sttEngine: SttEngine,
) : ViewModel() {

    /** 本页拥有的图（草稿恢复来的 + 本次选的）；须声明在 [_state] 之前——初值恢复会写它。 */
    private val ownedPaths = mutableSetOf<String>()

    /** 已结束（发布成功 / 选了不保留）：之后自动存与 [keepDraft] 一律不写。只在主线程读写。 */
    private var finished = false

    private val _state = MutableStateFlow(restoreFromDraft())
    val state: StateFlow<ComposeMomentState> = _state.asStateFlow()

    val userProfile: StateFlow<UserProfileEntity?> =
        userProfileDao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val characters: StateFlow<List<CharacterEntity>> =
        characterRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mentionAvailability = MutableStateFlow<Map<String, MentionAvailability>>(emptyMap())
    val mentionAvailability: StateFlow<Map<String, MentionAvailability>> = _mentionAvailability.asStateFlow()

    /** 「说一段」一次性提示文案（录音失败 / 太短 / 转写失败·已解析成串）。CONFLATED 一次性不随旋转重弹。 */
    private val _voiceMessage = Channel<String>(Channel.CONFLATED)
    val voiceMessage: Flow<String> = _voiceMessage.receiveAsFlow()

    /** 「说一段」协作者（日记同一件·原样复用）；internal 型故属性亦 internal。 */
    internal val voice = ComposeDiaryVoiceController(
        scope = viewModelScope,
        appContext = context,
        voiceRecorder = voiceRecorder,
        sttEngine = sttEngine,
        currentContent = { _state.value.content },
        setContent = ::setContent,
        emitMessage = { _voiceMessage.trySend(it) },
    )

    init {
        startAutosave()
    }

    /** 内容（字 / 图 / 提醒）变化防抖自动存；进页的初值不回写（drop(1)）。 */
    @OptIn(FlowPreview::class)
    private fun startAutosave() {
        viewModelScope.launch {
            _state.map { MomentComposeDraft(it.content, it.images, it.mentions) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { if (!finished) MomentComposeDraftStore.save(context, it) }
        }
    }

    /** 进页初值：有草稿就接着写（剔除已不在盘上的图·钳到 9 张），剩下的是空草稿则当作没有并清掉存储。 */
    private fun restoreFromDraft(): ComposeMomentState {
        val draft = MomentComposeDraftStore.load(context) ?: return ComposeMomentState()
        val images = draft.images.filter { File(it).exists() }.take(MAX_IMAGES)
        val mentions = draft.mentions.distinct()
        if (MomentComposeDraft(draft.content, images, mentions).isEmpty) {
            MomentComposeDraftStore.clear(context)
            return ComposeMomentState()
        }
        ownedPaths.addAll(images)
        return ComposeMomentState(content = draft.content, images = images, mentions = mentions, restoredFromDraft = true)
    }

    fun setContent(value: String) {
        _state.value = _state.value.copy(content = value)
    }

    /** 选图后落盘并追加（总数钳到 9，对齐 iOS `maxSelectionCount:9`）。 */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val saved = ContentImageStore.saveAll(context, uris)
            ownedPaths.addAll(saved)
            _state.value = _state.value.copy(images = (_state.value.images + saved).take(MAX_IMAGES))
        }
    }

    /** 从列表移除（删盘推迟到离开 / 发布统一处理，避免主线程文件 IO）。 */
    fun removeImage(path: String) {
        _state.value = _state.value.copy(images = _state.value.images - path)
    }

    /** 换位（按住拖动）：排第一的就是圈子里的第一张。任一越界或同位 → 不动。 */
    fun moveImage(from: Int, to: Int) {
        val images = _state.value.images
        if (from == to || from !in images.indices || to !in images.indices) return
        val moved = images.toMutableList().apply { add(to, removeAt(from)) }
        _state.value = _state.value.copy(images = moved)
    }

    /** 点选提醒：已在 → 移除；不在 → 追加到末尾。 */
    fun toggleMention(uuid: String) {
        val mentions = _state.value.mentions
        _state.value = _state.value.copy(mentions = if (uuid in mentions) mentions - uuid else mentions + uuid)
    }

    /** 移除提醒（不在则不动）。 */
    fun removeMention(uuid: String) {
        val mentions = _state.value.mentions
        if (uuid !in mentions) return
        _state.value = _state.value.copy(mentions = mentions - uuid)
    }

    /** 选人弹层打开时刷新每个角色此刻能不能来（判定只经 [MomentMentionAvailability]·J-6）。 */
    fun refreshMentionAvailability() {
        viewModelScope.launch {
            val scheduleOn = settingsRepo.getAppSettings().scheduleSystemEnabled
            val now = System.currentTimeMillis()
            _mentionAvailability.value = characterRepo.getAll().associate { c ->
                c.uuid to mentionAvailabilityChecker.of(c.uuid, scheduleOn, now)
            }
        }
    }

    fun consumeDraftRestored() {
        _state.value = _state.value.copy(restoredFromDraft = false)
    }

    /** 「清空」：删本页拥有的全部图、状态回初始、清草稿；不结束页面（之后继续写照常自动存）。 */
    fun clearAll() {
        ContentImageStore.delete(ownedPaths.toList())
        ownedPaths.clear()
        _state.value = ComposeMomentState()
        MomentComposeDraftStore.clear(context)
    }

    val hasUnsavedChanges: Boolean
        get() = _state.value.let { it.content.isNotBlank() || it.images.isNotEmpty() || it.mentions.isNotEmpty() }

    /** 保留这次编辑：存草稿、删「拥有但已被移除」的图，拥有集只留当前图。已结束 → 不动。 */
    fun keepDraft() {
        if (finished) return
        val s = _state.value
        MomentComposeDraftStore.save(context, MomentComposeDraft(s.content, s.images, s.mentions))
        val current = s.images.toSet()
        ContentImageStore.delete(ownedPaths.filter { it !in current })
        ownedPaths.retainAll(current)
    }

    /**
     * 发布（对齐 iOS `publishPost`）：trim 正文 → 落 user 帖（图按当前顺序、提醒已清洗）→ 排 AI 延迟互动
     * （`momentCommentDelay`）→ 结束并清草稿 → 删没发出去的图 → onDone。空正文 / 超长 / 正在发布时不动。
     */
    fun publish(onDone: () -> Unit) {
        val s = _state.value
        val content = s.content.trim()
        if (content.isEmpty() || s.content.length > MAX_CHARS || s.publishing) return
        viewModelScope.launch {
            _state.value = s.copy(publishing = true)
            val uuid = UUID.randomUUID().toString()
            val existingUuids = characterRepo.getAll().mapTo(HashSet()) { it.uuid }
            momentRepo.upsert(
                MomentPostEntity(
                    uuid = uuid,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    authorTypeRaw = MomentAuthorType.USER.raw,
                    characterUuid = null,
                    isAutoGenerated = false,
                    imagePathsJson = StringListJson.encode(s.images),
                    mentionedCharacterUuidsJson = StringListJson.encode(MomentMentionRules.sanitize(s.mentions, existingUuids)),
                )
            )
            val delayMinutes = settingsRepo.getAppSettings().momentCommentDelay
            interactionService.scheduleAIInteraction(uuid, delayMinutes)
            finished = true
            MomentComposeDraftStore.clear(context)
            // 已发布的图归该帖所有；删本页拥有但没进帖的（移除过的）。
            val finalSet = s.images.toSet()
            ContentImageStore.delete(ownedPaths.filter { it !in finalSet })
            ownedPaths.clear()
            onDone()
        }
    }

    /** 不保留：结束页面语义——删本页拥有的全部图（含草稿恢复来的）、状态回初始、清草稿。 */
    fun discard() {
        finished = true
        ContentImageStore.delete(ownedPaths.toList())
        ownedPaths.clear()
        _state.value = ComposeMomentState()
        MomentComposeDraftStore.clear(context)
    }

    /** 离开页面（系统返回 / 被回收）：没结束 = 自动保留草稿（V-1）。 */
    override fun onCleared() {
        voice.onCleared()
        if (!finished) keepDraft()
    }

    companion object {
        const val MAX_CHARS = 500
        const val MAX_IMAGES = 9

        /** 自动存草稿的防抖（J-7·锁定）。 */
        const val AUTOSAVE_DEBOUNCE_MS = 500L
    }
}
