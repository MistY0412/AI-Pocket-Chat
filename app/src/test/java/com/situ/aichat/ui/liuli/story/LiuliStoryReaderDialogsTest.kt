package com.situ.aichat.ui.liuli.story

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.story.ReaderDialog
import com.situ.aichat.ui.theme.AIPocketChatTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-D1–D10（琉璃 2.0 卷六·三·下乙 §7）：琉璃弹窗族十一支——标题 / 正文对、钮发对的回调、输入框确认 trim 后交出再关。
 * 直接测 [LiuliStoryReaderSheetFace.Dialogs]（= 接线层实际调用的入口）；期望值从暖陶 ReaderDialogs 与 zh 原文独立反推。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class LiuliStoryReaderDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(
        dialog: ReaderDialog?,
        currentHasPendingChoice: Boolean = false,
        storyTitle: String? = null,
        chapterSummary: String? = null,
        characterStates: String? = null,
    ) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.LIULI) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    LiuliStoryReaderSheetFace.Dialogs(
                        dialog = dialog,
                        currentHasPendingChoice = currentHasPendingChoice,
                        storyTitle = storyTitle,
                        chapterSummary = chapterSummary,
                        characterStates = characterStates,
                        onSaveChapterSummary = { calls += "saveSummary:$it" },
                        onDismiss = { calls += "dismiss" },
                        onFinishStory = { calls += "finish" },
                        onForceContinue = { calls += "forceContinue" },
                        onEnding = { type, detail -> calls += "ending:$type:$detail" },
                        onSkipChoiceThenEnding = { calls += "skipThenEnding" },
                        onGoToChoice = { calls += "goToChoice" },
                        onOpenEndingCustom = { calls += "openEndingCustom" },
                        onOpenRewriteInstruction = { calls += "openRewriteInstruction" },
                        onRewrite = { calls += "rewrite:$it" },
                        onPickGracefulFinale = { calls += "graceful" },
                        onPickImmediateEnding = { calls += "immediate" },
                        onCancelFinale = { calls += "cancelFinale" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** 按按钮角色取钮（标题与确认钮同字时，比如「取消收尾」，只取那枚钮）。 */
    private fun clickButton(text: String) {
        compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.waitForIdle()
    }

    private fun field() = compose.onAllNodes(hasSetTextAction()).onFirst()

    @Test fun D1_续写确认_挂着未答选择走提醒版正文_确认发强制续写() {
        show(ReaderDialog.Continue, currentHasPendingChoice = true)
        compose.onNodeWithText("继续推进").assertExists()
        compose.onNodeWithText("当前章节还有未完成的选择，继续推进将跳过该选择。确定要继续吗？").assertExists()
        clickButton("生成下一章")
        assertEquals(listOf("forceContinue"), calls)
    }

    @Test fun D2_就此完结确认_正文带书名_放入档案发完结() {
        show(ReaderDialog.ArchiveConfirm, storyTitle = "对面楼的灯")
        compose.onNodeWithText("放入档案？").assertExists()
        compose.onNodeWithText("《对面楼的灯》", substring = true).assertExists()
        clickButton("放入档案")
        assertEquals(listOf("finish"), calls)
    }

    @Test fun D3_收尾方式_两张卡各发各的() {
        show(ReaderDialog.FinaleMethod)
        compose.onNodeWithText("为这个故事收尾").assertExists()
        compose.onNodeWithText("从容收尾").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("立即结局").performClick()
        compose.waitForIdle()
        assertEquals(listOf("graceful", "immediate"), calls)
    }

    @Test fun D4_结局三选_开放式_AI_我来指定() {
        show(ReaderDialog.EndingPicker)
        compose.onNodeWithText("选择结局类型").assertExists()
        clickButton("开放式结局")
        clickButton("AI 自由发挥")
        clickButton("我来指定结局")
        assertEquals(listOf("ending:open:null", "ending:ai:null", "openEndingCustom"), calls)
    }

    @Test fun D5_自定义结局_trim后交出再关() {
        show(ReaderDialog.EndingCustom)
        compose.onNodeWithText("描述你想要的结局").assertExists()
        field().performTextInput("  她回来了  ")
        compose.waitForIdle()
        clickButton("确定")
        assertEquals(listOf("ending:custom:她回来了", "dismiss"), calls)
    }

    @Test fun D6_结局前未答选择_跳过与去做选择() {
        show(ReaderDialog.EndingPending)
        compose.onNodeWithText("当前章有未做的选择").assertExists()
        clickButton("跳过选择，直接写结局")
        clickButton("带我去做选择")
        assertEquals(listOf("skipThenEnding", "goToChoice"), calls)
    }

    @Test fun D7_重写确认_直接重写与补充指令() {
        show(ReaderDialog.RewriteConfirm)
        compose.onNodeWithText("重写本章").assertExists()
        clickButton("直接重写")
        clickButton("补充指令后重写")
        assertEquals(listOf("rewrite:null", "openRewriteInstruction"), calls)
    }

    @Test fun D8_重写指令_不输入确认_等于直接重写() {
        show(ReaderDialog.RewriteInstruction)
        compose.onNodeWithText("补充重写指令").assertExists()
        clickButton("确认重写")
        assertEquals(listOf("rewrite:null", "dismiss"), calls)
    }

    @Test fun D8_重写指令_输入后确认_交出指令() {
        show(ReaderDialog.RewriteInstruction)
        field().performTextInput("换视角")
        compose.waitForIdle()
        clickButton("确认重写")
        assertEquals(listOf("rewrite:换视角", "dismiss"), calls)
    }

    @Test fun D9_本章小结_回显旧值_改后trim保存并关() {
        show(ReaderDialog.ChapterSummary, chapterSummary = "旧小结")
        compose.onNodeWithText("本章小结").assertExists()
        compose.onNodeWithText("旧小结").assertExists()
        field().performTextClearance()
        field().performTextInput(" 新小结 ")
        compose.waitForIdle()
        clickButton("保存")
        assertEquals(listOf("saveSummary:新小结", "dismiss"), calls)
    }

    @Test fun D10_角色现状_空白兜底文案_确定即关() {
        show(ReaderDialog.CharacterStates, characterStates = "  ")
        compose.onNodeWithText("角色现状").assertExists()
        compose.onNodeWithText("还没有角色状态记录，生成一章后自动出现").assertExists()
        clickButton("确定")
        assertEquals(listOf("dismiss"), calls)
    }

    @Test fun D10_无弹窗_什么都不画() {
        show(null)
        listOf("继续推进", "放入档案？", "为这个故事收尾", "取消收尾", "选择结局类型", "描述你想要的结局", "当前章有未做的选择", "重写本章", "补充重写指令", "本章小结", "角色现状")
            .forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun 取消收尾确认_确认钮发取消收尾() {
        show(ReaderDialog.FinaleCancelConfirm)
        compose.onNodeWithText("取消收尾计划？下一章起回到正常连载。").assertExists()
        clickButton("取消收尾")
        assertEquals(listOf("cancelFinale"), calls)
    }
}
