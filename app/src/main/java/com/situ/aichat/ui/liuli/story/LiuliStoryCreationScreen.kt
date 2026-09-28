package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberScrollCollapsed
import com.situ.aichat.ui.story.StoryCreationForm
import com.situ.aichat.ui.story.StoryCreationViewModel
import com.situ.aichat.ui.story.StoryEditField
import com.situ.aichat.ui.story.storyCreationCanCreate
import com.situ.aichat.ui.story.storyEditFieldSheetSpec
import com.situ.aichat.ui.story.withCustomRoleAt
import com.situ.aichat.ui.story.withoutCustomRoleAt

/** 提交钮与脚注的缝（= 暖陶 6）。 */
private val SUBMIT_GAP = 6.dp

/**
 * 琉璃创建故事（琉璃 2.0 卷六·三·上 §4.15·设计稿 S5）：与暖陶 [com.situ.aichat.ui.story.StoryCreationScreen] 共用同一个 VM、
 * 字段枚举 / 可建判据 / 弹层规格 / 写入式 / 选项表；表单型 Column 页（跟键盘）+ 大标题随滚收起，每节一张琉璃分组卡，
 * 「开始创作」与脚注留在表单末尾（字段 / 顺序 / 默认值 / 提交按钮位置同现在）。创建中返回钮淡出不可点。
 */
@Composable
internal fun LiuliStoryCreationScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: StoryCreationViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LiuliStoryCreationPage(
        form, characters, nickname = userProfile?.nickname.orEmpty(), bio = userProfile?.bio.orEmpty(), creating = creating,
        update = viewModel::update, onCreate = { viewModel.createStory(onCreated) }, onBack = onBack,
    )
    error?.let { LiuliStoryAlert(stringResource(R.string.story_create_failed), it, viewModel::dismissError) }
}

/** 无 VM 的创建页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryCreationPage(
    form: StoryCreationForm,
    characters: List<CharacterEntity>,
    nickname: String,
    bio: String,
    creating: Boolean,
    update: ((StoryCreationForm) -> StoryCreationForm) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    var editingField by remember { mutableStateOf<StoryEditField?>(null) }
    val scrollState = rememberScrollState()
    val title = stringResource(R.string.story_create_title)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LiuliPage(title, onBack, rememberScrollCollapsed(scrollState), backEnabled = !creating) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).contentMaxWidth()
                .padding(top = LiuliPageGeometry.navRow, bottom = LiuliPageGeometry.pageBottom + navBarBottom),
        ) {
            LiuliLargeTitle(title)
            // 纯 Column：组间 24 = LiuliGroup 自带的 `padding(bottom = groupGap)`（再叠 spacedBy 会成 48·先例日记设置页·复核 R1 核准 D-3）。
            Column(Modifier.padding(horizontal = LiuliPageGeometry.gutter).padding(top = LiuliPageGeometry.titleGap)) {
                LiuliCreationGenreGroup(form, update)
                if (form.isCustomGenre) LiuliCreationCustomPromptGroup(form, update) { editingField = it }
                LiuliCreationCharacterGroup(form, characters, nickname, bio, update) { editingField = it }
                LiuliStoryCustomRolesGroup(
                    form.customRoles,
                    onAdd = { d -> update { it.copy(customRoles = it.customRoles + d) } },
                    onUpdate = { i, d -> update { it.withCustomRoleAt(i, d) } },
                    onRemove = { i -> update { it.withoutCustomRoleAt(i) } },
                )
                LiuliCreationAdvancedGroup(form, update) { editingField = it }
                Column(verticalArrangement = Arrangement.spacedBy(SUBMIT_GAP)) {
                    LiuliButton(onClick = onCreate, style = LiuliButtonStyle.Prominent, enabled = storyCreationCanCreate(form) && !creating, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.story_create_start), fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.story_create_footer), style = AppTypography.secondary, color = AppTheme.colors.text.secondary)
                }
            }
        }
    }
    editingField?.let { f -> LiuliStoryTextEditorSheet(storyEditFieldSheetSpec(f, form, update)) { editingField = null } }
}
