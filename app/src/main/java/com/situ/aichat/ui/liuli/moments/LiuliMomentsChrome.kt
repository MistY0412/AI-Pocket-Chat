package com.situ.aichat.ui.liuli.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.MomentPostWithRelations
import com.situ.aichat.ui.liuli.designsystem.LiuliAvatarRing
import com.situ.aichat.ui.liuli.designsystem.LiuliAvatarRingWidth
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliShapes
import com.situ.aichat.ui.liuli.designsystem.LiuliSpinner
import com.situ.aichat.ui.liuli.designsystem.liuliCardMaterial
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.moments.MomentPostCard
import com.situ.aichat.ui.theme.LocalIsDarkTheme

/** 卡间距（= 暖陶信息流卡间距）。 */
internal val MOMENT_CARD_GAP = 12.dp

/** 动态卡头像（= 暖陶卡头像 34·光环外径 38）。 */
private val MOMENT_CARD_AVATAR = 34.dp

/** 圈子屏底渐进带（= 发动态钮距导航栏 24 + 钮高 56 + 尾巴 12 = 92）。 */
internal val MOMENT_FEED_BOTTOM_EDGE = LiuliPageGeometry.fabBottom + LiuliPageGeometry.fab + LiuliPageGeometry.edgeTail

/** 详情屏底渐进带（= 评论条离底 12 + 条高 56 + 尾巴 12 = 80）。 */
internal val MOMENT_DETAIL_BOTTOM_EDGE = LiuliPageGeometry.floatingBarGap + LiuliPageGeometry.floatingBar + LiuliPageGeometry.edgeTail

/** 下拉刷新转圈直径（= 暖陶陶环 Medium 直径）。 */
private val REFRESH_SPINNER = 24.dp

/** 转圈顶（= 导航行 44 + 2 + (40 − 24) / 2 = 54·竖向居中在大标题带里，避开顶部渐进带那 46）。 */
private val REFRESH_SPINNER_TOP = LiuliPageGeometry.navRow + LiuliPageGeometry.titleTop + (LiuliPageGeometry.titleHeight - REFRESH_SPINNER) / 2

/** 刷新转圈的测试标记（生产期零影响）。 */
internal const val LIULI_MOMENTS_REFRESH_TAG = "liuliMomentsRefresh"

/** 动态卡 / 横幅 / 正文卡的卡面：琉璃半透明卡（圆角 20 = 动态枢纽卡 `LiuliHubCard` 同值）。 */
internal fun Modifier.liuliMomentCard(dark: Boolean): Modifier = liuliCardMaterial(LiuliShapes.medium, dark)

/** 评论小笺 / 点赞名单的底（设计稿 `.note2` = `--track`·圆角 10 = `LiuliShapes.small`）。 */
internal fun Modifier.liuliMomentNote(dark: Boolean): Modifier = clip(LiuliShapes.small).background(LiuliMaterials.segTrack(dark))

/** 头像光环（设计稿 M1「头像带光环」）：外径 = [avatarSize] + 2 × [LiuliAvatarRingWidth]，头像原尺寸压在正中。 */
@Composable
internal fun LiuliMomentAvatarRing(avatarSize: Dp, avatar: @Composable () -> Unit) {
    LiuliAvatarRing(avatarSize + LiuliAvatarRingWidth * 2) { avatar() }
}

/**
 * 琉璃动态卡（琉璃 2.0 卷六·二 §4.1·圈子 / 作者页 / 那天页共用）：内容件就是暖陶 [MomentPostCard]，材质外给——
 * 半透明卡面、排在卡面裁切之后的点击面、`segTrack` 小笺、头像光环。
 */
@Composable
internal fun LiuliMomentCard(
    post: MomentPostWithRelations,
    characters: Map<String, CharacterEntity>,
    userName: String,
    userAvatarPath: String?,
    onToggleLike: () -> Unit,
    interaction: Modifier,
    modifier: Modifier = Modifier,
    onCharacterTap: ((String) -> Unit)? = null,
) {
    val dark = LocalIsDarkTheme.current
    MomentPostCard(
        post = post,
        characterDict = characters,
        userName = userName,
        userAvatarPath = userAvatarPath,
        onToggleLike = onToggleLike,
        modifier = modifier,
        onCharacterTap = onCharacterTap,
        surface = Modifier.liuliMomentCard(dark),
        interaction = interaction,
        noteSurface = Modifier.liuliMomentNote(dark),
        avatarFrame = { avatar -> LiuliMomentAvatarRing(MOMENT_CARD_AVATAR, avatar) },
    )
}

/** 下拉刷新转圈（`PullToRefreshBox` 的 `indicator` 槽里调）：只按 [refreshing] 显隐、不做下拉联动（同暖陶）。 */
@Composable
internal fun BoxScope.LiuliMomentsRefreshIndicator(refreshing: Boolean) {
    if (refreshing) {
        LiuliSpinner(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = REFRESH_SPINNER_TOP).testTag(LIULI_MOMENTS_REFRESH_TAG),
            size = REFRESH_SPINNER,
        )
    }
}
