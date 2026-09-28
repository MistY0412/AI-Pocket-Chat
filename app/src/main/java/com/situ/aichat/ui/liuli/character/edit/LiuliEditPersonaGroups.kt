package com.situ.aichat.ui.liuli.character.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.situ.aichat.R
import com.situ.aichat.data.model.PersonaCompileMeta
import com.situ.aichat.data.model.PersonalitySpectrum
import com.situ.aichat.data.model.RelationshipQuality
import com.situ.aichat.data.model.personaCurrentMarkerVisible
import com.situ.aichat.ui.character.CharacterEditState
import com.situ.aichat.ui.character.OfflineThemePresets
import com.situ.aichat.ui.designsystem.AppTheme
import com.situ.aichat.ui.designsystem.AppTypography
import com.situ.aichat.ui.liuli.designsystem.LiuliButton
import com.situ.aichat.ui.liuli.designsystem.LiuliButtonStyle
import com.situ.aichat.ui.liuli.designsystem.LiuliChip
import com.situ.aichat.ui.liuli.designsystem.LiuliMaterials
import com.situ.aichat.ui.liuli.designsystem.LiuliSlider
import com.situ.aichat.ui.liuli.designsystem.LiuliSpinner
import com.situ.aichat.ui.liuli.page.LiuliGroup
import com.situ.aichat.ui.liuli.page.LiuliPageGeometry
import com.situ.aichat.ui.liuli.page.LiuliRowBase
import com.situ.aichat.ui.liuli.page.LiuliSliderRow
import com.situ.aichat.ui.offline.parseHexColorOrNull
import com.situ.aichat.ui.theme.LocalIsDarkTheme
import kotlin.math.roundToInt

/** 琉璃滑杆拇指直径——**必须与 `LiuliSlider.kt` 的 `THUMB = 20.dp` 同值**（「现在」竖线按同一几何量定位·§4.5）。 */
internal val LIULI_THUMB = 20.dp
/** 「现在」竖线：宽 2 · 高 8（§4.5 锁定值·= 暖陶同几何）。 */
private val MARKER_WIDTH = 2.dp
private val MARKER_HEIGHT = 8.dp
/** 「现在」竖线的测试标记（生产期零影响·竖线自身清空语义，不打标就量不到它的位置）。 */
internal const val LIULI_NOW_MARKER_TAG = "liuliNowMarker"
/** 线下主题色圆 36 · 选中环 2（§4.7 锁定值）。 */
private val SWATCH = 36.dp
private val SWATCH_RING = 2.dp

/** 性格光谱组（§4.5）：仅编辑的生成行 + 8 根本性滑杆（带只读「现在」竖线）。 */
@Composable
internal fun LiuliEditSpectrumGroup(
    state: CharacterEditState,
    isEditing: Boolean,
    compiling: Boolean,
    personaNeedsSave: Boolean,
    onCompile: () -> Unit,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_personality),
        footer = stringResource(R.string.char_footer_personality_spectrum),
    ) {
        // 生成行只在编辑态露面：新建时角色还没 uuid、编译无从谈起（同暖陶 D-1/Y-E21）。
        if (isEditing) {
            LiuliPersonaCompileRow(
                meta = state.personaCompileMeta,
                personaStale = state.personaStale,
                personaBlank = state.personalityDescription.isBlank(),
                compiling = compiling,
                needsSave = personaNeedsSave,
                onCompile = onCompile,
            )
        }
        val hints = stringArrayResource(R.array.char_personality_hints)
        PersonalitySpectrum.DIMENSION_NAMES.forEachIndexed { i, dim ->
            LiuliAnchorSliderRow(
                name = dim,
                hint = hints.getOrElse(i) { "" },
                anchor = state.personalityAnchor.values[i],
                current = state.personalitySpectrum.values[i],
                basis = state.personaCompileMeta.anchorBasis[PersonalitySpectrum.DIMENSION_KEYS[i]],
                onChange = { v -> onUpdate { it.copy(personalityAnchor = it.personalityAnchor.setValue(i, v)) } },
                divider = i > 0 || isEditing,
            )
        }
    }
}

