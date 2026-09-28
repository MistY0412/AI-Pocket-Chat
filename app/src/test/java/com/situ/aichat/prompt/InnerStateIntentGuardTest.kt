package com.situ.aichat.prompt

import com.situ.aichat.data.model.AffectField
import com.situ.aichat.data.model.CharacterIntent
import com.situ.aichat.data.model.IntentKind
import com.situ.aichat.data.model.IntentState
import com.situ.aichat.data.model.PersonaOperator
import com.situ.aichat.data.model.RelationshipPressure
import com.situ.aichat.data.model.RelationshipQuality
import com.situ.aichat.data.model.fromQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 心事护栏 T1（微图纸 2026-09-29-内心行心事护栏 §5）：[InnerStateRenderer.renderForPrompt] 只在心事句（意图 / 已表达 / 残留）
 * 真的进了内心行时，下一行附护栏；其余情况与 [InnerStateRenderer.render] 逐字相同，且 `render`（琉璃顶栏副标的来源）永不带护栏。
 * 护栏字面在此重新打字 = 用户拍板原话（独立反推·不引用常量）；`now` 与 [InnerStateRendererTest] 同，任何时区都落变体 0。
 */
class InnerStateIntentGuardTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private val calm = RelationshipPressure.fromQuality(RelationshipQuality())

    private fun intent(kind: IntentKind, state: IntentState = IntentState.ACTIVE, strength: Int = 50, residue: Boolean = false) =
        CharacterIntent(id = kind.key, kind = kind, state = state, strength = strength, bornAt = now - 3_600_000L, lastChangeAt = now - 3_600_000L, residue = residue)

    private fun op(condition: String, action: String) = PersonaOperator(id = condition + action, condition = condition, action = action, enabled = true)

    private fun render(field: AffectField = AffectField(), operators: List<PersonaOperator> = emptyList(), intents: List<CharacterIntent> = emptyList()) =
        InnerStateRenderer.render(field, calm, operators, "小明", 15, now, intents)

    private val guard = "（这是你心底的事，不是这会儿要聊的话题：对方在聊别的，就先好好接对方的话，等话头自然碰到了再流露一点，" +
        "别硬把话题往这上面拐，也别连着几轮都绕回去；它具体指什么，以聊天记录和记忆里有的为准，别凭空编。）"

    private fun renderP(
        field: AffectField = AffectField(),
        operators: List<PersonaOperator> = emptyList(),
        userName: String = "小明",
        intents: List<CharacterIntent> = emptyList(),
    ) = InnerStateRenderer.renderForPrompt(field, calm, operators, userName, 15, now, intents)

    @Test
    fun guard_followsLine_whenWorrySentenceIsIn_activeExpressedResidue() {
        assertEquals(
            "此刻你心里：你想试探一下小明对你到底怎么想，又怕问得太直。心里堵着一股闷气，没什么耐心。\n$guard",
            renderP(field = AffectField(valence = -70), intents = listOf(intent(IntentKind.WANT_PROBE))),
        )
        assertEquals(
            "此刻你心里：你已经道过歉了，还在琢磨小明是不是真的不介意。\n$guard",
            renderP(intents = listOf(intent(IntentKind.WANT_APOLOGIZE, state = IntentState.EXPRESSED, strength = 25))),
        )
        assertEquals(
            "此刻你心里：之前那件事其实没过去，你只是没再提。\n$guard",
            renderP(intents = listOf(intent(IntentKind.WANT_HIDE, state = IntentState.FADED, strength = 8, residue = true))),
        )
    }

    @Test
    fun noGuard_whenOnlyFieldOrOperatorSentence_orNothing() {
        assertEquals("此刻你心里：心里堵着一股闷气，没什么耐心。", renderP(field = AffectField(valence = -70)))
        val opField = AffectField(hits = listOf("g04"), hitsAt = now)
        assertEquals("此刻你心里：刚被小明夸了，你嘴上会否认，行动上在意。", renderP(field = opField, operators = listOf(op("c07", "a05"))))
        assertEquals("", renderP())
        // 消退且无残留 / 残留过期 ⇒ 没有心事句 ⇒ 不带护栏
        val stale = CharacterIntent(id = "r", kind = IntentKind.WANT_HIDE, state = IntentState.FADED, strength = 8,
            bornAt = now - 10 * day, lastChangeAt = now - 8 * day, residue = true)
        assertEquals("此刻你心里：心里堵着一股闷气，没什么耐心。", renderP(field = AffectField(valence = -70), intents = listOf(stale)))
    }

    @Test
    fun noGuard_whenWorrySentenceSkippedForOverlongName_E43() {
        val longName = "张".repeat(60)
        // 想道歉句 17 + 60 = 77 > 74 ⇒ 被跳过、只剩场句 ⇒ 这一行里没有心事 ⇒ 不带护栏
        assertEquals(
            "此刻你心里：心里堵着一股闷气，没什么耐心。",
            renderP(field = AffectField(valence = -70), userName = longName, intents = listOf(intent(IntentKind.WANT_APOLOGIZE))),
        )
    }

    @Test
    fun renderForPrompt_withoutGuard_isByteIdenticalToRender_andRenderNeverCarriesGuard() {
        val field = AffectField(valence = -70, hits = listOf("g04"), hitsAt = now)
        val ops = listOf(op("c07", "a05"))
        assertEquals(render(field = field, operators = ops), renderP(field = field, operators = ops))
        // render（琉璃顶栏副标的来源）永不带护栏
        assertFalse(render(intents = listOf(intent(IntentKind.WANT_PROBE))).contains("心底的事"))
    }
}
