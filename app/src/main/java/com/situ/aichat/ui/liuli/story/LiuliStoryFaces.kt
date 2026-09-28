package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.Composable
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.liuli.designsystem.LocalAppSkin
import com.situ.aichat.ui.story.StoryArchiveAllScreen
import com.situ.aichat.ui.story.StoryArchiveDetailScreen
import com.situ.aichat.ui.story.StoryBookHubScreen
import com.situ.aichat.ui.story.StoryBookshelfScreen
import com.situ.aichat.ui.story.StoryChapterListScreen
import com.situ.aichat.ui.story.StoryCreationScreen
import com.situ.aichat.ui.story.StoryFieldEditorScreen
import com.situ.aichat.ui.story.StoryReaderScreen
import com.situ.aichat.ui.story.StoryTemplateWallScreen

// 故事屏选脸包装（琉璃 2.0 卷六·三·上）：签名 = 暖陶去掉 VM 默认形参，由反射测试钉住（LiuliStoryPageFacesTest）。

@Composable
fun SkinnedStoryBookshelfScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenChapter: (String) -> Unit,
    onCreateStory: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onViewAllArchive: () -> Unit,
) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryBookshelfScreen(onBack, onOpenStory, onOpenChapter, onCreateStory, onOpenSettings, onOpenArchive, onViewAllArchive)
        return
    }
    StoryBookshelfScreen(onBack, onOpenStory, onOpenChapter, onCreateStory, onOpenSettings, onOpenArchive, onViewAllArchive)
}

@Composable
fun SkinnedStoryArchiveDetailScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryArchiveDetailScreen(onBack)
        return
    }
    StoryArchiveDetailScreen(onBack)
}

@Composable
fun SkinnedStoryArchiveAllScreen(onBack: () -> Unit, onOpenArchive: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryArchiveAllScreen(onBack, onOpenArchive)
        return
    }
    StoryArchiveAllScreen(onBack, onOpenArchive)
}

@Composable
fun SkinnedStoryChapterListScreen(onBack: () -> Unit, onStoryGone: () -> Unit, onOpenChapter: (String) -> Unit, onOpenSettings: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryChapterListScreen(onBack, onStoryGone, onOpenChapter, onOpenSettings)
        return
    }
    StoryChapterListScreen(onBack, onStoryGone, onOpenChapter, onOpenSettings)
}

@Composable
fun SkinnedStoryTemplateWallScreen(onBack: () -> Unit, onOpenCustom: (String?) -> Unit, onCreated: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryTemplateWallScreen(onBack, onOpenCustom, onCreated)
        return
    }
    StoryTemplateWallScreen(onBack, onOpenCustom, onCreated)
}

@Composable
fun SkinnedStoryFieldEditorScreen(onBack: () -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryFieldEditorScreen(onBack)
        return
    }
    StoryFieldEditorScreen(onBack)
}

@Composable
fun SkinnedStoryBookHubScreen(
    onBack: () -> Unit,
    onStoryGone: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenField: (String) -> Unit,
    onOpenGlobalSettings: () -> Unit,
) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryBookHubScreen(onBack, onStoryGone, onOpenChapter, onOpenField, onOpenGlobalSettings)
        return
    }
    StoryBookHubScreen(onBack, onStoryGone, onOpenChapter, onOpenField, onOpenGlobalSettings)
}

@Composable
fun SkinnedStoryCreationScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryCreationScreen(onBack, onCreated)
        return
    }
    StoryCreationScreen(onBack, onCreated)
}

@Composable
fun SkinnedStoryReaderScreen(onBack: () -> Unit, onGoToChat: () -> Unit, onOpenBookHub: (storyId: String) -> Unit) {
    if (LocalAppSkin.current == AppSkin.LIULI) {
        LiuliStoryReaderScreen(onBack, onGoToChat, onOpenBookHub)
        return
    }
    StoryReaderScreen(onBack, onGoToChat, onOpenBookHub)
}