/** 生成行（§4.5-1）：标题 / 说明 + 玻璃钮（三态文案同暖陶）+ 三条件提示条（同暖陶条件、顺序、资源）。 */
@Composable
internal fun LiuliPersonaCompileRow(
    meta: PersonaCompileMeta,
    personaStale: Boolean,
    personaBlank: Boolean,
    compiling: Boolean,
    needsSave: Boolean,
    onCompile: () -> Unit,
) {
    val colors = AppTheme.colors
    val neverCompiled = meta.source == PersonaCompileMeta.SOURCE_DEFAULT
    LiuliRowBase(divider = false, minHeight = 0.dp, verticalPadding = LiuliPageGeometry.groupPadH, verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (neverCompiled) R.string.persona_compile_title_idle else R.string.persona_compile_title_done),
                        style = AppTypography.body.copy(fontWeight = FontWeight.W600),
                        color = if (neverCompiled) colors.text.primary else colors.text.secondary,
                    )
                    if (neverCompiled) {
                        Text(stringResource(R.string.persona_compile_desc), style = AppTypography.caption, color = colors.text.secondary)
                    }
                }
                LiuliButton(onClick = onCompile, style = LiuliButtonStyle.Glass, enabled = !personaBlank && !compiling) {
                    if (compiling) LiuliSpinner()
                    Text(
                        stringResource(
                            when {
                                compiling -> R.string.persona_compile_button_running
                                neverCompiled -> R.string.persona_compile_button
                                else -> R.string.persona_compile_button_again
                            },
                        ),
                    )
                }
            }
            if (needsSave) {
                CompileHint(stringResource(R.string.persona_compile_needs_save_hint), colors.status.warningContainer, colors.status.onWarning)
            }
            if (personaStale) {
                CompileHint(stringResource(R.string.persona_compile_stale_hint), colors.status.warningContainer, colors.status.onWarning)
            }
            if (meta.lastFailedAt > meta.compiledAt) {
                CompileHint(stringResource(R.string.persona_compile_failed_hint), colors.status.errorContainer, colors.status.onError)
            } else if (meta.droppedCount > 0) {
                Text(
                    stringResource(R.string.persona_compile_dropped_hint, meta.droppedCount),
                    style = AppTypography.caption,
                    color = colors.text.secondary,
                )
            }
        }
    }
}

@Composable
private fun CompileHint(text: String, container: Color, content: Color) {
    Text(
        text,
        style = AppTypography.caption,
        color = content,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 竖线 / 「现在 N」标签的水平中心：拇指半径 + (宽 − 拇指) × 比例（= `LiuliSlider` 拇指中心算式）。 */
internal fun liuliMarkerX(width: Dp, current: Int): Dp =
    LIULI_THUMB / 2 + (width - LIULI_THUMB) * (current.coerceIn(0, 100) / 100f)

/** 本性滑杆行（§4.5-2）：可拖的是本性 [anchor]；[current]（现在）只作一条只读竖线，偏移 ≤ 5 整条隐藏。 */
@Composable
internal fun LiuliAnchorSliderRow(
    name: String,
    hint: String,
    anchor: Int,
    current: Int,
    basis: String?,
    onChange: (Int) -> Unit,
    divider: Boolean,
) {
    val colors = AppTheme.colors
    val markerVisible = personaCurrentMarkerVisible(anchor = anchor, current = current)
    LiuliRowBase(divider = divider, minHeight = 0.dp, verticalPadding = 10.dp, verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = AppTypography.body.copy(fontSize = 15.sp), color = colors.text.primary, modifier = Modifier.weight(1f))
                Text("$anchor", style = AppTypography.captionNumeric.copy(fontSize = 15.sp), color = colors.text.secondary)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                LiuliSlider(
                    value = anchor.toFloat(),
                    onValueChange = { onChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    // 无障碍：滑杆焦点播报「维度名, 数值」（同暖陶口径）。
                    modifier = Modifier.semantics { contentDescription = name },
                )
                if (markerVisible) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = liuliMarkerX(maxWidth, current) - MARKER_WIDTH / 2)
                            .size(width = MARKER_WIDTH, height = MARKER_HEIGHT)
                            .clip(RoundedCornerShape(50))
                            .background(colors.text.tertiary)
                            .testTag(LIULI_NOW_MARKER_TAG)
                            // 只读：不接手势，也不进无障碍焦点（免得与滑杆抢焦点）。
                            .clearAndSetSemantics {},
                    )
                }
            }
            if (markerVisible) CurrentMarkerLabel(current)
            if (!basis.isNullOrBlank()) {
                // 中文无斜体：引文走楷体点缀（设计语言 §2）。
                Text(stringResource(R.string.persona_anchor_basis, basis), style = AppTypography.kaiQuote, color = colors.text.secondary)
            }
            Text(hint, style = AppTypography.caption, color = colors.text.secondary)
        }
    }
}

