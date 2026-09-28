package com.situ.aichat.ui.liuli.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.situ.aichat.R
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserStoryTemplateEntity
import com.situ.aichat.story.StoryTemplate
import com.situ.aichat.story.StoryTemplates
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.components.contentMaxWidth
import com.situ.aichat.ui.liuli.designsystem.LiuliDialog
import com.situ.aichat.ui.liuli.designsystem.LiuliMenuEntry
import com.situ.aichat.ui.liuli.designsystem.LiuliPopupMenu
import com.situ.aichat.ui.liuli.page.LiuliLargeTitle
import com.situ.aichat.ui.liuli.page.LiuliPage
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.rememberLargeTitleCollapsed
import com.situ.aichat.ui.story.DiyCard
import com.situ.aichat.ui.story.StoryCreationViewModel
import com.situ.aichat.ui.story.StoryToastEvents
import com.situ.aichat.ui.story.StoryWallSectionHeader
import com.situ.aichat.ui.story.TemplateCard
import com.situ.aichat.ui.story.formatTemplateDate
import com.situ.aichat.ui.story.storyWallHeadRes
import com.situ.aichat.ui.story.toDisplayTemplate

/** 两列 · 列缝 14 · 行缝 16（= 暖陶网格）。 */
private const val WALL_COLUMNS = 2
private val WALL_COL_GAP = 14.dp
private val WALL_ROW_GAP = 16.dp

/** 模板墙的全部回调（与暖陶同一批 VM 方法、同一批实参）。 */
internal class LiuliWallCallbacks(
    val onBack: () -> Unit,
    val onOpenCustom: (String?) -> Unit,
    val onStart: (template: StoryTemplate, roles: Map<String, String>, includeUser: Boolean) -> Unit,
    val onRename: (uuid: String, name: String) -> Unit,
    val onDeleteTemplate: (uuid: String) -> Unit,
)

/**
 * 琉璃模板墙（琉璃 2.0 卷六·三·上 §4.6·设计稿 S4）：与暖陶 [com.situ.aichat.ui.story.StoryTemplateWallScreen] 共用同一个 VM、
 * Toast 事件 / 区头与名字判据 / 模板卡与尾卡内容件；大标题「开新故事」+ 「每行一个 item、行内等分」的两列（§0.2-3），
 * 点模板出玻璃开书弹层，「我的模板」长按玻璃菜单重命名 / 删除。创建中返回钮淡出不可点。
 */
@Composable
internal fun LiuliStoryTemplateWallScreen(
    onBack: () -> Unit,
    onOpenCustom: (String?) -> Unit,
    onCreated: () -> Unit,
    viewModel: StoryCreationViewModel = hiltViewModel(),
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val myTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
    StoryToastEvents(viewModel.toastEvents)

    LiuliStoryTemplateWallPage(
        myTemplates, characters, creating,
        LiuliWallCallbacks(
            onBack, onOpenCustom,
            onStart = { t, roles, inc -> viewModel.createFromTemplate(t, roles, inc) { onCreated() } },
            onRename = viewModel::renameUserTemplate,
            onDeleteTemplate = viewModel::deleteUserTemplate,
        ),
        rememberLazyListState(),
    )

    error?.let { LiuliStoryAlert(stringResource(R.string.story_create_failed), it, viewModel::dismissError) }
}

