package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*
import org.junit.Assert.*
import org.junit.Test

class PosTrainingLogicTest {
    private val n = setOf(PartOfSpeech.NOUN)
    private val nv = setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB)
    private val unknown = setOf(PartOfSpeech.UNKNOWN)

    private fun drill(word: String = "impact", parts: Set<PartOfSpeech> = nv) = PosExercise(
        "pos-word:$word", PosMode.DRILL, word, "CET4", word, word, word, parts, explanation = "影响")

    private fun cet(): PosExercise {
        val candidates = listOf(
            PosCandidate("A", "development", n, "word-a"),
            PosCandidate("B", "impact", nv, "word-b"),
            PosCandidate("C", "risk", nv, "word-c"),
        )
        return PosExercise("exercise:stable", PosMode.CET4, null, "CET4",
            "The rapid {{1}} of technology changed lives. These {{2}} remain.",
            correctParts = emptySet(), explanation = "练习", candidates = candidates,
            blanks = listOf(
                PosBlank(1, "A", PartOfSpeech.NOUN, "the + adjective + blank + of", "发展", mapOf("B" to PosErrorType.MEANING_WRONG, "C" to PosErrorType.COLLOCATION_WRONG)),
                PosBlank(2, "C", PartOfSpeech.NOUN, "these + blank", "风险", mapOf("A" to PosErrorType.MEANING_WRONG, "B" to PosErrorType.CONTEXT_WRONG)),
            ))
    }

    @Test fun impactAndRiskHaveBothPartsAndUnknownIsNeverAnAnswer() {
        listOf("impact", "risk").forEach { word ->
            val exercise = drill(word)
            assertTrue(PosPractice.attempt(exercise, "loose", 0, n, false, 1000).correct)
            assertTrue(PosPractice.attempt(exercise, "loose", 0, setOf(PartOfSpeech.VERB), false, 1000).correct)
            assertFalse(PosPractice.attempt(exercise, "complete", 0, n, true, 1000).correct)
            assertTrue(PosPractice.attempt(exercise, "complete", 0, nv, true, 1000).correct)
            assertFalse(PosPractice.attempt(exercise, "unknown", 0, unknown, false, 1000).correct)
            assertFalse(PosPractice.judge(unknown, unknown, false))
        }
    }

    @Test fun firstStageAllowsUnclassifiedAndUncertainWordsToContinue() {
        val progress = Cet4Training.classificationProgress(listOf("A", "B", "C", "D"),
            mapOf("A" to nv, "B" to unknown, "D" to n))
        assertEquals(2, progress.classified)
        assertEquals(1, progress.uncertain)
        assertEquals(1, progress.unprocessed)
        assertEquals(4, progress.total)
        assertTrue(progress.canContinue)
        assertTrue(Cet4Training.classificationProgress(listOf("A", "B"), emptyMap()).canContinue)
    }

    @Test fun resumedRevisionsKeepFirstAnswerAndRecordedActiveTime() {
        val q = cet()
        val slot = Cet4Training.slotExercise(q, q.blanks.first())
        val oldSlot = PosPractice.attempt(slot, "paused", 25, unknown, false, 5000)
        val updatedSlot = PosPractice.attempt(slot, "paused", 25, n, false, 1000, oldSlot)
        assertEquals(5000L, updatedSlot.activeTimeMs)
        assertEquals(oldSlot.firstAnswer, updatedSlot.firstAnswer)
        val oldChoice = PosPractice.cetAttempt(q, q.blanks.first(), "paused", "B", nv, 6000)
        val updatedChoice = PosPractice.cetAttempt(q, q.blanks.first(), "paused", "A", n, 1000, oldChoice)
        assertEquals(6000L, updatedChoice.activeTimeMs)
        assertEquals(oldChoice.firstAnswer, updatedChoice.firstAnswer)
        assertFalse(updatedChoice.firstAnswerCorrect)
    }

    @Test fun candidateFilterOnlyKnowsUserClassificationAndIds() {
        // The answer model is intentionally absent from this API. Even a mistaken noun
        // classification must place B in N, rather than silently using its real part.
        val ids = listOf("A", "B", "C", "D")
        val ownChoices = mapOf("A" to nv, "B" to n, "C" to unknown)
        assertEquals(ids, Cet4Training.filterCandidates(ids, ownChoices))
        assertEquals(listOf("A", "B"), Cet4Training.filterCandidates(ids, ownChoices, PartOfSpeech.NOUN))
        assertEquals(listOf("A"), Cet4Training.filterCandidates(ids, ownChoices, PartOfSpeech.VERB))
        assertEquals(listOf("C", "D"), Cet4Training.filterCandidates(ids, ownChoices, PartOfSpeech.UNKNOWN))
        assertEquals(emptyList<String>(), Cet4Training.filterCandidates(ids, ownChoices, PartOfSpeech.ADVERB))
    }

    @Test fun derivedSlotIdIsStableAndNoOtherAnswerIsDisclosed() {
        val q = cet()
        val slot = Cet4Training.slotExercise(q, q.blanks.first())
        assertEquals("cet4-slot-pos:exercise:stable:1", slot.id)
        assertEquals(slot, Cet4Training.slotExercise(q, q.blanks.first()))
        assertEquals(PosMode.SLOT, slot.mode)
        assertEquals(n, slot.correctParts)
        assertEquals("word-a", slot.sourceWordId)
        assertTrue(slot.sentence.contains("______"))
        assertTrue(slot.sentence.contains("〔第2空〕"))
        assertFalse(slot.sentence.contains("risk"))
        assertFalse(slot.sentence.contains("development"))
        assertTrue(slot.targetWord.isEmpty())
        assertTrue(slot.lemma.isEmpty())
        assertNotEquals(slot.id, Cet4Training.slotExercise(q, q.blanks.last()).id)
    }

    @Test fun slotAndWordAndCetSelectionHaveIndependentStatistics() {
        val q = cet()
        val word = PosPractice.attempt(drill(), "same-session", 0, n, false, 1000)
        val slot = PosPractice.attempt(Cet4Training.slotExercise(q, q.blanks.first()), "same-session", 0,
            setOf(PartOfSpeech.ADVERB), false, 2000)
        val selection = PosPractice.cetAttempt(q, q.blanks.first(), "same-session", "B", nv, 3000,
            slotSelected = setOf(PartOfSpeech.ADVERB))
        val stats = PosAnalytics.calculate(listOf(word, slot, selection))
        assertEquals(1, stats.wordRecognition.count)
        assertEquals(1.0, stats.wordRecognition.accuracy, 0.0)
        assertEquals(1, stats.slotRecognition.count)
        assertEquals(0.0, stats.slotRecognition.accuracy, 0.0)
        assertEquals(1, stats.cetSelection.count)
        assertEquals(0.0, stats.cetSelection.accuracy, 0.0)
        assertEquals(2, stats.judgmentCount)
        assertEquals(2, stats.rates.getValue(PartOfSpeech.NOUN).count)
        assertEquals(1, stats.confusion[PartOfSpeech.NOUN to PartOfSpeech.ADVERB])
        assertEquals(PosErrorType.POS_WRONG, selection.errorType)
        assertTrue(selection.explanation.orEmpty().contains("空位词性判断错误"))
    }

    @Test fun meaningErrorsNeverPollutePosAccuracyOrConfusion() {
        val q = cet()
        val word = PosPractice.attempt(drill(), "word-session", 0, n, false, 2000)
        val semantic = PosPractice.cetAttempt(q, q.blanks.first(), "cet-session", "B", nv, 3000, slotSelected = n)
        assertEquals(PosErrorType.MEANING_WRONG, semantic.errorType)
        val stats = PosAnalytics.calculate(listOf(word, semantic))
        assertEquals(1.0, stats.rates.getValue(PartOfSpeech.NOUN).accuracy, 0.0)
        assertEquals(1, stats.rates.getValue(PartOfSpeech.NOUN).count)
        assertTrue(stats.confusion.isEmpty())
        assertTrue(semantic.explanation.orEmpty().contains("空位词性和候选词词性均已识别"))
    }

    @Test fun wrongSlotAndCandidateAreExplainedSeparatelyFromFinalSelection() {
        val q = cet()
        val blank = q.blanks.first()
        val candidateWrong = PosPractice.cetAttempt(q, blank, "candidate", "B", n, 2000, slotSelected = n)
        assertEquals(PosErrorType.MULTI_POS_CONFUSION, candidateWrong.errorType)
        assertTrue(candidateWrong.explanation.orEmpty().contains("候选词词性判断错误"))
        val correctWordWrongSlot = PosPractice.cetAttempt(q, blank, "selection", "A", n, 2000, slotSelected = unknown)
        assertTrue(correctWordWrongSlot.correct)
        assertNull(correctWordWrongSlot.errorType)
        assertTrue(correctWordWrongSlot.explanation.orEmpty().contains("空位词性判断错误"))
    }

    @Test fun omissionExcludesCorrectLooseAnswersUnknownAndUnrelatedAnswers() {
        val q = drill()
        val loose = PosPractice.attempt(q, "loose", 0, n, false, 1000)
        val complete = PosPractice.attempt(q, "complete", 0, n, true, 1000)
        val uncertain = PosPractice.attempt(q, "uncertain", 0, unknown, true, 1000)
        val unrelated = PosPractice.attempt(q, "unrelated", 0, setOf(PartOfSpeech.ADVERB), true, 1000)
        val blank = PosPractice.attempt(q, "blank", 0, emptySet(), true, 1000)
        val stats = PosAnalytics.calculate(listOf(loose, complete, uncertain, unrelated, blank))
        assertEquals(5, stats.multiPosCount)
        assertEquals(1, stats.multiPosOmissionCount)
        assertEquals(0.2, stats.multiPosOmissionRate, 0.0)
        assertEquals(1, stats.uncertainCount)
        assertEquals(5, stats.judgmentCount)
        assertEquals(0.2, stats.uncertainRate, 0.0)
    }

    @Test fun latestAnswerWinsEvenWhenTwoChangesHaveSameTimestamp() {
        val wrong = PosPractice.attempt(drill(), "session", 0, unknown, false, 1000).copy(timestamp = 10)
        val right = PosPractice.attempt(drill(), "session", 0, n, false, 2000, wrong).copy(timestamp = 10)
        val stats = PosAnalytics.calculate(listOf(wrong, right))
        assertEquals(1, stats.wordRecognition.count)
        assertEquals(1.0, stats.wordRecognition.accuracy, 0.0)
        assertEquals(0, stats.uncertainCount)
    }

    @Test fun originalPosJsonStillDecodesWithoutNewFields() {
        val original = """{"id":"pos-word:impact","mode":"DRILL","sourceWordId":"impact","sourceTag":"CET4","sentence":"impact","targetWord":"impact","lemma":"impact","parts":"NOUN,VERB","grammarClue":"","explanation":"影响","difficulty":"CET4","candidates":[],"blanks":[]}"""
        val decoded = PosCodec.decode(original)
        assertEquals(nv, decoded.correctParts)
        assertEquals(PosMode.DRILL, decoded.mode)
        assertEquals(decoded, PosCodec.decode(PosCodec.encode(decoded)))
    }
}
