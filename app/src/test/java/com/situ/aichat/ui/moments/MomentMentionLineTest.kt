package com.situ.aichat.ui.moments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.data.model.AppSkin
import com.situ.aichat.ui.components.AppHaptics
import com.situ.aichat.ui.components.LocalAppHaptics
import com.situ.aichat.ui.theme.AIPocketChatTheme
import com.situ.aichat.util.DateFormatters
import com.situ.aichat.util.StringListJson
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T2-4（朋友圈发布页·乙 §7·E22）：暖陶主题下直接渲染圈子卡片 [MomentPostCard] 与详情正文区 [MomentPostContentSection]——
 * 用户帖提醒 [c1, ghost, c2] → 「提醒了 小满、阿澈」（跳过已删·按帖上顺序·「、」连）；无提醒 / AI 帖不出现那一行。
 * 期望文本从图纸 §4.13 重新打字（前缀 + 半角空格 + 名字）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
class MomentMentionLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private val characters = mapOf(
        "c1" to CharacterEntity(uuid = "c1", name = "小满", creationDate = 1),
        "c2" to CharacterEntity(uuid = "c2", name = "阿澈", creationDate = 1),
    )

    private fun userPost(vararg mentions: String) = MomentPostWithRelations(
        post = MomentPostEntity(
            uuid = "p1", content = "今天的晚霞像橘子汽水", timestamp = now - 3_600_000, authorTypeRaw = "user",
            mentionedCharacterUuidsJson = StringListJson.encode(mentions.toList()),
        ),
        comments = emptyList(),
        likes = emptyList(),
    )

    private val aiPost = MomentPostWithRelations(
        post = MomentPostEntity(uuid = "p2", content = "今天的晚霞像橘子汽水", timestamp = now - 3_600_000, authorTypeRaw = "character", characterUuid = "c1"),
        comments = emptyList(),
        likes = emptyList(),
    )

    private fun showCard(post: MomentPostWithRelations) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.CLAY) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    MomentPostCard(post = post, characterDict = characters, userName = "阿满", userAvatarPath = null, onToggleLike = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showDetail(post: MomentPostWithRelations) {
        compose.setContent {
            AIPocketChatTheme(darkTheme = false, skin = AppSkin.CLAY) {
                CompositionLocalProvider(LocalAppHaptics provides mockk<AppHaptics>(relaxed = true)) {
                    MomentPostContentSection(
                        post = post, characterDict = characters, userName = "阿满", userAvatarPath = null,
                        relStrings = DateFormatters.RelativeTimeStrings("刚刚", "%1\$d 分钟前", "%1\$d 小时前", "昨天"),
                        nowMillis = now, meLabel = "我", aiLabel = "AI",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertLineShown() {
        compose.onNodeWithText("提醒了 小满、阿澈", useUnmergedTree = true).assertExists()
    }

    private fun assertNoLine() {
        compose.onNodeWithText("今天的晚霞像橘子汽水", useUnmergedTree = true).assertExists() // 正向证据：卡片确已渲染
        compose.onAllNodesWithText("提醒了", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun 卡片_提醒两位跳过已删() { showCard(userPost("c1", "ghost", "c2")); assertLineShown() }
    @Test fun 卡片_没提醒不出现() { showCard(userPost()); assertNoLine() }
    @Test fun 卡片_AI帖不出现() { showCard(aiPost); assertNoLine() }
    @Test fun 详情_提醒两位跳过已删() { showDetail(userPost("c1", "ghost", "c2")); assertLineShown() }
    @Test fun 详情_没提醒不出现() { showDetail(userPost()); assertNoLine() }
    @Test fun 详情_AI帖不出现() { showDetail(aiPost); assertNoLine() }
}
