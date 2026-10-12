package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PosPracticeTest {
    private val nv = setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB)
    private fun word(id: String = "increase", parts: Set<PartOfSpeech> = nv) = WordEntry(
        id, null, id, null, "增加", "CET4", 0, 0, 1, 2.5, 0, 0, false, parts)

    @Test fun serializationAllowsMultiplePartsAndUnknown() {
        assertEquals(nv, PartOfSpeech.decode(PartOfSpeech.encode(nv)))
        assertEquals(setOf(PartOfSpeech.UNKNOWN), PartOfSpeech.decode(""))
        assertEquals(setOf(PartOfSpeech.UNKNOWN), PartOfSpeech.decode(null))
        assertEquals(setOf(PartOfSpeech.UNKNOWN), word(parts = setOf(PartOfSpeech.UNKNOWN)).partsOfSpeech)
        assertFalse(PosPractice.eligible(word(parts = setOf(PartOfSpeech.UNKNOWN))))
    }
    @Test fun looseAndCompleteJudgingDifferForIncrease() {
        assertTrue(PosPractice.judge(setOf(PartOfSpeech.NOUN), nv, false))
        assertTrue(PosPractice.judge(setOf(PartOfSpeech.VERB), nv, false))
        assertFalse(PosPractice.judge(setOf(PartOfSpeech.NOUN), nv, true))
        assertTrue(PosPractice.judge(nv, nv, true))
        assertFalse(PosPractice.judge(emptySet(), nv, false))
        assertFalse(PosPractice.judge(setOf(PartOfSpeech.ADVERB), nv, false))
    }
    @Test fun explicitTranslationMarkersAreAcceptedButSuffixesAreNotAnswers() {
        assertEquals(nv, PosPractice.translationParts("n.增加 v.增长"))
        assertEquals(setOf(PartOfSpeech.ADJECTIVE, PartOfSpeech.ADVERB), PosPractice.translationParts("adj.有效 adv.逐渐"))
        assertEquals(setOf(PartOfSpeech.UNKNOWN), PosPractice.translationParts("development gradually"))
    }
    @Test fun annotationsRejectInventedWordsBadEnumDuplicateAndConfidence() {
        val valid = """{"words":[{"word":"increase","pos":["NOUN","VERB"],"confidence":0.98}]}"""
        assertEquals(nv, PosPractice.parseAnnotations(valid, listOf(word())).single().parts)
        rejected { PosPractice.parseAnnotations(valid.replace("increase", "banana"), listOf(word())) }
        rejected { PosPractice.parseAnnotations(valid.replace("VERB", "ADJ"), listOf(word())) }
        rejected { PosPractice.parseAnnotations(valid.replace("0.98", "1.2"), listOf(word())) }
        rejected { PosPractice.parseAnnotations("```json\n$valid\n```", listOf(word())) }
        rejected { PosPractice.parseAnnotations(valid + "{}", listOf(word())) }
        rejected { PosPractice.parseAnnotations(valid.replace("\"words\"", "words"), listOf(word())) }
        rejected { PosPractice.parseAnnotations("""{"words":[]} """, listOf(word())) }
        val repeated = JSONObject(valid).getJSONArray("words").getJSONObject(0)
        rejected { PosPractice.parseAnnotations(JSONObject().put("words", JSONArray().put(repeated).put(repeated)).toString(), listOf(word())) }
    }
    @Test fun contextAndSlotValidateTargetAndUniquePos() {
        val body = """{"questions":[{"sourceWordId":"increase","lemma":"increase","targetWord":"increase","sentence":"The increase in prices affected families.","correctPos":"NOUN","possiblePos":["NOUN"],"grammarClue":"冠词后作名词","explanation":"价格上涨"}]}"""
        assertEquals(PosMode.CONTEXT, PosPractice.parseQuestions(body, PosMode.CONTEXT, listOf(word())).single().mode)
        rejected { PosPractice.parseQuestions(body.replace("The increase in prices", "The change in prices"), PosMode.CONTEXT, listOf(word())) }
        rejected { PosPractice.parseQuestions(body.replace("[\"NOUN\"]", "[\"NOUN\",\"VERB\"]"), PosMode.CONTEXT, listOf(word())) }
        val slot = body.replace("The increase in prices affected families.", "The rapid ______ in prices affected families.")
        assertEquals(setOf(PartOfSpeech.NOUN), PosPractice.parseQuestions(slot, PosMode.SLOT, listOf(word())).single().correctParts)
        rejected { PosPractice.parseQuestions(slot.replace("______", "______ ______"), PosMode.SLOT, listOf(word())) }
        rejected { PosPractice.parseQuestions(slot.replace("[\"NOUN\"]", "[\"NOUN\",\"VERB\"]"), PosMode.SLOT, listOf(word())) }
    }
    @Test fun practiceAttemptWrongResultAndConfusionUseActualAndSelectedParts() {
        val adj = PosPractice.drill(word("effective", setOf(PartOfSpeech.ADJECTIVE)))
        val a = PosPractice.attempt(adj, "session", 0, setOf(PartOfSpeech.ADVERB), false, 2200)
        assertFalse(a.correct); assertEquals(PosErrorType.POS_WRONG, a.errorType)
        assertEquals(PracticeAttemptMode.WORD_POS_CLASSIFICATION.name, PosPractice.wrongResult(a).category)
        val stats = PosAnalytics.calculate(listOf(a))
        assertEquals(1, stats.confusion[PartOfSpeech.ADJECTIVE to PartOfSpeech.ADVERB])
        assertEquals(1, stats.rates.getValue(PartOfSpeech.ADJECTIVE).count)
        assertEquals(0.0, stats.rates.getValue(PartOfSpeech.ADJECTIVE).within3, 0.0)
        val corrected = PosPractice.attempt(adj, "session", 0, setOf(PartOfSpeech.ADJECTIVE), false, 4200, a)
        assertEquals(a.firstAnswer, corrected.firstAnswer); assertFalse(corrected.firstAnswerCorrect)
        assertEquals(1, PosAnalytics.calculate(listOf(a, corrected)).rates.getValue(PartOfSpeech.ADJECTIVE).count)
    }
    @Test fun mixedPosAllocatesTwentyPercentAndFallsBackWhenInsufficient() {
        val words = (1..20).map { word("word$it", if (it <= 2) nv else setOf(PartOfSpeech.UNKNOWN)) }
        val enabled = setOf(MixedReviewMode.DICTATION, MixedReviewMode.POS)
        val plan = MixedReviewPlanner.plan(words, setOf("CET4"), 20, enabled,
            mapOf(MixedReviewMode.DICTATION to 80, MixedReviewMode.POS to 20), Random(1))
        assertEquals(20, plan.size); assertEquals(2, plan.count { it.mode == MixedReviewMode.POS })
        assertEquals(20, plan.map { it.itemId }.distinct().size)
        assertEquals(4, MixedReviewPlanner.allocateCounts(20, enabled, mapOf(MixedReviewMode.DICTATION to 80, MixedReviewMode.POS to 20))[MixedReviewMode.POS])
        val none = MixedReviewPlanner.plan(words.map { it.copy(partsOfSpeech = setOf(PartOfSpeech.UNKNOWN)) }, setOf("CET4"), 20, enabled,
            mapOf(MixedReviewMode.DICTATION to 80, MixedReviewMode.POS to 20), Random(1))
        assertEquals(20, none.count { it.mode == MixedReviewMode.DICTATION })
    }
    private fun cetFixture(): Pair<String, List<WordEntry>> {
        val words = ('a'..'o').mapIndexed { i, c -> word("word$c", setOf(PartOfSpeech.core[i % 4])) }
        val candidates = JSONArray().apply { words.forEachIndexed { i, w -> put(JSONObject().put("id", ('A' + i).toString()).put("word", w.spelling).put("sourceWordId", w.id).put("pos", JSONArray(w.partsOfSpeech.map { it.name }))) } }
        val blanks = JSONArray().apply { repeat(10) { i ->
            val answer = ('A' + i).toString(); val pos = PartOfSpeech.core[i % 4]
            put(JSONObject().put("index", i + 1).put("answerId", answer).put("correctPos", pos.name).put("possiblePos", JSONArray().put(pos.name))
                .put("grammarClue", "句法依据").put("explanation", "语境解释").put("distractorErrors", JSONObject().apply { ('A'..'O').filter { it.toString() != answer }.forEach { put(it.toString(), "MEANING_WRONG") } }))
        } }
        return JSONObject().put("passage", (1..10).joinToString(" ") { "Sentence {{$it}}." }).put("candidates", candidates).put("blanks", blanks).toString() to words
    }

    @Test fun mixedPosAndSemanticRespectBothPoolCapacities() {
        val words = (1..20).map { word("word$it", if (it <= 10) nv else setOf(PartOfSpeech.UNKNOWN)) }
        val seeds = (1..2).map { ParaphraseSeed("seed$it", null, "source$it", "target$it", null, "CET4", null, 0, 0, false) }
        val plan = MixedReviewPlanner.plan(words, setOf("CET4"), 30, setOf(MixedReviewMode.POS, MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH),
            mapOf(MixedReviewMode.POS to 50, MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH to 50), Random(1), seeds)
        assertEquals(12, plan.size); assertEquals(10, plan.count { it.mode == MixedReviewMode.POS })
        assertEquals(2, plan.count { it.mode == MixedReviewMode.ENGLISH_DEFINITION_TO_ENGLISH })
    }
    @Test fun cet4ParsesAndCachesWithoutLosingMetadata() {
        val (text, words) = cetFixture()
        val q = PosPractice.parseCet4(text, words, "CET4")
        assertEquals(15, q.candidates.size); assertEquals(10, q.blanks.size)
        assertEquals(q, PosCodec.decode(PosCodec.encode(q)))
        val parsed = PosCodec.toQuiz(q, 0)
        val original = QuizQuestion("question", "bank", 0, parsed.score, parsed.text, parsed.options, parsed.answers,
            explanation = parsed.explanation, category = parsed.category, sourceReference = parsed.sourceReference)
        val output = java.io.ByteArrayOutputStream()
        QuizXmlWriter.write(listOf(original), output, "CET4")
        val imported = QuizXmlParser.parse(output.toByteArray().inputStream(), "CET4.xml").questions.single()
        assertEquals("POS_JSON:" + PosCodec.encode(q), imported.sourceReference)
    }
    @Test fun cet4RejectsDuplicateCandidateAndReusedAnswer() {
        val (text, words) = cetFixture()
        val duplicate = JSONObject(text)
        duplicate.getJSONArray("candidates").getJSONObject(1).put("word", "worda")
        rejected { PosPractice.parseCet4(duplicate.toString(), words, "CET4") }
        val reused = JSONObject(text)
        reused.getJSONArray("blanks").getJSONObject(4).put("answerId", "A")
        rejected { PosPractice.parseCet4(reused.toString(), words, "CET4") }
        rejected { PosPractice.assign(mapOf(1 to "A"), 2, "A") }
        assertEquals(mapOf(1 to "B"), PosPractice.assign(mapOf(1 to "A"), 1, "B"))
    }
    @Test fun cetErrorDistinguishesPosAndMeaningAndMultiplePos() {
        val blank = PosBlank(1, "A", PartOfSpeech.ADJECTIVE, "修饰名词", "有效", mapOf("B" to PosErrorType.MEANING_WRONG))
        val adj = PosCandidate("B", "different", setOf(PartOfSpeech.ADJECTIVE), null)
        assertEquals(PosErrorType.MEANING_WRONG, PosPractice.cetError(blank, adj, setOf(PartOfSpeech.ADJECTIVE)))
        assertEquals(PosErrorType.POS_WRONG, PosPractice.cetError(blank, adj, setOf(PartOfSpeech.NOUN)))
        assertEquals(PosErrorType.MULTI_POS_CONFUSION, PosPractice.cetError(blank, adj.copy(parts = setOf(PartOfSpeech.NOUN, PartOfSpeech.ADJECTIVE)), setOf(PartOfSpeech.NOUN)))
    }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected invalid data to be rejected") } catch (e: IllegalArgumentException) { } catch (e: IllegalStateException) { } }
}
