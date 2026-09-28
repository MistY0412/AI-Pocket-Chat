package com.situ.aichat.ui.story

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.situ.aichat.R
import com.situ.aichat.data.model.UserStoryTemplatePayload
import com.situ.aichat.story.StoryChapterLength
import com.situ.aichat.story.StoryCreationCatalog
import com.situ.aichat.story.StoryNarrativePerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 创作七行与两类弹层规格（琉璃 2.0 卷六·三·上 §3.10·T1-6）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN")
class StoryHubCreativeSupportTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    private val draft = StorySettingsDraft(
        updateMode = "free", unlockHour = 8, unlockMinute = 0, genre = "  ", writingStyle = "细腻",
        narrativePerson = StoryNarrativePerson.FIRST, chapterLengthPreference = 1500, chatInfluenceWeight = "medium",
        worldSetting = "这是一座越夜越亮的一线城市", plotDirection = "",
    )

    /** 在组合里取值（规格件要读资源）。 */
    private fun <T> inCompose(block: @androidx.compose.runtime.Composable () -> T): T {
        var out: T? = null
        compose.setContent { out = block() }
        compose.waitForIdle()
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    @Test fun 七行_标题值目标与顺序() {
        val rows = inCompose { storyCreativeRows(draft) }
        assertEquals(
            listOf(
                R.string.story_settings_genre_row, R.string.story_settings_style_row, R.string.story_settings_person_row,
                R.string.story_settings_field_length, R.string.story_settings_field_influence, R.string.story_settings_world_row, R.string.story_settings_plot_row,
            ).map { app.getString(it) },
            rows.map { app.getString(it.titleRes) },
        )
        assertEquals("题材", app.getString(rows[0].titleRes))
        assertEquals("剧情方向", app.getString(rows[6].titleRes))
        val world13 = "这是一座越夜越亮的一线城市"
        assertEquals(13, world13.length)
        assertEquals(
            listOf("未填写", "细腻", app.getString(R.string.story_narrative_first), app.getString(R.string.story_length_medium), app.getString(R.string.story_influence_medium), world13.take(12) + "…", "未填写"),
            rows.map { it.value },
        )
        assertEquals(
            listOf(
                StoryCreativeTarget.Text(HubCreativeTextField.GENRE), StoryCreativeTarget.Choice(HubCreativeChoice.STYLE),
                StoryCreativeTarget.Choice(HubCreativeChoice.PERSON), StoryCreativeTarget.Choice(HubCreativeChoice.LENGTH),
                StoryCreativeTarget.Choice(HubCreativeChoice.INFLUENCE), StoryCreativeTarget.Text(HubCreativeTextField.WORLD),
                StoryCreativeTarget.Text(HubCreativeTextField.PLOT),
            ),
            rows.map { it.target },
        )
    }

    @Test fun 人称弹窗_三项顺序第二第一第三_当前第一人称_选第三写回() {
        var captured: ((StorySettingsDraft) -> StorySettingsDraft)? = null
        val spec = inCompose { storyCreativeChoiceSpec(HubCreativeChoice.PERSON, draft) { captured = it } }
        assertEquals(
            listOf(R.string.story_narrative_second, R.string.story_narrative_first, R.string.story_narrative_third).map { app.getString(it) },
            spec.options.map { it.label },
        )
        assertEquals(listOf(false, true, false), spec.options.map { it.selected })
        spec.options[2].onSelect()
        assertEquals(StoryNarrativePerson.THIRD, captured!!(draft).narrativePerson)
    }

    @Test fun 章长弹窗_未知字数按中档选中_选长篇写回字数() {
        var captured: ((StorySettingsDraft) -> StorySettingsDraft)? = null
        val odd = draft.copy(chapterLengthPreference = 1234)
        val spec = inCompose { storyCreativeChoiceSpec(HubCreativeChoice.LENGTH, odd) { captured = it } }
        val medium = StoryChapterLength.entries.indexOf(StoryChapterLength.MEDIUM)
        assertTrue(spec.options[medium].selected)
        assertEquals(1, spec.options.count { it.selected })
        spec.options[StoryChapterLength.entries.indexOf(StoryChapterLength.LONG)].onSelect()
        assertEquals(StoryChapterLength.LONG.words, captured!!(odd).chapterLengthPreference)
    }

    @Test fun 文本弹层_题材副标含预设_确认去空白_世界观原样不trim_无上限无填默认() {
        var captured: ((StorySettingsDraft) -> StorySettingsDraft)? = null
        val (genre, world) = inCompose {
            storyCreativeTextSpec(HubCreativeTextField.GENRE, draft) { captured = it } to storyCreativeTextSpec(HubCreativeTextField.WORLD, draft) { captured = it }
        }
        assertTrue(genre.subtitle!!.contains(StoryCreationCatalog.genres.first()))
        genre.onConfirm("  奇幻 ")
        assertEquals("奇幻", captured!!(draft).genre)
        assertNull(genre.maxLength)
        assertNull(genre.fillDefault)
        world.onConfirm("雨\n")
        assertEquals("雨\n", captured(draft).worldSetting)
        assertNull(world.subtitle)
    }

    @Test fun 模板上限与输入截断() {
        assertFalse(storyTemplateAtLimit(UserStoryTemplatePayload.MAX_USER_TEMPLATES - 1))
        assertTrue(storyTemplateAtLimit(UserStoryTemplatePayload.MAX_USER_TEMPLATES))
        assertEquals("abc", storyEditorClamp("abcdef", 3))
        assertEquals("ab", storyEditorClamp("ab", 3))
        assertEquals("x", storyEditorClamp("x", null))
    }
}