/** 无 VM 的模板墙页（测试直接驱动它）。 */
@Composable
internal fun LiuliStoryTemplateWallPage(
    myTemplates: List<UserStoryTemplateEntity>,
    characters: List<CharacterEntity>,
    creating: Boolean,
    callbacks: LiuliWallCallbacks,
    listState: LazyListState,
) {
    var sheetTemplate by remember { mutableStateOf<StoryTemplate?>(null) }
    var menuTemplateUuid by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<UserStoryTemplateEntity?>(null) }
    var deleteTargetUuid by remember { mutableStateOf<String?>(null) }
    val haptics = LocalAppHaptics.current
    val templates = remember { StoryTemplates.all }
    val title = stringResource(R.string.story_new_story_title)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headModifier = Modifier.padding(horizontal = LiuliPageGeometry.gutter).padding(bottom = 2.dp)

    // 创建中禁止退出：钮淡出但仍在原位（同暖陶）。
    LiuliPage(title, callbacks.onBack, rememberLargeTitleCollapsed(listState), backEnabled = !creating) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().contentMaxWidth(),
            contentPadding = PaddingValues(top = LiuliPageGeometry.navRow, bottom = navBarBottom + LiuliPageGeometry.pageBottom),
            verticalArrangement = Arrangement.spacedBy(WALL_ROW_GAP),
        ) {
            item(key = "large-title") { LiuliLargeTitle(title) }
            // ⚠️ 大标题之后的首项 key 恒为 `_head`、恒存在（只换文案）：模板是从库里异步来的，若它随之新增，
            // 列表会锚住原首项、把新插的项顶到可视区**上方**——整个区肉眼看不见（暖陶网格同理·装机实测）。
            item(key = "_head") { StoryWallSectionHeader(stringResource(storyWallHeadRes(myTemplates)), headModifier) }
            if (myTemplates.isNotEmpty()) {
                items(myTemplates.chunked(WALL_COLUMNS), key = { "my-" + it.first().uuid }) { row ->
                    WallRow(row.size) {
                        row.forEach { t ->
                            Box(Modifier.weight(1f)) {
                                val display = t.toDisplayTemplate(tagline = stringResource(R.string.story_my_template_tagline, formatTemplateDate(t.createdAt)))
                                TemplateCard(display, onClick = { sheetTemplate = display }, onLongPress = { haptics.light(); menuTemplateUuid = t.uuid })
                                LiuliPopupMenu(
                                    expanded = menuTemplateUuid == t.uuid,
                                    onDismiss = { menuTemplateUuid = null },
                                    items = listOf(
                                        LiuliMenuEntry(stringResource(R.string.story_template_rename), onClick = { menuTemplateUuid = null; renameTarget = t }),
                                        LiuliMenuEntry(stringResource(R.string.story_template_delete), danger = true, onClick = { menuTemplateUuid = null; deleteTargetUuid = t.uuid }),
                                    ),
                                )
                            }
                        }
                    }
                }
                item(key = "_builtin_header") { StoryWallSectionHeader(stringResource(R.string.story_wall_subtitle), headModifier) }
            }
            // 内置 12 套 + 尾卡「自己从头写」（null）。
            val cells = templates.map<StoryTemplate, StoryTemplate?> { it } + null
            items(cells.chunked(WALL_COLUMNS), key = { row -> "tpl-" + (row.first()?.id ?: "_diy") }) { row ->
                WallRow(row.size) {
                    row.forEach { t ->
                        Box(Modifier.weight(1f)) {
                            if (t != null) TemplateCard(t, onClick = { sheetTemplate = t }) else DiyCard(onClick = { callbacks.onOpenCustom(null) })
                        }
                    }
                }
            }
        }
    }

    sheetTemplate?.let { t ->
        LiuliStoryOpenBookSheet(
            template = t,
            characters = characters,
            creating = creating,
            onStart = { roles, inc -> callbacks.onStart(t, roles, inc) },
            onTweak = { sheetTemplate = null; callbacks.onOpenCustom(t.id) },
            onDismiss = { sheetTemplate = null },
        )
    }

    renameTarget?.let { row ->
        LiuliStoryNameDialog(
            title = stringResource(R.string.story_template_rename),
            message = null,
            initialName = row.name,
            onConfirm = { callbacks.onRename(row.uuid, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTargetUuid?.let { uuid ->
        LiuliDialog(
            onDismissRequest = { deleteTargetUuid = null },
            title = stringResource(R.string.story_template_delete),
            body = stringResource(R.string.story_template_delete_confirm),
            confirmText = stringResource(R.string.story_template_delete),
            onConfirm = { callbacks.onDeleteTemplate(uuid); deleteTargetUuid = null },
            confirmDanger = true,
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteTargetUuid = null },
        )
    }
}

/** 一行模板格：左右 gutter · 列缝 14；末行不满时等宽空位补齐，格宽不变（E38）。 */
@Composable
private fun WallRow(filled: Int, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LiuliPageGeometry.gutter), horizontalArrangement = Arrangement.spacedBy(WALL_COL_GAP)) {
        content()
        repeat(WALL_COLUMNS - filled) { Spacer(Modifier.weight(1f)) }
    }
}