/** 「现在 N」标签：水平与竖线对齐，贴边时向内收（同暖陶 `CurrentMarkerLabel` 算法·按琉璃拇指 20 定位）。 */
@Composable
private fun CurrentMarkerLabel(current: Int) {
    val density = LocalDensity.current
    var labelWidth by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rawX = liuliMarkerX(maxWidth, current) - labelWidth / 2
        val clampedX = rawX.coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
        Text(
            stringResource(R.string.persona_anchor_current_marker, current),
            style = AppTypography.caption,
            color = AppTheme.colors.text.secondary,
            modifier = Modifier
                .offset(x = clampedX)
                .onSizeChanged { labelWidth = with(density) { it.width.toDp() } },
        )
    }
}

/** 关系质感组（§4.6）：8 根 0–100 滑杆行，端点说明走「活例子」句。 */
@Composable
internal fun LiuliEditRelationshipQualityGroup(
    state: CharacterEditState,
    onUpdate: ((CharacterEditState) -> CharacterEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_relationship),
        footer = stringResource(R.string.char_footer_relationship),
    ) {
        val hints = stringArrayResource(R.array.char_relationship_hints)
        RelationshipQuality.DIMENSION_NAMES.forEachIndexed { i, dim ->
            val v = state.relationshipQuality.values[i]
            LiuliSliderRow(
                title = dim,
                valueLabel = "$v",
                value = v.toFloat(),
                onValueChange = { f -> onUpdate { it.copy(relationshipQuality = it.relationshipQuality.setValue(i, f.roundToInt())) } },
                valueRange = 0f..100f,
                example = hints.getOrElse(i) { "" },
                divider = i > 0,
            )
        }
    }
}

/** 线下主题色组（§4.7）：「默认」胶囊 + 8 枚预设色圆（单选语义·以序号命名）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiuliEditOfflineThemeGroup(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dark = LocalIsDarkTheme.current
    LiuliGroup(
        modifier = modifier,
        header = stringResource(R.string.char_section_offline_theme),
        footer = stringResource(R.string.char_footer_offline_theme),
    ) {
        LiuliRowBase(divider = false, minHeight = 0.dp, verticalPadding = 12.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiuliChip(selected = selectedHex.isBlank(), onClick = { onSelect("") }, label = stringResource(R.string.char_theme_default))
                OfflineThemePresets.forEachIndexed { i, preset ->
                    val isSelected = selectedHex.equals(preset, ignoreCase = true)
                    val desc = stringResource(R.string.char_theme_swatch_desc, i + 1)
                    Box(
                        Modifier
                            .size(SWATCH)
                            .then(if (isSelected) Modifier.border(SWATCH_RING, colors.accent.text, CircleShape).padding(SWATCH_RING + 2.dp) else Modifier)
                            .clip(CircleShape)
                            .background(parseHexColorOrNull(preset) ?: LiuliMaterials.segTrack(dark))
                            .border(1.dp, LiuliMaterials.cardRim(dark), CircleShape)
                            .clickable { onSelect(preset) }
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                                contentDescription = desc
                            },
                    )
                }
            }
        }
    }
}
